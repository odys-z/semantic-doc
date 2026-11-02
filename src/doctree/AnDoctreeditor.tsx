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
 *  - The server owns the tree: every structural change is a request (u/move, i, d)
 *    answered with the reloaded subtree, which replaces ours. The client only works
 *    out where a node goes (moveReq, insertReq in doctree-tier).
 *  - Autosave: text is sent SAVE_DELAY ms after typing stops, or at once when the
 *    user leaves the block. Commit marks the work as finished.
 *  - ref: one read-only line. avail === 'false': a placeholder, e.g. not on this synode.
 */
import React from 'react';
import type { AnTreeNode } from '@anclient/semantier';
import {
	AnTreeditorComp, type AnTreeditorProps, type AnTreegridCol, TreeNodeVisual,
	treeditorStyles, withStyles, withWidth,
} from '@anclient/anreact';

import { ParagraphEditor, StaticBlock, type JSONContent, type RstResult } from '../editor';
import {
	DocKind, type DocCols, type DocTreeNode, type DoctreeResp, type DoctreeTier, type MoveTo,
	fromTiptap, insertReq, isContainer, locate, moveReq, received, referencedIn, toTiptap,
} from './doctree-tier';
import './doctree.css';

/** version of the json -> rst serializer, stored in DocNode.rstver */
export const RSTVER = 1;
/** autosave delay after typing stops, ms */
export const SAVE_DELAY = 1500;

export interface AnDoctreeditorProps extends Partial<Omit<AnTreeditorProps, 'columns' | 'pk' | 'tier'>> {
	docId: string;
	/** requests to the doc-content tier */
	doctier: DoctreeTier;
}

interface DoctreeState {
	forest: DocTreeNode[];
	tobeLoad: boolean;
	/** id of the block being edited; at most one */
	editing: string | null;
	/** false after any change since the last commit */
	committed: boolean;
	committedAt?: string | null;
	/** a text change not yet confirmed by the server */
	saving: boolean;
	/** an error, or why the server refused a change */
	error?: string;
}

/** an empty paragraph, stored form */
const EMPTY = '{"contype":"doc","content":[{"contype":"paragraph"}]}';

/** stored form of the serializer's result: the parts the builder merges */
const rstOf = (r: RstResult) =>
	JSON.stringify({ body: r.body, roleDecls: r.roleDecls, substitutions: r.substitutions, targets: r.targets });

