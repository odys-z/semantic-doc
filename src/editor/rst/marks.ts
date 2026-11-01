/**
 * Tiptap schema for one RST block: a paragraph, or a block formula.
 *
 * Marks combine freely (bold + italic + red + yellow background + link …).
 * RST inline markup cannot nest, so serialize.ts flattens each run of text
 * with the same marks into ONE piece of markup: a native construct when a
 * single effect is used, otherwise a composite custom role, e.g.
 *
 *   .. role:: strong_strike_red(strong)
 *      :class: strike red
 *
 *  mark          alone in RST                in a combination
 *  -----------   -------------------------   ---------------------------------
 *  strong        **text**                    base role strong, else .sd-strong
 *  emphasis      *text*                      base role emphasis, else .sd-em
 *  literal       ``text``                    base role literal, else .sd-code
 *  sup / sub     :sup:`text` / :sub:`text`   base role (wins over the others)
 *  underline     :underline:`text`           class .underline
 *  strike        :strike:`text`              class .strike
 *  size          :large:`text`               class .large   (see ROLES)
 *  color         :red:`text`                 class .red
 *  background    :bg-yellow:`text`           class .bg-yellow
 *  link          `text <url>`__              |sd-hash|__ + substitution + target
 *  inlineMath    :math:`latex`               base role math, classes: size/color/background only
 *  blockMath     .. math::                   .. math:: with :class: (size/color/background attrs)
 *
 * Formulas only take size, colour and background: B, I, U, S, code, sup, sub
 * and link are stripped from them (MathMarkFilter) and ignored by the serializer.
 */
import { Extension, Mark } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
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
 * Class-based effects. Each needs a rule in styles/theme-roles.css (shared
 * with the Sphinx theme). size / color / background are one mark each (with
 * the role name as attribute); decoration is two marks, underline and strike.
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

/** marks holding a role name: one of ROLES[group] */
export const CLASS_MARKS = ['size', 'color', 'background'] as const;
export type ClassMark = (typeof CLASS_MARKS)[number];

/** the only marks a formula (inline or block) can take */
export const MATH_MARKS: ReadonlySet<string> = new Set<string>(CLASS_MARKS);

/** Document holding exactly one block: the tree UI owns structure. */
export const OneBlock = Document.extend({ content: 'paragraph | blockMath' });

