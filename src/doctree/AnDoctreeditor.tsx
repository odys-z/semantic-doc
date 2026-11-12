/**
 * AnDoctreeditor: the content tree of one doc, extending anreact's AnTreeditor
 * through its column formatters:
 *
 *   | indents, expand, § | content (formatter) | actions (formatter) |
 *
 * The content cell is the one ParagraphEditor (the block being edited) or a
 * StaticBlock; sections, blocks and the doc root all render through the same
 * formatters, in AnTreeditor's folder heads and TreeCards alike.
 *
 *  - The server owns the tree: every structural change is a TreeModify (u/tree) as the
 *    user means it, answered with the reloaded subtree, which replaces ours. Buttons
 *    that can't apply are greyed out by a copy of the table's rules (doc-stree Rules).
 *  - Autosave: text is sent SAVE_DELAY ms after typing stops, or at once when the
 *    user leaves the block. Commit marks the work as finished.
 *  - docref: one read-only line.
 *
 * Layout (MUI): a fixed head (error banner, toolbar, column header), the rows in a
 * scrolling body, and a snackbar for refused or failed changes. With props.preview (a Sphinx
 * template url), the doc's html preview is beside the rows (below them on narrow screens),
 * rebuilt after every load, structural change and saved text. Work at risk (a failed
 * load or save) shows in the banner with Retry; a refused change only in the snackbar.
 */
import React from 'react';
import type { AnTreeNode, TreeModify } from '@anclient/semantier';
import {
	AnTreeditorComp, AnTreegridComp, CrudCompW, type AnTreeditorProps, type AnTreegridCol,
	TreeNodeVisual, treeditorStyles, withStyles, withWidth,
} from '@anclient/anreact';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Collapse from '@mui/material/Collapse';
import Snackbar from '@mui/material/Snackbar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';

import { ParagraphEditor, StaticBlock, type JSONContent, type RstResult } from '../editor';
import {
	DocNode, DoctreeResp, type DocInf, type DocKind, type DocTreeNode, type MoveOp,
	fromTiptap, insertMod, isContainer, locate, moveMod, received, removeMod, replaced, toTiptap,
	StreeDoctier,
} from './doc-stree';
import './doctree.css';
import { assemble, type RstFile } from './rst-assembler';
import { fillTipjson } from './facts';
import { DocPreview } from '../preview/DocPreview';

/** version of the tipjson -> rstext serializer, stored in DocNode.rstver */
export const RSTVER = 1;
/** autosave delay after typing stops, ms */
export const SAVE_DELAY = 1500;
/** the synode's html build is asked every BUILD_POLL ms while building */
export const BUILD_POLL = 2000;

export interface AnDoctreeditorProps extends Partial<Omit<AnTreeditorProps, 'columns' | 'pk' | 'tier'>> {
	docId: string;
	/** requests to the doc-tree tier, e.g. a StreeDoctier */
	doctier: StreeDoctier;
	/** height of the editor; the rows scroll inside. Default 100% of the parent */
	height?: number | string;
	/**
	 * Url of the Sphinx template folder for the html preview, relative to the page,
	 * e.g. "sdoc-template/" (see make-template.py). No preview if not set.
	 */
	preview?: string;
}

interface DoctreeState {
	forest: DocTreeNode[];
	/** the doc as RST files, for the preview; reassembled after each change the server took */
	rstFiles: RstFile[];
	/** the preview pane is shown (with props.preview) */
	previewing: boolean;
	tobeLoad: boolean;
	/** id of the block being edited; at most one */
	editing: string | null;
	/** false after any change since the last commit */
	committed: boolean;
	committedAt?: string | null;
	/** a text change not yet confirmed by the server */
	saving: boolean;
	/** header of the doc, from the last load */
	doc?: DocInf;
	/** banner: work at risk (load or save failed), with a retry */
	alert?: { msg: string; retry: () => void };
	/** snackbar: a change refused or failed; the tree is reloaded */
	snack?: string;
	/** state of the synode's html build of the last commit, see DoctreeResp.Build */
	build?: string | null;
}

