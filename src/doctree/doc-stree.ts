/**
 * The doc-tree tier, client side: types the same as doctree.jserv's (io.oz.sdoc), and
 * {@link StreeDoctier}, its requests through a SessionClient.
 *
 *   request   DoctreeReq  {a, docId, doc, srcId, node, mod, res, rstFiles, puckpage}   a: one of DoctreeReq.A
 *   response  DoctreeResp {doc, docs, root, forest, rejected, newId, build, buildlog, page}
 *
 * The server owns the tree: every tree change is a TreeModify as the user means it
 * (up, down, indent, outdent, insert, remove), and the server works out where nodes go
 * (semantic.jserv's SemanticTree.onModifyTree()). It answers the reloaded subtree under
 * root, or the whole forest when root is absent, which replaces the client's.
 *
 * The client keeps a copy of the table's rules ({@link Rules}, as S_DocNodeMeta's) only
 * to grey out what can't apply; the server checks them again.
 *
 * Each doc is one tree: a root node (kind doc, fullpath = its nid) with sections and blocks
 * under it, so a change inside a doc never reloads other docs.
 */
import {
	AnDatasetReq, AnDatasetResp, Anson, Protocol, TreeModify, StreeTier,
	type AnTreeNode, type AnsonMsg, type AnsonResp, type SessionClient,
} from '@anclient/semantier';
import type { PuckNode } from '../storytree/templates';

/**
 * io.oz.sdoc.SDocRef, a semantic.DA DocRef: doc header, of a doc tree or a story.
 * Only the fields the browser gets: DocRef's others, and SDocRef.resources, are exchanged
 * between the synode and its html-service only.
 */
export class DocInf extends Anson {
	static __type__ = 'io.oz.sdoc.SDocRef';

	/** io.oz.sdoc.SDocRef.State */
	static State = { parsing: 'parsing', ready: 'ready', failed: 'failed' } as const;

	/** DocRef.docId */
	docId?: string;
	title?: string;
	creator?: string;
	/** see {@link DocInf.State} */
	state?: string | null;
	/** why the import failed */
	msg?: string | null;
	/** compiled html uri; null if never built */
	htmlUri?: string | null;
	builtAt?: string | null;
	builtBy?: string | null;
	/** id of the template the html package was built with, e.g. "rtd-0.8"; null if never built */
	template?: string | null;
	/**
	 * published url path at html-service, without the leading '/', e.g. "pub/er3cou";
	 * null if not published
	 */
	pubpath?: string | null;
	/** true if changed since the last commit */
	dirty?: boolean;
	/** time of the last commit, null if never committed */
	committedAt?: string | null;
	/** What the doc's content is, see {@link DocInf.Kind}: story for a story (s_stories); null: a doc tree (s_docs) */
	mime?: string | null;
	/** a story's subtitle */
	subtitle?: string | null;
	/** a story's cover media, its io_oz_synuid, set when committed */
	cover?: string | null;

	/** values of {@link DocInf.mime} */
	static Kind = {
		/** a doc tree, paragraphs in RST, published as a Sphinx html package */
		doctree: 'doctree',
		/** a media story, a Puck page (page.json), published as a boilerplate at html-service */
		story: 'story',
	} as const;

	constructor(opts: { docId?: string, title?: string, creator?: string } = {}) {
		super(DocInf.__type__);
		Object.assign(this, opts);
	}
}

/**
 * io.oz.sdoc.rst.RstFile: one file of a doc's Sphinx source, assembled by rst-assembler.ts.
 * The files are the preview's source, and go with c/commit: the synode builds the doc's html
 * package from them (it never generates RST).
 */
export class RstFile extends Anson {
	static __type__ = 'io.oz.sdoc.rst.RstFile';

	/** the section (or doc root) the file is built from */
	nid!: string;
	/** path in the Sphinx source folder, "index.rst" for the doc root */
	path!: string;
	/** docname for toctrees: the path without ".rst" */
	name!: string;
	rst!: string;
	/** names of the resources the file's images link, in media/ beside it, e.g. ["image1.png"] */
	media!: string[];

	constructor(opts: { nid: string, path: string, name: string, rst: string, media: string[] }) {
		super(RstFile.__type__);
		Object.assign(this, opts);
	}
}

