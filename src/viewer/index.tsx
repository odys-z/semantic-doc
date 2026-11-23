/**
 * STUB (media story, WP5): the viewer of published stories, viewer.js / viewer.css, one bundle
 * for all the stories, installed into html-service at /<pubroot>/_story/.
 *
 * html-service writes each story's index.html, a shell loading this script:
 *
 *     <div id="story"></div>
 *     <script src="/pub/_story/viewer.js" data-page="page.json"></script>
 *
 * page.json is the committed page, a tree of PuckNode whose media (AnTreeNode) have node.synuid.
 * The script renders it with the editor's config (storyConfig, Puck's Render); the media files
 * are m/<synuid>, relative to the story's path, served by html-service from its map of the
 * story's media (synuid → the synode's volume path).
 *
 * TODO WP5: a webpack entry (and html-service's web assets list) for this bundle.
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

	// TODO WP5: errors shown in the page
	const page = await fetch(script?.dataset.page ?? 'page.json').then((r) => r.json()) as PuckNode;

	createRoot(el).render(<Render config={storyConfig} data={toPuck(page)} metadata={viewerMetadata()} />);
}

main();