/** an empty paragraph, stored form */
const EMPTY = '{"contype":"doc","content":[{"contype":"paragraph"}]}';

/** stored form of the serializer's result: the parts the builder merges */
const rstOf = (r: RstResult) =>
	JSON.stringify({ body: r.body, roleDecls: r.roleDecls, substitutions: r.substitutions, targets: r.targets });

/** keep a click in a cell from toggling the folder or selecting the row */
const stop = (e: React.UIEvent) => e.stopPropagation();

/** a node's columns, as the tree carries them */
type Cols = DocTreeNode['node'];

/** a row button; no run: disabled */
type Act = { label: string; title: string; run?: () => void };

/** the comp the columns of one AnDoctreeditor render with */
type Owner = { ed?: AnDoctreeditorComp };

/** columns of one editor; formatters delegate to the comp, which registers in owner */
function docColumns(owner: Owner): AnTreegridCol[] {
	return [
		{ type: 'text', field: '', label: '', grid: { xs: 3, md: 2 } },
		{ type: 'formatter', field: '', label: 'Content', grid: { xs: 6, md: 7 },
		  formatter: (_c, t) => owner.ed!.content(t as DocTreeNode) },
		{ type: 'formatter', field: '', label: '', grid: { xs: 3, md: 3 },
		  formatter: (_c, t) => owner.ed!.actions(t as DocTreeNode) },
	];
}

class AnDoctreeditorComp extends AnTreeditorComp<any> {
	declare props: AnDoctreeditorProps & AnTreeditorProps & { owner: Owner };

	state: DoctreeState = { forest: [], rstFiles: [], previewing: true,
		tobeLoad: false, editing: null, committed: true, saving: false };

	/** the doc's RST files, if previewing */
	rstOf(forest: DocTreeNode[]): RstFile[] {
		return this.props.preview && forest[0] ? assemble(forest[0]) : [];
	}

	/** url of a resource of the doc, for the preview */
	mediaSrc = (name: string) => this.props.doctier.resSrc?.(this.props.docId, name);

	/** a text change waiting for autosave */
	pending: { node: Cols; timer: ReturnType<typeof setTimeout> } | undefined;

	/** Tiptap JSON by its stored text, so static rows keep their content object */
	parsed = new Map<string, JSONContent>();

	constructor(props: AnDoctreeditorProps & AnTreeditorProps & { owner: Owner }) {
		super(props);
		props.owner.ed = this;
	}

	componentDidMount() {
		this.load();
	}

	componentWillUnmount() {
		this.flush();
		clearTimeout(this.building);
	}

	// ---- server ------------------------------------------------------------

	load() {
		this.props.doctier.tree(this.props.docId, (resp) => {
			const forest = fillTipjson(received(resp.forest));
			console.debug('rst files', assemble(forest[0]));
			expandAll(forest);
			this.setState({ forest, rstFiles: this.rstOf(forest), doc: resp.doc, alert: undefined,
				committed: !resp.doc?.dirty, committedAt: resp.doc?.committedAt });
		}, (msg) => this.setState({ alert: { msg: `Can't load the doc: ${msg}`, retry: () => this.load() } }));
	}

	/** apply a structural change: the server's subtree replaces ours */
	applied = (resp: DoctreeResp) => {
		if (resp.rejected) {
			this.setState({ snack: `Not changed: ${resp.rejected}` });
			return;
		}
		const sub = fillTipjson(received(resp.forest));
		const old = resp.root == null ? this.state.forest : locate(this.state.forest, resp.root)?.t;
		if (old) keepExpanded(Array.isArray(old) ? old : [old], sub);
		const forest = replaced(this.state.forest, resp);
		if (!forest) return this.load(); // not ours to patch
		this.setState({ forest, rstFiles: this.rstOf(forest), committed: resp.doc ? !resp.doc.dirty : false });
	};

	/** a change failed: tell, and reload, as the server is the truth */
	failed = (msg: string) => {
		this.setState({ snack: msg });
		this.load();
	};

	// ---- autosave ----------------------------------------------------------

