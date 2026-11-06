/**
 * Doc tree → RST files, laid out like the tree: one file per section.
 *
 *   doc root  → index.rst:   title, its own blocks, toctree of its sections
 *   section   → <nid>.rst:   heading, its blocks, toctree of its sub-sections
 *   p / math  → the block's stored rst (DocCols.rst), else its Tiptap json serialized,
 *               else its text (a new, imported block)
 *
 * Blocks and sections keep their order: consecutive sections share one toctree.
 * Each file declares the roles and defines the substitutions its own blocks use (joinRst).
 *
 * Pure: no React, no DOM; runs in Node (npm test) and in the browser.
 */
import { getSchema } from '@tiptap/core';
import { Node } from '@tiptap/pm/model';
import { rstExtensions } from '../editor/rst/marks';
import { joinRst, serializeBlockMath, serializeDoc, textRst, type RstResult } from '../editor/rst/serialize';
import { DocKind, toTiptap, type DocCols, type DocTreeNode } from './doctree-tier';

/** one file of the Sphinx source */
export interface RstFile {
	/** the section (or doc root) the file is built from */
	nid: string;
	/** path in the Sphinx source folder, "index.rst" for the doc root */
	path: string;
	/** docname for toctrees: the path without ".rst" */
	name: string;
	rst: string;
}

/** a block's parts, as joinRst merges them */
type Part = Omit<RstResult, 'rst'>;

/** docname of a section: its nid, with characters Sphinx paths don't like replaced */
export const docname = (t: DocTreeNode) =>
	t.node.kind === DocKind.doc ? 'index' : t.node.nid.replace(/[^\w.-]/g, '_');

/**
 * Display width as docutils measures a title: East Asian wide characters count 2.
 * An underline may be longer than the title, so wide is assumed generously.
 */
const width = (s: string) => [...s].reduce((w, c) => w + (c.charCodeAt(0) >= 0x1100 ? 2 : 1), 0);

function heading(title: string): string {
	const t = textRst(title || ' ');
	return `${t}\n${'='.repeat(Math.max(width(t), 3))}`;
}

/** the stored rst of a block: '{ body, roleDecls, substitutions, targets }' */
function stored(rst: string): Part | undefined {
	try {
		const r = JSON.parse(rst) as Partial<Part>;
		if (typeof r.body !== 'string') return undefined;
		const roleDecls = r.roleDecls ?? [];
		// a declaration is its own key: the same role always gets the same declaration
		return { body: r.body, roles: roleDecls, roleDecls, substitutions: r.substitutions ?? [], targets: r.targets ?? [] };
	} catch {
		return undefined;
	}
}

const plain = (body: string): Part => ({ body, roles: [], roleDecls: [], substitutions: [], targets: [] });

let schema: ReturnType<typeof getSchema> | undefined;

/** a block's Tiptap json (stored form), serialized; undefined if it can't be read */
function serialized(json: string): Part | undefined {
	try {
		schema ??= getSchema(rstExtensions);
		const r = serializeDoc(Node.fromJSON(schema, toTiptap(json)));
		return r.body ? r : undefined;
	} catch {
		return undefined;
	}
}

/** one block (not a section) as joinRst parts */
export function blockPart(n: DocCols): Part {
	const part = (n.rst ? stored(n.rst) : undefined) ?? (n.json ? serialized(n.json) : undefined);
	if (part) return part;

	switch (n.kind) {
		case DocKind.math: // an empty math directive is an error in Sphinx
			return plain(n.text?.trim() ? serializeBlockMath(n.text) : textRst('[formula]'));
		case DocKind.image:
			return plain(textRst(`[image: ${n.resId ?? n.text ?? ''}]`));
		case DocKind.ref:
			return plain(textRst(`[ref: ${n.text ?? n.refId ?? ''}]`));
		default: // p, table (text only for now)
			return plain(textRst(n.text ?? ''));
	}
}

function toctree(names: string[]): string {
	return '.. toctree::\n   :maxdepth: 2\n\n' + names.map((n) => '   ' + n).join('\n');
}

/**
 * The files of a doc, or of a subtree: the root's file first, then its sections depth-first.
 *
 * @param root the doc root (kind doc) or a section
 */
export function assemble(root: DocTreeNode): RstFile[] {
	const files: RstFile[] = [];

	const build = (sec: DocTreeNode) => {
		const parts: Part[] = [plain(heading(sec.node.text ?? ''))];
		const subs: DocTreeNode[] = [];
		let toc: string[] = [];

		const flush = () => {
			if (toc.length) parts.push(plain(toctree(toc)));
			toc = [];
		};

		for (const t of sec.node.children ?? []) {
			if (t.node.kind === DocKind.section) {
				toc.push(docname(t));
				subs.push(t);
			} else {
				flush();
				parts.push(blockPart(t.node));
			}
		}
		flush();

		const name = docname(sec);
		files.push({ nid: sec.node.nid, path: name + '.rst', name, rst: joinRst(parts) + '\n' });
		subs.forEach(build);
	};

	build(root);
	return files;
}
