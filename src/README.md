# semantic-doc / src

Sphinx output looks the same as the editor.

Requires Node ≥ 22.18 (Babel 8).


## Commands

```bash
npm install
npm start           # http://localhost:8080/  demo page
                    # http://localhost:8080/test.html  test page
npm test            # bundle tests for Node and run them; exit 1 on failure
npm run typecheck   # tsc only — Babel strips types without checking them
npm run build       # production bundle → dist/ (demo page only)
```

## Layout

```
editor/            the widget (package entry: editor/index.ts)
  ParagraphEditor.tsx, Toolbar.tsx
  rst/marks.ts       Tiptap schema: marks, roles (ROLES), math nodes
  rst/serialize.ts   Tiptap doc → RST
  styles/            theme-roles.css (share with Sphinx), editor.css
demo/              dev page using the widget through its public API
test/cases/        serializer cases: inline, format, math
```

## Use as a widget

```tsx
import { ParagraphEditor } from 'semantic-doc';
import 'katex/dist/katex.min.css';    // once in the host app

<ParagraphEditor
  content={json}                      // Tiptap JSON: one paragraph or one blockMath
  onChange={(json, rst) => save(json, rst.rst)}
  editable={selected}                 // false: read-only, no toolbar
  editMath={(kind, latex) => openDialog(kind, latex)}  // optional; default window.prompt
/>
```

Peer dependencies: `react`, `react-dom`, `katex`. The package exports
TypeScript source, so the host compiles it (as anreact's own `.tsx` babel-loader
rule does). KaTeX CSS is left to the host because its fonts need a webpack asset
rule.
