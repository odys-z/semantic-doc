/**
 * STUB (media story, WP5): the viewer of published stories, viewer.js / viewer.css, one bundle
 * for all the stories, installed into html-service at /<pubroot>/_story/.
 *
 * html-service writes each story's index.html, a shell loading this script:
 *
 *     <div id="story"></div>
 *     <script src="/pub/_story/viewer.js" data-page="page.json"></script>
 *
 * The script fetches the committed page and renders it with the editor's blocks (storyConfig,
 * Puck's Render); the media files are m/<uid>, relative to the story's path, served by
 * html-service from its map of the story's media (uid → the synode's volume path).
 *
 * TODO WP5: a webpack entry (and html-service's web assets list) for this bundle.
 */
import React from 'react';
import { createRoot } from 'react-dom/client';
import { Render } from '@puckeditor/core';

import { storyConfig } from '../storytree/story-config';
import type { StoryData, StoryMetadata } from '../storytree/page';

/** published media: html-service's mapping, relative to the story's path */
const metadata: StoryMetadata = {
	mediaSrc: (it) => `m/${encodeURIComponent(it.uid ?? '')}`,
};

async function main() {
	const script = document.currentScript as HTMLScriptElement | null;
	const el = document.getElementById('story');
	if (!el) return;

	// TODO WP5: errors shown in the page
	const data = await fetch(script?.dataset.page ?? 'page.json').then((r) => r.json()) as StoryData;

	createRoot(el).render(<Render config={storyConfig} data={data} metadata={metadata} />);
}

main();
