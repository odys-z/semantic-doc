/**
 * The doc-content tier, client side.
 *
 * Wire shapes are doctree.jserv's io.oz.sdoc.DoctreeReq / DoctreeResp, which extend
 * AnDatasetReq / AnDatasetResp for semantic-jserv's s-tree modification (SemanticTree.onMoveTree,
 * onInsertTree, onDelTree; test data: semantic.jserv/src/test/res/stree/stree-cases.json):
 *
 *   request   {a, docId, node, rootId, toParent, toIndex, nvs}   a: one of DoctreeReq.A
 *   response  {doc, docs, root, forest, rejected, newId}        root: reload root, null for the whole forest
 *
 * The server owns the tree: it sends forests (built by DatasetHelper.buildForest) and
 * reshapes on every change. The client only works out *where* a node goes
 * ({@link moveReq}, {@link insertReq}), never renumbers or rebuilds.
 *
 * Each doc is one tree: a root node (kind doc, id = docId) with sections and blocks
 * under it, so a change inside a doc never reloads other docs.
 */
import type { AnTreeNode } from '@anclient/semantier';

/** node kinds, io.oz.sdoc.DocNode.Kind (+ doc: the doc root) */
export enum DocKind {
	doc = 'doc',
	section = 'section',
	p = 'p',
	math = 'math',
	image = 'image',
	ref = 'ref',
}

/**
 * Requests of the doc-tree tier, io.oz.sdoc.DoctreeReq (extending AnDatasetReq).
 * A: the values of a(), the same as the Java DoctreeReq.A.
 */
export class DoctreeReq {
	static __type__ = 'io.oz.sdoc.DoctreeReq';

	static A = {
		/** list docs (headers only) → docs */
		docs: 'r/docs',
		/** load a doc: the doc root with its subtree → doc, forest */
		tree: 'r/tree',
		/** save a node's content (node: json + rst) → doc */
		update: 'u/node',
		/** move rootId under toParent at toIndex (AnDatasetReq.A.umove) → root, forest, rejected */
		move: 'u/move',
		/** a new node under toParent at toIndex, columns nvs (AnDatasetReq.A.insrt) → newId, root, forest */
		insert: 'i',
		/** delete rootId with its subtree (AnDatasetReq.A.del) → root, forest */
		del: 'd',
		/** mark the doc's work as finished → doc */
		commit: 'c/commit',
		/** create a doc header with its root node → doc */
		createDoc: 'c/doc',
		/** import a docx (srcId: its record id): answers the header (state parsing) at once */
		importDocx: 'c/import',
	} as const;
}

/** a value of {@link DoctreeReq.A} */
export type DoctreeA = (typeof DoctreeReq.A)[keyof typeof DoctreeReq.A];

/** io.oz.sdoc.DocInf: doc header */
export interface DocInf {
	docId: string;
	title: string;
	creator: string;
	/** importing: 'parsing' | 'ready' | 'failed' */
	state?: string | null;
	/** why the import failed */
	msg?: string | null;
	/** the source docx's record id (h_photos); null if created in the editor */
	srcId?: string | null;
	nextNid?: number;
	/** compiled html uri; null if never built */
	htmlUri?: string | null;
	builtAt?: string | null;
	builtBy?: string | null;
	/** true if changed since the last commit */
	dirty?: boolean;
	/** time of the last commit */
	committedAt?: string | null;
}

/**
 * Columns of a s_docnodes record, as AnTreeNode.node carries them: the server puts every
 * non-null column of the row, as a string (DatasetHelper.formatSemanticNode).
 */
export interface DocCols {
	nid: string;
	docId: string;
	parentId?: string;
	sort: string;
	fullpath: string;
	kind: DocKind | string;
	/**
	 * Tiptap JSON, kept serialized (escaped) on the wire and in the db, with every "type"
	 * key stored as "contype": Anson reads "type" as a class name. Read with
	 * {@link toTiptap}, write with {@link fromTiptap}.
	 */
	json?: string;
	/** derived from json: { body, roleDecls, substitutions, targets }, serialized */
	rst?: string;
	rstver?: string;
	resId?: string;
	refId?: string;
	src?: string;
	style?: string;
	/** plain text; for a ref: the referenced node's title (TODO jserv: joined on loading) */
	text?: string;
	/** 'true': other docs reference this node (TODO jserv: computed on loading) */
	referenced?: string;
	/** 'false': this synode can't present the node, e.g. a ref to a doc not synchronized */
	avail?: string;
}

/** a tree node as the server sends it */
export type DocTreeNode = AnTreeNode & { node: AnTreeNode['node'] & DocCols & { children?: DocTreeNode[] } };

/** structure modification, AnDatasetReq fields */
export interface TreeModify {
	a: typeof DoctreeReq.A.move | typeof DoctreeReq.A.insert | typeof DoctreeReq.A.del;
	/** the node moved / deleted; none for insert (auto key, TODO jserv) */
	rootId?: string;
	/** null: top level */
	toParent?: string | null;
	/** final position among toParent's children, counted after the node is detached */
	toIndex?: number;
	/** insert: other columns, [name, value] */
	nvs?: [string, string | null][];
}

/** io.oz.sdoc.DoctreeResp, extending AnDatasetResp */
export interface DoctreeResp {
	doc?: DocInf;
	/** r/docs: the docs' headers */
	docs?: DocInf[];
	/** root of the reloaded subtree in forest; null: forest is the whole tree */
	root?: string | null;
	forest?: DocTreeNode[];
	/** the change is refused, the table unchanged: 'no-op', 'cycle' */
	rejected?: string | null;
	/** insert: the generated key of the new node */
	newId?: string;
}

