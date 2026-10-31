import React from 'react';
import { useEditor, EditorContent, type JSONContent } from '@tiptap/react';
import { rstExtensions } from '../marks';
import { serializeDoc, type RstResult } from '../rst/serialize';
import { Toolbar } from './Toolbar';

export interface ParagraphEditorProps {
  /** Tiptap JSON (a doc with one paragraph) or an HTML string */
  content?: JSONContent | string;
  onChange?: (json: JSONContent, rst: RstResult) => void;
}

/** Edits one paragraph of the content tree; output is RST via serializeDoc. */
export function ParagraphEditor({ content, onChange }: ParagraphEditorProps) {
  const editor = useEditor({
    extensions: rstExtensions,
    content: content ?? '<p></p>',
    onCreate: ({ editor }) => onChange?.(editor.getJSON(), serializeDoc(editor.state.doc)),
    onUpdate: ({ editor }) => onChange?.(editor.getJSON(), serializeDoc(editor.state.doc)),
  });

  if (!editor) return null;
  return (
    <div className="sd-editor">
      <Toolbar editor={editor} />
      <EditorContent editor={editor} className="sd-content" />
    </div>
  );
}
