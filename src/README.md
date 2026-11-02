# semantic-doc / src

Sphinx output looks the same as the editor.

Requires Node ≥ 22.18 (Babel 8).


## Commands

```bash
npm install
npm start           # http://localhost:8080/  demo page
                    # http://localhost:8080/doctree.html  doc tree page
                    # http://localhost:8080/test.html  test page
npm test            # bundle tests for Node and run them; exit 1 on failure
npm run typecheck   # tsc only — Babel strips types without checking them
npm run build       # production bundle → dist/ (demo page only)
```

The doc tree needs the anclient sources next to this repo (`../../anclient/js`,
linked by `npm install`). Webpack compiles them with Babel and resolves their
imports from this project's `node_modules`; `npm run typecheck` also checks them,
and reports anclient's own errors under this project's strict settings.

## Layout

```
editor/            the widget (package entry: editor/index.ts)
  ParagraphEditor.tsx, Toolbar.tsx
  rst/marks.ts       Tiptap schema: marks, roles (ROLES), math nodes
  rst/serialize.ts   Tiptap doc → RST
  styles/            theme-roles.css (share with Sphinx), editor.css
doctree/           AnDoctreeditor (package entry: doctree/index.ts), on anreact's AnTreeditor
  doctree-tier.ts    wire types (s-tree modification) + where a node goes (moveReq, insertReq)
demo/              dev pages using the widgets through their public API
  App.tsx            paragraph editor page (index.html)
  doctree.tsx        doc tree page (doctree.html)
  stub-tier.ts       plays the server over demo/jsons (load, text saves, commit)
  jsons/             server-shaped r/tree answers, until SmandocServ serves them
test/cases/        serializer cases: inline, format, math
```

## Combining styles

Marks combine freely. RST cannot nest inline markup, so each run with several
effects becomes one composite role (base role + classes from `theme-roles.css`):

```rst
.. role:: strong_strike_red(strong)
   :class: strike red

:strong_strike_red:`text`
```

- Formulas take only size / colour / background (inline: a `math`-based role;
  block: `.. math::` with `:class:`). B, I, U, S, code, sup/sub and links are
  stripped from them.
- A styled link becomes `|sd-<hash>|__` + a substitution definition + an
  anonymous target after the paragraph. To build a whole document from many
  blocks, use `joinRst(results)`: it declares each role once and defines each
  shared substitution once (docutils rejects duplicate substitutions).
- Classes on formulas survive in Sphinx (MathJax), not in plain docutils MathML.

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