/** a button's direction, turned into a {@link TreeModify} by {@link moveReq} */
export type MoveTo = 'up' | 'down' | 'indent' | 'outdent';

export type OnOk = (resp: DoctreeResp) => void;
export type OnErr = (msg: string) => void;

/** Requests of the doc-content tier. TODO: the jserv one, on semantier's StreeTier. */
export interface DoctreeTier {
	/** r/tree: the doc root with its subtree */
	tree(docId: string, ok: OnOk, err?: OnErr): void;
	/** u/node: save json + rst of one node; answers the doc header, no forest */
	update(docId: string, node: DocCols, ok: OnOk, err?: OnErr): void;
	/** u/move, i, d: answers the reloaded subtree */
	modify(docId: string, req: TreeModify, ok: OnOk, err?: OnErr): void;
	/** TODO jserv c/commit: mark the doc's work as finished */
	commit(docId: string, ok: OnOk, err?: OnErr): void;
}

// ---- stored json <-> Tiptap ---------------------------------------------------

/** rename one key at every level of a json value */
function rekey(v: unknown, from: string, to: string): unknown {
	if (Array.isArray(v)) return v.map((x) => rekey(x, from, to));
	if (v && typeof v === 'object')
		return Object.fromEntries(Object.entries(v).map(([k, x]) => [k === from ? to : k, rekey(x, from, to)]));
	return v;
}

/** stored DocCols.json ("contype") -> Tiptap JSON ("type") */
export const toTiptap = <T = unknown>(json: string): T => rekey(JSON.parse(json), 'contype', 'type') as T;

/** Tiptap JSON ("type") -> stored DocCols.json ("contype") */
export const fromTiptap = (json: unknown): string => JSON.stringify(rekey(json, 'type', 'contype'));

// ---- where a node goes ------------------------------------------------------

/** a node with its parent and position, found in a forest */
export interface Located {
	t: DocTreeNode;
	parent?: DocTreeNode;
	/** siblings, the parent's children or the forest */
	sibs: DocTreeNode[];
	ix: number;
}

export function locate(forest: DocTreeNode[], id: string, parent?: DocTreeNode): Located | undefined {
	for (let ix = 0; ix < forest.length; ix++) {
		const t = forest[ix];
		if (t.id === id) return { t, parent, sibs: forest, ix };
		const kids = t.node.children;
		const found = kids && locate(kids, id, t);
		if (found) return found;
	}
	return undefined;
}

/** sections and doc roots hold children */
export const isContainer = (t?: DocTreeNode) =>
	t?.node.kind === DocKind.section || t?.node.kind === DocKind.doc;

/**
 * The u/move request for a button, or undefined where the move isn't possible:
 * a doc root, up / down at an end, indent without a section above,
 * outdent out of the doc root.
 *
 *   up / down  → toParent = parent,        toIndex = ix ∓ 1
 *   indent     → toParent = prevSibling,   toIndex = prevSibling.children.length
 *   outdent    → toParent = grandparent,   toIndex = ix(parent) + 1
 */
export function moveReq(forest: DocTreeNode[], id: string, to: MoveTo): TreeModify | undefined {
	const at = locate(forest, id);
	if (!at || !at.parent) return undefined; // doc roots stay
	const { parent, sibs, ix } = at;
	const move = (toParent: DocTreeNode, toIndex: number): TreeModify =>
		({ a: DoctreeReq.A.move, rootId: id, toParent: toParent.id, toIndex });

	switch (to) {
		case 'up':
			return ix > 0 ? move(parent, ix - 1) : undefined;
		case 'down':
			return ix < sibs.length - 1 ? move(parent, ix + 1) : undefined;
		case 'indent': {
			const prev = sibs[ix - 1];
			return isContainer(prev) ? move(prev, prev.node.children?.length ?? 0) : undefined;
		}
		case 'outdent': {
			const p = locate(forest, parent.id);
			return p?.parent ? move(p.parent, p.ix + 1) : undefined;
		}
	}
}

/**
 * The insert request for a new node of kind, from the row `at`:
 *  - a doc root: last child;
 *  - a section, a block kind: first child (a paragraph goes into the section);
 *  - otherwise: the next sibling.
 * The id is generated by the server.
 */
export function insertReq(forest: DocTreeNode[], at: string, kind: DocKind): TreeModify | undefined {
	const l = locate(forest, at);
	if (!l) return undefined;
	const nvs: [string, string | null][] = [['kind', kind], ['docId', l.t.node.docId]];
	if (l.t.node.kind === DocKind.doc)
		return { a: DoctreeReq.A.insert, toParent: l.t.id, toIndex: l.t.node.children?.length ?? 0, nvs };
	if (l.t.node.kind === DocKind.section && kind !== DocKind.section)
		return { a: DoctreeReq.A.insert, toParent: l.t.id, toIndex: 0, nvs };
	return l.parent ? { a: DoctreeReq.A.insert, toParent: l.parent.id, toIndex: l.ix + 1, nvs } : undefined;
}

/** true if the node or one in its subtree is referenced by other docs */
export function referencedIn(t: DocTreeNode): boolean {
	return t.node.referenced === 'true' || (t.node.children ?? []).some(referencedIn);
}

/**
 * Received forests: semantic-DA's IndentFlag says 'space' where semantier's
 * IndentName says 'spacex'. TODO semantier: accept 'space', then drop this.
 */
export function received(forest: DocTreeNode[] | undefined): DocTreeNode[] {
	const fix = (t: DocTreeNode) => {
		t.indents = t.indents?.map((i) => ((i as string) === 'space' ? 'spacex' : i));
		t.node.children?.forEach(fix);
	};
	(forest ?? []).forEach(fix);
	return forest ?? [];
}
