/**
 * Tables: the importer's marker text → a Tiptap table, and the layout of an RST grid table.
 *
 * Marker text, a tabl node's ptext (doctree.jserv io.oz.sdoc.docx.DocxWalker#table()):
 *   rows joined by " // ", cells by " | "; a cell's text may end with
 *     " {colspan=n}"     the cell spans n grid columns
 *     " {vmerge-start}"  the first of the cells merged down
 *     " {vmerge-cont}"   merged into the cell above (its text is empty)
 *   paragraphs (or soft line breaks) in a cell are joined by " {br} ".
 *
 * Grid tables are the only RST tables with both row and column spans:
 *
 *   +-------+-------+
 *   | merged across |
 *   +=======+=======+    header rows end with "=", if any
 *   | down  | r1c1  |
 *   |       +-------+
 *   |       | r2c1  |
 *   +-------+-------+
 *
 * Docutils measures East Asian wide characters as two columns (utils.column_width,
 * StringList.pad_double_width), so the borders are laid out by display width.
 *
 * Pure: no React, no DOM.
 */
import type { JSONContent } from '@tiptap/core';

const ROW = ' // ';
const CELL = ' | ';
const BR = ' {br} ';
const MARKER = /\s*\{(colspan=(\d+)|vmerge-start|vmerge-cont)\}\s*$/;

interface MarkedCell {
	text: string;
	colspan: number;
	vmerge?: 'start' | 'cont';
}

function parseCell(s: string): MarkedCell {
	const c: MarkedCell = { text: s, colspan: 1 };
	for (let m = c.text.match(MARKER); m; m = c.text.match(MARKER)) {
		if (m[2]) c.colspan = Math.max(1, parseInt(m[2], 10));
		else c.vmerge = m[1] === 'vmerge-start' ? 'start' : 'cont';
		c.text = c.text.slice(0, m.index);
	}
	c.text = c.text.trim();
	return c;
}

const paragraphs = (text: string): JSONContent[] =>
	text.split(BR).map((t) => t.trim()).filter((t, i, all) => t || all.length === 1)
		.map((t) => (t ? { type: 'paragraph', content: [{ type: 'text', text: t }] } : { type: 'paragraph' }));

/**
 * A tabl node's marker text as a Tiptap doc of one table: merged cells as
 * colspan / rowspan, a cell's paragraphs as paragraphs.
 */
export function markersToTiptap(ptext: string): JSONContent {
	const rows: JSONContent[] = [];
	/** per grid column: the cell above that a "{vmerge-cont}" continues */
	const open: (JSONContent | undefined)[] = [];

	for (const line of (ptext ?? '').split(ROW)) {
		if (!line.trim() && rows.length) continue;
		const cells: JSONContent[] = [];
		let col = 0;
		for (const raw of line.split(CELL)) {
			const c = parseCell(raw);
			const above = open[col];
			if (c.vmerge === 'cont' && above) {
				above.attrs!.rowspan += 1;
				col += c.colspan;
				continue;
			}
			const cell: JSONContent = {
				type: 'tableCell',
				attrs: { colspan: c.colspan, rowspan: 1, colwidth: null },
				content: paragraphs(c.text),
			};
			for (let k = 0; k < c.colspan; k++) open[col + k] = c.vmerge === 'start' ? cell : undefined;
			cells.push(cell);
			col += c.colspan;
		}
		// a short row ends the merges of the columns it doesn't reach
		for (let k = col; k < open.length; k++) open[k] = undefined;
		rows.push(cells.length ? { type: 'tableRow', content: cells } : { type: 'tableRow' });
	}
	return { type: 'doc', content: [{ type: 'table', content: rows }] };
}

// ---- display width, as docutils measures it ---------------------------------

