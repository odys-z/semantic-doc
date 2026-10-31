# semantic-doc / src

One webpack project (React + Tiptap, Babel) containing the paragraph editor and
its tests. Paragraphs are edited WYSIWYG and stored as RST, so docutils /
Sphinx output looks the same as the editor.

Requires Node ≥ 22.18 (Babel 8).

```
src/
├── package.json, webpack.config.js, babel.config.js, tsconfig.json
├── editor/        # the page: index.tsx, App.tsx
│   ├── marks/       Tiptap marks limited to what RST can express
│   ├── rst/         Tiptap doc → RST serializer
│   ├── components/  ParagraphEditor, Toolbar
│   └── styles/      theme-roles.css (share with Sphinx), editor.css
├── test/
│   ├── cases/       serializer cases (expected RST verified with docutils)
│   ├── run.ts       npm test (Node)
│   └── index.tsx    browser test page (/test.html)
└── template/      docx → tree mapping templates (to come)
```

## Commands

```bash
npm install
npm start           # http://localhost:8080/  editor
                    # http://localhost:8080/test.html  test page
npm test            # bundle tests for Node and run them; exit 1 on failure
npm run typecheck   # tsc only — Babel strips types without checking them
npm run build       # production bundle → dist/ (editor page only)
```

## Notes

- **Babel does not type-check.** Run `npm run typecheck` (or put it in CI /
  before `build`).
- **Use `import type` for type-only imports.** Babel compiles one file at a
  time and cannot tell a type from a value; `verbatimModuleSyntax` in
  tsconfig makes `tsc` report any you miss.
- **Import ProseMirror only via `@tiptap/pm/*`** to keep a single copy.

## RST rules the editor enforces

- **No nested inline markup.** Every mark has `excludes: '_'`; applying one
  replaces another.
- **Word boundaries.** Markup inside a word or next to CJK text gets `\ `:
  `**bold**\ er`, `中文\ **粗体**\ 文字`.
- **Escaping.** `` * ` _ | \ `` in plain text are backslash-escaped; paragraphs
  that look like list items (`- `, `1. `) get a leading `\`.
- **Roles for visual effects.** Size / colour are roles (`:large:`text``),
  rendered as `<span class="large">` by docutils and the editor alike. To add
  one: `ROLES` in `editor/marks/index.ts` + a rule in
  `editor/styles/theme-roles.css` (add that file to Sphinx `html_css_files`).
