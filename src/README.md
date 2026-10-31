# semantic-doc / src

Sphinx output looks the same as the editor.

Requires Node ≥ 22.18 (Babel 8).


## Commands

```bash
npm install
npm start           # http://localhost:8080/  editor
                    # http://localhost:8080/test.html  test page
npm test            # bundle tests for Node and run them; exit 1 on failure
npm run typecheck   # tsc only — Babel strips types without checking them
npm run build       # production bundle → dist/ (editor page only)
```

