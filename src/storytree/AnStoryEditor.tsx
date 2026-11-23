/**
 * STUB (media story, WP2): the story editor.
 *
 * A story is a doc of the doc-tree tier with kind 'story' (DocInf.Kind.story), its content a
 * Puck page (StoryData) instead of RST nodes:
 *  - a new story starts from a template (templates.ts);
 *  - the album tree beside the page (AlbumPanel), its media and folders dragged into the blocks
 *    (MediaDrop): a folder dropped on the page becomes a Gallery;
 *  - text edited in place; Puck's blocks drawer and fields panel are hidden (overrides), and
 *    kept for later;
 *  - saved with u/page (autosave), committed and published as doc trees are (c/commit, c/publish).
 *
 * The media are shown with anreact's GalleryView / Lightbox; their files are session links, by
 * Puck's metadata (StoryMetadata.mediaSrc, items' pid). The published page uses the same blocks,
 * with html-service's m/<uid> (items' uid, mapped by the synode when the story is committed).
 */
import React from 'react';
import { Puck } from '@puckeditor/core';
import '@puckeditor/core/puck.css';
import type { StreeTier } from '@anclient/semantier';

import type { StreeDoctier } from '../doctree/doc-stree';
import { templates, type PuckNode } from './templates';
import { AlbumPanel } from './AlbumPanel';

export interface AnStoryEditorProps {
	docId: string;
	doctier: StreeDoctier;
	/** the app's tier for the album tree, see AlbumPanel */
	albumtier?: StreeTier;
	/** semantics key of the album tree, e.g. 'tree-album-family-folder' */
	albumsk: string;
	height?: string;
}

export function AnStoryEditor(props: AnStoryEditorProps) {
	const [data] = React.useState<PuckNode>(templates[0].data);

	let storyConfig = {} as React.ComponentProps<typeof Puck>['config'];

	return (
		<div style={{ height: props.height }}>
			<Puck config={storyConfig} data={data} metadata={data}
				overrides={{
					drawer: () => <AlbumPanel albumtier={props.albumtier} albumsk={props.albumsk} />,
				}} />
		</div>);
}
