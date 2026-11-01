/**
 * AnDoctreeditor: the content tree of one doc, extending anreact's AnTreeditor.
 *
 *  - The server owns the tree: it sends the forest (DoctreeResp) and every
 *    structural change is a request; the server answers with the changed
 *    subtree, which replaces ours. The client never rebuilds or renumbers.
 *  - Autosave: text edits are sent SAVE_DELAY ms after typing stops, or when
 *    the user leaves the paragraph. Commit marks the work as finished.
 *  - One ParagraphEditor at a time, in the row being edited; other rows are static.
 *  - ref rows are one read-only line; the subtree is expanded only when compiled.
 *  - Nodes this synode can't present (node.avail === false) are placeholders.
 *
 * Relies on anreact 0.8 hooks: visualOf(), renderLeaf(), sectionTitle(), rowActions().
 * Collapsed sections are not rendered by AnTreeditor, so no editor is mounted there.
 */
import React from 'react';
import type { AnTreeNode } from '@anclient/semantier';
import { replaceNode, findNode } from '@anclient/semantier';
import {
	AnTreeditorComp, type AnTreeditorProps, TreeNodeVisual,
	withStyles, withWidth, type CompOpts,
} from '@anclient/anreact';

import { ParagraphEditor, StaticBlock, type JSONContent, type RstResult } from '../editor';
import { type DocNode, DocKind, type DoctreeResp, DoctreeTier } from './doctree-tier';

/** version of the json -> rst serializer, stored in DocNode.rstver */
export const RSTVER = 1;
/** autosave delay after typing stops */
const SAVE_DELAY = 1500;

export interface AnDoctreeditorProps extends AnTreeditorProps {
	docId: string;
	/** requests to the doc-content tier (hard-coded json files for now) */
	tier: DoctreeTier;
}

interface DoctreeState {
	forest: AnTreeNode[];
	/** nid of the node being edited; at most one */
	editing: string | null;
	/** false after any change since the last commit */
	committed: boolean;
	committedAt?: string;
	error?: string;
}

/** the content record carried by a tree node */
const rec = (t: AnTreeNode) => t.node.rec as DocNode;

/** stored form of the serializer's result: the parts the builder merges */
const rstOf = (r: RstResult) =>
	JSON.stringify({ body: r.body, roleDecls: r.roleDecls, substitutions: r.substitutions, targets: r.targets });