/** East Asian Wide (W) and Fullwidth (F), the common ranges of EastAsianWidth.txt */
const WIDE: [number, number][] = [
	[0x1100, 0x115f], [0x231a, 0x231b], [0x2329, 0x232a], [0x23e9, 0x23ec], [0x23f0, 0x23f0], [0x23f3, 0x23f3],
	[0x25fd, 0x25fe], [0x2614, 0x2615], [0x2648, 0x2653], [0x267f, 0x267f], [0x2693, 0x2693], [0x26a1, 0x26a1],
	[0x26aa, 0x26ab], [0x26bd, 0x26be], [0x26c4, 0x26c5], [0x26ce, 0x26ce], [0x26d4, 0x26d4], [0x26ea, 0x26ea],
	[0x26f2, 0x26f3], [0x26f5, 0x26f5], [0x26fa, 0x26fa], [0x26fd, 0x26fd], [0x2705, 0x2705], [0x270a, 0x270b],
	[0x2728, 0x2728], [0x274c, 0x274c], [0x274e, 0x274e], [0x2753, 0x2755], [0x2757, 0x2757], [0x2795, 0x2797],
	[0x27b0, 0x27b0], [0x27bf, 0x27bf], [0x2b1b, 0x2b1c], [0x2b50, 0x2b50], [0x2b55, 0x2b55],
	[0x2e80, 0x303e], [0x3041, 0x3247], [0x3250, 0x4dbf], [0x4e00, 0xa4cf], [0xa960, 0xa97f], [0xac00, 0xd7a3],
	[0xf900, 0xfaff], [0xfe10, 0xfe19], [0xfe30, 0xfe6f], [0xff00, 0xff60], [0xffe0, 0xffe6],
	[0x16fe0, 0x16fe4], [0x17000, 0x18aff], [0x1b000, 0x1b2ff], [0x1f004, 0x1f004], [0x1f0cf, 0x1f0cf],
	[0x1f18e, 0x1f18e], [0x1f191, 0x1f19a], [0x1f200, 0x1f251], [0x1f300, 0x1f320], [0x1f32d, 0x1f335],
	[0x1f337, 0x1f37c], [0x1f37e, 0x1f393], [0x1f3a0, 0x1f3ca], [0x1f3cf, 0x1f3d3], [0x1f3e0, 0x1f3f0],
	[0x1f3f4, 0x1f3f4], [0x1f3f8, 0x1f43e], [0x1f440, 0x1f440], [0x1f442, 0x1f4fc], [0x1f4ff, 0x1f53d],
	[0x1f54b, 0x1f54e], [0x1f550, 0x1f567], [0x1f57a, 0x1f57a], [0x1f595, 0x1f596], [0x1f5a4, 0x1f5a4],
	[0x1f5fb, 0x1f64f], [0x1f680, 0x1f6c5], [0x1f6cc, 0x1f6cc], [0x1f6d0, 0x1f6d2], [0x1f6d5, 0x1f6d7],
	[0x1f6eb, 0x1f6ec], [0x1f6f4, 0x1f6fc], [0x1f7e0, 0x1f7eb], [0x1f90c, 0x1f93a], [0x1f93c, 0x1f945],
	[0x1f947, 0x1f9ff], [0x1fa70, 0x1faff], [0x20000, 0x2fffd], [0x30000, 0x3fffd],
];

/** columns a character takes: 2 (wide), 0 (combining mark) or 1 */
export function charWidth(ch: string): number {
	const cp = ch.codePointAt(0)!;
	if (cp < 0x1100) return /\p{Mn}/u.test(ch) ? 0 : 1;
	let lo = 0, hi = WIDE.length - 1;
	while (lo <= hi) {
		const mid = (lo + hi) >> 1;
		if (cp < WIDE[mid][0]) hi = mid - 1;
		else if (cp > WIDE[mid][1]) lo = mid + 1;
		else return 2;
	}
	return /\p{Mn}/u.test(ch) ? 0 : 1;
}

export const displayWidth = (s: string) => [...s].reduce((w, c) => w + charWidth(c), 0);

// ---- grid table ----------------------------------------------------------------

/** a cell as the serializer gives it: its RST lines (paragraphs separated by '') */
export interface GridCellIn {
	rowspan: number;
	colspan: number;
	lines: string[];
}

interface Placed extends GridCellIn {
	row: number;
	col: number;
}

