import React from 'react';
import { type Editor, useEditorState } from '@tiptap/react';
import { ROLES } from '../marks';

export function Toolbar({ editor }: { editor: Editor }) {
  const s = useEditorState({
    editor,
    selector: ({ editor }) => ({
      strong: editor.isActive('strong'),
      emphasis: editor.isActive('emphasis'),
      literal: editor.isActive('literal'),
      link: editor.isActive('link'),
      role: (editor.getAttributes('role').name as string | undefined) ?? '',
    }),
  });

  const toggle = (mark: string) => editor.chain().focus().toggleMark(mark).run();

  const setLink = () => {
    if (s.link) return editor.chain().focus().unsetMark('link').run();
    const href = window.prompt('URL');
    if (href) editor.chain().focus().setMark('link', { href }).run();
  };

  const setRole = (name: string) =>
    name
      ? editor.chain().focus().setMark('role', { name }).run()
      : editor.chain().focus().unsetMark('role').run();

  const btn = (on: boolean) => 'sd-btn' + (on ? ' on' : '');

  return (
    <div className="sd-toolbar">
      <button className={btn(s.strong)} onClick={() => toggle('strong')} title="Strong (Ctrl+B)"><b>B</b></button>
      <button className={btn(s.emphasis)} onClick={() => toggle('emphasis')} title="Emphasis (Ctrl+I)"><i>I</i></button>
      <button className={btn(s.literal)} onClick={() => toggle('literal')} title="Literal (Ctrl+E)"><code>{'<>'}</code></button>
      <button className={btn(s.link)} onClick={setLink} title="Link">🔗</button>
      <select value={s.role} onChange={(e) => setRole(e.target.value)} title="Role / size">
        <option value="">Normal</option>
        {ROLES.map((r) => (
          <option key={r.name} value={r.name}>{r.label}</option>
        ))}
      </select>
      <button className="sd-btn" onClick={() => editor.chain().focus().unsetAllMarks().run()} title="Clear formatting">⨯</button>
    </div>
  );
}
