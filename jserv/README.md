# semantic-doc demo jserv

**This project is for demo only.**

It is a copy of [semantic-jserv/jserv-sample](https://github.com/odys-z/semantic-jserv/tree/master/jserv-sample),
used to serve the semantic-doc demo (`src/demo`) with a doc-content tier.
The jsample code (now package `io.oz.semandoc`, and `io.oz.jservapp`) and settings are kept as in jserv-sample,
except the x-visual (vec3) and cheap-flow (workflow) tiers, which are removed with their configs.

The doc-tree tier core (package `io.oz.sdoc`) is in [semantic-doc/doctree.jserv](../doctree.jserv),
shared with the synodes (docsync). This project is the app and tests depending on it.

## Build & test

Install the doc-tree tier first:

```
    cd ../doctree.jserv && mvn clean install
```

Then, here:

```
    mvn test
    mvn clean package -DskipTests
```

The server is `io.oz.semandoc.SmandocServ` (servlet path `smandoc`), copied from jserv-sample's `JSampleApp`.
To package and run it, see the doc string of `tasks.py` (`invoke package`).