/**
 * io.oz.sdoc.docx.DocxNode: one node of a doc.
 *
 * tipjson is the Tiptap document serialized, with every "type" key stored as "contype":
 * Anson reads "type" as a class name. Read with {@link toTiptap}, write with {@link fromTiptap}.
 */
export class DocNode extends Anson {
	static __type__ = 'io.oz.sdoc.docx.DocxNode';

	/** io.oz.sdoc.DocNode.Kind */
	static Kind = {
		/** the doc root, fullpath = its nid (nodes imported before doctree.jserv 0.1.4: nid = docId) */
		doc: 'doc',
		section: 'section',
		p: 'p',
		math: 'math',
		/** media, content in resId */
		image: 'image',
		/** live, read-only reference to another doc's subtree, root refId */
		docref: 'docref',
		/** docx table: ptext, the importer's marker text; edited as a Tiptap table (editor/rst/table.ts) */
		tabl: 'tabl',
	} as const;

	nid?: string;
	docId?: string;
	/** null for root-level nodes */
	parentId?: string | null;
	/** order among siblings */
	sort?: number;
	/** e.g. "02.01.03", sibling sorts joined by '.' */
	fullpath?: string;
	/** see {@link DocNode.Kind} */
	kind?: string;
	/** Tiptap JSON (source), serialized, "contype" for "type"; null for image / docref */
	tipjson?: string;
	/** RST derived from tipjson: { body, roleDecls, substitutions, targets }, serialized */
	rstext?: string;
	/** version of the tipjson -> rstext serializer that produced rstext; 0 = not yet */
	rstver?: number;
	/** resource id, for {@link DocNode.Kind.image} */
	resId?: string;
	/** referenced node id, for {@link DocNode.Kind.docref} */
	refId?: string;
	/** docx origin, e.g. "body/2", from the importer; null if created in editor */
	src?: string;
	/** docx paragraph style, e.g. "heading 1", "Normal" */
	docxstyle?: string;
	/** plain text, from the importer: a heading's title, a paragraph's text */
	ptext?: string;
	/** the importer's facts of a paragraph's runs, {"v":1,"runs":[...]} (facts.ts) */
	facts?: string;

	constructor(opts: Partial<Omit<DocNode, 'type'>> = {}) {
		super(DocNode.__type__);
		Object.assign(this, opts);
	}
}

/** a value of {@link DocNode.Kind} */
export type DocKind = (typeof DocNode.Kind)[keyof typeof DocNode.Kind];

/**
 * A tree node as the server sends it: AnTreeNode.node carries the s_docnodes columns
 * (DatasetHelper.formatSemanticNode), every value a string, e.g. sort "2".
 */
export type DocTreeNode = AnTreeNode & {
	node: AnTreeNode['node'] & { [c in Exclude<keyof DocNode, 'type'>]?: string } & { children?: DocTreeNode[] };
};

/** io.oz.sdoc.tier.DoctreeReq, extending AnDatasetReq: tree changes are its mod, a TreeModify */
export class DoctreeReq extends AnDatasetReq {
	static __type__ = 'io.oz.sdoc.tier.DoctreeReq';

	/** io.oz.sdoc.tier.DoctreeReq.A */
	static A = {
		/** list docs (headers only) → docs */
		docs: 'r/docs',
		/** load a doc: the doc root with its subtree → doc, forest */
		tree: 'r/tree',
		/** save a node's content, node: tipjson + rstext → doc */
		update: 'u/node',
		/** modify the doc's tree as mod says → doc, root, forest (the reloaded subtree), rejected, newId */
		modify: 'u/tree',
		/** mark the doc's work as finished, rstFiles: the synode builds the html package from them → doc, build */
		commit: 'c/commit',
		/** the state of the doc's html build, started by commit → doc, build, buildlog */
		build: 'r/build',
		/** publish the doc's html package at the local html-service, or replace it → doc, with pubpath */
		publish: 'c/publish',
		/** remove the doc from html-service → doc, without pubpath */
		unpublish: 'd/publish',
		/** import a docx, srcId: answers the header (state parsing) at once */
		importDocx: 'c/import',
		/** a doc's resource file, res: a GET of Doctree206Serv (doctree206.serv), e.g. an img's src */
		res: 'r/res',
		/** the stories' headers (kind story) → docs */
		stories: 'r/stories',
		/** load a story's page → doc, page */
		page: 'r/page',
		/** save a story's page, puckpage; docId absent: a new story → doc */
		savePage: 'u/page',
		/**
		 * the pages published at html-service, of both trees → docs (with their resources):
		 * the local html-service's query (loopback, no session), not the browser's
		 */
		published: 'r/published',
	} as const;

