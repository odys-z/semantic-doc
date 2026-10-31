/**
 * Tiptap extensions for a single RST paragraph.
 *
 * RST inline markup cannot nest, so every mark declares `excludes: '_'`:
 * applying one mark replaces any other on the same text. The editor can
 * therefore only hold content that serializes cleanly to RST.
 */
import { Mark } from '@tiptap/core';
import Document from '@tiptap/extension-document';
import Paragraph from '@tiptap/extension-paragraph';
import Text from '@tiptap/extension-text';

/** Role names offered in the toolbar. Each needs a CSS rule in styles/theme-roles.css. */
export const ROLES = [
  { name: 'small', label: 'Small' },
  { name: 'large', label: 'Large' },
  { name: 'x-large', label: 'X-Large' },
  { name: 'red', label: 'Red' },
] as const;

/** Document holding exactly one paragraph: the tree UI owns structure. */
export const OneParagraph = Document.extend({ content: 'paragraph' });

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
});

/** Everything the paragraph editor is allowed to contain. */
export const rstExtensions = [OneParagraph, Paragraph, Text, Strong, Emphasis, Literal, Link, Role];
