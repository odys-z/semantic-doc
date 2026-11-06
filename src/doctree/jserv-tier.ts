/**
 * The doc-tree tier on a synode (doctree.jserv's DoctreeTier, port "doctree", doctree.less),
 * over semantier's SessionClient.
 *
 * The host app must register the port, e.g. album-web's AnReactExt.ExtendPort(SynDocollPort),
 * with SynDocollPort.doctree = "doctree.less".
 *
 * Errors are the server's message (exSemantic: a request that can't be served, for users),
 * passed to the OnErr of each call; the editor shows them in its banner / snackbar.
 */
import { AnsonBody, AnsonResp, Protocol, type AnsonMsg, type SessionClient } from '@anclient/semantier';
import {
	DoctreeReq, type DocCols, type DocInf, type DoctreeResp, type DoctreeTier, type OnErr, type OnOk, type TreeModify,
} from './doctree-tier';

/** io.oz.sdoc.DoctreeReq on the wire, extending AnDatasetReq */
export class DoctreeReqBody extends AnsonBody {
	static __type__ = DoctreeReq.__type__;

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

	constructor(uri: string, a: string, opts: Partial<Omit<DoctreeReqBody, 'type' | 'a' | 'uri'>> = {}) {
		super({ type: DoctreeReqBody.__type__, a, uri });
		Object.assign(this, opts);
	}
}

/** io.oz.sdoc.DoctreeResp, extending AnDatasetResp: the fields as they come */
export class DoctreeRespBody extends AnsonResp {
	static __type__ = 'io.oz.sdoc.DoctreeResp';
	constructor(json: any) {
		super(json);
		Object.assign(this, json);
	}
}
Protocol.registerBody(DoctreeRespBody.__type__, (json) => new DoctreeRespBody(json));

/** a node as the server's DocNode reads it: only its own fields, typed */
const docNode = (n: DocCols) => ({
	type: 'io.oz.sdoc.DocNode',
	nid: n.nid, docId: n.docId,
	json: n.json, rst: n.rst, rstver: Number(n.rstver ?? 0),
});

export class JservDoctier implements DoctreeTier {
	/**
	 * @param client logged-in client
	 * @param uri function uri, e.g. "/album/sys" (no session check on the server for now)
	 * @param port default "doctree"
	 */
	constructor(readonly client: SessionClient, readonly uri: string, readonly port = 'doctree') {}

	private send(a: string, opts: ConstructorParameters<typeof DoctreeReqBody>[2], ok: OnOk, err?: OnErr) {
		const req = this.client.userReq(this.uri, this.port, new DoctreeReqBody(this.uri, a, opts));
		this.client.commit(req,
			(resp: AnsonMsg<AnsonResp>) => ok(resp.Body() as unknown as DoctreeResp),
			{ onError: (code: string, resp: AnsonMsg<AnsonResp>) => {
				const msg = (resp?.Body?.() as AnsonResp | undefined)?.msg?.();
				err ? err(msg || code) : console.error('[doctree]', code, msg);
			} });
	}

	/** r/docs: the docs' headers */
	docs(ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.docs, {}, ok, err);
	}

	/**
	 * c/import: import a docx of the album, answering the header (state parsing) at once.
	 * @param synuid the docx record's io_oz_synuid
	 */
	importDocx(synuid: string, title: string | undefined, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.importDocx, { srcId: synuid, doc: { type: 'io.oz.sdoc.DocInf', title } }, ok, err);
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
			else if (doc.state === 'ready') ok(doc);
			else if (doc.state === 'failed') err?.(`Importing failed: ${doc.msg ?? ''}`);
			else if (left > 0) setTimeout(() => ask(left - 1), interval);
			else err?.(`${docId} is still being parsed, try again later.`);
		}, err);
		ask(tries);
	}

	tree(docId: string, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.tree, { docId }, ok, err);
	}

	update(docId: string, node: DocCols, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.update, { docId, node: docNode(node) }, ok, err);
	}

	modify(docId: string, req: TreeModify, ok: OnOk, err?: OnErr) {
		const { a, ...fields } = req;
		this.send(a, { docId, ...fields }, ok, err);
	}

	commit(docId: string, ok: OnOk, err?: OnErr) {
		this.send(DoctreeReq.A.commit, { docId }, ok, err);
	}
}
