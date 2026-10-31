/**
 * Serialize a Tiptap / ProseMirror document (paragraphs with RST marks) to RST.
 *
 * Handles the RST inline-markup rules:
 *  - special characters in plain text are backslash-escaped;
 *  - markup must not start or end with whitespace, so edge spaces are moved outside;
 *  - markup must be delimited by whitespace or punctuation; otherwise an
 *    escaped space "\ " is inserted (e.g. **bold**\ er, 中文\ **粗体**);
 *  - roles used are collected so ".. role::" declarations can be emitted once.
 */
import type { Node as PMNode, Mark as PMMark } from '@tiptap/pm/model';

export interface RstResult {
  /** paragraphs only */
  body: string;
  /** role names used, in first-use order */
  roles: string[];
  /** role declarations + body: a complete RST fragment */
  rst: string;
}

interface Token {
  s: string;
  markup: boolean;
}

/** Characters with meaning in RST inline markup. */
const SPECIAL = /([\\*`_|])/g;
export const escapeText = (s: string) => s.replace(SPECIAL, '\\$1');

// docutils "inline markup recognition rules": whitespace, a fixed set of ASCII
// characters, or non-ASCII Unicode punctuation (ASCII * ` _ etc. must NOT count).
const isNonAsciiPunct = (c: string, cats: RegExp) => c.charCodeAt(0) > 127 && cats.test(c);
const BEFORE_ASCII = /[\s\-:/'"<(\[{]/;
const AFTER_ASCII = /[\s\-.,:;!?\\/'")\]}>]/;
const BEFORE_UNI = /[\s\p{Ps}\p{Pi}\p{Pf}\p{Pd}\p{Po}]/u;
const AFTER_UNI = /[\s\p{Pe}\p{Pi}\p{Pf}\p{Pd}\p{Po}]/u;
/** may precede a start-string */
const BEFORE_OK = { test: (c: string) => BEFORE_ASCII.test(c) || isNonAsciiPunct(c, BEFORE_UNI) };
/** may follow an end-string */
const AFTER_OK = { test: (c: string) => AFTER_ASCII.test(c) || isNonAsciiPunct(c, AFTER_UNI) };

/** Paragraph text that would be read as a list item, comment, etc. */
const BLOCK_START = /^(?:[-+•]|\d+[.)]|#[.)]|\(\d+\)|[a-zA-Z][.)]|\.\.)(?=\s|$)/;

function wrap(text: string, mark: PMMark, roles: Set<string>): string {
  switch (mark.type.name) {
    case 'strong':
      return `**${escapeText(text)}**`;
    case 'emphasis':
      return `*${escapeText(text)}*`;
    case 'literal':
      // ``...`` cannot contain `` or end with `; fall back to the :literal: role
      return text.includes('``') || text.endsWith('`')
        ? `:literal:\`${escapeText(text)}\``
        : `\`\`${text}\`\``;
    case 'link':
      return `\`${escapeText(text).replace(/</g, '\\<')} <${mark.attrs.href}>\`__`;
    case 'role': {
      const name = mark.attrs.name as string;
      roles.add(name);
      return `:${name}:\`${escapeText(text)}\``;
    }
    default:
      return escapeText(text);
  }
}

function tokenize(para: PMNode, roles: Set<string>): Token[] {
  // merge adjacent text nodes carrying the same mark
  const segs: { text: string; mark: PMMark | null }[] = [];
  para.forEach((child) => {
    if (!child.isText || !child.text) return;
    const mark = child.marks[0] ?? null; // marks are mutually exclusive
    const last = segs[segs.length - 1];
    if (last && (last.mark === mark || (last.mark && mark && last.mark.eq(mark)))) last.text += child.text;
    else segs.push({ text: child.text, mark });
  });

  const tokens: Token[] = [];
  for (const { text, mark } of segs) {
    if (!mark) {
      tokens.push({ s: escapeText(text), markup: false });
      continue;
    }
    const [, lead, body, trail] = text.match(/^(\s*)([\s\S]*?)(\s*)$/)!;
    if (!body) {
      tokens.push({ s: text, markup: false }); // whitespace only: drop the mark
      continue;
    }
    if (lead) tokens.push({ s: lead, markup: false });
    tokens.push({ s: wrap(body, mark, roles), markup: true });
    if (trail) tokens.push({ s: trail, markup: false });
  }
  return tokens;
}

function join(tokens: Token[]): string {
  let out = '';
  let prevMarkup = false;
  for (const t of tokens) {
    if (!t.s) continue;
    const prev = out.slice(-1);
    const needSep =
      (prevMarkup && !AFTER_OK.test(t.s[0])) || (t.markup && prev !== '' && !BEFORE_OK.test(prev));
    if (needSep) out += '\\ ';
    out += t.s;
    prevMarkup = t.markup;
  }
  return out;
}

export function serializeParagraph(para: PMNode, roles: Set<string> = new Set()): string {
  const text = join(tokenize(para, roles));
  return BLOCK_START.test(text) ? '\\' + text : text;
}

export function serializeDoc(doc: PMNode): RstResult {
  const roles = new Set<string>();
  const paras: string[] = [];
  doc.forEach((node) => {
    if (node.type.name === 'paragraph') paras.push(serializeParagraph(node, roles));
  });
  const body = paras.join('\n\n');
  const decl = [...roles].map((r) => `.. role:: ${r}`).join('\n');
  return { body, roles: [...roles], rst: decl ? `${decl}\n\n${body}` : body };
}
