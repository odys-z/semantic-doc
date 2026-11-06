/**
 * doctree/rst-assembler: a doc tree → RST files, one per section.
 * Not serializer cases (no Tiptap doc), so they have their own runner, runAssemble().
 */
import { assemble } from '../../doctree/rst-assembler';
import type { DocTreeNode } from '../../doctree/doctree-tier';
import type { Result } from '../runner';
import docA from '../../demo/jsons/doc-a.json';

/** a tree node, server shaped: node fields as strings */
const t = (nid: string, kind: string, cols: Record<string, string>, children?: DocTreeNode[]): DocTreeNode =>
	({ id: nid, node: { nid, docId: 'd', sort: '1', fullpath: '', kind, ...cols, children } }) as unknown as DocTreeNode;

/** stored rst of a paragraph, as AnDoctreeditor saves it */
const rst = (body: string, roleDecls: string[] = []) =>
	JSON.stringify({ body, roleDecls, substitutions: [], targets: [] });

const red = '.. role:: red\n   :class: red';

const doc = t('d', 'doc', { text: 'Annual Report' }, [
	t('d.1', 'p', { text: 'Preface * text' }),
	t('d.2', 'section', { text: '第一章 总则' }, [
		t('d.3', 'p', { text: 'ignored', rst: rst('Revenue :red:`grew`.', [red]) }),
		t('d.4', 'section', { text: '1.1 Scope' }, [t('d.5', 'p', { text: '1. not a list' })]),
		t('d.6', 'p', { text: 'After the sub-section, :red: again', rst: rst('Again :red:`red`.', [red]) }),
	]),
	t('d.7', 'section', { text: 'Empty' }),
]);

interface ACase { name: string; actual: () => string; expected: string }

const files = () => assemble(doc);
const file = (path: string) => files().find((f) => f.path === path)?.rst ?? '(no file)';

const cases: ACase[] = [
	{
		name: 'assemble: one file per section, depth-first',
		actual: () => files().map((f) => `${f.nid}:${f.path}`).join(' '),
		expected: 'd:index.rst d.2:d.2.rst d.4:d.4.rst d.7:d.7.rst',
	},
	{
		name: 'assemble: index = title, own blocks, toctree of top sections',
		actual: () => file('index.rst'),
		expected: 'Annual Report\n=============\n\nPreface \\* text\n\n.. toctree::\n   :maxdepth: 2\n\n   d.2\n   d.7\n',
	},
	{
		name: 'assemble: section keeps order, wide title underline, role declared once',
		actual: () => file('d.2.rst'),
		expected: `${red}\n\n第一章 总则\n===========\n\nRevenue :red:\`grew\`.\n\n.. toctree::\n   :maxdepth: 2\n\n   d.4\n\nAgain :red:\`red\`.\n`,
	},
	{
		name: 'assemble: text without rst is escaped, not a list',
		actual: () => file('d.4.rst'),
		expected: '1.1 Scope\n=========\n\n\\1. not a list\n',
	},
	{
		name: 'assemble: empty section',
		actual: () => file('d.7.rst'),
		expected: 'Empty\n=====\n',
	},
	{
		name: 'assemble: Tiptap json without rst is serialized (doc-a, a1)',
		actual: () => assemble((docA as unknown as { forest: DocTreeNode[] }).forest[0]).find((f) => f.nid === 'a1')!.rst,
		expected: '.. role:: strong_green(strong)\n   :class: green\n\nOverview\n========\n\nRevenue grew :strong_green:`12%` this year.\n',
	},
	{
		name: 'assemble: block formula from json (doc-a, a3)',
		actual: () => assemble((docA as unknown as { forest: DocTreeNode[] }).forest[0]).find((f) => f.nid === 'a3')!.rst,
		expected: 'Finance\n=======\n\n.. math::\n\n   \\text{margin} = \\frac{\\text{profit}}{\\text{revenue}}\n',
	},
	{
		name: 'assemble: demo doc-a',
		actual: () => assemble((docA as unknown as { forest: DocTreeNode[] }).forest[0]).map((f) => f.path).join(' '),
		expected: 'index.rst a1.rst a2.rst a3.rst',
	},
];

export function runAssemble(): Result[] {
	return cases.map((c) => {
		try {
			const actual = c.actual();
			return { name: c.name, pass: actual === c.expected, expected: c.expected, actual, rst: actual };
		} catch (e) {
			return { name: c.name, pass: false, expected: c.expected, actual: '', rst: '', error: String(e) };
		}
	});
}
