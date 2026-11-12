/**
 * Plays the doc-tree server over hard-coded json files (demo/jsons). The files are what the server would answer to r/tree:
 * the doc root with its subtree, node fields as strings.
 *
 * Text saves and commits work on an in-memory copy. Structure changes need the
 * server's reshaping, so they are answered with an error naming the request.
 */
import type { TreeModify } from '@anclient/semantier';
import { DoctreeResp, DocNode, locate, type Doctier, type OnErr, type OnOk, type RstFile } from '../doctree';

/** urls of the json files; webpack emits them as assets */
export const stubDocs: Record<string, string> = {
	'doc-a': new URL('./jsons/doc-a.json', import.meta.url).href,
	'doc-b': new URL('./jsons/doc-b.json', import.meta.url).href,
};

const clone = <T,>(o: T): T => JSON.parse(JSON.stringify(o));

/** a modification as one line, e.g. "indent a2.3", "insert after a1 p" */
export const describe = (q: TreeModify) =>
	[q.op, q.at, q.nid, q.nvs?.find((nv) => nv[0] === 'kind')?.[1]].filter(Boolean).join(' ');

export class StubDoctreeTier implements Doctier {
	/** the "server's" copy of each doc */
	docs = new Map<string, Promise<DoctreeResp>>();
	/** answer delay, ms */
	delay = 120;
	/** demo: answer text saves with an error */
	failSaves = false;
	/** every structure request, for the demo page */
	log: TreeModify[] = [];
	onLog?: (log: TreeModify[]) => void;

	private doc(docId: string) {
		if (!this.docs.has(docId)) {
			const url = stubDocs[docId];
			this.docs.set(docId, url
				? fetch(url).then((r) => (r.ok ? r.json() : Promise.reject(`${r.status} ${url}`)))
				: Promise.reject(`no such doc: ${docId}`));
		}
		return this.docs.get(docId)!;
	}

	/** answer like a server: later, a copy */
	private answer(docId: string, ok: OnOk, err: OnErr | undefined, f: (d: DoctreeResp) => DoctreeResp) {
		this.doc(docId)
			.then((d) => setTimeout(() => ok(new DoctreeResp(clone(f(d)))), this.delay))
			.catch((e) => err?.(String(e)));
	}

	tree(docId: string, ok: OnOk, err?: OnErr) {
		this.answer(docId, ok, err, (d) => new DoctreeResp({ doc: d.doc, forest: d.forest }));
	}

	update(docId: string, node: DocNode, ok: OnOk, err?: OnErr) {
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

	modify(_docId: string, req: TreeModify, _ok: OnOk, err?: OnErr) {
		this.log = [...this.log, req];
		this.onLog?.(this.log);
		setTimeout(() => err?.(`${describe(req)}: structure changes need the server (doctree.serv)`), this.delay);
	}

	commit(docId: string, _rstFiles: RstFile[], ok: OnOk, err?: OnErr) {
		this.answer(docId, ok, err, (d) => {
			d.doc!.dirty = false;
			d.doc!.committedAt = new Date().toLocaleString();
			return new DoctreeResp({ doc: d.doc, forest: [] });
		});
	}
}
