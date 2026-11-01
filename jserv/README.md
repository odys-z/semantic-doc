# semantic-doc demo jserv

**This project is for demo only.**

It is a copy of [semantic-jserv/jserv-sample](https://github.com/odys-z/semantic-jserv/tree/master/jserv-sample),
used to serve the semantic-doc demo (`src/demo`) with a doc-content tier.
The jsample code (`io.odysz.jsample`, `io.oz.jservapp`) and settings are kept as in jserv-sample,
except the x-visual (vec3) and cheap-flow (workflow) tiers, which are removed with their configs.

The actual doc-content tier is planned to be moved into `semantic-doc/docsync-jserv` in the future.

## What's added

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

```
    mvn test
    mvn clean package -DskipTests
```

To run it as jserv-sample's test app, see the doc string of `tasks.py` (`invoke package`).