/** keep a click in a cell from toggling the folder or selecting the row */
const stop = (e: React.UIEvent) => e.stopPropagation();

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

	state: DoctreeState = { forest: [], tobeLoad: false, editing: null, committed: true, saving: false };

	/** a text change waiting for autosave */
	pending: { node: DocCols; timer: ReturnType<typeof setTimeout> } | undefined;

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
	}

	// ---- server ------------------------------------------------------------

	load() {
		this.props.doctier.tree(this.props.docId, (resp) => {
			const forest = received(resp.forest);
			expandAll(forest);
			this.setState({ forest, committed: !resp.doc?.dirty, committedAt: resp.doc?.committedAt });
		}, this.failed);
	}

	/** apply a structural change: the server's subtree replaces ours */
	applied = (resp: DoctreeResp) => {
		if (resp.rejected) {
			this.setState({ error: `Not changed: ${resp.rejected}` });
			return;
		}
		const sub = received(resp.forest);
		let forest: DocTreeNode[];
		if (resp.root == null) {
			keepExpanded(this.state.forest, sub);
			forest = sub;
		} else {
			const old = locate(this.state.forest, resp.root);
			if (!old || sub.length !== 1) return this.load(); // not ours to patch
			keepExpanded([old.t], sub);
			placeAs(sub[0], old.t);
			old.sibs[old.ix] = sub[0];
			forest = [...this.state.forest];
		}
		this.setState({ forest, error: undefined, committed: resp.doc ? !resp.doc.dirty : false });
	};

	failed = (msg: string) => {
		this.setState({ error: msg, saving: false });
		this.load(); // the server is the truth: reload instead of guessing
	};

	// ---- autosave ----------------------------------------------------------

	/** ParagraphEditor reports every change; save after typing stops */
	changed(node: DocCols, json: JSONContent, rst: RstResult) {
		const s = fromTiptap(json);
		if (s === (node.json ?? EMPTY)) return; // e.g. the editor's report on creation
		node.json = s;
		node.rst = rstOf(rst);
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
		this.props.doctier.update(this.props.docId, node,
			(resp) => this.setState({ saving: !!this.pending, committed: !resp.doc?.dirty }),
			this.failed);
	}

	// ---- user actions ------------------------------------------------------

	edit(id: string | null) {
		if (id === this.state.editing) return;
		this.flush();
		this.setState({ editing: id });
	}

	modify(req: ReturnType<typeof moveReq>, open?: boolean) {
		if (!req) return;
		this.flush();
		this.props.doctier.modify(this.props.docId, req, (resp) => {
			this.applied(resp);
			if (open && resp.newId) this.setState({ editing: resp.newId });
		}, this.failed);
	}

	del(t: DocTreeNode) {
		const question = referencedIn(t)
			? 'Other documents reference this part. Deleting it removes it from them too. Delete anyway?'
			: t.node.children?.length ? 'Delete this section and everything in it?' : undefined;
		if (question && !window.confirm(question)) return;

		// the block being edited is inside: drop its pending save, close the editor
		if (this.state.editing && locate([t], this.state.editing)) {
			if (this.pending) clearTimeout(this.pending.timer);
			this.pending = undefined;
			this.setState({ editing: null, saving: false });
		}
		this.modify({ a: 'd', rootId: t.id });
	}

	commit() {
		this.flush();
		this.props.doctier.commit(this.props.docId,
			(resp) => this.setState({ committed: !resp.doc?.dirty, committedAt: resp.doc?.committedAt }),
			this.failed);
	}

	// ---- anreact hooks -----------------------------------------------------

	visualOf(t: AnTreeNode) {
		return isContainer(t as DocTreeNode) ? TreeNodeVisual.collapsable : TreeNodeVisual.card;
	}

	/** the row head after the indents; the title itself is in the content column */
	sectionTitle(t: AnTreeNode) {
		return (t as DocTreeNode).node.kind === DocKind.section ? <span className="sd-glyph">§</span> : null;
	}

	/** AnTreeditor's header click: a new section at the end of the doc */
	toAddChild(e: React.MouseEvent<HTMLElement>) {
		e?.stopPropagation();
		const root = this.state.forest[0];
		if (root) this.modify(insertReq(this.state.forest, root.id, DocKind.section), true);
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
		if (n.avail === 'false')
			cell = <div className="sd-unavail">{n.text} — not available here</div>;
		else switch (n.kind) {
			case DocKind.doc:
				cell = <div className="sd-doctitle">{n.text}</div>;
				break;
			case DocKind.ref:
				cell = <div className="sd-ref" title="Read-only, expanded when compiled">↪ {n.text}</div>;
				break;
			case DocKind.image:
				cell = <div className="sd-media">[image {n.resId}]</div>;
				break;
			default: // section title, paragraph, formula
				cell = n.nid === this.state.editing
					? <ParagraphEditor
						content={this.parse(n.json)}
						autofocus
						onChange={(j, rst) => this.changed(n, j, rst)}
						editMath={(kind, latex) => window.prompt(kind === 'block' ? 'Block formula (LaTeX)' : 'Formula (LaTeX)', latex)}
					  />
					: <StaticBlock json={this.parse(n.json)} className="sd-clickable" onClick={() => this.edit(n.nid)} />;
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

		const add = (label: string, kind: DocKind, title: string): Act =>
			({ label, title, run: () => this.modify(insertReq(f, t.id, kind), true) });

		const mov = (label: string, to: MoveTo, title: string): Act => {
			const req = moveReq(f, t.id, to);
			return { label, title, run: req && (() => this.modify(req)) };
		};

		const del: Act = { label: '✕', title: 'Delete', run: () => this.del(t) };
		const at = t.node.kind === DocKind.section ? ' at the start of this section' : ' after';

		const btns: Act[] =
			t.node.avail === 'false' ? [del]
			: t.node.kind === DocKind.doc ? [
				add('§+', DocKind.section, 'Add section at the end'),
				add('¶+', DocKind.p, 'Add paragraph at the end'),
			] : [
				add('¶+', DocKind.p, 'Add paragraph' + at),
				add('∑+', DocKind.math, 'Add formula' + at),
				add('§+', DocKind.section, 'Add section after'),
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
		const { committed, committedAt, saving, error } = this.state;
		return (
			<div className="sd-doctree">
				<div className="sd-doctree-bar">
					<button onClick={() => this.commit()} disabled={committed && !saving}>Commit</button>
					<span className="sd-status">
						{saving ? 'Saving…' : committed ? `Committed ${committedAt ?? ''}` : 'Draft — not committed'}
					</span>
					{error && <span className="sd-error" title="Dismiss" onClick={() => this.setState({ error: undefined })}>{error}</span>}
				</div>
				{super.render() /* the tree, from this.state.forest */}
			</div>
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

/**
 * A reloaded subtree comes with its root at level 0 and no indents. Display only:
 * give it the place of the node it replaces, and its descendants the indents
 * TreeIndenode.indents() would give them in the whole tree.
 */
function placeAs(sub: DocTreeNode, old: DocTreeNode) {
	sub.level = old.level;
	sub.indents = old.indents;
	sub.islastSibling = old.islastSibling ?? (old as any).lastSibling;
	const down = (p: DocTreeNode) => {
		const kids = (p.node.children ?? []) as DocTreeNode[];
		const base = p.indents?.length
			? [...p.indents.slice(0, -1), (p.islastSibling ?? (p as any).lastSibling) ? 'spacex' : 'vlink'] as DocTreeNode['indents']
			: [];
		kids.forEach((k, x) => {
			k.level = p.level + 1;
			k.islastSibling = x === kids.length - 1;
			k.indents = [...base!, k.islastSibling ? 'childx' : 'childi'];
			down(k);
		});
	};
	down(sub);
}

/** columns are bound to each editor instance */
const Styled = withStyles<any, any, any>(treeditorStyles)(withWidth()(AnDoctreeditorComp));

export function AnDoctreeditor(props: AnDoctreeditorProps) {
	const [owner] = React.useState<Owner>(() => ({}));
	const [columns] = React.useState(() => docColumns(owner));
	return <Styled reload={false} uri="/sdoc" {...props} columns={columns} pk="nid" owner={owner} />;
}

export { AnDoctreeditorComp };
