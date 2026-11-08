/**
 * doctree/doc-stree: requests as doctree.jserv reads them, the client's copy of the
 * table's rules (which buttons are greyed out), and replacing a received subtree.
 * Not serializer cases, so they have their own runner, runDocstree().
 */
import { AnsonMsg, TreeModify } from '@anclient/semantier';
import {
	DocNode, DoctreeReq, DoctreeResp, insertMod, moveMod, removeMod, replaced, type DocTreeNode, type MoveOp,
} from '../../doctree/doc-stree';
import type { Result } from '../runner';

/** a tree node, server shaped: node fields as strings */
const t = (nid: string, kind: string, children?: DocTreeNode[], level = 0): DocTreeNode =>
	({ type: 'io.odysz.semantic.DA.DatasetCfg$AnTreeNode', id: nid, level,
	   node: { nid, docId: 'd', sort: '1', kind, ptext: nid, children } }) as unknown as DocTreeNode;

/**
 *  d   doc
 *    s1  section: p1, p2
 *    p3  p
 */
const forest = () => [t('d', 'doc', [t('s1', 'section', [t('p1', 'p', [], 2), t('p2', 'p', [], 2)], 1), t('p3', 'p', [], 1)])];

/** the buttons not greyed out of each node, e.g. "p1: down outdent remove" */
const enabled = (f: DocTreeNode[], nid: string) => {
	const ops: MoveOp[] = ['up', 'down', 'indent', 'outdent'];
	return [nid + ':', ...ops.filter((op) => moveMod(f, nid, op)), ...(removeMod(f, nid) ? ['remove'] : [])].join(' ');
};

const json = (o: unknown) => JSON.stringify(JSON.parse(JSON.stringify(o)));

const cases: { name: string; actual: () => string; expected: string }[] = [
	{
		name: 'doc-stree: u/tree request, as doctree.jserv reads it',
		actual: () => {
			const r = JSON.parse(JSON.stringify(new DoctreeReq('/sdoc', DoctreeReq.A.modify, { docId: 'd', mod: TreeModify.indent('p3') })));
			return json({ type: r.type, a: r.a, uri: r.uri, docId: r.docId, mod: r.mod });
		},
		expected: '{"type":"io.oz.sdoc.DoctreeReq","a":"u/tree","uri":"/sdoc","docId":"d",'
				+ '"mod":{"type":"io.odysz.semantic.ext.TreeModify","op":"indent","nid":"p3"}}',
	},
	{
		name: 'doc-stree: u/node request, a DocNode',
		actual: () => json(new DoctreeReq('/sdoc', DoctreeReq.A.update,
				{ docId: 'd', node: new DocNode({ nid: 'p1', tipjson: '{}', rstext: '{}', rstver: 1 }) }).node),
		expected: '{"type":"io.oz.sdoc.DocNode","nid":"p1","tipjson":"{}","rstext":"{}","rstver":1}',
	},
	{
		name: 'doc-stree: greyed out by the rules (holders doc, section; fixed roots)',
		actual: () => ['d', 's1', 'p1', 'p2', 'p3'].map((n) => enabled(forest(), n)).join(' | '),
		expected: 'd: | s1: down remove | p1: down outdent remove | p2: up outdent remove | p3: up indent remove',
	},
	{
		name: 'doc-stree: insert, where it goes',
		actual: () => {
			const f = forest();
			const at = (nid: string, kind: 'p' | 'section') => { const m = insertMod(f, nid, kind); return m ? `${m.at} ${m.nid}` : '-'; };
			return [at('d', 'section'), at('s1', 'p'), at('s1', 'section'), at('p1', 'p')].join(', ');
		},
		expected: 'last d, first s1, after s1, after p1',
	},
	{
		name: 'doc-stree: the received subtree replaces the node, in its place',
		actual: () => {
			const f = forest();
			const resp = new DoctreeResp({ root: 's1', forest: [t('s1', 'section', [t('p2', 'p'), t('p1', 'p')])] });
			const g = replaced(f, resp)!;
			const s1 = g[0].node.children![0];
			return `${s1.node.children!.map((k) => k.id).join(' ')}; levels ${s1.level} ${s1.node.children![0].level}`;
		},
		expected: 'p2 p1; levels 1 2',
	},
	{
		name: 'doc-stree: the whole forest, or reload',
		actual: () => {
			const whole = replaced(forest(), new DoctreeResp({ forest: [t('d', 'doc')] }))!;
			const lost = replaced(forest(), new DoctreeResp({ root: 'x', forest: [t('x', 'p')] }));
			return `${whole.length} ${whole[0].node.children ?? 'no children'}; ${lost === undefined ? 'reload' : 'patched'}`;
		},
		expected: '1 no children; reload',
	},
	{
		name: 'doc-stree: DoctreeResp from the server, rejected',
		actual: () => {
			const msg = new AnsonMsg({ type: 'io.odysz.semantic.jprotocol.AnsonMsg', code: 'ok', port: 'doctree.serv',
				body: [{ type: 'io.oz.sdoc.DoctreeResp', rejected: 'holder', forest: null, doc: { docId: 'd', dirty: true } }] });
			const r = msg.Body() as DoctreeResp;
			return `${r instanceof DoctreeResp} ${r.rejected === TreeModify.Rejected.holder} ${r.doc?.dirty}`;
		},
		expected: 'true true true',
	},
];

export function runDocstree(): Result[] {
	return cases.map((c) => {
		try {
			const actual = c.actual();
			return { name: c.name, pass: actual === c.expected, expected: c.expected, actual, rst: '' };
		} catch (e) {
			return { name: c.name, pass: false, expected: c.expected, actual: '', rst: '', error: String(e) };
		}
	});
}
