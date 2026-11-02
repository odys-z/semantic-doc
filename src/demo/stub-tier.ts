/**
 * Plays the doc-content server over hard-coded json files (demo/jsons), until
 * SmandocServ serves them. The files are what the server would answer to r/tree:
 * the doc root with its subtree, node fields as strings.
 *
 * Text saves and commits work on an in-memory copy. Structure changes need the
 * server's reshaping, so they are answered with an error naming the request.
 */
import {
	locate, type DocCols, type DoctreeResp, type DoctreeTier, type OnErr, type OnOk, type TreeModify,
} from '../doctree';

/** urls of the json files; webpack emits them as assets */
export const stubDocs: Record<string, string> = {
	'doc-a': new URL('./jsons/doc-a.json', import.meta.url).href,
	'doc-b': new URL('./jsons/doc-b.json', import.meta.url).href,
};

const clone = <T,>(o: T): T => JSON.parse(JSON.stringify(o));

/** a request as one line, e.g. "u/move a2.3 → a2[0]" */
export const describe = (q: TreeModify) =>
	q.a === 'd' ? `d ${q.rootId}`
	: `${q.a} ${q.rootId ?? q.nvs?.find((nv) => nv[0] === 'kind')?.[1] ?? ''} → ${q.toParent ?? '(top)'}[${q.toIndex}]`;

export class StubDoctreeTier implements DoctreeTier {
	/** the "server's" copy of each doc */
	docs = new Map<string, Promise<DoctreeResp>>();
	/** answer delay, ms */
	delay = 120;
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
			.then((d) => setTimeout(() => ok(clone(f(d))), this.delay))
			.catch((e) => err?.(String(e)));
	}

	tree(docId: string, ok: OnOk, err?: OnErr) {
		this.answer(docId, ok, err, (d) => ({ doc: d.doc, root: null, forest: d.forest }));
	}

	update(docId: string, node: DocCols, ok: OnOk, err?: OnErr) {
		this.answer(docId, ok, err, (d) => {
			const n = locate(d.forest ?? [], node.nid)?.t.node;
			if (n) Object.assign(n, { json: node.json, rst: node.rst, rstver: node.rstver });
			d.doc!.dirty = true;
			return { doc: d.doc };
		});
	}

	modify(_docId: string, req: TreeModify, _ok: OnOk, err?: OnErr) {
		this.log = [...this.log, req];
		this.onLog?.(this.log);
		setTimeout(() => err?.(`${describe(req)}: structure changes need SmandocServ`), this.delay);
	}

	commit(docId: string, ok: OnOk, err?: OnErr) {
		this.answer(docId, ok, err, (d) => {
			d.doc!.dirty = false;
			d.doc!.committedAt = new Date().toLocaleString();
			return { doc: d.doc };
		});
	}
}
