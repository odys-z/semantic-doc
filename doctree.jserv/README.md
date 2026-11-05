# semantic-doc doc-tree tier

The core of semantic-doc's doc-content (doc-tree) tier, a plain jar
(`io.github.odys-z:doctree.jserv`).

Depended on by:

- the synodes (docsync), and
- the demo server, [semantic-doc/jserv](../jserv) (app and tests).

No servlet container or jdbc driver is included; those belong to the apps.

## Working copy

`DoctreeDB`: the doc trees being edited, in their own sqlite db (e.g. `$VOLUME_HOME/doctree.db`,
connection `doctree.VOLUME_HOME` in jserv-album). Its tables are created / migrated at the first
doc-tree request, so a synode never editing doc trees keeps an empty file. Not synchronized.

- `s_docs`: doc headers (`DocInf`), with the import state (parsing, ready, failed)
- `s_docnodes`: nodes (`DocNode`), fullpath semantics in `semantics-doctree.xml`
- `s_doctree_meta`: schema version; `oz_autoseq`: auto keys

Ids: doc: the synode + an auto key (autopk semantics, `oz_autoseq` in the db), e.g. `X.000A`, in Radix32
with the synode's config.xml `file-sys = windows` (case-insensitive file names on every synode), `id-len`
digits; node `<docId>.<n>`. The doc root is a node too: its nid and fullpath are the
docId, so a subtree, selected by fullpath prefix, stays in its doc.

Connects requires the db file to exist: call `DoctreeDB.touch()` before `Connects.init()`.

## Importing a docx

`c/import` (`DoctreeReq.importDocx(srcId, title)`) answers the doc header (state parsing) at once;
a worker parses the docx and inserts the nodes (state ready, or failed with a message).

    DocxSource (record id → path)  →  IBlockParser (docx → Blocks)  →  Outliner (Blocks → nodes)

The parser is `io.oz.sdoc.docx.DocxWalker` (docx4j), the "facts first" walker verified in
hello/docx4j (FactsDemo); `src/test/res/tricky.docx` is its sample. New nodes carry structure,
text, src and style only; the client fills Tiptap json and rst.

Fullpath: 4 digits per level (`DoctreeDB.sortDigits`, the fullpath semantics' args[3], registered to
`SemanticTree.sortDigits()`); a fullpath starts with its docId.

```java
    new DoctreeTier(port)
        .conn("doctree.VOLUME_HOME", synode)
        .importer(DocxSource.extfiles(synconn, "h_photos"), DocxWalker::blocks);
```

## Types

Package `io.oz.sdoc`:

| Type          | What                                                                 |
|---------------|----------------------------------------------------------------------|
| `DocInf`      | doc header, the `"doc"` part of `src/dist/jsons/doc-a.json`, `doc-b.json` |
| `DocNode`     | content record, one element of `"nodes"`                             |
| `DoctreeReq`  | request body, actions in `DoctreeReq.A`                              |
| `DoctreeResp` | response body, `{ doc, nodes }` for `r/tree`                         |
| `DoctreeTier` | the ServPort: `r/docs`, `c/import` so far                             |
| `DoctreeDB`   | the working copy's db                                                |

`DocNode.json` (Tiptap JSON) is carried as a serialized string, because Tiptap's
`"type"` key collides with Anson's `"type"` (class name) key.

## Build & test

Tests use `src/test/res/WEB-INF` and create `src/test/res/doctree-test.db`.
Install it before building the apps depending on it:

```
    mvn test
    mvn clean install
```
