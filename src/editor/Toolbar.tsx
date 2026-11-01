import React from 'react';
import { type Editor, useEditorState } from '@tiptap/react';
import { ROLES, CLASS_MARKS, type ClassMark, type RoleDef } from './rst/marks';
import type { MathKind } from './ParagraphEditor';

export interface ToolbarProps {
  editor: Editor;
  /** open the formula editor; pos < 0 means insert a new formula */
  onEditMath: (kind: MathKind, latex: string, pos: number) => void;
}

const MARKS = ['strong', 'emphasis', 'underline', 'strike', 'literal', 'sup', 'sub', 'link'] as const;
type ToggleMark = (typeof MARKS)[number];

export function Toolbar({ editor, onEditMath }: ToolbarProps) {
  const s = useEditorState({
    editor,
    selector: ({ editor }) => {
      const first = editor.state.doc.firstChild;
      const isBlockMath = first?.type.name === 'blockMath';
      return {
        ...(Object.fromEntries(MARKS.map((m) => [m, editor.isActive(m)])) as Record<ToggleMark, boolean>),
        // role name per class mark; for a block formula, its attributes
        ...(Object.fromEntries(
          CLASS_MARKS.map((k) => [
            k,
            ((isBlockMath ? first!.attrs[k] : editor.getAttributes(k).name) as string | null) ?? '',
          ]),
        ) as Record<ClassMark, string>),
        canUndo: editor.can().undo(),
        canRedo: editor.can().redo(),
        isBlockMath,
      };
    },
  });

  const run = () => editor.chain().focus();
  const toggle = (mark: ToggleMark) => run().toggleMark(mark).run();

  /** size / color / background: a mark on text and inline formulas, an attribute of a block formula */
  const setClass = (k: ClassMark, name: string) => {
    if (s.isBlockMath)
      return run()
        .command(({ tr }) => {
          tr.setNodeMarkup(0, undefined, { ...tr.doc.firstChild!.attrs, [k]: name || null });
          return true;
        })
        .run();
    return name ? run().setMark(k, { name }).run() : run().unsetMark(k).run();
  };

  const clearFormatting = () =>
    s.isBlockMath
      ? run()
          .command(({ tr }) => {
            const attrs = { ...tr.doc.firstChild!.attrs };
            CLASS_MARKS.forEach((k) => (attrs[k] = null));
            tr.setNodeMarkup(0, undefined, attrs);
            return true;
          })
          .run()
      : run().unsetAllMarks().run();

  const setLink = () => {
    if (s.link) return run().unsetMark('link').run();
    const href = window.prompt('URL');
    if (href) run().setMark('link', { href }).run();
  };

  // new inline formula, prefilled with the selected text
  const insertInlineMath = () => {
    const { from, to } = editor.state.selection;
    onEditMath('inline', editor.state.doc.textBetween(from, to), -1);
  };

  const toggleBlockMath = () =>
    s.isBlockMath
      ? // block formula → empty paragraph (undo restores the formula)
        run().setContent({ type: 'doc', content: [{ type: 'paragraph' }] }).run()
      : // paragraph → block formula, prefilled with the paragraph text
        onEditMath('block', editor.state.doc.textContent, -1);

  const btn = (on: boolean) => 'sd-btn' + (on ? ' on' : '');
  // a block formula takes only size / colour / background
  const off = s.isBlockMath;

  const classSelect = (k: ClassMark, title: string, roles: RoleDef[]) => {
    const value = roles.some((r) => r.name === s[k]) ? s[k] : '';
    return (
      <select
        className={'sd-select' + (value ? ' on' : '')}
        value={value}
        title={title}
        onChange={(e) => setClass(k, e.target.value)}
      >
        <option value="">{title}</option>
        {roles.map((r) => (
          <option key={r.name} value={r.name}>{r.label}</option>
        ))}
      </select>
    );
  };

  return (
    <div className="sd-toolbar">
      <button className="sd-btn" disabled={!s.canUndo} onClick={() => run().undo().run()} title="Undo (Ctrl+Z)">↶</button>
      <button className="sd-btn" disabled={!s.canRedo} onClick={() => run().redo().run()} title="Redo (Ctrl+Shift+Z / Ctrl+Y)">↷</button>
      <span className="sd-sep" />
      <button className={btn(s.strong)} disabled={off} onClick={() => toggle('strong')} title="Strong (Ctrl+B)"><b>B</b></button>
      <button className={btn(s.emphasis)} disabled={off} onClick={() => toggle('emphasis')} title="Emphasis (Ctrl+I)"><i>I</i></button>
      <button className={btn(s.underline)} disabled={off} onClick={() => toggle('underline')} title="Underline (Ctrl+U)"><u>U</u></button>
      <button className={btn(s.strike)} disabled={off} onClick={() => toggle('strike')} title="Strike (Ctrl+Shift+X)"><s>S</s></button>
      <button className={btn(s.literal)} disabled={off} onClick={() => toggle('literal')} title="Literal (Ctrl+E)"><code>{'<>'}</code></button>
      <button className={btn(s.sup)} disabled={off} onClick={() => toggle('sup')} title="Superscript (Ctrl+.)">x<sup>2</sup></button>
      <button className={btn(s.sub)} disabled={off} onClick={() => toggle('sub')} title="Subscript (Ctrl+,)">x<sub>2</sub></button>
      <button className={btn(s.link)} disabled={off} onClick={setLink} title="Link">🔗</button>
      <span className="sd-sep" />
      {classSelect('size', 'Size', ROLES.size)}
      {classSelect('color', 'Color', ROLES.color)}
      {classSelect('background', 'Background', ROLES.background)}
      <span className="sd-sep" />
      <button className="sd-btn" disabled={off} onClick={insertInlineMath} title="Inline formula (or type $$x^2$$)">∑</button>
      <button className={btn(s.isBlockMath)} onClick={toggleBlockMath} title={s.isBlockMath ? 'Back to paragraph' : 'Block formula'}>
        {s.isBlockMath ? '¶' : '∑▭'}
      </button>
      <span className="sd-sep" />
      <button className="sd-btn" onClick={clearFormatting} title="Clear formatting">⨯</button>
    </div>
  );
}