	/** ParagraphEditor reports every change; save after typing stops */
	changed(node: Cols, json: JSONContent, rst: RstResult) {
		const s = fromTiptap(json);
		if (s === (node.tipjson ?? EMPTY)) return; // e.g. the editor's report on creation
		node.tipjson = s;
		node.rstext = rstOf(rst);
		node.rstver = String(RSTVER);

		if (this.pending && this.pending.node !== node) this.flush();
		if (this.pending) clearTimeout(this.pending.timer);
		this.pending = { node, timer: setTimeout(() => this.flush(), SAVE_DELAY) };
		if (!this.state.saving || this.state.committed) this.setState({ committed: false, saving: true });
	}

	/** send the waiting text change now */
	flush() {
		if (!this.pending) return;
		const { node, timer } = this.pending;
		clearTimeout(timer);
		this.pending = undefined;
		this.save(node);
	}

	/** send one node's text; on failure keep it and offer a retry (no reload: it would drop the text) */
	save(node: Cols) {
		const n = new DocNode({ nid: node.nid, tipjson: node.tipjson, rstext: node.rstext, rstver: RSTVER });
		this.props.doctier.update(this.props.docId, n,
			(resp) => this.setState({ saving: !!this.pending, committed: !resp.doc?.dirty, alert: undefined,
				rstFiles: this.rstOf(this.state.forest) }),
			(msg) => this.setState({ saving: false, alert: { msg: `Not saved: ${msg}`, retry: () => this.save(node) } }));
	}

	// ---- user actions ------------------------------------------------------

	edit(id: string | null) {
		if (id === this.state.editing) return;
		this.flush();
		this.setState({ editing: id });
	}

	modify(mod: TreeModify | undefined, open?: boolean) {
		if (!mod) return;
		this.flush();
		this.props.doctier.modify(this.props.docId, mod, (resp) => {
			this.applied(resp);
			if (open && resp.newId) this.setState({ editing: resp.newId });
		}, this.failed);
	}

	del(t: DocTreeNode) {
		const question = t.node.children?.length ? 'Delete this section and everything in it?' : undefined;
		if (question && !window.confirm(question)) return;

		// the block being edited is inside: drop its pending save, close the editor
		if (this.state.editing && locate([t], this.state.editing)) {
			if (this.pending) clearTimeout(this.pending.timer);
			this.pending = undefined;
			this.setState({ editing: null, saving: false });
		}
		this.modify(removeMod(this.state.forest, t.id));
	}

	/** polling the synode's html build */
	building: ReturnType<typeof setTimeout> | undefined;

	/**
	 * Commit with the doc's RST files: the synode builds the html package from them in the
	 * background, and its state is polled until the build ends.
	 */
	commit() {
		this.flush();
		const forest = this.state.forest;
		this.props.doctier.commit(this.props.docId, forest[0] ? assemble(forest[0]) : [],
			(resp) => {
				this.setState({ committed: !resp.doc?.dirty, committedAt: resp.doc?.committedAt, build: resp.build,
					doc: resp.doc ?? this.state.doc });
				this.pollBuild(resp.build);
			},
			(msg) => this.setState({ snack: `Not committed: ${msg}` }));
	}

	pollBuild(build?: string | null) {
		clearTimeout(this.building);
		if (build !== DoctreeResp.Build.building || !this.props.doctier.build) return;
		this.building = setTimeout(() => this.props.doctier.build!(this.props.docId,
			(resp) => {
				this.setState({ build: resp.build, doc: resp.doc ?? this.state.doc,
					snack: resp.build === DoctreeResp.Build.failed ? `Html build failed: ${resp.buildlog ?? ''}` : this.state.snack });
				this.pollBuild(resp.build);
			},
			() => this.pollBuild(build)), BUILD_POLL);
	}

	// ---- anreact hooks -----------------------------------------------------

	visualOf(t: AnTreeNode) {
		return isContainer(t as DocTreeNode) ? TreeNodeVisual.collapsable : TreeNodeVisual.card;
	}

	/** the row head after the indents; the title itself is in the content column */
	sectionTitle(t: AnTreeNode) {
		return (t as DocTreeNode).node.kind === DocNode.Kind.section ? <span className="sd-glyph">§</span> : null;
	}

