/**
 * Serialize a Tiptap / ProseMirror document (paragraphs with RST marks) to RST.
 *
 * Handles the RST inline-markup rules:
 *  - special characters in plain text are backslash-escaped;
 *  - markup must not start or end with whitespace, so edge spaces are moved outside;
 *  - markup must be delimited by whitespace or punctuation; otherwise an
 *    escaped space "\ " is inserted (e.g. **bold**\ er, 中文\ **粗体**);
 *  - inline markup cannot nest, so a run with several effects becomes one
 *    composite custom role (see rst/marks.ts), declared once with ".. role::";
 *  - a link with effects becomes a substitution reference |sd-hash|__ whose
 *    definition holds the styled text, followed by an anonymous target.
 */
import type { Node as PMNode, Mark as PMMark } from '@tiptap/pm/model';
import { CLASS_MARKS, MATH_MARKS, blockMathClasses } from './marks';

export interface RstResult {
  /** paragraphs only */
  body: string;
  /** role names used, in first-use order */
  roles: string[];
  /** one `.. role::` declaration per role (may span lines), same order as roles */
  roleDecls: string[];
  /**
   * `.. |sd-…| replace::` definitions for styled links. Names are content
   * hashes: when joining blocks, keep one copy of each (see joinRst).
   */
  substitutions: string[];
  /** `.. __: url` anonymous targets, in reference order; must follow the body */
  targets: string[];
  /** declarations + body + substitutions + targets: a complete RST fragment */
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

// ---- styles ---------------------------------------------------------------

/** Structural marks, by priority as the base role of a composite. */
const STRUCT = ['sup', 'sub', 'literal', 'strong', 'emphasis'] as const;
type Struct = (typeof STRUCT)[number];
/** token in a composite role name */
const NAME: Record<Struct, string> = { sup: 'sup', sub: 'sub', literal: 'code', strong: 'strong', emphasis: 'em' };
/** CSS class when the mark is not the base role (theme-roles.css) */
const CLASS: Record<Struct, string> = { sup: 'sd-sup', sub: 'sd-sub', literal: 'sd-code', strong: 'sd-strong', emphasis: 'sd-em' };

/** The effects of one run of text, in canonical order. */
interface Style {
  struct: Struct[];
  /** underline, strike, then size / color / background role names */
  classes: string[];
  href: string | null;
}

function styleOf(marks: readonly PMMark[]): Style {
  const has = (n: string) => marks.find((m) => m.type.name === n);
  const struct = STRUCT.filter((s) => has(s));
  const classes = (['underline', 'strike'] as const).filter((c) => has(c)) as string[];
  for (const k of CLASS_MARKS) {
    const name = has(k)?.attrs.name;
    if (name) classes.push(name);
  }
  return { struct, classes, href: (has('link')?.attrs.href as string | null) ?? null };
}

const styleKey = (s: Style) => `${s.struct.join(' ')}|${s.classes.join(' ')}|${s.href ?? ''}`;

/** collects declarations while a block is serialized */
class Ctx {
  roles = new Map<string, string>(); // name → declaration
  subs = new Map<string, string>(); // name → definition
  targets: string[] = [];

  role(name: string, base?: string, classes?: string[]): string {
    if (!this.roles.has(name))
      this.roles.set(
        name,
        `.. role:: ${name}${base ? `(${base})` : ''}` + (classes ? `\n   :class: ${classes.join(' ')}` : ''),
      );
    return name;
  }
}

/** 32-bit FNV-1a, as 8 hex digits */
function hash(s: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 0x01000193);
  return (h >>> 0).toString(16).padStart(8, '0');
}

const literal = (text: string) =>
  // ``...`` cannot contain `` or end with `; fall back to the :literal: role
  text.includes('``') || text.endsWith('`') ? `:literal:\`${escapeText(text)}\`` : `\`\`${text}\`\``;

/** text with struct + classes (no link) as one piece of markup */
function styled(text: string, struct: Struct[], classes: string[], ctx: Ctx): string {
  const n = struct.length + classes.length;
  if (n === 0) return escapeText(text);
  if (n === 1) {
    switch (struct[0]) {
      case 'strong': return `**${escapeText(text)}**`;
      case 'emphasis': return `*${escapeText(text)}*`;
      case 'literal': return literal(text);
      case 'sup': return `:sup:\`${escapeText(text)}\``;
      case 'sub': return `:sub:\`${escapeText(text)}\``;
    }
    return `:${ctx.role(classes[0])}:\`${escapeText(text)}\``;
  }
  // composite: the first structural mark is the base role, the rest are classes
  const [base, ...rest] = struct;
  const name = [...struct.map((s) => NAME[s]), ...classes].join('_');
  const cls = [...rest.map((s) => CLASS[s]), ...classes];
  return `:${ctx.role(name, base, cls)}:\`${escapeText(text)}\``;
}

