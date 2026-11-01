/**
 * The doc-content tier, client side. Types mirror jserv's io.oz.sdoc
 * (DocInf, DocNode, DoctreeReq, DoctreeResp).
 *
 * The server owns the tree: it answers a load with the forest, and every
 * change with the changed subtree. No ServPort yet, so the demo uses
 * StubDoctreeTier (demo/stub-tier.ts), which plays the server over
 * hard-coded json files.
 */
import type { AnTreeNode } from '@anclient/semantier';

/** io.oz.sdoc.DocNode.Kind */
export enum DocKind {
	section = 'section',
	p = 'p',
	math = 'math',
	image = 'image',
	ref = 'ref',
}

/** io.oz.sdoc.DocInf: doc header */
export interface DocInf {
	docId: string;
	title: string;
	creator: string;
	/** compiled html uri; null if never built */
	htmlUri?: string | null;
	builtAt?: string | null;
	builtBy?: string | null;
	/** TODO jserv: true if changed since the last commit */
	dirty?: boolean;
	/** TODO jserv: time of the last commit */
	committedAt?: string | null;
}

/** io.oz.sdoc.DocNode: content record, one tree node */
export interface DocNode {
	nid: string;
	docId: string;
	parentId: string | null;
	sort: number;
	fullpath: string;
	kind: DocKind | string;
	/** Tiptap JSON, serialized (Anson's "type" collides with Tiptap's) */
	json: string | null;
	/** derived from json: { body, roleDecls, substitutions, targets }, serialized */
	rst: string | null;
	rstver: number;
	resId?: string | null;
	refId?: string | null;
	src?: string | null;
	style?: string | null;
	/** TODO jserv: other docs reference this node or one of its ancestors */
	referenced?: boolean;
}

/** Tree node as the server sends it: AnTreeNode with the record in node.rec. */
export type DocTreeNode = AnTreeNode & {
	node: AnTreeNode['node'] & {
		rec: DocNode;
		/** title of a section / ref, plain text */
		text: string;
		/** false: this synode can't present the node (e.g. a ref to a doc not synchronized) */
		avail?: boolean;
	};
};

/** io.oz.sdoc.DoctreeResp */
export interface DoctreeResp {
	doc: DocInf;
	/** TODO jserv: load: the whole forest; change: the changed subtree(s) */
	forest: DocTreeNode[];
	/** TODO jserv: the changed subtree is the whole forest (a top-level change) */
	root?: boolean;
	/** TODO jserv: insert: nid of the new node */
	nid?: string;
}

/** where a node moves, io.oz.sdoc.DoctreeReq.A.move */
export type MoveTo = 'up' | 'down' | 'indent' | 'outdent';

export type OnOk = (resp: DoctreeResp) => void;
export type OnErr = (msg: string) => void;

/** Requests of the doc-content tier, one per DoctreeReq.A action. */
export interface DoctreeTier {
	/** r/tree */
	tree(docId: string, ok: OnOk, err?: OnErr): void;
	/** u/node: save json + rst of one node */
	update(node: DocNode, ok: OnOk, err?: OnErr): void;
	/** c/node: new node after `after` (null: last at top level) */
	insert(docId: string, after: string | null, kind: DocKind, ok: OnOk, err?: OnErr): void;
	/** u/move */
	move(docId: string, nid: string, to: MoveTo, ok: OnOk, err?: OnErr): void;
	/** d/nodes: delete with subtrees */
	del(docId: string, nids: string[], ok: OnOk, err?: OnErr): void;
	/** TODO jserv c/commit: mark the doc's work as finished */
	commit(docId: string, ok: OnOk, err?: OnErr): void;
}