	/** AnTreeditor's header click: a new section at the end of the doc */
	toAddChild(e: React.MouseEvent<HTMLElement>) {
		e?.stopPropagation();
		const root = this.state.forest[0];
		if (root) this.modify(insertMod(this.state.forest, root.id, DocNode.Kind.section), true);
	}

	/** AnTreeditor's own delete */
	removeNode(id: string) {
		const l = locate(this.state.forest, id);
		if (l) this.del(l.t);
	}

	// ---- column formatters -------------------------------------------------

	/** content cell: the one editor, a static block that opens it, or a read-only line */
	content(t: DocTreeNode) {
		const n = t.node;
		let cell: React.ReactNode;
		switch (n.kind) {
			case DocNode.Kind.doc:
				cell = <div className="sd-doctitle">{n.ptext}</div>;
				break;
			case DocNode.Kind.docref:
				cell = <div className="sd-ref" title="Read-only, expanded when compiled">↪ {n.ptext}</div>;
				break;
			case DocNode.Kind.image: {
				const src = n.resId ? this.props.doctier.resSrc?.(this.props.docId, n.resId) : undefined;
				cell = src
					? <div className="sd-media"><img src={src} alt={n.resId} title={n.resId} /></div>
					: <div className="sd-media">[image {n.resId}]</div>;
				break;
			}
			default: // section title, paragraph, formula
				cell = n.nid === this.state.editing
					? <ParagraphEditor
						content={this.parse(n.tipjson)}
						autofocus
						onChange={(j, rst) => this.changed(n, j, rst)}
						editMath={(kind, latex) => window.prompt(kind === 'block' ? 'Block formula (LaTeX)' : 'Formula (LaTeX)', latex)}
					  />
					: <StaticBlock json={this.parse(n.tipjson)} className="sd-clickable" onClick={() => this.edit(n.nid!)} />;
		}
		return <div className={'sd-cell sd-' + n.kind} onClick={stop}>{cell}</div>;
	}

	/** Tiptap JSON of a stored json (contype -> type), parsed once per text */
	parse(s?: string): JSONContent {
		const k = s ?? EMPTY;
		let j = this.parsed.get(k);
		if (!j) this.parsed.set(k, (j = toTiptap<JSONContent>(k)));
		return j!;
	}

	/** actions cell: buttons from one list; a button without run is disabled */
	actions(t: DocTreeNode) {
		const f = this.state.forest;

		// a button without run is greyed out: the rules (doc-stree Rules) say it can't apply
		const add = (label: string, kind: DocKind, title: string): Act => {
			const mod = insertMod(f, t.id, kind);
			return { label, title, run: mod && (() => this.modify(mod, true)) };
		};

		const mov = (label: string, op: MoveOp, title: string): Act => {
			const mod = moveMod(f, t.id, op);
			return { label, title, run: mod && (() => this.modify(mod)) };
		};

		const del: Act = { label: '✕', title: 'Delete', run: removeMod(f, t.id) && (() => this.del(t)) };
		const K = DocNode.Kind;
		const at = t.node.kind === K.section ? ' at the start of this section' : ' after';

		const btns: Act[] =
			t.node.kind === K.doc ? [
				add('§+', K.section, 'Add section at the end'),
				add('¶+', K.p, 'Add paragraph at the end'),
			] : [
				add('¶+', K.p, 'Add paragraph' + at),
				add('∑+', K.math, 'Add formula' + at),
				add('§+', K.section, 'Add section after'),
				mov('↑', 'up', 'Up'),
				mov('↓', 'down', 'Down'),
				mov('→', 'indent', 'Indent (into the section above)'),
				mov('←', 'outdent', 'Outdent'),
				del,
			];

		return (
			<div className="sd-actions" onClick={stop}>
				{btns.map((b) => (
					<button key={b.label} className="sd-act" title={b.title} disabled={!b.run} onClick={b.run}>{b.label}</button>
				))}
			</div>
		);
	}

