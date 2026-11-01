import type { JSONContent } from '@tiptap/core';

export interface Case {
  name: string;
  doc: JSONContent;
  /** expected paragraph body (role declarations excluded) */
  rst: string;
  roles?: string[];
}

export type Run = [text: string, mark?: string, attrs?: Record<string, unknown>];

/**
 * Build a one-paragraph doc from runs:
 * ['plain'], ['bold', 'strong'], ['x', 'role', {name:'large'}], ['x^2', 'math'] (inline formula)
 */
export const p = (...runs: Run[]): JSONContent => ({
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: runs.map(([text, mark, attrs]) =>
        mark === 'math'
          ? { type: 'inlineMath', attrs: { latex: text } }
          : mark
            ? { type: 'text', text, marks: [{ type: mark, attrs }] }
            : { type: 'text', text },
      ),
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
    doc: p(['Size '], ['big', 'role', { name: 'large' }]),
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
