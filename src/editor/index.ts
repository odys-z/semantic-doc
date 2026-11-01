/**
 * semantic-doc editor: public API.
 *
 *   import { ParagraphEditor, serializeDoc } from 'semantic-doc';
 *   import 'katex/dist/katex.min.css';   // once in the host app, for formulas
 *
 * The widget imports its own CSS (role styles + editor chrome). KaTeX CSS is
 * left to the host because it references font files, which need a webpack
 * asset rule (or load it from a CDN).
 */
export { ParagraphEditor, editorExtensions } from './ParagraphEditor';
export type { ParagraphEditorProps, EditMath, MathKind, EditorExtensionOptions } from './ParagraphEditor';

export { Toolbar } from './Toolbar';
export type { ToolbarProps } from './Toolbar';

export { rstExtensions, ROLES } from './rst/marks';
export type { RoleDef, RoleGroup } from './rst/marks';

export { serializeDoc, serializeParagraph, serializeBlockMath, escapeText } from './rst/serialize';
export type { RstResult } from './rst/serialize';

export type { JSONContent } from '@tiptap/core';
