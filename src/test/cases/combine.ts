import { p, type Case } from './inline';

const red = ['color', { name: 'red' }] as [string, Record<string, unknown>];
const bgYellow = ['background', { name: 'bg-yellow' }] as [string, Record<string, unknown>];
const large = ['size', { name: 'large' }] as [string, Record<string, unknown>];
const link = (href: string) => ['link', { href }] as [string, Record<string, unknown>];

/** marks combined: composite roles, styled formulas, styled links */
export const combineCases: Case[] = [
  {
    name: 'strong + emphasis',
    doc: p(['both', ['strong', 'emphasis']]),
    rst: ':strong_em:`both`',
    roles: ['strong_em'],
    full: '.. role:: strong_em(strong)\n   :class: sd-em\n\n:strong_em:`both`',
  },
  {
    name: 'strike + colour + background',
    doc: p(['was '], ['old', ['strike', red, bgYellow]], ['.']),
    rst: 'was :strike_red_bg-yellow:`old`.',
    roles: ['strike_red_bg-yellow'],
    full: '.. role:: strike_red_bg-yellow\n   :class: strike red bg-yellow\n\nwas :strike_red_bg-yellow:`old`.',
  },
  {
    name: 'underline + strike',
    doc: p(['x', ['underline', 'strike']]),
    rst: ':underline_strike:`x`',
    roles: ['underline_strike'],
  },
  {
    name: 'mark order in the doc does not matter',
    doc: p(['a', [bgYellow, 'strike', red]], ['b', ['strike', red, bgYellow]]),
    rst: ':strike_red_bg-yellow:`ab`',
    roles: ['strike_red_bg-yellow'],
  },
  {
    name: 'sup is the base, strong becomes a class',
    doc: p(['x'], ['2', ['strong', 'sup', red]]),
    rst: 'x\\ :sup_strong_red:`2`',
    roles: ['sup_strong_red'],
    full: '.. role:: sup_strong_red(sup)\n   :class: sd-strong red\n\nx\\ :sup_strong_red:`2`',
  },
  {
    name: 'literal + colour',
    doc: p(['call '], ['f(x)', ['literal', red]]),
    rst: 'call :code_red:`f(x)`',
    roles: ['code_red'],
  },
  {
    name: 'overlapping runs split into separate markup',
    doc: p(['a', ['strong']], ['b', ['strong', red]], ['c', [red]]),
    rst: '**a**\\ :strong_red:`b`\\ :red:`c`',
    roles: ['strong_red', 'red'],
  },
  {
    name: 'formula with colour + background',
    doc: p(['see '], ['x^2', ['math', red, bgYellow]]),
    rst: 'see :math_red_bg-yellow:`x^2`',
    roles: ['math_red_bg-yellow'],
    full: '.. role:: math_red_bg-yellow(math)\n   :class: red bg-yellow\n\nsee :math_red_bg-yellow:`x^2`',
  },
  {
    name: 'formula ignores B / I / U / S',
    doc: p(['\\alpha', ['math', 'strong', 'emphasis', 'underline', 'strike']]),
    rst: ':math:`\\alpha`',
  },
  {
    name: 'formula keeps size + colour, drops strike',
    doc: p(['\\beta', ['math', 'strike', large, red]]),
    rst: ':math_large_red:`\\beta`',
    roles: ['math_large_red'],
  },
  {
    name: 'link + colour',
    doc: p(['see '], ['docs', [link('https://x.org'), red]], ['.']),
    rst: 'see |sd-87926dbb|__.',
    roles: ['red'],
    full: '.. role:: red\n\nsee |sd-87926dbb|__.\n\n.. |sd-87926dbb| replace:: :red:`docs`\n.. __: https://x.org',
  },
  {
    name: 'same styled link twice: one substitution, two targets',
    doc: p(['a', [link('https://x.org'), 'strong']], [' and '], ['a', [link('https://y.org'), 'strong']]),
    rst: '|sd-a2829b18|__ and |sd-a2829b18|__',
    full:
      '|sd-a2829b18|__ and |sd-a2829b18|__\n\n' +
      '.. |sd-a2829b18| replace:: **a**\n.. __: https://x.org\n.. __: https://y.org',
  },
  {
    name: 'block formula with colour + background',
    doc: {
      type: 'doc',
      content: [{ type: 'blockMath', attrs: { latex: 'E = mc^2', color: 'red', background: 'bg-yellow' } }],
    },
    rst: '.. math::\n   :class: red bg-yellow\n\n   E = mc^2',
  },
];
