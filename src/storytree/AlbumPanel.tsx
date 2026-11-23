/**
 * STUB (media story, WP2): the album tree beside the page, the tab "Album" of the editor's left
 * panel (Puck's drawer, "Blocks", may be a second tab later).
 *
 * The tree is anreact's (AnTreegrid), loaded by the app's tier as a StreeTier with the app's
 * semantics key, e.g. album-web's GalleryTier with 'tree-album-family-folder': semantic-doc
 * knows nothing of the album. Nodes are dragged with anreact's treeDragPayload().
 */
import React from 'react';
import type { StreeTier } from '@anclient/semantier';

export interface AlbumPanelProps {
	/** the app's tier for the album tree; undefined: no tree */
	albumtier?: StreeTier;
	/** semantics key of the tree, e.g. 'tree-album-family-folder' */
	albumsk: string;
}

export function AlbumPanel(_props: AlbumPanelProps) {
	// TODO WP2: albumtier.stree({ sk: albumsk, ... }) → forest;
	// <AnTreegrid forest dragPayload={treeDragPayload} ... />
	return <div className='sdoc-story-album'>TODO: album tree</div>;
}