	docId?: string;
	/** for importDocx: title etc. */
	doc?: DocInf;
	/** for importDocx: the docx's record id in the doc table (h_photos) */
	srcId?: string;
	/** for update: the node's columns, tipjson + rstext */
	node?: DocNode;
	/** for res: the resource's name in the doc's folder, e.g. "image1.png" */
	res?: string;
	/** for commit: the doc as RST files; absent: committed without a html build */
	rstFiles?: RstFile[];
	/**
	 * for savePage: the story's page, fromPuck()'s. Not named page, which is QueryReq's
	 * paging index, as Java's DoctreeReq.puckpage isn't to hide AnQueryReq.page.
	 */
	puckpage?: PuckNode;

	constructor(uri: string, a: Doctree_A, opts: { docId?: string, doc?: DocInf, srcId?: string, node?: DocNode, mod?: TreeModify, res?: string, rstFiles?: RstFile[], puckpage?: PuckNode } = {}) {
		super({ uri, type: DoctreeReq.__type__ });
		this.a = a; // after super: AnDatasetReq's TA() is for its own A
		Object.assign(this, opts);
	}
}

/** a value of {@link DoctreeReq.A} */
export type Doctree_A = (typeof DoctreeReq.A)[keyof typeof DoctreeReq.A];

/** io.oz.sdoc.tier.DoctreeResp, extending AnDatasetResp */
export class DoctreeResp extends AnDatasetResp {
	static __type__ = 'io.oz.sdoc.tier.DoctreeResp';

	doc?: DocInf;
	/** io.oz.sdoc.rst.HtmlBuilder.State */
	static Build = { building: 'building', built: 'built', failed: 'failed' } as const;

	/** r/docs: the docs' headers */
	docs?: DocInf[];
	declare forest: DocTreeNode[];
	/** c/commit, r/build: the state of the doc's html build, see {@link DoctreeResp.Build}; null: no build */
	build?: string | null;
	/** output of the build tool: warnings, or why it failed */
	buildlog?: string | null;
	/** r/page: the story's page, as saved (toPuck() for Puck) */
	page?: PuckNode | null;

	constructor(json: any) {
		super({ ...json, forest: json.forest, type: DoctreeResp.__type__ });
		this.doc = json.doc;
		this.docs = json.docs;
		this.build = json.build;
		this.buildlog = json.buildlog;
		this.page = json.page;
	}
}
Protocol.registerBody(DoctreeResp.__type__, (json) => new DoctreeResp(json));

export type OnOk = (resp: DoctreeResp) => void;
export type OnErr = (msg: string) => void;

/** base64 of a string's UTF-8 bytes (btoa() takes Latin-1 only) */
function base64(s: string): string {
	let bin = '';
	for (const b of new TextEncoder().encode(s)) bin += String.fromCharCode(b);
	return btoa(bin);
}

/** The doc-tree tier on a synode, doctree.jserv's DoctreeTier */
export class StreeDoctier extends StreeTier {
	/**
	 * @param client logged-in client
	 * @param uri function uri, e.g. "/album/sdoc"
	 * @param port the port's value, e.g. album-web's SynDocollPort.doctree, "doctree.serv"
	 * @param port206 the port of the docs' resources, doctree.jserv's Doctree206Serv, e.g.
	 * SynDocollPort.doctree206, "doctree206.serv"; absent: no resources shown
	 */
	constructor(readonly client: SessionClient, readonly uri: string, readonly port: string,
		readonly port206?: string) {
		super({uri, port});
	}

	/** urls of resources, so an img keeps its src across renders */
	private srcs = new Map<string, string>();

	private send(a: Doctree_A, opts: ConstructorParameters<typeof DoctreeReq>[2], ok: OnOk, err?: OnErr) {
		const req = this.client.userReq(this.uri, this.port, new DoctreeReq(this.uri, a, opts));
		this.client.commit(req,
			(resp: AnsonMsg<AnsonResp>) => ok(resp.Body() as DoctreeResp),
			{ onError: (code: string, resp: AnsonMsg<AnsonResp>) => {
				const msg = resp?.Body?.()?.msg?.();
				err ? err(msg || code) : console.error('[doctree]', code, msg);
			} });
	}