class AnDoctreeditorComp extends AnTreeditorComp<AnDoctreeditorProps> {
	state: DoctreeState = { forest: [], editing: null, committed: true };

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
		this.props.tier.tree(this.props.docId, (resp: DoctreeResp) =>
			this.setState({
				forest: resp.forest,
				committed: !resp.doc.dirty,
				committedAt: resp.doc.committedAt,
			}));
	}

	/** apply the subtrees returned by a structural request */
	applied = (resp: DoctreeResp) => {
		let forest = this.state.forest;
		for (const sub of resp.forest) forest = replaceNode(forest, sub);
		this.setState({ forest, committed: false, error: undefined });
	};

	failed = (err: string) => {
		this.setState({ error: err });
		this.load(); // server is the truth: reload instead of guessing
	};

	// ---- autosave ----------------------------------------------------------

	/** ParagraphEditor reports every change; save after typing stops */
	changed(node: DocNode, json: JSONContent, rst: RstResult) {
		const s = JSON.stringify(json);
		if (s === node.json) return; // e.g. the editor's initial report
		node.json = s;
		node.rst = rstOf(rst);
		node.rstver = RSTVER;

		if (this.pending) clearTimeout(this.pending.timer);
		this.pending = { node, timer: setTimeout(() => this.flush(), SAVE_DELAY) };
		if (this.state.committed) this.setState({ committed: false });
	}

	/** send the waiting text change now */
	flush() {
		if (!this.pending) return;
		const { node, timer } = this.pending;
		clearTimeout(timer);
		this.pending = undefined;
		this.props.tier.update(node, () => {}, this.failed);
	}

	// ---- user actions ------------------------------------------------------

	edit(nid: string | null) {
		this.flush();
		this.setState({ editing: nid });
	}

	insert(after: AnTreeNode, kind: DocKind) {
		this.flush();
		this.props.tier.insert(this.props.docId, after.id, kind, (resp) => {
			this.applied(resp);
			this.setState({ editing: resp.nid }); // edit the new node at once
		}, this.failed);
	}

	del(t: AnTreeNode) {
		const r = rec(t);
		const hasChildren = (t.node.children?.length ?? 0) > 0;
		if ((hasChildren || r.referenced) &&
			!window.confirm(r.referenced
				? 'Other documents reference this part. Delete it anyway?'
				: 'Delete this section and everything in it?'))
			return;

		// the node being edited is inside: drop its pending save, close the editor
		if (this.state.editing && findNode([t], this.state.editing)) {
			if (this.pending) clearTimeout(this.pending.timer);
			this.pending = undefined;
			this.setState({ editing: null });
		}
		this.props.tier.del(this.props.docId, [t.id], this.applied, this.failed);
	}

	move(t: AnTreeNode, to: 'up' | 'down' | 'indent' | 'outdent') {
		this.flush();
		this.props.tier.move(this.props.docId, t.id, to, this.applied, this.failed);
	}

	commit() {
		this.flush();
		this.props.tier.commit(this.props.docId, (resp) =>
			this.setState({ committed: true, committedAt: resp.doc.committedAt }), this.failed);
	}

	// ---- anreact hooks -----------------------------------------------------

	visualOf(t: AnTreeNode) {
		return rec(t).kind === DocKind.section ? TreeNodeVisual.collapsable : TreeNodeVisual.card;
	}

	/** one row: placeholder, ref line, media line, or a paragraph / formula */
	renderLeaf(t: AnTreeNode, _parent: AnTreeNode, _opts: CompOpts) {
		const r = rec(t);
		if (t.node.avail === false)
			return <div className="sd-unavail">{t.node.text} (not on this synode)</div>;

		switch (r.kind) {
			case DocKind.ref:
				return <div className="sd-ref">↪ {t.node.text}</div>; // read-only, title from server
			case DocKind.image:
				return <div className="sd-media">[image {r.resId}]</div>;
			default:
				return this.block(r);
		}
	}

	/** section titles are edited the same way as paragraphs */
	sectionTitle(t: AnTreeNode) {
		return this.block(rec(t));
	}

	/** the single editor, or a static view that opens it on click */
	block(r: DocNode) {
		return r.nid === this.state.editing ? (
			<ParagraphEditor
				content={JSON.parse(r.json)}
				autofocus
				onChange={(json, rst) => this.changed(r, json, rst)}
			/>
		) : (
			<StaticBlock json={JSON.parse(r.json)} onClick={() => this.edit(r.nid)} />
		);
	}

	rowActions(t: AnTreeNode) {
		if (t.node.avail === false) return null;
		return (
			<>
				<button onClick={() => this.insert(t, DocKind.p)} title="Add paragraph after">¶+</button>
				<button onClick={() => this.insert(t, DocKind.math)} title="Add formula after">∑+</button>
				<button onClick={() => this.insert(t, DocKind.section)} title="Add section after">§+</button>
				<button onClick={() => this.move(t, 'up')} title="Up">↑</button>
				<button onClick={() => this.move(t, 'down')} title="Down">↓</button>
				<button onClick={() => this.move(t, 'indent')} title="Indent">→</button>
				<button onClick={() => this.move(t, 'outdent')} title="Outdent">←</button>
				<button onClick={() => this.del(t)} title="Delete">✕</button>
			</>
		);
	}

	render() {
		const { committed, committedAt, error } = this.state;
		return (
			<div className="sd-doctree">
				<div className="sd-doctree-bar">
					<button onClick={() => this.commit()} disabled={committed}>Commit</button>
					<span>{committed ? `Committed ${committedAt ?? ''}` : 'Draft — not committed'}</span>
					{error && <span className="sd-error">{error}</span>}
				</div>
				{super.render() /* the tree, from this.state.forest */}
			</div>
		);
	}
}

export const AnDoctreeditor = withStyles({})(withWidth()(AnDoctreeditorComp));
export { AnDoctreeditorComp };