/** cells → grid positions, as HTML tables do: a cell takes the first free column of its row */
function place(rows: GridCellIn[][]): { cells: Placed[]; nrows: number; ncols: number } {
	const nrows = rows.length;
	const taken: boolean[][] = rows.map(() => []);
	const cells: Placed[] = [];
	let ncols = 0;
	rows.forEach((row, r) => {
		let c = 0;
		for (const cell of row) {
			while (taken[r][c]) c++;
			const rowspan = Math.max(1, Math.min(cell.rowspan || 1, nrows - r));
			const colspan = Math.max(1, cell.colspan || 1);
			for (let i = r; i < r + rowspan; i++) for (let j = c; j < c + colspan; j++) taken[i][j] = true;
			cells.push({ ...cell, rowspan, colspan, row: r, col: c });
			c += colspan;
			ncols = Math.max(ncols, c);
		}
	});
	// a grid table is rectangular: the holes of short rows are empty cells
	for (let r = 0; r < nrows; r++)
		for (let c = 0; c < ncols; c++)
			if (!taken[r][c]) cells.push({ row: r, col: c, rowspan: 1, colspan: 1, lines: [] });
	return { cells, nrows, ncols };
}

/** grow sizes[from, from + span) to hold need, separators (sep each) included; spread evenly */
function fit(sizes: number[], from: number, span: number, need: number, sep: number) {
	let have = sep * (span - 1);
	for (let i = from; i < from + span; i++) have += sizes[i];
	for (let k = 0; have < need; k = (k + 1) % span, have++) sizes[from + span - 1 - k]++;
}

/**
 * The RST grid table of cells, row by row (HTML order: each row lists the cells starting in it).
 *
 * @param headerRows leading rows separated by "=" (ignored if a cell spans across the separator,
 *                   or if no body row would remain)
 */
export function gridTable(rows: GridCellIn[][], headerRows = 0): string {
	const { cells, nrows, ncols } = place(rows);
	if (!nrows || !ncols) return '';

	const width = cells.map((c) => Math.max(0, ...c.lines.map(displayWidth)));
	const w = new Array<number>(ncols).fill(1);
	const h = new Array<number>(nrows).fill(1);
	// single spans first, so a span only adds what its columns / rows lack
	const order = cells.map((_, i) => i);
	order.sort((a, b) => cells[a].colspan - cells[b].colspan).forEach((i) => fit(w, cells[i].col, cells[i].colspan, width[i], 3));
	order.sort((a, b) => cells[a].rowspan - cells[b].rowspan).forEach((i) => fit(h, cells[i].row, cells[i].rowspan, cells[i].lines.length, 1));

	const X = [0], Y = [0];
	w.forEach((x, c) => X.push(X[c] + x + 3));
	h.forEach((y, r) => Y.push(Y[r] + y + 1));

	if (headerRows <= 0 || headerRows >= nrows || cells.some((c) => c.row < headerRows && c.row + c.rowspan > headerRows))
		headerRows = 0;
	const headY = headerRows ? Y[headerRows] : -1;

	// a canvas of display columns: a wide character takes its column and the next ('')
	const canvas: string[][] = Array.from({ length: Y[nrows] + 1 }, () => new Array<string>(X[ncols] + 1).fill(' '));

	for (const c of cells) {
		const x1 = X[c.col], x2 = X[c.col + c.colspan], y1 = Y[c.row], y2 = Y[c.row + c.rowspan];
		for (const y of [y1, y2]) for (let x = x1 + 1; x < x2; x++) canvas[y][x] = y === headY ? '=' : '-';
		for (let y = y1 + 1; y < y2; y++) canvas[y][x1] = canvas[y][x2] = '|';
		c.lines.forEach((line, i) => {
			const row = canvas[y1 + 1 + i];
			let x = x1 + 2;
			for (const ch of line) {
				const cw = charWidth(ch);
				if (cw === 0) row[Math.max(x1 + 2, x - 1)] += ch;
				else {
					row[x] = ch;
					if (cw === 2) row[x + 1] = '';
					x += cw;
				}
			}
		});
	}
	for (const c of cells)
		for (const y of [Y[c.row], Y[c.row + c.rowspan]])
			for (const x of [X[c.col], X[c.col + c.colspan]]) canvas[y][x] = '+';

	return canvas.map((line) => line.join('')).join('\n');
}
