import React, { useEffect, useRef, useState } from 'react';
import { useEditor, EditorContent, type JSONContent } from '@tiptap/react';
import { UndoRedo } from '@tiptap/extensions';
import { InlineMath, BlockMath } from '@tiptap/extension-mathematics';
import { rstExtensions } from './rst/marks';
import { serializeDoc, type RstResult } from './rst/serialize';
import { Toolbar } from './Toolbar';
import './styles/theme-roles.css';
import './styles/editor.css';

export type MathKind = 'inline' | 'block';

export interface EditorExtensionOptions {
  /** called when a formula is clicked, e.g. to open a LaTeX editor */
  onMathClick?: (kind: MathKind, latex: string, pos: number) => void;
}

/**
 * Schema + editor-only behaviour: undo / redo (Ctrl+Z, Ctrl+Shift+Z / Ctrl+Y)
 * and click-to-edit formulas.
 */
export function editorExtensions(opts: EditorExtensionOptions = {}) {
  const { onMathClick } = opts;
  return [
    ...rstExtensions.filter((e) => e !== InlineMath && e !== BlockMath),
    InlineMath.configure({ onClick: (node, pos) => onMathClick?.('inline', node.attrs.latex, pos) }),
    BlockMath.configure({ onClick: (node, pos) => onMathClick?.('block', node.attrs.latex, pos) }),
    UndoRedo,
  ];
}

/**
 * Ask the user for LaTeX. Return the new LaTeX, '' to delete the formula,
 * or null to cancel. May be async (e.g. a MUI dialog in the host app).
 */
export type EditMath = (kind: MathKind, latex: string) => string | null | Promise<string | null>;

export interface ParagraphEditorProps {
  /** Tiptap JSON (doc with one paragraph or one blockMath) or an HTML string */
  content?: JSONContent | string;
  /** called on every change with the JSON (to store) and the RST */
  onChange?: (json: JSONContent, rst: RstResult) => void;
  /** false: read-only, toolbar hidden. Default true */
  editable?: boolean;
  /** show the toolbar. Default true (always hidden when not editable) */
  toolbar?: boolean;
  /** formula editor; default is window.prompt */
  editMath?: EditMath;
  autofocus?: boolean;
  className?: string;
}

const promptMath: EditMath = (kind, latex) =>
  window.prompt(kind === 'block' ? 'Block formula (LaTeX)' : 'Formula (LaTeX)', latex);

/**
 * WYSIWYG editor for one RST block (a paragraph or a block formula).
 * Self-contained widget, e.g. for an AnTreeditor column formatter:
 *
 *   formatter: (col, tnode) => (
 *     <ParagraphEditor content={tnode.node.json}
 *       onChange={(json, rst) => { tnode.node.json = json; tnode.node.rst = rst.rst; }} />)
 */
export function ParagraphEditor({
  content,
  onChange,
  editable = true,
  toolbar = true,
  editMath = promptMath,
  autofocus = false,
  className,
}: ParagraphEditorProps) {
  // latest props for handlers created once with the editor
  const latest = useRef({ onChange, editMath });
  latest.current = { onChange, editMath };

  const editorRef = useRef<ReturnType<typeof useEditor>>(null);

  // editor extensions are created once; math clicks go through the ref
  const [extensions] = useState(() =>
    editorExtensions({
      onMathClick: (kind, latex, pos) => editFormula(kind, latex, pos),
    }),
  );

  /** edit the formula at pos, or insert a new one when pos < 0 */
  async function editFormula(kind: MathKind, latex: string, pos: number) {
    const editor = editorRef.current;
    if (!editor || !editor.isEditable) return;
    const next = await latest.current.editMath(kind, latex);
    if (next === null || (pos >= 0 && next === latex)) return;
    const chain = editor.chain().focus();
    if (pos < 0) {
      if (!next) return;
      if (kind === 'inline') chain.insertInlineMath({ latex: next }).run();
      else chain.setContent({ type: 'doc', content: [{ type: 'blockMath', attrs: { latex: next } }] }).run();
    } else if (kind === 'inline')
      (next ? chain.updateInlineMath({ latex: next, pos }) : chain.deleteInlineMath({ pos })).run();
    else if (next) chain.updateBlockMath({ latex: next, pos }).run();
    else chain.setContent({ type: 'doc', content: [{ type: 'paragraph' }] }).run();
  }

  const emit = (e: NonNullable<typeof editorRef.current>) =>
    latest.current.onChange?.(e.getJSON(), serializeDoc(e.state.doc));

  const editor = useEditor({
    extensions,
    content: content ?? '<p></p>',
    editable,
    autofocus,
    onCreate: ({ editor }) => emit(editor),
    onUpdate: ({ editor }) => emit(editor),
  });
  editorRef.current = editor;

  // follow prop changes (e.g. the tree selects another node), without echo loops
  useEffect(() => {
    if (!editor || content === undefined) return;
    const same =
      typeof content === 'string'
        ? content === editor.getHTML()
        : JSON.stringify(content) === JSON.stringify(editor.getJSON());

    if (!same)
      editor.chain()
            .setMeta('addToHistory', false)
            .setContent(content, { emitUpdate: false })
            .run();
  }, [editor, content]);

  useEffect(() => {
    editor?.setEditable(editable);
  }, [editor, editable]);

  if (!editor) return null;
  return (
    <div className={'sd-editor' + (editable ? '' : ' sd-readonly') + (className ? ' ' + className : '')}>
      {editable && toolbar && (
        <Toolbar editor={editor} onEditMath={(kind, latex, pos) => editFormula(kind, latex, pos)} />
      )}
      <EditorContent editor={editor} className="sd-content" />
    </div>
  );
}