/** **strong** */
export const Strong = Mark.create({
  name: 'strong',
  parseHTML: () => [{ tag: 'strong' }, { tag: 'b' }],
  renderHTML: () => ['strong', 0],
  addKeyboardShortcuts() {
    return { 'Mod-b': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** *emphasis* */
export const Emphasis = Mark.create({
  name: 'emphasis',
  parseHTML: () => [{ tag: 'em' }, { tag: 'i' }],
  renderHTML: () => ['em', 0],
  addKeyboardShortcuts() {
    return { 'Mod-i': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** ``literal`` */
export const Literal = Mark.create({
  name: 'literal',
  code: true,
  parseHTML: () => [{ tag: 'code' }],
  renderHTML: () => ['code', 0],
  addKeyboardShortcuts() {
    return { 'Mod-e': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** :sup:`text` (docutils standard role); excludes sub */
export const Sup = Mark.create({
  name: 'sup',
  excludes: 'sup sub',
  parseHTML: () => [{ tag: 'sup' }],
  renderHTML: () => ['sup', 0],
  addKeyboardShortcuts() {
    return { 'Mod-.': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** :sub:`text` (docutils standard role); excludes sup */
export const Sub = Mark.create({
  name: 'sub',
  excludes: 'sup sub',
  parseHTML: () => [{ tag: 'sub' }],
  renderHTML: () => ['sub', 0],
  addKeyboardShortcuts() {
    return { 'Mod-,': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** :underline:`text` → <span class="underline"> */
export const Underline = Mark.create({
  name: 'underline',
  parseHTML: () => [{ tag: 'span.underline' }, { tag: 'u' }],
  renderHTML: () => ['span', { class: 'underline' }, 0],
  addKeyboardShortcuts() {
    return { 'Mod-u': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** :strike:`text` → <span class="strike"> */
export const Strike = Mark.create({
  name: 'strike',
  parseHTML: () => [{ tag: 'span.strike' }, { tag: 's' }, { tag: 'del' }],
  renderHTML: () => ['span', { class: 'strike' }, 0],
  addKeyboardShortcuts() {
    return { 'Mod-Shift-x': () => this.editor.commands.toggleMark(this.name) };
  },
});

/** `text <url>`__ */
export const Link = Mark.create({
  name: 'link',
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

/**
 * A mark carrying one role of a group, rendered as <span class="name">, the
 * way docutils renders :name:`text`. Marks of the same type exclude each
 * other, so setting a colour replaces the previous colour only.
 */
function classMark(name: ClassMark) {
  const data = `data-${name}`;
  return Mark.create({
    name,
    addAttributes() {
      return {
        name: {
          default: null,
          parseHTML: (el) => el.getAttribute(data),
          renderHTML: (attrs) => ({ class: attrs.name, [data]: attrs.name }),
        },
      };
    },
    parseHTML: () => [{ tag: `span[${data}]` }],
    renderHTML: ({ HTMLAttributes }) => ['span', HTMLAttributes, 0],
  });
}

export const Size = classMark('size');
export const Color = classMark('color');
export const Background = classMark('background');

/** size / color / background as node attributes of a block formula */
const blockAttr = (name: ClassMark) => ({
  default: null,
  parseHTML: (el: HTMLElement) => el.getAttribute(`data-${name}`),
  renderHTML: (attrs: Record<string, unknown>) => (attrs[name] ? { [`data-${name}`]: attrs[name] } : {}),
});

/** CSS classes of a block formula, also its `.. math::` :class: option */
export const blockMathClasses = (attrs: Record<string, unknown>): string[] =>
  CLASS_MARKS.map((k) => attrs[k]).filter((v): v is string => typeof v === 'string' && v !== '');

/** BlockMath with size / color / background, shown as classes on the formula */
export const StyledBlockMath = BlockMath.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      size: blockAttr('size'),
      color: blockAttr('color'),
      background: blockAttr('background'),
    };
  },
  addNodeView() {
    const factory = this.parent?.();
    if (!factory) return null;
    // the parent view has no update(): an attribute change re-creates it
    return (props) => {
      const view = factory(props);
      const cls = blockMathClasses(props.node.attrs);
      if (cls.length) (view.dom as HTMLElement).classList.add(...cls);
      return view;
    };
  },
});

/** Remove from inline formulas every mark but size / color / background. */
export const MathMarkFilter = Extension.create({
  name: 'mathMarkFilter',
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: new PluginKey('mathMarkFilter'),
        appendTransaction(trs, _old, state) {
          if (!trs.some((t) => t.docChanged)) return null;
          const tr = state.tr;
          state.doc.descendants((node, pos) => {
            if (node.type.name !== 'inlineMath') return;
            for (const m of node.marks)
              if (!MATH_MARKS.has(m.type.name)) tr.removeMark(pos, pos + node.nodeSize, m.type);
          });
          return tr.docChanged ? tr : null;
        },
      }),
    ];
  },
});

/**
 * The schema: everything an RST block may contain.
 * Safe to use without an editor view, e.g. getSchema(rstExtensions) in tests.
 * Editor-only behaviour (undo, click-to-edit formulas) is added in ParagraphEditor.tsx.
 *
 * Mark order is nesting order in the editor's HTML (first = outermost).
 */
export const rstExtensions = [
  OneBlock, Paragraph, Text,
  Link, Background, Color, Size, Underline, Strike, Strong, Emphasis, Literal, Sup, Sub,
  InlineMath, StyledBlockMath,
];
