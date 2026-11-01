/**
 * AnDoctreeditor: the content tree of one doc, extending anreact's AnTreeditor.
 *
 *  - The server owns the tree: it sends the forest, and answers every
 *    structural change with the changed subtree, which replaces ours.
 *    The client never rebuilds or renumbers the tree.
 *  - Autosave: text changes are sent SAVE_DELAY ms after typing stops, or at
 *    once when the user leaves the block. Commit marks the work as finished.
 *  - One ParagraphEditor at a time, in the block being edited; other blocks
 *    are StaticBlock. Collapsed sections are not rendered by AnTreeditor.
 *  - ref: one read-only line; the subtree is expanded only when compiled.
 *  - node.avail === false: a placeholder, e.g. not on this synode.
 *
 * Uses anreact 0.8 hooks: visualOf, renderLeaf, sectionTitle, rowActions.
 */
import React from 'react';
import { findNode, replaceNode, type AnTreeNode } from '@anclient/semantier';
import {
	AnTreeditorComp, type AnTreeditorProps, type AnTreegridCol, TreeNodeVisual,
	treeditorStyles, withStyles, withWidth, type CompOpts,
} from '@anclient/anreact';

import { ParagraphEditor, StaticBlock, type JSONContent, type RstResult } from '../editor';
import { DocKind, type DocNode, type DocTreeNode, type DoctreeResp, type DoctreeTier, type MoveTo } from './doctree-tier';
import './doctree.css';

/** version of the json -> rst serializer, stored in DocNode.rstver */
export const RSTVER = 1;
/** autosave delay after typing stops, ms */
export const SAVE_DELAY = 1500;

export interface AnDoctreeditorProps extends Omit<AnTreeditorProps, 'columns' | 'pk'> {
	docId: string;
	/** requests to the doc-content tier */
	doctier: DoctreeTier;
	/** default: content + actions */
	columns?: AnTreegridCol[];
	pk?: string;
}

interface DoctreeState {
	forest: DocTreeNode[];
	tobeLoad: boolean;
	/** nid of the block being edited; at most one */
	editing: string | null;
	/** false after any change since the last commit */
	committed: boolean;
	committedAt?: string | null;
	/** a text change not yet confirmed by the server */
	saving: boolean;
	error?: string;
}

const COLUMNS: AnTreegridCol[] = [
	{ type: 'text', field: 'text', label: 'Content', grid: { xs: 8, md: 9 } },
	{ type: 'actions', field: '', label: '', grid: { xs: 4, md: 3 } },
];

/** the content record carried by a tree node */
const rec = (t: AnTreeNode) => (t as DocTreeNode).node.rec;

/** stored form of the serializer's result: the parts the builder merges */
const rstOf = (r: RstResult) =>
	JSON.stringify({ body: r.body, roleDecls: r.roleDecls, substitutions: r.substitutions, targets: r.targets });

class AnDoctreeditorComp extends AnTreeditorComp<AnDoctreeditorProps & AnTreeditorProps> {
	state: DoctreeState = { forest: [], tobeLoad: false, editing: null, committed: true, saving: false };

	/** a text change waiting for autosave */
	pending: { node: DocNode; timer: ReturnType<typeof setTimeout> } | undefined;

	componentDidMount() {
		this.load();
	}

	componentWillUnmount() {
		this.flush();
	}

	// ---- server ------------------------------------------------------------

	load() {
		this.props.doctier.tree(this.props.docId, (resp) =>
			this.setState({ forest: resp.forest, committed: !resp.doc.dirty, committedAt: resp.doc.committedAt }),
			this.failed);
	}

	/** apply the changed subtree(s) returned by the server */
	applied = (resp: DoctreeResp) => {
		let forest = resp.root ? resp.forest : this.state.forest;
		if (!resp.root)
			for (const sub of resp.forest) forest = replaceNode(forest, sub) as DocTreeNode[];
		this.setState({ forest, committed: !resp.doc.dirty, error: undefined });
	};

	failed = (msg: string) => {
		this.setState({ error: msg, saving: false });
		this.load(); // the server is the truth: reload instead of guessing
	};

	// ---- autosave ----------------------------------------------------------

	/** ParagraphEditor reports every change; save after typing stops */
	changed(node: DocNode, json: JSONContent, rst: RstResult) {
		const s = JSON.stringify(json);
		if (s === node.json) return; // e.g. the editor's report on creation
		node.json = s;
		node.rst = rstOf(rst);
		node.rstver = RSTVER;

		if (this.pending) clearTimeout(this.pending.timer);
		this.pending = { node, timer: setTimeout(() => this.flush(), SAVE_DELAY) };
		this.setState({ committed: false, saving: true });
	}

	/** send the waiting text change now */
	flush() {
		if (!this.pending) return;
		const { node, timer } = this.pending;
		clearTimeout(timer);
		this.pending = undefined;
		this.props.doctier.update(node, (resp) => this.setState({ saving: !!this.pending, committed: !resp.doc.dirty }), this.failed);
	}

	// ---- user actions ------------------------------------------------------

	edit(nid: string | null) {
		if (nid === this.state.editing) return;
		this.flush();
		this.setState({ editing: nid });
	}

	/** after `after`, or last at the top level */
	insert(after: AnTreeNode | null, kind: DocKind) {
		this.flush();
		this.props.doctier.insert(this.props.docId, after?.id ?? null, kind, (resp) => {
			this.applied(resp);
			if (kind !== DocKind.section) this.setState({ editing: resp.nid ?? null });
		}, this.failed);
	}

