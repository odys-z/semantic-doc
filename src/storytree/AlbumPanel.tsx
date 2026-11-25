/**
 * The album tree beside the page, in the editor's left panel (Puck's drawer; "Blocks" may be a
 * second tab later).
 *
 * The tree is anreact's AnTreeditor, loaded by the app's tier as a StreeTier with the app's
 * semantics key, e.g. album-web's GalleryTier with 'tree-album-family-folder': semantic-doc
 * knows nothing of the album. Its folders ('gallery') and media (leaves, also a gallery's photos)
 * are dragged as they are, AnTreeNodes (TreegridProps.draggable), onto the blocks (MediaDrop).
 *
 * Each node has a checkbox (TreegridProps.checkbox), checked if the page uses it, by the page's
 * refs (story-config.tsx pageRefs()), a folder if all its media are used. Unchecking clears the
 * node's media from all the blocks using them (unref()), autosaved as any change of the page.
 *
 * Rendered inside Puck: the page is Puck's state (createUsePuck()).
 */
import React from 'react';
import { createUsePuck } from '@puckeditor/core';
import type { AnDatasetResp, AnsonMsg, AnsonResp, AnTreeNode, StreeTier } from '@anclient/semantier';
import { AnContext, type AnContextType, AnTreeditor, Lightbox, L } from '@anclient/anreact';

import { pageRefs, unref } from './story-config';

const usePuck = createUsePuck();

export interface AlbumPanelProps {
	/** the app's tier for the album tree; undefined: no tree */
	albumtier?: StreeTier;
	/** semantics key of the tree, e.g. 'tree-album-family-folder' */
	albumsk: string;
	/** the album's doc uri, e.g. album-web's synuri, '/album/syn': loading the tree, its pictures */
	docuri: string;
}

/** folders of media, and media, can be dragged into the page */
const draggable = (n: AnTreeNode) => n.node.nodetype === 'gallery' || !n.node.children?.length;

export function AlbumPanel({ albumtier, albumsk, docuri }: AlbumPanelProps) {
	const ctx = React.useContext(AnContext) as AnContextType;
	const [forest, setForest] = React.useState<AnTreeNode[]>();

	const content = usePuck((s) => s.appState.data.content);
	const dispatch = usePuck((s) => s.dispatch);

	React.useEffect(() => {
		if (!albumtier) return;
		albumtier.stree({ uri: albumtier.uri, synuri: docuri, sk: albumsk,
			onOk: (rep: AnsonMsg<AnsonResp>) => {
				albumtier.forest = (rep.Body() as AnDatasetResp).forest as AnTreeNode[];
				setForest(albumtier.forest);
			} },
			ctx.error);
	}, [albumtier, albumsk, docuri]);

	/** the media the page uses, pid → block ids: the checked nodes */
	const selected = React.useMemo(() => ({ ids: new Map<string, any>(Object.entries(pageRefs(content))) }),
		[content]);

	/** nodes unchecked: their media cleared from the page */
	const onSelectChange = React.useCallback((ids: Map<string, any>) => {
		const gone = [...selected.ids.keys()].filter((pid) => !ids.has(pid));
		if (gone.length)
			dispatch({ type: 'setData', data: (d) => unref(d, gone), recordHistory: true });
	}, [selected, dispatch]);

	const lightbox = React.useCallback((photos: AnTreeNode[], opts: { ix: number, open: boolean, onClose: () => void }) =>
		<Lightbox {...opts} showResourceCount photos={photos} tier={albumtier} />, [albumtier]);

	if (!albumtier) return <div className='sdoc-story-album'>{L('No album.')}</div>;

	return (
		<div className='sdoc-story-album'>
			{forest &&
			  <AnTreeditor pk='pid' sk={albumsk} tier={albumtier} forest={forest} reload={false}
				uri={albumtier.uri} docuri={docuri}
				columns={[
					{ type: 'text',     field: 'text', label: L('Album'), grid: {xs: 8} },
					{ type: 'icon-sum', field: '',     label: '',         grid: {xs: 4} },
				]}
				checkbox selected={selected} onSelectChange={onSelectChange}
				draggable={draggable}
				lightbox={lightbox} />}
		</div>);
}
