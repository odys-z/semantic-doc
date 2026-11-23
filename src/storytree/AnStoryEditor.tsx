/**
 * The story editor (WP2, being built).
 *
 * A story is a doc of the doc-tree tier with kind 'story' (DocInf.Kind.story), its content a
 * page, a tree of PuckNode (templates.ts), instead of RST nodes:
 *  - a new story starts from a template;
 *  - the album tree beside the page (AlbumPanel), its media and folders dropped into the blocks
 *    as they are, AnTreeNodes (MediaDrop, story-config.tsx dropTo());
 *  - text edited in place; Puck's blocks drawer and fields panel are hidden (overrides), and
 *    kept for later;
 *  - saved with u/page (autosave), committed and published as doc trees are (c/commit, c/publish).
 *
 * Media are shown with anreact's GalleryView / Lightbox, their src session links (Puck's
 * metadata, editorMetadata()); the published page uses the same config, with html-service's
 * m/<synuid> (viewerMetadata()).
 */
import React from 'react';
import { Puck, type Data } from '@puckeditor/core';
import '@puckeditor/core/puck.css';
import type { AnTreeNode, StreeTier } from '@anclient/semantier';

import type { StreeDoctier } from '../doctree/doc-stree';
import { templates } from './templates';
import { AlbumPanel } from './AlbumPanel';
import { dropTo, editorMetadata, fromPuck, storyConfig, toPuck } from './story-config';

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
	// TODO WP2: doctier.page(docId) → resp.page, or a template for a new story
	const [data, setData] = React.useState<Data>(() => toPuck(templates[0].data));

	/** a drop on block id: its new props by dropTo(), or ignored */
	const onDrop = React.useCallback((id: string, dropped: AnTreeNode[]) => {
		setData((d) => {
			const ix = d.content.findIndex((c) => c.props.id === id);
			if (ix < 0) return d;
			const props = dropTo(d.content[ix].type as string, d.content[ix].props, dropped);
			if (!props) return d;
			const content = [...d.content];
			content[ix] = { ...content[ix], props };
			// TODO WP2: Puck's dispatch (setData) instead of remounting, and autosave fromPuck(next)
			return { ...d, content };
		});
	}, []);

	/** TODO WP2: GalleryView.imgSrcReq(n.id, 'h_photos', { ...albumtier, docuri }) */
	const metadata = React.useMemo(() => editorMetadata((_n) => '', onDrop), [props.albumtier, onDrop]);

	return (
		<div style={{ height: props.height }}>
			<Puck config={storyConfig} data={data} metadata={metadata}
				// TODO WP2: u/page (autosave) with fromPuck(d)
				onChange={(d) => void fromPuck(d)}
				overrides={{
					drawer: () => <AlbumPanel albumtier={props.albumtier} albumsk={props.albumsk} />,
				}} />
		</div>);
}
