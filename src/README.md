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
npm run build       # production bundle → dist/ (demo page only), then npm run pack
npm run pack        # the package tarball → dist-pack/semantic-doc-<version>.tgz
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
  rst/table.ts       tables: the importer's marker text → Tiptap table, grid table layout
  styles/            theme-roles.css (share with Sphinx), editor.css
doctree/           AnDoctreeditor (package entry: doctree/index.ts), on anreact's AnTreeditor
  doc-stree.ts       types as doctree.jserv's (DoctreeReq, DocNode, ...), StreeDoctier,
                     the table's rules (greying out buttons), replacing a received subtree
  rst-assembler.ts   doc tree → RST files, one per section (index.rst, <nid>.rst)
preview/           html preview (package entry: preview/index.ts): Sphinx in Pyodide, in a Web Worker
  RstPreviewer.ts    the worker's page side, one per template; sphinx-worker.ts the worker
  DocPreview.tsx     iframe showing the built pages
web-dist/          static resources of the demo pages (served by npm start)
  sdoc-template/     the demo's Sphinx template, made by make-template.py
demo/              dev pages using the widgets through their public API
  App.tsx            paragraph editor page (index.html)
  doctree.tsx        doc tree page (doctree.html)
  stub-tier.ts       plays the server over demo/jsons (load, text saves, commit)
  jsons/             server-shaped r/tree answers
test/cases/        serializer cases: inline, format, math, table; rst-assembler, doc-stree
```

## Commit and publish

Commit (c/commit) marks the doc's work as finished and sends its RST files: the synode builds
the doc's html package. Publish (c/publish) puts the package of the last commit at the synode's
html-service, or replaces the published one, and shows the doc's link, e.g. `/pub/er3cou/`, to open
in a new tab or copy; the Link button shows it again. The link is of the page's origin (the page is served by
html-service), or of `<AnDoctreeditor pubroot="http://127.0.0.1:8900" …>`. Html-service lists
the published docs at `/pub/`.

## Html preview

`<AnDoctreeditor preview="sdoc-template/" …>` shows the doc as html beside the tree: the
doc is assembled into RST files (`rst-assembler.ts`), built by Sphinx running in the browser
(Pyodide, in a Web Worker), and shown in an iframe. Rebuilt after every load, structural
change and saved text; the first build waits for Pyodide and Sphinx (seconds).
Expand shows the preview in the whole width, the tree hidden (still open, with the block
being edited); Show tree brings it back.

The template folder is a resource of the host app, served at the given url:

```
sdoc-template/
  manifest.json    source files to load, pinned packages (sphinx, theme, docutils), Pyodide url
  source/conf.py   Sphinx configuration
  source/static/theme-roles.css   the editor's role styles
  _static/         the theme's static files, from a native build; built pages load them via <base>
```

Make or refresh one with `python3 make-template.py [folder]` (needs the pinned packages
installed natively). The demo's is `web-dist/sdoc-template`; the synode release's is
jserv-album's `sdoc-template-0.8`.

## Tables

A block can be a table (@tiptap/extension-table): cells hold paragraphs, with the same
formatting and inline formulas; merged cells (rowspan / colspan) are kept. The toolbar's ⊞
turns a paragraph into a 2 × 2 table (the paragraph in its first cell), ¶ turns a table back
into one paragraph of its cells' content; in a table it adds row / column / merge / split /
header buttons (merge needs cells selected by dragging across them).

A table is serialized as an RST grid table, the only RST table with both row and column
spans; a top row of header cells (Header) is the grid table's header. Columns are laid out by
display width, East Asian wide characters taking two columns, as docutils measures them.

An imported docx table (node kind tabl) is the importer's marker text in ptext
(doctree.jserv DocxWalker#table()): rows joined by ` // `, cells by ` | `, a cell ending with
`{colspan=n}`, `{vmerge-start}` or `{vmerge-cont}`, paragraphs in a cell joined by ` {br} `.
`fillTipjson()` turns it into a Tiptap table when the tree is loaded; it's saved as tipjson /
rstext at the first edit, like a paragraph.

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

## Package tarball

Hosts install semantic-doc from its tarball, as a regular package, e.g. Anclient's album-web:

```bash
npm run pack                    # dist-pack/semantic-doc-0.1.0.tgz
cd <host> && npm install <path>/semantic-doc/src/dist-pack/semantic-doc-0.1.0.tgz
```

The tarball has `editor/`, `doctree/` and this README (`files` in package.json); npm installs
its dependencies (Tiptap) with it. Re-pack and re-install after changing semantic-doc.

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
