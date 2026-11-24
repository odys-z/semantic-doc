/**
 * Plays the doc-tree server over hard-coded json files (demo/jsons). The files are what the server would answer to r/tree:
 * the doc root with its subtree, node fields as strings.
 *
 * Text saves and commits work on an in-memory copy. Structure changes need the
 * server's reshaping, so they are answered with an error naming the request.
 *
 * A StreeDoctier answering its requests itself: it has no client, and the requests it doesn't
 * override (e.g. publish) are never enabled by the demo's docs (no html package).
 */
import type { SessionClient, TreeModify } from '@anclient/semantier';
import { DoctreeResp, DocNode, StreeDoctier, locate, type OnErr, type OnOk, type RstFile } from '../doctree';

/** urls of the json files; webpack emits them as assets */
export const stubDocs: Record<string, string> = {
	'doc-a': new URL('./jsons/doc-a.json', import.meta.url).href,
	'doc-b': new URL('./jsons/doc-b.json', import.meta.url).href,
};

const clone = <T,>(o: T): T => JSON.parse(JSON.stringify(o));

/** a modification as one line, e.g. "indent a2.3", "insert after a1 p" */
export const describe = (q: TreeModify) =>
	[q.op, q.at, q.nid, q.nvs?.find((nv) => nv[0] === 'kind')?.[1]].filter(Boolean).join(' ');

export class StubDoctreeTier extends StreeDoctier {
	/** the "server's" copy of each doc */
	copies = new Map<string, Promise<DoctreeResp>>();
	/** answer delay, ms */
	delay = 120;
	/** demo: answer text saves with an error */
	failSaves = false;
	/** every structure request, for the demo page */
	log: TreeModify[] = [];
	onLog?: (log: TreeModify[]) => void;

	constructor() {
		super(undefined as unknown as SessionClient, '/demo/doctree', 'doctree.serv');
	}

	private doc(docId: string) {
		if (!this.copies.has(docId)) {
			const url = stubDocs[docId];
			this.copies.set(docId, url
				? fetch(url).then((r) => (r.ok ? r.json() : Promise.reject(`${r.status} ${url}`)))
				: Promise.reject(`no such doc: ${docId}`));
		}
		return this.copies.get(docId)!;
	}

	/** answer like a server: later, a copy */
	private answer(docId: string, ok: OnOk, err: OnErr | undefined, f: (d: DoctreeResp) => DoctreeResp) {
		this.doc(docId)
			.then((d) => setTimeout(() => ok(new DoctreeResp(clone(f(d)))), this.delay))
			.catch((e) => err?.(String(e)));
	}

	override tree(docId: string, ok: OnOk, err?: OnErr) {
		this.answer(docId, ok, err, (d) => new DoctreeResp({ doc: d.doc, forest: d.forest }));
	}

	override update(docId: string, node: DocNode, ok: OnOk, err?: OnErr) {
		if (this.failSaves) {
			setTimeout(() => err?.('server unreachable (simulated)'), this.delay);
			return;
		}
		this.answer(docId, ok, err, (d) => {
			const n = locate(d.forest ?? [], node.nid!)?.t.node;
			if (n) Object.assign(n, { tipjson: node.tipjson, rstext: node.rstext, rstver: String(node.rstver) });
			d.doc!.dirty = true;
			return new DoctreeResp({ doc: d.doc, forest: [] });
		});
	}

	override modify(_docId: string, req: TreeModify, _ok: OnOk, err?: OnErr) {
		this.log = [...this.log, req];
		this.onLog?.(this.log);
		setTimeout(() => err?.(`${describe(req)}: structure changes need the server (doctree.serv)`), this.delay);
	}

	override commitDoc(docId: string, _rstFiles: RstFile[] | undefined, ok: OnOk, err?: OnErr) {
		this.answer(docId, ok, err, (d) => {
			d.doc!.dirty = false;
			d.doc!.committedAt = new Date().toLocaleString();
			return new DoctreeResp({ doc: d.doc, forest: [] });
		});
	}
}
