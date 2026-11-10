/**
 * Source of the preview's Web Worker: hello/rst-playground's start-up and build, off the page's thread.
 *
 * Kept as plain JavaScript text and started from a Blob (RstPreviewer), so it needs no bundler
 * support in the host app, and Babel can't rewrite it. A classic worker: importScripts() loads
 * Pyodide from the URL in the template's manifest.json.
 *
 * Messages, page → worker: { id, op, args }, answered with { id, ok, r | err };
 * worker → page, any time: { status } (progress text).
 *
 *   init  { template }   fetch manifest.json, load Pyodide, micropip-install manifest.packages,
 *                        copy manifest.files from template/source/ into /sphinx/source/
 *   build { files, media }
 *                        files: [{ path, rst }], the doc's files. Writes the changed ones,
 *                        deletes the ones gone, runs sphinx-build (incremental).
 *                        media: [{ path, key, bytes }], resources, e.g. media/image1.png;
 *                        written when the key (its url) changed.
 *                        → { log, pages: ["index.html", ...] } (the doc's pages)
 *   page  { path }       → the html of build/html/<path>
 */
export const workerSource = String.raw`
"use strict";
const SRC = "/sphinx/source", OUT = "/sphinx/build/html", DOCTREES = "/sphinx/build/doctrees";
let py;          // the Pyodide instance
let ready;       // promise of init
const written = new Map(); // doc files in SRC: path -> rst
const writtenMedia = new Map(); // resources in SRC: path -> key

const status = (text) => self.postMessage({ status: text });

async function init({ template }) {
  const t0 = performance.now();
  status("Loading the template…");
  const res = await fetch(new URL("manifest.json", template), { cache: "no-cache" });
  if (!res.ok) throw new Error("Cannot load " + new URL("manifest.json", template) + ": " + res.status);
  const m = await res.json();

  status("Loading Pyodide…");
  importScripts(m.pyodide);
  py = await loadPyodide();

  status("Installing Sphinx…");
  await py.loadPackage("micropip");
  const micropip = py.pyimport("micropip");
  await micropip.install(m.packages);
  micropip.destroy();
  // a failed download is only logged by Pyodide: import now, so it fails here
  py.runPython("import sphinx, sphinx_rtd_theme, docutils");

  const source = new URL(m.source, template);
  await Promise.all(m.files.map(async (file) => {
    const url = new URL(file, source);
    const r = await fetch(url, { cache: "no-cache" });
    if (!r.ok) throw new Error("Cannot load " + url + ": " + r.status);
    const path = SRC + "/" + file;
    py.FS.mkdirTree(path.slice(0, path.lastIndexOf("/")));
    py.FS.writeFile(path, new Uint8Array(await r.arrayBuffer()));
  }));
  status("Ready in " + ((performance.now() - t0) / 1000).toFixed(1) + " s");
  return { files: m.files.length, packages: m.packages };
}

async function build({ files, media }) {
  await ready;
  const t0 = performance.now();
  status("Building…");
  for (const m of media || [])
    if (writtenMedia.get(m.path) !== m.key) {
      const path = SRC + "/" + m.path;
      py.FS.mkdirTree(path.slice(0, path.lastIndexOf("/")));
      py.FS.writeFile(path, new Uint8Array(m.bytes));
      writtenMedia.set(m.path, m.key);
    }
  const keep = new Set(files.map((f) => f.path));
  for (const path of [...written.keys()])
    if (!keep.has(path)) {
      py.FS.unlink(SRC + "/" + path);
      written.delete(path);
    }
  // only changed files get a new mtime, so Sphinx rebuilds only those
  for (const f of files)
    if (written.get(f.path) !== f.rst) {
      py.FS.writeFile(SRC + "/" + f.path, f.rst);
      written.set(f.path, f.rst);
    }

  const result = py.runPython([
    "import io, contextlib",
    "from sphinx.cmd.build import build_main",
    "_log = io.StringIO()",
    "with contextlib.redirect_stdout(_log), contextlib.redirect_stderr(_log):",
    "    _rc = build_main(['-b', 'html', '-q', '-d', '" + DOCTREES + "', '" + SRC + "', '" + OUT + "'])",
    "(_rc, _log.getvalue())",
  ].join("\n"));
  const [code, log] = result.toJs();
  result.destroy();
  if (code !== 0) throw new Error("sphinx-build failed (exit code " + code + ")\n\n" + log);

  // the doc's pages: Sphinx keeps the html of deleted files, e.g. of the previous doc
  const pages = [...written.keys()].map((p) => p.replace(/\.rst$/, ".html"));
  const warnings = log.trim() ? log.trim().split("\n").length : 0;
  status("Built in " + ((performance.now() - t0) / 1000).toFixed(1) + " s"
         + (warnings ? ", " + warnings + " warning line(s)" : ""));
  return { log, pages };
}

async function page({ path }) {
  await ready;
  return py.FS.readFile(OUT + "/" + path, { encoding: "utf8" });
}

const ops = {
  init: (args) => (ready = init(args)),
  build,
  page,
};

self.onmessage = async (e) => {
  const { id, op, args } = e.data;
  try {
    self.postMessage({ id, ok: true, r: await ops[op](args) });
  } catch (err) {
    if (op === "init") status("Start-up failed: " + (err && err.message || err));
    self.postMessage({ id, ok: false, err: String(err && err.message || err) });
  }
};
`;
