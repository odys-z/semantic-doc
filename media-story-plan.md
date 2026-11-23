# Media story page — layout and work packages

Status: plan, 2026-11-23. Stub patches: claude/patches/*-media-story-stubs.patch.

## Idea

A *media story* is a doctree doc (s_docs, kind=story) whose content is a Puck page (JSON) instead of RST.
Users start from a template, drag media / folders from the album tree into it, and edit text in place.
Published as a **half-dynamic boilerplate** at html-service: a shell page + the shared viewer + the story's
committed page.json; media served by html-service from a per-story json map (synuid → volume path),
given by the synode at publish and refresh. No copies, no build, no synode call per request.

## Layout

```
 album-web (sys-menu item /story, lazy chunk "story")
   └─ semantic-doc/story  AnStoryEditor        ← Puck (drawer/fields hidden by overrides), templates, blocks
        ├─ anreact          tree (draggable nodes, payload with pid), GalleryView, Lightbox (use node.src)
        └─ semantier        StreeTier (generic stree), doctree tier requests (r/page, u/page)

 synode (jserv-album process)
   └─ doctree.jserv (lib)  story save/load (pids), commit (pid → io_oz_synuid, s_docmedia), publish (+ PubMedia)
        └─ MediaSource by synuid, injected like .importer(DocxSource.extfiles(cfg.synconn, "h_photos"))

 html-service
   /pub/<id>/              shell index.html (StoryShell, j2html)
   /pub/_story/viewer.js   shared viewer: Puck's <Render> + blocks + GalleryView/Lightbox
   /pub/<id>/page.json     the committed page (items by uid)
   /pub/<id>/m/<synuid>    StoryMedia filter: <staging>/maps/<id>.media.json → file, ranges; 404 if unmapped
   /pub/                   index (title, creator) — docs and stories
```

## Media references and src

- Editing page: items by **pid** (local, as the album tree gives them). The tree needn't provide synuids.
- Commit: the synode maps each pid → the record's **io_oz_synuid** ("<synode>,<pid>"), writes the committed page
  (the story's h_photos record file; htmlUri) and s_docmedia(docId, synuid).
- Nothing of a src is saved. Blocks read Puck's `metadata.mediaSrc(item)`:
  editor `<Puck metadata>` = session link `GalleryView.imgSrcReq(pid, 'h_photos', tier)`;
  viewer `<Render metadata>` = `m/${encodeURIComponent(uid)}`.
- Blocks build GalleryView nodes with `node.src`; GalleryView.parse / Lightbox.parse use `p.node.src ?? imgSrcReq(...)`;
  GalleryView passes `props.photos` to the lightbox (not tnode children). No MediaSrc type / context in anreact.

## Published media map

- PubMedia {pubpath, paths: synuid → volume path}: synode → html-service only (never in DocInf, which goes to browsers).
  Sent with ResPublishReq.A.deploy (the story's) and ResPublishResp of A.refresh (all published stories).
- html-service keeps one json per story, `<resource>.staging/maps/<story>.media.json` (not served), written by temp file + move,
  removed with the story; read per media request. Survives restarts; refresh corrects it.
- No cache now. DynaResource.storyMediaCache (default 0) reserved for a future configurable LRU.
- StoryMedia.report() at startup and after refresh: stories, media count, maps' size on disk;
  warn over 100,000 media in all, or 5,000 in one story.

## Page JSON (committed)

```json
{
  "root": { "props": { "title": "Spring Trip to Cameron Highlands", "template": "photo-story" } },
  "content": [
    { "type": "Hero", "props": { "id": "Hero-1",
        "media": { "pid": "00A3F1", "uid": "X29,00A3F1", "mime": "image/jpeg", "wh": [4032, 3024] },
        "title": "Spring Trip to Cameron Highlands", "subtitle": "April 2026" } },
    { "type": "Text", "props": { "id": "Text-1", "content": "Three days in the tea hills…" } },
    { "type": "Gallery", "props": { "id": "Gallery-1", "folder": "2026-04 Cameron", "layout": "rows",
        "items": [ { "pid": "00A3F2", "uid": "X29,00A3F2", "mime": "image/jpeg", "wh": [4032, 3024] } ] } }
  ]
}
```

## Work packages

| WP | Where | What | Depends |
|---|---|---|---|
| 0 | hello/puck-media | Prototype: Puck + our dnd-kit tree draggables coexisting; folder → Gallery block; inline text; iframe off; metadata. Mock data. | — |
| 1 | anreact | Draggable tree nodes (payload: pid, mime, wh; folder + child items). node.src fallback in GalleryView / Lightbox, props.photos to lightbox; works without tier / onFullScreen. | 0 |
| 2 | semantic-doc (TS) | src/story: AnStoryEditor (metadata mediaSrc), blocks, templates, AlbumPanel, MediaDrop, tier requests; subpath export, lazy. | 0, 1 |
| 3 | semantic-doc (Java) | s_docs.kind, page storage, s_docmedia(docId, synuid) via DoctreeDBUpgrade + picks; r/page, u/page; StoryPages.commit (pid→synuid); MediaSource by synuid; PubMedia on deploy / refresh. | — |
| 4 | html-service | StoryShell; story deploy (shell + page.json + map json); StoryMedia filter + report(); _story folder reserved. | 3, 5 |
| 5 | semantic-doc (viewer) | viewer.js / viewer.css entry: <Render metadata={{mediaSrc: m/<uid>}}>; installed into html-service as web assets. | 1, 2 |
| 6 | album-web + jserv-album | /story page (GalleryTier + sk), webpack vendor-puck; .media(MediaSource.extfiles(...)) once doctree.jserv has it. | 2, 3 |

Order: WP0 → WP1 ∥ WP3 → WP2 → WP5 → WP4 → WP6. One combined patch per repo per WP.

## Decided

- Editor: Puck (@puckeditor/core ^0.24, MIT, no account; no Puck AI / cloud packages).
- Users don't learn Puck: templates + drag media + in-place text. Puck drawer/fields hidden now, kept for later.
- Drag uses dnd-kit (pointer/touch/keyboard). Folder drop → new Gallery block (lightbox), items copied at drop, folder kept as label.
- No desktop-file upload from the editor.
- Media treated like doc-refs: local files; commit fails listing records not found.
- Permission tagging: later, if needed.
- No server-side preview generation; same GalleryView/Lightbox as webview for editor and published page.
- Editor lives in semantic-doc (knows only StreeTier); tree widgets in anreact; lazy loaded after the sys-menu click.
- Media src via Puck metadata into node.src; editing by pid, committed by synuid; html-service per-story json maps.

## Open

- Page storage: column on s_docs vs own table (versions per commit?).
- Gallery layout: current GalleryView only, or add react-photo-album layouts in anreact later.
