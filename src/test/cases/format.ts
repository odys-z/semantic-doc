import { p, type Case } from './inline';

const name = (name: string) => ({ name });

/** strike, underline, colour, background, superscript, subscript */
export const formatCases: Case[] = [
  { name: 'superscript', doc: p(['x'], ['2', 'sup']), rst: 'x\\ :sup:`2`' },
  { name: 'subscript mid-word', doc: p(['H'], ['2', 'sub'], ['O']), rst: 'H\\ :sub:`2`\\ O' },
  { name: 'sup after space', doc: p(['note '], ['1', 'sup']), rst: 'note :sup:`1`' },
  {
    name: 'strike',
    doc: p(['was '], ['old', 'strike']),
    rst: 'was :strike:`old`',
    roles: ['strike'],
  },
  {
    name: 'underline',
    doc: p(['an '], ['important', 'underline'], [' word']),
    rst: 'an :underline:`important` word',
    roles: ['underline'],
  },
  {
    name: 'font colour',
    doc: p(['in '], ['red', 'color', name('red')], ['.']),
    rst: 'in :red:`red`.',
    roles: ['red'],
  },
  {
    name: 'background colour',
    doc: p(['a '], ['highlight', 'background', name('bg-yellow')]),
    rst: 'a :bg-yellow:`highlight`',
    roles: ['bg-yellow'],
  },
  {
    name: 'roles declared once, in first-use order',
    doc: p(['a', 'color', name('red')], [' '], ['b', 'strike'], [' '], ['c', 'color', name('red')]),
    rst: ':red:`a` :strike:`b` :red:`c`',
    roles: ['red', 'strike'],
  },
];
