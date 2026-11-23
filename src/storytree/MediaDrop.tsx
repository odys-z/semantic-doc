/**
 * Where a block accepts album nodes dragged from the album tree: the AnTreeNodes themselves,
 * a folder ('gallery') with its children. What a drop does is story-config.tsx dropTo()'s:
 * a Gallery adds the folder's images (or the media), a Hero takes a media, others ignore it.
 *
 * TODO WP2: the droppable area, highlighted while an album node is over it; how it hooks into
 * the drag engine shared with Puck (dnd-kit) is decided by the prototype, hello/puck-media (WP0).
 */
import React from 'react';
import type { AnTreeNode } from '@anclient/semantier';

export interface MediaDropProps {
	/** the album nodes dropped, as dragged */
	onDrop: (dropped: AnTreeNode[]) => void;
	children?: React.ReactNode;
}

export function MediaDrop({ children }: MediaDropProps) {
	return <div className='sdoc-story-drop'>{children}</div>;
}
