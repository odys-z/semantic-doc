/**
 * Where a block accepts album nodes dragged from the album tree: the AnTreeNodes themselves,
 * a folder ('gallery') with its children. What a drop does is story-config.tsx dropTo()'s:
 * a Gallery adds the folder's images (or the media), a Hero takes a media, others ignore it.
 *
 * The drag is anreact's (TreegridProps.draggable): a HTML5 drag, its data 'application/json',
 * the node's {type, id, node}; Puck's own drags (dnd-kit, pointer events) are not HTML5 drags,
 * so the two don't meet.
 */
import React from 'react';
import type { AnTreeNode } from '@anclient/semantier';

export interface MediaDropProps {
	/** the album nodes dropped, as dragged */
	onDrop: (dropped: AnTreeNode[]) => void;
	children?: React.ReactNode;
}

/** a drag of the album tree, by its data type */
const isTreeDrag = (e: React.DragEvent) => e.dataTransfer.types.includes('application/json');

export function MediaDrop({ onDrop, children }: MediaDropProps) {
	/** an album node is over the block */
	const [over, setOver] = React.useState(false);

	return (
		<div className={`sdoc-story-drop${over ? ' sdoc-story-drop-over' : ''}`}
			style={over ? { outline: '2px dashed #1976d2', outlineOffset: -2 } : undefined}
			onDragOver={(e) => {
				if (!isTreeDrag(e)) return;
				e.preventDefault(); // accepts the drop
				e.dataTransfer.dropEffect = 'copy';
				setOver(true);
			}}
			onDragLeave={() => setOver(false)}
			onDrop={(e) => {
				setOver(false);
				if (!isTreeDrag(e)) return;
				e.preventDefault();
				try {
					const n = JSON.parse(e.dataTransfer.getData('application/json')) as AnTreeNode;
					if (n?.id && n.node) onDrop([n]);
				}
				catch (_) { /* not an album node */ }
			}} >
			{children}
		</div>);
}
