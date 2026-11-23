/**
 * STUB (media story, WP2): where a block accepts media dragged from the album tree.
 *
 * Hero accepts one media item; Gallery appends media, and a folder fills it. Dropping a folder
 * on the page, outside any block, inserts a new Gallery there (Puck's dispatch: insert, then
 * replace with the items). How it hooks into the drag engine shared with Puck (dnd-kit) is
 * decided by the prototype, hello/puck-media (WP0).
 */
import React from 'react';
import type { AnTreeNode } from '@anclient/semantier';

export interface MediaDropProps {
	/** 'one': a media item replaces; 'many': media are appended, a folder's items too */
	accept: 'one' | 'many';

	onDrop: (payload: AnTreeNode[]) => void;
	children?: React.ReactNode;
}

export function MediaDrop({ children }: MediaDropProps) {
	return <div className='sdoc-story-drop'>{children}</div>;
}
