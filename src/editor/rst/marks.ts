/**
 * Tiptap schema for one RST block: a paragraph, or a block formula.
 *
 * RST inline markup cannot nest, so every mark declares `excludes: '_'`:
 * applying one mark replaces any other on the same text. The editor can
 * therefore only hold content that serializes cleanly to RST.
 *
 *  mark / node   RST                         HTML (docutils & editor)
 *  -----------   -------------------------   ------------------------
 *  strong        **text**                    <strong>
 *  emphasis      *text*                      <em>
 *  literal       ``text``                    <code>
 *  link          `text <url>`__              <a href>
 *  sup / sub     :sup:`text` / :sub:`text`   <sup> / <sub>
 *  role          :name:`text` + .. role::    <span class="name">
 *  inlineMath    :math:`latex`               KaTeX
 *  blockMath     .. math::                   KaTeX
 */
import { Mark } from '@tiptap/core';
import Document from '@tiptap/extension-document';
import Paragraph from '@tiptap/extension-paragraph';
import Text from '@tiptap/extension-text';
import { InlineMath, BlockMath } from '@tiptap/extension-mathematics';

export interface RoleDef {
  /** role name in RST and CSS class name in HTML */
  name: string;
  label: string;
}

/**
 * Class-based effects, rendered by the `role` mark. Each needs a rule in
 * styles/theme-roles.css (shared with the Sphinx theme).
 */
export type RoleGroup = 'size' | 'color' | 'background' | 'decoration';

export const ROLES: Record<RoleGroup, RoleDef[]> = {
  size: [
    { name: 'small', label: 'Small' },
    { name: 'large', label: 'Large' },
    { name: 'x-large', label: 'X-Large' },
  ],
  color: [
    { name: 'red', label: 'Red' },
    { name: 'orange', label: 'Orange' },
    { name: 'green', label: 'Green' },
    { name: 'blue', label: 'Blue' },
    { name: 'gray', label: 'Gray' },
  ],
  background: [
    { name: 'bg-yellow', label: 'Yellow' },
    { name: 'bg-green', label: 'Green' },
    { name: 'bg-blue', label: 'Blue' },
    { name: 'bg-red', label: 'Red' },
    { name: 'bg-gray', label: 'Gray' },
  ],
  decoration: [
    { name: 'underline', label: 'Underline' },
    { name: 'strike', label: 'Strike' },
  ],
};

/** Document holding exactly one block: the tree UI owns structure. */
export const OneBlock = Document.extend({ content: 'paragraph | blockMath' });

/** **strong** */
export const Strong = Mark.create({
  name: 'strong',
  excludes: '_',
  parseHTML: () => [{ tag: 'strong' }, { tag: 'b' }],
  renderHTML: () => ['strong', 0],
  addKeyboardShortcuts() {
    return { 'Mod-b': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** *emphasis* */
export const Emphasis = Mark.create({
  name: 'emphasis',
  excludes: '_',
  parseHTML: () => [{ tag: 'em' }, { tag: 'i' }],
  renderHTML: () => ['em', 0],
  addKeyboardShortcuts() {
    return { 'Mod-i': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** ``literal`` */
export const Literal = Mark.create({
  name: 'literal',
  excludes: '_',
  code: true,
  parseHTML: () => [{ tag: 'code' }],
  renderHTML: () => ['code', 0],
  addKeyboardShortcuts() {
    return { 'Mod-e': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** :sup:`text` (docutils standard role) */
export const Sup = Mark.create({
  name: 'sup',
  excludes: '_',
  parseHTML: () => [{ tag: 'sup' }],
  renderHTML: () => ['sup', 0],
  addKeyboardShortcuts() {
    return { 'Mod-.': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** :sub:`text` (docutils standard role) */
export const Sub = Mark.create({
  name: 'sub',
  excludes: '_',
  parseHTML: () => [{ tag: 'sub' }],
  renderHTML: () => ['sub', 0],
  addKeyboardShortcuts() {
    return { 'Mod-,': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** `text <url>`__ */
export const Link = Mark.create({
  name: 'link',
  excludes: '_',
  inclusive: false,
  addAttributes() {
    return {
      href: {
        default: null,
        parseHTML: (el) => el.getAttribute('href'),
        renderHTML: (attrs) => ({ href: attrs.href }),
      },
    };
  },
  parseHTML: () => [{ tag: 'a[href]' }],
  renderHTML: ({ HTMLAttributes }) => ['a', HTMLAttributes, 0],
});

/** :name:`text` — rendered as <span class="name">, exactly as docutils does. */
export const Role = Mark.create({
  name: 'role',
  excludes: '_',
  addAttributes() {
    return {
      name: {
        default: null,
        parseHTML: (el) => el.getAttribute('data-role'),
        renderHTML: (attrs) => ({ class: attrs.name, 'data-role': attrs.name }),
      },
    };
  },
  parseHTML: () => [{ tag: 'span[data-role]' }],
  renderHTML: ({ HTMLAttributes }) => ['span', HTMLAttributes, 0],
  addKeyboardShortcuts() {
    const toggle = (name: string) => () =>
      this.editor.isActive('role', { name })
        ? this.editor.commands.unsetMark('role')
        : this.editor.commands.setMark('role', { name });
    return { 'Mod-u': toggle('underline'), 'Mod-Shift-x': toggle('strike') };
  },
});

/**
 * The schema: everything an RST block may contain.
 * Safe to use without an editor view, e.g. getSchema(rstExtensions) in tests.
 * Editor-only behaviour (undo, click-to-edit formulas) is added in ParagraphEditor.tsx.
 */
export const rstExtensions = [
  OneBlock, Paragraph, Text,
  Strong, Emphasis, Literal, Sup, Sub, Link, Role,
  InlineMath, BlockMath,
];
