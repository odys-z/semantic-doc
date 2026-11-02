# semantic-doc doc-tree tier

The core of semantic-doc's doc-content (doc-tree) tier, a plain jar
(`io.github.odys-z:doctree.jserv`).

Depended on by:

- the synodes (docsync), and
- the demo server, [semantic-doc/jserv](../jserv) (app and tests).

No servlet container or jdbc driver is included; those belong to the apps.

## Types

Package `io.oz.sdoc`, data types only (no ServPort yet):

| Type          | What                                                                 |
|---------------|----------------------------------------------------------------------|
| `DocInf`      | doc header, the `"doc"` part of `src/dist/jsons/doc-a.json`, `doc-b.json` |
| `DocNode`     | content record, one element of `"nodes"`                             |
| `DoctreeReq`  | request body, actions in `DoctreeReq.A`                              |
| `DoctreeResp` | response body, `{ doc, nodes }` for `r/tree`                         |

`DocNode.json` (Tiptap JSON) is carried as a serialized string, because Tiptap's
`"type"` key collides with Anson's `"type"` (class name) key.

## Build & test

Install it before building the apps depending on it:

```
    mvn test
    mvn clean install
```
