/**
 * The viewer of published stories, viewer.js, one bundle for all the stories (React, Puck's Render, the
 * blocks' widgets and css): built by the app (album-web, web-dist/_story/), copied by html-service into
 * /<pubroot>/_story/ when it starts (io.oz.sdoc.htmlsrv.ResPathsTier).
 *
 * html-service writes each story's index.html, a shell loading this script (StoryShell):
 *
 *     <div id="story"></div>
 *     <script src="/pub/_story/viewer.js" data-page="page.json"></script>
 *
 * page.json is the committed page, a tree of PuckNode whose media (AnTreeNode) have node.synuid.
 * The script renders it with the editor's config (storyConfig, Puck's Render); the media files
 * are m/<synuid>, relative to the story's path, served by html-service from its map of the
 * story's media (synuid → the synode's volume path).
 */
import React from 'react';
import { createRoot } from 'react-dom/client';
import { Render } from '@puckeditor/core';

import { storyConfig, toPuck, viewerMetadata } from '../storytree/story-config';
import type { PuckNode } from '../storytree/templates';

async function main() {
	const script = document.currentScript as HTMLScriptElement | null;
	const el = document.getElementById('story');
	if (!el) return;

	// read now: document.currentScript is only set while the script runs, not after an await
	const src = script?.dataset.page ?? 'page.json';
	try {
		const r = await fetch(src);
		if (!r.ok) throw new Error(`${src}: ${r.status} ${r.statusText}`);
		const page = await r.json() as PuckNode;
		createRoot(el).render(<Render config={storyConfig} data={toPuck(page)} metadata={viewerMetadata()} />);
	}
	catch (e) {
		// shown in the page, not only in the console
		el.textContent = `The story can't be shown. ${e instanceof Error ? e.message : String(e)}`;
		console.error(e);
	}
}

main();
