import type { JSONContent } from '@tiptap/core';

export interface Case {
  name: string;
  doc: JSONContent;
  /** expected paragraph body (role declarations excluded) */
  rst: string;
  roles?: string[];
  /** expected complete fragment (declarations, body, link definitions), if checked */
  full?: string;
}

type Attrs = Record<string, unknown>;
/** a mark: 'strong', or [type, attrs] e.g. ['color', { name: 'red' }] */
export type MarkSpec = string | [type: string, attrs: Attrs];
export type Run = [text: string, mark?: string | MarkSpec[], attrs?: Attrs];

const toMark = (m: MarkSpec) => (typeof m === 'string' ? { type: m } : { type: m[0], attrs: m[1] });

/**
 * Build a one-paragraph doc from runs:
 * ['plain'], ['bold', 'strong'], ['x', 'size', {name:'large'}], ['x^2', 'math'] (inline formula),
 * ['x', ['strong', 'strike', ['color', {name:'red'}]]] (several marks),
 * ['x^2', ['math', ['color', {name:'red'}]]] (inline formula with marks)
 */
export const p = (...runs: Run[]): JSONContent => ({
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: runs.map(([text, mark, attrs]) => {
        if (mark === 'math') return { type: 'inlineMath', attrs: { latex: text } };
        if (Array.isArray(mark)) {
          const isMath = mark[0] === 'math';
          const marks = (isMath ? mark.slice(1) : mark).map(toMark);
          return isMath ? { type: 'inlineMath', attrs: { latex: text }, marks } : { type: 'text', text, marks };
        }
        return mark ? { type: 'text', text, marks: [{ type: mark, attrs }] } : { type: 'text', text };
      }),
    },
  ],
});

export const inlineCases: Case[] = [
  { name: 'plain text', doc: p(['Hello world']), rst: 'Hello world' },
  { name: 'strong', doc: p(['Hello '], ['world', 'strong']), rst: 'Hello **world**' },
  { name: 'emphasis', doc: p(['an '], ['idea', 'emphasis'], ['.']), rst: 'an *idea*.' },
  { name: 'literal', doc: p(['call '], ['f(x)', 'literal']), rst: 'call ``f(x)``' },
  { name: 'literal containing ``', doc: p(['a``b', 'literal']), rst: ':literal:`a\\`\\`b`' },
  { name: 'mid-word markup', doc: p(['bold', 'strong'], ['er']), rst: '**bold**\\ er' },
  { name: 'CJK boundaries', doc: p(['中文'], ['粗体', 'strong'], ['文字']), rst: '中文\\ **粗体**\\ 文字' },
  { name: 'CJK punctuation needs no escape', doc: p(['粗体', 'strong'], ['，好']), rst: '**粗体**，好' },
  { name: 'edge spaces moved outside', doc: p(['a'], [' b ', 'strong'], ['c']), rst: 'a **b** c' },
  { name: 'adjacent marks', doc: p(['a', 'strong'], ['b', 'emphasis']), rst: '**a**\\ *b*' },
  { name: 'punctuation after markup', doc: p(['a', 'strong'], [', b']), rst: '**a**, b' },
  { name: 'escape specials', doc: p(['2 * 3 and a_b `x`']), rst: '2 \\* 3 and a\\_b \\`x\\`' },
  {
    name: 'role',
    doc: p(['Size '], ['big', 'size', { name: 'large' }]),
    rst: 'Size :large:`big`',
    roles: ['large'],
  },
  {
    name: 'link',
    doc: p(['see '], ['docs', 'link', { href: 'https://x.org' }]),
    rst: 'see `docs <https://x.org>`__',
  },
  { name: 'list-like start escaped', doc: p(['- not a list']), rst: '\\- not a list' },
  { name: 'enumerator-like start escaped', doc: p(['1. not a list']), rst: '\\1. not a list' },
];