	render() {
		const { committed, committedAt, saving, doc, alert, snack, previewing, rstFiles, build } = this.state;
		const preview = this.props.preview && previewing;
		const classes = this.props.classes || {};
		const media = CrudCompW.getMedia(this.props.width);
		const html = build === DoctreeResp.Build.building ? ', building html…'
				: build === DoctreeResp.Build.built ? ', html built'
				: build === DoctreeResp.Build.failed ? ', html build failed' : '';
		// nothing to commit: no change since the last commit, of a doc committed before, with its html
		// package. A new doc (never committed), or one without a html package, can be committed as it is.
		const nothing = committed && !saving && !!committedAt && !!doc?.htmlUri;
		const building = build === DoctreeResp.Build.building;
		const status = saving ? 'Saving…' : committed ? `Committed ${committedAt ?? ''}${html}` : 'Draft — not committed';

		return (
			<Box className="sd-doctree" sx={{
				display: 'flex', flexDirection: 'column', position: 'relative',
				height: this.props.height ?? '100%', minHeight: 0 }}>
				<Box className="sd-head" sx={{ flex: 'none' }}>
					<Collapse in={!!alert}>
						<Alert severity="error"
							action={<Button color="inherit" size="small" onClick={() => alert?.retry()}>Retry</Button>}
							onClose={() => this.setState({ alert: undefined })}>
							{alert?.msg}
						</Alert>
					</Collapse>
					<Toolbar variant="dense" disableGutters sx={{ gap: 2, px: 1 }}>
						<Typography variant="subtitle1" noWrap sx={{ flex: 1, fontWeight: 600 }}>{doc?.title}</Typography>
						<Typography variant="body2" color="text.secondary" noWrap>{status}</Typography>
						{this.props.preview &&
							<Button size="small" onClick={() => this.setState({ previewing: !previewing })}>
								{previewing ? 'Hide preview' : 'Preview'}</Button>}
						<Button variant="contained" size="small" disableElevation
							disabled={nothing || building} onClick={() => this.commit()}>Commit</Button>
					</Toolbar>
				</Box>

				{/* the tree (column header + rows), and the preview beside it, or below on narrow screens */}
				<Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 1 }}>
					<Box sx={{ flex: 1, minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
						<Box sx={{ flex: 'none' }}>
							{AnTreegridComp.th(this.props.columns, classes, media, { onThClick: this.toAddChild })}
						</Box>
						{/* the rows; AnTreeditor's render() would put the column header in here too */}
						<Box className="sd-body" sx={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
							{this.treeNodes({ classes, media })}
							{this.editForm}
						</Box>
					</Box>
					{preview && <DocPreview template={this.props.preview!} files={rstFiles} mediaSrc={this.mediaSrc} style={{ flex: 1 }} />}
				</Box>

				<Snackbar open={!!snack} message={snack} autoHideDuration={6000}
					anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
					onClose={(_e, why) => why !== 'clickaway' && this.setState({ snack: undefined })}
					sx={{ position: 'absolute' /* in the editor, not the window */ }} />
			</Box>
		);
	}
}

// ---- view state of received trees -------------------------------------------

const walk = (f: DocTreeNode[], v: (t: DocTreeNode) => void) =>
	f.forEach((t) => { v(t); walk(t.node.children ?? [], v); });

function expandAll(forest: DocTreeNode[]) {
	walk(forest, (t) => { if (isContainer(t)) t.node.expandChildren = true; });
}

/** a received tree keeps the folders' open / closed state; new folders open */
function keepExpanded(old: DocTreeNode[], sub: DocTreeNode[]) {
	const was = new Map<string, boolean>();
	walk(old, (t) => was.set(t.id, !!t.node.expandChildren));
	walk(sub, (t) => { if (isContainer(t)) t.node.expandChildren = was.get(t.id) ?? true; });
}

/** columns are bound to each editor instance */
const Styled = withStyles<any, any, any>(treeditorStyles)(withWidth()(AnDoctreeditorComp));

export function AnDoctreeditor(props: AnDoctreeditorProps) {
	const [owner] = React.useState<Owner>(() => ({}));
	const [columns] = React.useState(() => docColumns(owner));
	return <Styled reload={false} uri="/sdoc" {...props} columns={columns} pk="nid" owner={owner} />;
}

export { AnDoctreeditorComp };