	/** r/docs: the docs' headers */
	docs(ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.docs, {}, ok, err);
	}

	tree(docId: string, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.tree, { docId }, ok, err);
	}

	update(docId: string, node: DocNode, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.update, { docId, node }, ok, err);
	}

	modify(docId: string, mod: TreeModify, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.modify, { docId, mod }, ok, err);
	}

	/**
	 * c/commit: a doc tree with its RST files, the synode building its html package from them;
	 * a story without (its page is committed by the synode) → resp.doc, build
	 */
	commitDoc(docId: string, rstFiles: RstFile[] | undefined, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.commit, { docId, rstFiles }, ok, err);
	}

	build(docId: string, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.build, { docId }, ok, err);
	}

	/**
	 * c/publish: the doc's html package, built since the last commit, is published at the
	 * synode's html-service, or replaces the published one; answers the header with pubpath.
	 */
	publish(docId: string, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.publish, { docId }, ok, err);
	}

	/** d/publish: the doc is removed from html-service; answers the header without pubpath */
	unpublish(docId: string, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.unpublish, { docId }, ok, err);
	}

	/** r/stories: the stories' headers → resp.docs */
	stories(ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.stories, {}, ok, err);
	}

	/** r/page: the story's page → resp.page */
	page(docId: string, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.page, { docId }, ok, err);
	}

	/**
	 * u/page: save the story's page, fromPuck()'s → resp.doc
	 * @param docId undefined: a new story, its id in resp.doc
	 */
	savePage(docId: string | undefined, page: PuckNode, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.savePage, { docId, puckpage: page }, ok, err);
	}

	/**
	 * r/res: a GET of Doctree206Serv, the request (with the session) as anson64,
	 * as anreact's GalleryView.imgSrcReq() does for album pictures; range requests resume.
	 */
	resSrc(docId: string, name: string): string | undefined {
		if (!this.port206) return undefined;
		const key = docId + '/' + name;
		let src = this.srcs.get(key);
		if (!src) {
			const msg = this.client.userReq(this.uri, this.port206,
				new DoctreeReq(this.uri, DoctreeReq.A.res, { docId, res: name }));
			// an app's port, e.g. SynDocollPort.doctree206 (servUrl() is typed with the built-in ports)
			const serv = this.client.an.servUrl(this.port206 as Parameters<typeof this.client.an.servUrl>[0]);
			src = `${serv}?anson64=${encodeURIComponent(base64(JSON.stringify(msg)))}`;
			this.srcs.set(key, src);
		}
		return src;
	}

	/**
	 * c/import: import a docx of the album, answering the header (state parsing) at once.
	 * @param synuid the docx record's io_oz_synuid
	 */
	importDocx(synuid: string, title: string | undefined, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.importDocx, { srcId: synuid, doc: new DocInf({ title }) }, ok, err);
	}

	/**
	 * Ask r/docs every interval ms until the doc is parsed.
	 * @param ok the header, state ready
	 * @param err also on state failed (with the server's reason), or when tries run out
	 */
	whenParsed(docId: string, ok: (doc: DocInf) => void, err?: OnErr, interval = 1500, tries = 80) {
		const ask = (left: number) => this.docs((resp) => {
			const doc = resp.docs?.find((d) => d.docId === docId);
			if (!doc) err?.(`no such doc: ${docId}`);
			else if (doc.state === DocInf.State.ready) ok(doc);
			else if (doc.state === DocInf.State.failed) err?.(`Importing failed: ${doc.msg ?? ''}`);
			else if (left > 0) setTimeout(() => ask(left - 1), interval);
			else err?.(`${docId} is still being parsed, try again later.`);
		}, err);
		ask(tries);
	}
}

// ---- the table's rules, a copy of S_DocNodeMeta's -----------------------------

/**
 * S_DocNodeMeta's rules (ITreeMeta): docs and sections hold children, the docs (top level)
 * are fixed. Only to grey out buttons; the server checks again and may reject.
 */
export const Rules = {
	holders: [DocNode.Kind.doc, DocNode.Kind.section] as string[],
	canHold: (kind?: string) => !!kind && Rules.holders.includes(kind),
	fixedRoots: true,
};

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

/** a node holds children, see {@link Rules} */
export const isContainer = (t?: DocTreeNode) => Rules.canHold(t?.node.kind);

/** a move button's op */
export type MoveOp = 'up' | 'down' | 'indent' | 'outdent';

