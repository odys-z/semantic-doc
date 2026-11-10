/**
 * The importer's run facts → Tiptap JSON (the stored form, "contype"), for nodes that
 * have facts but no tipjson yet (a newly imported doc). Mapping to the editor's schema lives
 * here, beside the schema (editor/rst/marks.ts), so a palette change needs no re-import.
 *
 * Facts (doctree.jserv io.oz.sdoc.imports.Run), version 1:
 *   {"v":1,"runs":[{"t":"Revenue grew "},{"t":"12%","b":1,"color":"1E8449"}]}
 *   t text; b i u s: bold, italic, underline, strike; va: "sup" | "sub";
 *   color, shd: hex RRGGBB; hl: highlight name; sz: half-points; href: link target
 *
 * Colours go to the nearest of the palette (ROLES), or none if nearer to black / white.
 */
import { DocNode, fromTiptap, type DocTreeNode } from './doc-stree';

export interface RunFacts {
	t: string;
	b?: number; i?: number; u?: number; s?: number;
	va?: 'sup' | 'sub';
	color?: string; hl?: string; shd?: string;
	sz?: number;
	href?: string;
}

type Mark = { type: string; attrs?: Record<string, unknown> };

/** palette of the color / background marks, as theme-roles.css draws them */
const COLORS: [string, string][] = [
	['red', 'c0392b'], ['orange', 'd35400'], ['green', '1e8449'], ['blue', '1f5fbf'], ['gray', '707070'],
	['', '000000'], // black: no colour mark
];
const BACKGROUNDS: [string, string][] = [
	['bg-yellow', 'fff3a3'], ['bg-green', 'd5f5dc'], ['bg-blue', 'dbe9ff'], ['bg-red', 'fde0dc'], ['bg-gray', 'ececec'],
	['', 'ffffff'], // white: no background mark
];
/** Word's highlight names */
const HIGHLIGHTS: Record<string, string> = {
	yellow: 'bg-yellow', darkYellow: 'bg-yellow',
	green: 'bg-green', darkGreen: 'bg-green',
	cyan: 'bg-blue', blue: 'bg-blue', darkBlue: 'bg-blue', darkCyan: 'bg-blue',
	red: 'bg-red', magenta: 'bg-red', darkRed: 'bg-red', darkMagenta: 'bg-red',
	lightGray: 'bg-gray', darkGray: 'bg-gray',
};

const rgb = (hex: string) => [0, 2, 4].map((x) => parseInt(hex.slice(x, x + 2), 16));

/** the nearest palette name of a hex colour; '' for none */
function nearest(hex: string, palette: [string, string][]): string {
	if (!/^[0-9a-f]{6}$/i.test(hex)) return '';
	const [r, g, b] = rgb(hex);
	let best = '', dist = Infinity;
	for (const [name, p] of palette) {
		const [pr, pg, pb] = rgb(p);
		const d = (r - pr) ** 2 + (g - pg) ** 2 + (b - pb) ** 2;
		if (d < dist) [best, dist] = [name, d];
	}
	return best;
}

/** size role of a font size (half-points; Word's body text is 21 – 24) */
function size(sz?: number): string {
	if (!sz) return '';
	return sz <= 18 ? 'small' : sz >= 32 ? 'x-large' : sz >= 28 ? 'large' : '';
}

export function marksOf(f: RunFacts): Mark[] {
	const marks: Mark[] = [];
	if (f.b) marks.push({ type: 'strong' });
	if (f.i) marks.push({ type: 'emphasis' });
	if (f.u) marks.push({ type: 'underline' });
	if (f.s) marks.push({ type: 'strike' });
	if (f.va) marks.push({ type: f.va });
	const color = f.color ? nearest(f.color, COLORS) : '';
	if (color) marks.push({ type: 'color', attrs: { name: color } });
	const bg = (f.hl && HIGHLIGHTS[f.hl]) || (f.shd ? nearest(f.shd, BACKGROUNDS) : '');
	if (bg) marks.push({ type: 'background', attrs: { name: bg } });
	const sz = size(f.sz);
	if (sz) marks.push({ type: 'size', attrs: { name: sz } });
	if (f.href) marks.push({ type: 'link', attrs: { href: f.href } });
	return marks;
}

/**
 * A paragraph's facts as Tiptap JSON ("type" keys).
 * @returns undefined if the facts can't be read, or are of another version
 */
export function factsToTiptap(facts: string) {
	let v: { v?: number; runs?: RunFacts[] };
	try {
		v = JSON.parse(facts);
	} catch {
		return undefined;
	}
	if (v.v !== 1 || !Array.isArray(v.runs)) return undefined;

	const content = v.runs
		.filter((r) => r.t)
		.map((r) => {
			const marks = marksOf(r);
			return marks.length ? { type: 'text', text: r.t, marks } : { type: 'text', text: r.t };
		});
	return { type: 'doc', content: [content.length ? { type: 'paragraph', content } : { type: 'paragraph' }] };
}

/** the stored form ("contype") of a paragraph's facts, or undefined */
export function factsToJson(facts: string): string | undefined {
	const doc = factsToTiptap(facts);
	return doc && fromTiptap(doc);
}

/**
 * Nodes without tipjson get one in memory, built from the importer's facts (paragraphs),
 * else from its plain text (a section's title, a table's text): the tree shows them
 * formatted, the preview serializes them; saved when the user edits them.
 */
/** kinds whose ptext is a label, not text to edit */
const untexted = new Set<string>([DocNode.Kind.doc, DocNode.Kind.math, DocNode.Kind.image, DocNode.Kind.docref]);

export function fillTipjson(forest: DocTreeNode[]): DocTreeNode[] {
	const fill = (t: DocTreeNode) => {
		const n = t.node;
		if (!n.tipjson && n.facts && n.kind === DocNode.Kind.p)
			n.tipjson = factsToJson(n.facts);
		// not for nodes whose content isn't text: doc, math, image (resId), docref (refId)
		if (!n.tipjson && n.ptext && !untexted.has(n.kind!))
			n.tipjson = fromTiptap({ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: n.ptext }] }] });
		n.children?.forEach(fill);
	};
	forest.forEach(fill);
	return forest;
}
