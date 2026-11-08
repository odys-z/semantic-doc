import { AnElemFormatter, Anson, AnsonBody, AnsonResp, AnTreeNode, CRUD, DatasetOpts, DbRelations, ErrorCtx, LogAct, OnCommitOk, OnLoadOk, PageInf, PkVal, Protocol, Semantext, Semantier, StreeTier, TierCol, Tierec, type AnsonMsg, type SessionClient } from '@anclient/semantier';

/** type: 'io.oz.sdoc.DoctreeReq' */
export class DoctreeReq extends AnsonBody {
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

	docId?: string;
	/** createDoc, importDocx: io.oz.sdoc.DocInf */
	doc?: { type: string; title?: string };
	srcId?: string;
	/** update: io.oz.sdoc.DocNode */
	node?: Record<string, unknown>;
	rootId?: string;
	toParent?: string | null;
	toIndex?: number;
	nvs?: [string, string | null][];

	constructor(uri: string, a: string, opts: Partial<Omit<DoctreeReq, 'type' | 'a' | 'uri'>> = {}) {
		super({ type: DoctreeReq.__type__, a, uri });
		Object.assign(this, opts);
	}
}

/** a value of {@link DoctreeReq.A} */
export type Doctree_A = (typeof DoctreeReq.A)[keyof typeof DoctreeReq.A];

/** io.oz.sdoc.DocInf: doc header */
export class DocInf extends Anson {
	// docId: string;
	// title: string;
	// creator: string;
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

    constructor(readonly docId: string, title: string = '', creator: string = '') {
        super('io.oz.sdoc.DocInf');
    }
}

/**
 * Columns of a s_docnodes record, as AnTreeNode.node carries them: the server puts every
 * non-null column of the row, as a string (DatasetHelper.formatSemanticNode).
 */
export class DocNode extends Anson {
    static Kind = {
        doc: 'doc',
        section: 'section',
        p: 'p',
        math: 'math',
        image: 'image',
        ref: 'ref',
        tabl: "tabl"
    }

	nid!: string;
	docId!: string;
	/** null for root-level nodes */
	parentId: string | null | undefined;
	/** order among siblings */
	sort: number = 0;
	/** e.g. "02.01.03", sibling sorts joined by '.' */
	fullpath: string | undefined;
	/** see {@link Kind} */
	kind: string | undefined;

	/** Tiptap JSON (source), serialized; null for image / ref */
	tipjson: string | undefined;
	/** RST derived from {@link #json} */
    rst?: string;
	/** version of the json ->; rst serializer that produced {@link #rst}; 0 = not yet */
    rstver: number = 0;

	/** resource id (image / video on server), for {@link Kind#image} */
    resId?: string;
	/** referenced node id (subtree root, may be in another doc), for {@link Kind#ref} */
    refId?: string;

	/** docx origin, e.g. "body/2", from the importer; null if created in editor */
    src?: string;

	/** docx paragraph style, e.g. "heading 1", "Normal" */
    docxstyle?: string;

	/**
	 * Plain text, from the importer (a heading's title, a paragraph's text);
	 * the client builds {@link #json} from it while json is null.
	 */
    ptext?: string;

	/**
	 * The importer's facts of a paragraph's runs, {"v":1,"runs":[...]}, see {@link io.oz.sdoc.imports.Run};
	 * the client builds {@link #json} from them.
	 */
    facts?: string;
}

/** a tree node as the server sends it */
export type DocTreeNode = AnTreeNode & { node: AnTreeNode['node'] & DocNode & { children?: DocTreeNode[] } };

/** io.oz.sdoc.DoctreeResp, extending AnDatasetResp: the fields as they come */
export class DoctreeResp extends AnsonResp {
	static __type__ = 'io.oz.sdoc.DoctreeResp';
	constructor(json: any) {
		super(json);
		Object.assign(this, json);
	}
}
Protocol.registerBody(DoctreeResp.__type__, (json) => new DoctreeResp(json));

export class StreeDoctier implements StreeTier {
	/**
	 * @param client logged-in client
	 * @param uri function uri, e.g. "/album/sys" (no session check on the server for now)
	 * @param port e.g. SynDocollPort.doctree
	 */
	constructor(readonly client: SessionClient, readonly uri: string, readonly port: string) {}

	private send(a: string, opts: ConstructorParameters<typeof DoctreeReq>[2], ok: OnCommitOk, err?: ErrorCtx) {
		const req = this.client.userReq(this.uri, this.port, new DoctreeReq(this.uri, a, opts));
		this.client.commit(req,
			(resp: AnsonMsg<AnsonResp>) => ok(resp.Body() as unknown as DoctreeResp),
			{ onError: (code: string, resp: AnsonMsg<AnsonResp>) => {
				const msg = (resp?.Body?.() as AnsonResp | undefined)?.msg?.();
				err ? err(msg || code) : console.error('[doctree]', code, msg);
			} });
	}

	/** r/docs: the docs' headers */
	docs(ok: OnCommitOk, err?: ErrorCtx) {
		this.send(DoctreeReq.A.docs, {}, ok, err);
	}

	/**
	 * c/import: import a docx of the album, answering the header (state parsing) at once.
	 * @param synuid the docx record's io_oz_synuid
	 */
	importDocx(synuid: string, title: string | undefined, ok: OnCommitOk, err?: ErrorCtx) {
		this.send(DoctreeReq.A.importDocx, { srcId: synuid, doc: { type: 'io.oz.sdoc.DocInf', title } }, ok, err);
	}

	/**
	 * Ask r/docs every interval ms until the doc is parsed.
	 * @param ok the header, state ready
	 * @param err also on state failed (with the server's reason), or when tries run out
	 */
	whenParsed(docId: string, ok: (doc: DocInf) => void, err?: ErrorCtx, interval = 1500, tries = 80) {
		const ask = (left: number) => this.docs((resp) => {
			const doc = resp.docs?.find((d) => d.docId === docId);
			if (!doc) err?.(`no such doc: ${docId}`);
			else if (doc.state === 'ready') ok(doc);
			else if (doc.state === 'failed') err?.(`Importing failed: ${doc.msg ?? ''}`);
			else if (left > 0) setTimeout(() => ask(left - 1), interval);
			else err?.(`${docId} is still being parsed, try again later.`);
		}, err);
		ask(tries);
	}


}