/**
 * The modification for a move button, or undefined where the rules say it can't apply,
 * the same as SemanticTree.onModifyTree() would reject (noop, holder, fixed):
 *
 *   up / down  the first / last child can't
 *   indent     the previous sibling must hold children
 *   outdent    the parent must not be top level (fixed roots)
 */
export function moveMod(forest: DocTreeNode[], id: string, op: MoveOp): TreeModify | undefined {
	const at = locate(forest, id);
	if (!at || (Rules.fixedRoots && !at.parent)) return undefined;
	const { parent, sibs, ix } = at;
	switch (op) {
		case 'up':
			return ix > 0 ? TreeModify.up(id) : undefined;
		case 'down':
			return ix < sibs.length - 1 ? TreeModify.down(id) : undefined;
		case 'indent':
			return isContainer(sibs[ix - 1]) ? TreeModify.indent(id) : undefined;
		case 'outdent':
			return locate(forest, parent!.id)?.parent || !Rules.fixedRoots ? TreeModify.outdent(id) : undefined;
	}
}

/**
 * The modification inserting a new node of kind, from the row of id:
 *  - the doc root: its last child;
 *  - a section, kind not a section: its first child;
 *  - otherwise: the next sibling (not of the doc root: fixed roots).
 * The id is generated by the server, the docId set by the server.
 */
export function insertMod(forest: DocTreeNode[], id: string, kind: DocKind): TreeModify | undefined {
	const l = locate(forest, id);
	if (!l) return undefined;
	if (l.t.node.kind === DocNode.Kind.doc)
		return TreeModify.insert(id, 'last', 'kind', kind);
	if (l.t.node.kind === DocNode.Kind.section && kind !== DocNode.Kind.section)
		return TreeModify.insert(id, 'first', 'kind', kind);
	return l.parent || !Rules.fixedRoots ? TreeModify.insert(id, 'after', 'kind', kind) : undefined;
}

/** removing a node, undefined for a top-level one (fixed roots) */
export function removeMod(forest: DocTreeNode[], id: string): TreeModify | undefined {
	const l = locate(forest, id);
	return l && (l.parent || !Rules.fixedRoots) ? TreeModify.remove(id) : undefined;
}

// ---- the server's answer -------------------------------------------------------

/**
 * The forest after a tree modification: the received subtree replaces the node of the
 * same id (root), or the received forest replaces the whole one (root absent).
 * @return the new forest, or undefined if the answer can't be applied here (reload)
 */
export function replaced(forest: DocTreeNode[], resp: DoctreeResp): DocTreeNode[] | undefined {
	const sub = received(resp.forest);
	if (resp.root == null) return sub;
	const old = locate(forest, resp.root);
	if (!old || sub.length !== 1) return undefined;
	placeAs(sub[0], old.t);
	old.sibs[old.ix] = sub[0];
	return [...forest];
}

/** Received forests, as they are: semantier's IndentName is semantic-DA's IndentFlag ('space') */
export function received(forest: DocTreeNode[] | undefined): DocTreeNode[] {
	return forest ?? [];
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
			? [...p.indents.slice(0, -1), (p.islastSibling ?? (p as any).lastSibling) ? 'space' : 'vlink'] as DocTreeNode['indents']
			: [];
		kids.forEach((k, x) => {
			k.level = (p.level ?? 0) + 1;
			k.islastSibling = x === kids.length - 1;
			k.indents = [...base!, k.islastSibling ? 'childx' : 'childi'];
			down(k);
		});
	};
	down(sub);
}

// ---- stored tipjson <-> Tiptap -----------------------------------------------

/** rename one key at every level of a json value */
function rekey(v: unknown, from: string, to: string): unknown {
	if (Array.isArray(v)) return v.map((x) => rekey(x, from, to));
	if (v && typeof v === 'object')
		return Object.fromEntries(Object.entries(v).map(([k, x]) => [k === from ? to : k, rekey(x, from, to)]));
	return v;
}

/** stored tipjson ("contype") -> Tiptap JSON ("type") */
export const toTiptap = <T = unknown>(json: string): T => rekey(JSON.parse(json), 'contype', 'type') as T;

/** Tiptap JSON ("type") -> stored tipjson ("contype") */
export const fromTiptap = (json: unknown): string => JSON.stringify(rekey(json, 'type', 'contype'));
