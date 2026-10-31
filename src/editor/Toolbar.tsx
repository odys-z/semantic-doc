import React from 'react';
import { type Editor, useEditorState } from '@tiptap/react';
import { ROLES, type RoleDef } from './rst/marks';
import type { MathKind } from './ParagraphEditor';

export interface ToolbarProps {
  editor: Editor;
  /** open the formula editor; pos < 0 means insert a new formula */
  onEditMath: (kind: MathKind, latex: string, pos: number) => void;
}

const MARKS = ['strong', 'emphasis', 'literal', 'sup', 'sub', 'link'] as const;

export function Toolbar({ editor, onEditMath }: ToolbarProps) {
  const s = useEditorState({
    editor,
    selector: ({ editor }) => ({
      ...Object.fromEntries(MARKS.map((m) => [m, editor.isActive(m)])) as Record<(typeof MARKS)[number], boolean>,
      role: (editor.getAttributes('role').name as string | undefined) ?? '',
      canUndo: editor.can().undo(),
      canRedo: editor.can().redo(),
      isBlockMath: editor.state.doc.firstChild?.type.name === 'blockMath',
    }),
  });

  const run = () => editor.chain().focus();
  const toggle = (mark: string) => run().toggleMark(mark).run();
  const setRole = (name: string) => (name ? run().setMark('role', { name }).run() : run().unsetMark('role').run());
  const toggleRole = (name: string) => setRole(s.role === name ? '' : name);

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
  const off = s.isBlockMath; // inline formatting does not apply to a block formula

  const roleSelect = (title: string, roles: RoleDef[]) => {
    const value = roles.some((r) => r.name === s.role) ? s.role : '';
    return (
      <select
        className={'sd-select' + (value ? ' on' : '')}
        value={value}
        disabled={off}
        title={title}
        onChange={(e) => setRole(e.target.value)}
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
      <button className={btn(s.role === 'underline')} disabled={off} onClick={() => toggleRole('underline')} title="Underline (Ctrl+U)"><u>U</u></button>
      <button className={btn(s.role === 'strike')} disabled={off} onClick={() => toggleRole('strike')} title="Strike (Ctrl+Shift+X)"><s>S</s></button>
      <button className={btn(s.literal)} disabled={off} onClick={() => toggle('literal')} title="Literal (Ctrl+E)"><code>{'<>'}</code></button>
      <button className={btn(s.sup)} disabled={off} onClick={() => toggle('sup')} title="Superscript (Ctrl+.)">x<sup>2</sup></button>
      <button className={btn(s.sub)} disabled={off} onClick={() => toggle('sub')} title="Subscript (Ctrl+,)">x<sub>2</sub></button>
      <button className={btn(s.link)} disabled={off} onClick={setLink} title="Link">🔗</button>
      <span className="sd-sep" />
      {roleSelect('Size', ROLES.size)}
      {roleSelect('Color', ROLES.color)}
      {roleSelect('Background', ROLES.background)}
      <span className="sd-sep" />
      <button className="sd-btn" disabled={off} onClick={insertInlineMath} title="Inline formula (or type $$x^2$$)">∑</button>
      <button className={btn(s.isBlockMath)} onClick={toggleBlockMath} title={s.isBlockMath ? 'Back to paragraph' : 'Block formula'}>
        {s.isBlockMath ? '¶' : '∑▭'}
      </button>
      <span className="sd-sep" />
      <button className="sd-btn" disabled={off} onClick={() => run().unsetAllMarks().run()} title="Clear formatting">⨯</button>
    </div>
  );
}