	/** header click of AnTreeditor: a new top-level section */
	toAddChild(e: React.MouseEvent<HTMLElement>) {
		e?.stopPropagation();
		this.insert(null, DocKind.section);
	}

	del(t: AnTreeNode) {
		const r = rec(t);
		const hasChildren = (t.node.children?.length ?? 0) > 0;
		if ((hasChildren || r.referenced) &&
			!window.confirm(r.referenced
				? 'Other documents reference this part. Delete it anyway?'
				: 'Delete this section and everything in it?'))
			return;

		// the block being edited is inside: drop its pending save, close the editor
		if (this.state.editing && findNode([t], this.state.editing)) {
			if (this.pending) clearTimeout(this.pending.timer);
			this.pending = undefined;
			this.setState({ editing: null, saving: false });
		}
		this.props.doctier.del(this.props.docId, [t.id], this.applied, this.failed);
	}

	/** AnTreeditor's delete button */
	removeNode(id: string) {
		const t = findNode(this.state.forest, id);
		if (t) this.del(t);
	}

	move(t: AnTreeNode, to: MoveTo) {
		this.flush();
		this.props.doctier.move(this.props.docId, t.id, to, this.applied, this.failed);
	}

	commit() {
		this.flush();
		this.props.doctier.commit(this.props.docId,
			(resp) => this.setState({ committed: !resp.doc.dirty, committedAt: resp.doc.committedAt }),
			this.failed);
	}

	// ---- anreact hooks -----------------------------------------------------

	visualOf(t: AnTreeNode) {
		return rec(t).kind === DocKind.section ? TreeNodeVisual.collapsable : TreeNodeVisual.card;
	}

	/** a block row: indent + content + actions */
	renderLeaf(t: AnTreeNode, _parent: AnTreeNode | undefined, _opts: CompOpts) {
		return (
			<div key={t.id} className="sd-row" style={{ paddingLeft: `${(t.level ?? 0) * 1.5 + 2}em` }}>
				<div className="sd-row-content">{this.content(t as DocTreeNode)}</div>
				<div className="sd-row-actions">{this.rowActions(t)}</div>
			</div>
		);
	}

	content(t: DocTreeNode) {
		const r = t.node.rec;
		if (t.node.avail === false)
			return <div className="sd-unavail">{t.node.text} — not available here</div>;
		switch (r.kind) {
			case DocKind.ref:
				return <div className="sd-ref" title="Read-only, expanded when compiled">↪ {t.node.text}</div>;
			case DocKind.image:
				return <div className="sd-media">[image {r.resId}]</div>;
			default:
				return this.block(r);
		}
	}

	/** section titles are edited like paragraphs; clicks don't toggle the folder */
	sectionTitle(t: AnTreeNode) {
		return (
			<span className="sd-title" onClick={(e) => e.stopPropagation()}>
				{this.block(rec(t))}
			</span>
		);
	}

	/** the single editor, or a static view that opens it on click */
	block(r: DocNode) {
		const json = JSON.parse(r.json ?? '{"type":"doc","content":[{"type":"paragraph"}]}');
		return r.nid === this.state.editing ? (
			<ParagraphEditor
				content={json}
				autofocus
				onChange={(j, rst) => this.changed(r, j, rst)}
				editMath={(kind, latex) => window.prompt(kind === 'block' ? 'Block formula (LaTeX)' : 'Formula (LaTeX)', latex)}
			/>
		) : (
			<StaticBlock json={json} className="sd-clickable" onClick={() => this.edit(r.nid)} />
		);
	}

	rowActions(t: AnTreeNode) {
		if ((t as DocTreeNode).node.avail === false)
			return <button className="sd-act" onClick={() => this.del(t)} title="Delete">✕</button>;
		const act = (label: string, title: string, f: () => void) => (
			<button className="sd-act" title={title} onClick={(e) => { e.stopPropagation(); f(); }}>{label}</button>
		);
		return (
			<>
				{act('¶+', 'Add paragraph after', () => this.insert(t, DocKind.p))}
				{act('∑+', 'Add formula after', () => this.insert(t, DocKind.math))}
				{act('§+', 'Add section after', () => this.insert(t, DocKind.section))}
				{act('↑', 'Up', () => this.move(t, 'up'))}
				{act('↓', 'Down', () => this.move(t, 'down'))}
				{act('→', 'Indent (into the section above)', () => this.move(t, 'indent'))}
				{act('←', 'Outdent', () => this.move(t, 'outdent'))}
				{act('✕', 'Delete', () => this.del(t))}
			</>
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
					{error && <span className="sd-error" onClick={() => this.setState({ error: undefined })}>{error}</span>}
				</div>
				{super.render() /* the tree, from this.state.forest */}
			</div>
		);
	}
}

/** columns default to content + actions */
function withDefaults(Comp: React.ComponentType<any>) {
	return (props: AnDoctreeditorProps) =>
		<Comp columns={COLUMNS} pk="nid" uri={props.uri ?? '/sdoc'} {...props} />;
}

export const AnDoctreeditor = withDefaults(withStyles<any, any, any>(treeditorStyles)(withWidth()(AnDoctreeditorComp)));
export { AnDoctreeditorComp };
