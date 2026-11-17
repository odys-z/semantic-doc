import type { JSONContent } from '@tiptap/core';
import type { Case } from './inline';
import { markersToTiptap } from '../../editor/rst/table';

type Attrs = { colspan?: number; rowspan?: number };
/** a cell: its paragraphs' text, plain; 'th' for a header cell */
const cell = (texts: string | string[], attrs: Attrs = {}, type = 'tableCell'): JSONContent => ({
	type,
	attrs: { colspan: 1, rowspan: 1, ...attrs },
	content: (Array.isArray(texts) ? texts : [texts])
		.map((t) => (t ? { type: 'paragraph', content: [{ type: 'text', text: t }] } : { type: 'paragraph' })),
});
const th = (texts: string | string[], attrs: Attrs = {}) => cell(texts, attrs, 'tableHeader');
const table = (...rows: JSONContent[][]): JSONContent => ({
	type: 'doc',
	content: [{ type: 'table', content: rows.map((content) => ({ type: 'tableRow', content })) }],
});

/** tables as grid tables; the importer's marker text as tables */
export const tableCases: Case[] = [
	{
		name: 'table: 2 x 2',
		doc: table([cell('a'), cell('bb')], [cell('ccc'), cell('d')]),
		rst: [
			'+-----+----+',
			'| a   | bb |',
			'+-----+----+',
			'| ccc | d  |',
			'+-----+----+',
		].join('\n'),
	},
	{
		name: 'table: header row',
		doc: table([th('Name'), th('Qty')], [cell('apple'), cell('3')]),
		rst: [
			'+-------+-----+',
			'| Name  | Qty |',
			'+=======+=====+',
			'| apple | 3   |',
			'+-------+-----+',
		].join('\n'),
	},
	{
		name: 'table: column span, wider than its columns',
		doc: table([cell('a wide merged title', { colspan: 2 })], [cell('x'), cell('y')]),
		rst: [
			'+---------------------+',
			'| a wide merged title |',
			'+----------+----------+',
			'| x        | y        |',
			'+----------+----------+',
		].join('\n'),
	},
	{
		name: 'table: row span, taller than its rows',
		doc: table([cell(['one', 'two', 'three'], { rowspan: 2 }), cell('r0')], [cell('r1')]),
		rst: [
			'+-------+----+',
			'| one   | r0 |',
			'|       |    |',
			'| two   +----+',
			'|       | r1 |',
			'| three |    |',
			'+-------+----+',
		].join('\n'),
	},
	{
		name: 'table: Chinese text takes two columns a character',
		doc: table([cell('模块名称'), cell('ab')], [cell('x'), cell('核心技能')]),
		rst: [
			'+----------+----------+',
			'| 模块名称 | ab       |',
			'+----------+----------+',
			'| x        | 核心技能 |',
			'+----------+----------+',
		].join('\n'),
	},
	{
		name: 'table: cells keep marks, escapes and list-like text',
		doc: {
			type: 'doc',
			content: [{
				type: 'table',
				content: [{
					type: 'tableRow',
					content: [
						{ type: 'tableCell', attrs: { colspan: 1, rowspan: 1 }, content: [{ type: 'paragraph', content: [{ type: 'text', text: 'bold', marks: [{ type: 'strong' }] }] }] },
						cell('1. not a list *'),
					],
				}],
			}],
		},
		rst: [
			'+----------+-------------------+',
			'| **bold** | \\1. not a list \\* |',
			'+----------+-------------------+',
		].join('\n'),
	},
	{
		name: 'table: empty cells and a short row',
		doc: table([cell('a'), cell(''), cell('c')], [cell('d')]),
		rst: [
			'+---+---+---+',
			'| a |   | c |',
			'+---+---+---+',
			'| d |   |   |',
			'+---+---+---+',
		].join('\n'),
	},
	{
		name: 'markers: colspan, vmerge, {br}',
		doc: markersToTiptap(
			'Title {colspan=3} // ' +
			'A {vmerge-start} | B | x {br} y {vmerge-start} // ' +
			' {vmerge-cont} | C |  {vmerge-cont} // ' +
			'D | E {colspan=2}'),
		rst: [
			'+-----------+',
			'| Title     |',
			'+---+---+---+',
			'| A | B | x |',
			'|   +---+   |',
			'|   | C | y |',
			'+---+---+---+',
			'| D | E     |',
			'+---+-------+',
		].join('\n'),
	},
];
