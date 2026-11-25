/**
 * The story editor (WP2, being built).
 *
 * A story is a doc of the doc-tree tier with mime 'story' (DocInf.Kind.story), its content a
 * page, a tree of PuckNode (templates.ts), instead of RST nodes:
 *  - loaded with r/page, a new story's page (none saved yet) from a template;
 *  - the album tree beside the page (AlbumPanel), its media and folders dropped into the blocks
 *    as they are, AnTreeNodes (MediaDrop, story-config.tsx dropTo()); the tree's checkboxes show
 *    the media the page uses (the page's refs, s_stories.refs), unchecking clears them;
 *  - text edited in place; Puck's blocks drawer and fields panel are hidden (overrides), and
 *    kept for later;
 *  - saved with u/page (autosave), committed and published as doc trees are (c/commit, c/publish).
 *
 * Puck takes its data once, when mounted: changes made outside it, a drop, are dispatched to it
 * (setData), as its own are, and saved by onChange.
 *
 * Media are shown with anreact's GalleryView / Lightbox, their src session links (Puck's
 * metadata, editorMetadata()); the published page uses the same config, with html-service's
 * m/<synuid> (viewerMetadata()).
 */
import React from 'react';
import { Puck, useGetPuck, type Data } from '@puckeditor/core';
import '@puckeditor/core/puck.css';
import type { AnTreeNode, StreeTier } from '@anclient/semantier';

import type { StreeDoctier } from '../doctree/doc-stree';
import { SAVE_DELAY } from '../doctree/AnDoctreeditor';
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
	/** the album's doc uri, e.g. album-web's synuri, '/album/syn', see AlbumPanel */
	docuri: string;
	height?: string;
}

export function AnStoryEditor(props: AnStoryEditorProps) {
	const { docId, doctier, albumtier, albumsk, docuri } = props;

	/** the page as loaded; undefined: loading */
	const [data, setData] = React.useState<Data>();
	/** error of loading / saving */
	const [msg, setMsg] = React.useState<string>();

	/** Puck's state, got inside Puck (the drawer) */
	const getPuck = React.useRef<ReturnType<typeof useGetPuck>>(undefined);
	/** the page changed and not saved yet */
	const pending = React.useRef<{ d: Data, timer: number }>(undefined);

	React.useEffect(() => {
		setData(undefined);
		doctier.page(docId, (resp) => setData(toPuck(resp.page ?? templates[0].data)), setMsg);
	}, [doctier, docId]);

	/** u/page, the changes not saved yet */
	const flush = React.useCallback(() => {
		const p = pending.current;
		if (!p) return;
		window.clearTimeout(p.timer);
		pending.current = undefined;
		doctier.savePage(docId, fromPuck(p.d), () => setMsg(undefined), setMsg);
	}, [doctier, docId]);

	// saved when the editor is left
	React.useEffect(() => flush, [flush]);

	/** Puck's changes, its own and dispatched: autosaved */
	const onChange = React.useCallback((d: Data) => {
		if (pending.current) window.clearTimeout(pending.current.timer);
		pending.current = { d, timer: window.setTimeout(flush, SAVE_DELAY) };
	}, [flush]);

	/** a drop on block id: its new props by dropTo(), dispatched to Puck; or ignored */
	const onDrop = React.useCallback((id: string, dropped: AnTreeNode[]) => {
		const puck = getPuck.current?.();
		if (!puck) return;
		const content = puck.appState.data.content;
		const ix = content.findIndex((c) => c.props.id === id);
		if (ix < 0) return;
		const p = dropTo(content[ix].type as string, content[ix].props, dropped);
		if (!p) return;
		const next = [...content];
		next[ix] = { ...next[ix], props: p };
		puck.dispatch({ type: 'setData', data: { content: next } });
	}, []);

	/** TODO WP2: GalleryView.imgSrcReq(n.id, 'h_photos', { ...albumtier, docuri }) */
	const metadata = React.useMemo(() => editorMetadata((_n) => '', onDrop), [onDrop]);

	/** the album tree, inside Puck: where Puck's state is got for onDrop */
	const overrides = React.useMemo(() => ({
		drawer: function Drawer() {
			getPuck.current = useGetPuck();
			return <AlbumPanel albumtier={albumtier} albumsk={albumsk} docuri={docuri} />;
		},
	}), [albumtier, albumsk, docuri]);

	if (!data)
		return <div style={{ height: props.height }}>{msg ?? 'Loading the story ...'}</div>;

	return (
		<div style={{ height: props.height }}>
			{msg && <div className='sdoc-story-msg'>{msg}</div>}
			<Puck config={storyConfig} data={data} metadata={metadata}
				onChange={onChange} overrides={overrides} />
		</div>);
}
