/**
 * Doc tree → RST files, laid out like the tree: one file per section.
 *
 *   doc root  → index.rst:   title, its own blocks, toctree of its sections
 *   section   → <nid>.rst:   heading, its blocks, toctree of its sub-sections
 *   p / math  → the block's stored rstext, else its tipjson serialized,
 *               else its ptext (a new, imported block)
 *   image     → .. image:: media/<resId>, a static, relative link; the file's {@link RstFile#media}
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
import { DocNode, toTiptap, type DocTreeNode } from './doc-stree';

/** a node's columns, as the tree carries them */
type Cols = DocTreeNode['node'];

/** one file of the Sphinx source */
export interface RstFile {
	/** the section (or doc root) the file is built from */
	nid: string;
	/** path in the Sphinx source folder, "index.rst" for the doc root */
	path: string;
	/** docname for toctrees: the path without ".rst" */
	name: string;
	rst: string;
	/** names of the resources the file's images link, in media/ beside it, e.g. ["image1.png"] */
	media: string[];
}

/** the folder of the resources, beside the RST files, in the Sphinx source and the package */
export const MEDIA = 'media';

/** a block's parts, as joinRst merges them */
type Part = Omit<RstResult, 'rst'>;

/** docname of a section: its nid, with characters Sphinx paths don't like replaced */
export const docname = (t: DocTreeNode) =>
	t.node.kind === DocNode.Kind.doc ? 'index' : (t.node.nid ?? t.id).replace(/[^\w.-]/g, '_');

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
export function blockPart(n: Cols): Part {
	// a picture is its link, whatever text the node carries
	if (n.kind === DocNode.Kind.image && n.resId)
		return plain(`.. image:: ${MEDIA}/${n.resId}`);

	const part = (n.rstext ? stored(n.rstext) : undefined) ?? (n.tipjson ? serialized(n.tipjson) : undefined);
	if (part) return part;

	switch (n.kind) {
		case DocNode.Kind.math: // an empty math directive is an error in Sphinx
			return plain(n.ptext?.trim() ? serializeBlockMath(n.ptext) : textRst('[formula]'));
		case DocNode.Kind.image:
			return plain(n.resId ? `.. image:: ${MEDIA}/${n.resId}` : textRst(`[image: ${n.ptext ?? ''}]`));
		case DocNode.Kind.docref:
			return plain(textRst(`[ref: ${n.ptext ?? n.refId ?? ''}]`));
		default: // p, table (text only for now)
			return plain(textRst(n.ptext ?? ''));
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
		const parts: Part[] = [plain(heading(sec.node.ptext ?? ''))];
		const subs: DocTreeNode[] = [];
		const media: string[] = [];
		let toc: string[] = [];

		const flush = () => {
			if (toc.length) parts.push(plain(toctree(toc)));
			toc = [];
		};

		for (const t of sec.node.children ?? []) {
			if (t.node.kind === DocNode.Kind.section) {
				toc.push(docname(t));
				subs.push(t);
			} else {
				flush();
				parts.push(blockPart(t.node));
				if (t.node.kind === DocNode.Kind.image && t.node.resId && !media.includes(t.node.resId))
					media.push(t.node.resId);
			}
		}
		flush();

		const name = docname(sec);
		files.push({ nid: sec.node.nid ?? sec.id, path: name + '.rst', name, rst: joinRst(parts) + '\n', media });
		subs.forEach(build);
	};

	build(root);
	return files;
}