function wrap(text: string, style: Style, ctx: Ctx): string {
  const { struct, classes, href } = style;
  if (!href) return styled(text, struct, classes, ctx);
  if (!struct.length && !classes.length) return `\`${escapeText(text).replace(/</g, '\\<')} <${href}>\`__`;
  // styled link: |sub|__ with ".. |sub| replace:: <styled text>" and ".. __: url"
  const inner = styled(text, struct, classes, ctx);
  const name = `sd-${hash(inner)}`;
  ctx.subs.set(name, `.. |${name}| replace:: ${inner}`);
  ctx.targets.push(`.. __: ${href}`);
  return `|${name}|__`;
}

/** :math:`latex`, or a math-based role carrying size / color / background */
function math(node: PMNode, ctx: Ctx): string {
  const classes = styleOf(node.marks.filter((m) => MATH_MARKS.has(m.type.name))).classes;
  // :math: content is raw: LaTeX backslashes are kept as written
  if (!classes.length) return `:math:\`${node.attrs.latex}\``;
  const name = ['math', ...classes].join('_');
  return `:${ctx.role(name, 'math', classes)}:\`${node.attrs.latex}\``;
}

// ---- paragraphs -----------------------------------------------------------

function tokenize(para: PMNode, ctx: Ctx): Token[] {
  // merge adjacent text nodes with the same effects; inline formulas stay separate
  type Seg = { kind: 'text'; text: string; style: Style; key: string } | { kind: 'math'; node: PMNode };
  const segs: Seg[] = [];
  para.forEach((child) => {
    if (child.type.name === 'inlineMath') {
      if (child.attrs.latex) segs.push({ kind: 'math', node: child });
      return;
    }
    if (!child.isText || !child.text) return;
    const style = styleOf(child.marks);
    const key = styleKey(style);
    const last = segs[segs.length - 1];
    if (last?.kind === 'text' && last.key === key) last.text += child.text;
    else segs.push({ kind: 'text', text: child.text, style, key });
  });

  const tokens: Token[] = [];
  for (const seg of segs) {
    if (seg.kind === 'math') {
      tokens.push({ s: math(seg.node, ctx), markup: true });
      continue;
    }
    const { text, style, key } = seg;
    if (key === '||') {
      tokens.push({ s: escapeText(text), markup: false });
      continue;
    }
    const [, lead, body, trail] = text.match(/^(\s*)([\s\S]*?)(\s*)$/)!;
    if (!body) {
      tokens.push({ s: text, markup: false }); // whitespace only: drop the marks
      continue;
    }
    if (lead) tokens.push({ s: lead, markup: false });
    tokens.push({ s: wrap(body, style, ctx), markup: true });
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

function paragraph(para: PMNode, ctx: Ctx): string {
  const text = join(tokenize(para, ctx));
  return BLOCK_START.test(text) ? '\\' + text : text;
}

/** One paragraph's body; declarations it needs are dropped (use serializeDoc for those). */
export function serializeParagraph(para: PMNode): string {
  return paragraph(para, new Ctx());
}

/** `.. math::` directive with the LaTeX indented as its content. */
export function serializeBlockMath(latex: string, classes: string[] = []): string {
  const lines = latex.trim().split('\n').map((l) => (l.trim() ? '   ' + l : ''));
  const opt = classes.length ? `   :class: ${classes.join(' ')}\n` : '';
  return '.. math::\n' + opt + '\n' + lines.join('\n');
}

export function serializeDoc(doc: PMNode): RstResult {
  const ctx = new Ctx();
  const blocks: string[] = [];
  doc.forEach((node) => {
    if (node.type.name === 'paragraph') blocks.push(paragraph(node, ctx));
    else if (node.type.name === 'blockMath')
      blocks.push(serializeBlockMath(node.attrs.latex ?? '', blockMathClasses(node.attrs)));
  });
  const res = {
    body: blocks.join('\n\n'),
    roles: [...ctx.roles.keys()],
    roleDecls: [...ctx.roles.values()],
    substitutions: [...ctx.subs.values()],
    targets: ctx.targets,
  };
  return { ...res, rst: joinRst([res]) };
}

/**
 * Join blocks (e.g. every paragraph of the content tree, in order) into one
 * RST document: role declarations first, each declared once, then each body
 * followed by its link definitions. A substitution shared by several blocks is
 * defined once (docutils rejects duplicates).
 */
export function joinRst(blocks: Omit<RstResult, 'rst'>[]): string {
  const decls = new Map<string, string>();
  const subs = new Set<string>();
  const parts: string[] = [];
  for (const b of blocks) {
    b.roles.forEach((r, i) => decls.has(r) || decls.set(r, b.roleDecls[i]));
    const defs = [...b.substitutions.filter((s) => !subs.has(s)), ...b.targets];
    b.substitutions.forEach((s) => subs.add(s));
    if (b.body) parts.push(b.body);
    if (defs.length) parts.push(defs.join('\n'));
  }
  return [[...decls.values()].join('\n'), ...parts].filter(Boolean).join('\n\n');
}
