import type { Case } from './inline';
import { p } from './inline';

/** inline :math: and block .. math:: */
export const mathCases: Case[] = [
  { name: 'inline math', doc: p(['Energy '], ['E = mc^2', 'math'], [' holds']), rst: 'Energy :math:`E = mc^2` holds' },
  {
    name: 'inline math keeps backslashes raw',
    doc: p(['half '], ['\\frac{1}{2}', 'math']),
    rst: 'half :math:`\\frac{1}{2}`',
  },
  { name: 'inline math next to text', doc: p(['x'], ['^2', 'math'], ['y']), rst: 'x\\ :math:`^2`\\ y' },
  { name: 'inline math then punctuation', doc: p(['\\alpha', 'math'], [', ok']), rst: ':math:`\\alpha`, ok' },
  {
    name: 'inline math next to markup',
    doc: p(['bold', 'strong'], ['x', 'math']),
    rst: '**bold**\\ :math:`x`',
  },
  {
    name: 'block math',
    doc: { type: 'doc', content: [{ type: 'blockMath', attrs: { latex: '\\int_0^1 x\\,dx = \\frac{1}{2}' } }] },
    rst: '.. math::\n\n   \\int_0^1 x\\,dx = \\frac{1}{2}',
  },
  {
    name: 'block math, multi-line',
    doc: { type: 'doc', content: [{ type: 'blockMath', attrs: { latex: 'a = b\n\\\\\nc = d' } }] },
    rst: '.. math::\n\n   a = b\n   \\\\\n   c = d',
  },
];
