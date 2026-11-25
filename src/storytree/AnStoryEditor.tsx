/**
 * The story editor (WP2, being built).
 *
 * A story is a doc of the doc-tree tier with mime 'story' (DocInf.Kind.story), its content a
 * page, a tree of PuckNode (templates.ts), instead of RST nodes:
 *  - loaded with r/page, a new story's page (none saved yet) from a template;
 *  - the album tree beside the page (AlbumPanel), in Puck's left side bar or above the page, as
 *    toggled beside Puck's side bar toggles (headerActions, rendered there); its media and folders
 *    dropped into the blocks, which are shown while an album node is dragged
 *    as they are, AnTreeNodes (MediaDrop, story-config.tsx dropTo()); the tree's checkboxes show
 *    the media the page uses (the page's refs, s_stories.refs), unchecking clears them;
 *  - text edited in place; Puck's blocks drawer and fields panel are hidden (overrides), and
 *    kept for later;
 *  - the album tree and the page scroll each on its own, Puck being as high as the editor (height);
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
import { createPortal } from 'react-dom';
import { Puck, useGetPuck, type Data } from '@puckeditor/core';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import VerticalSplit from '@mui/icons-material/VerticalSplit';
import HorizontalSplit from '@mui/icons-material/HorizontalSplit';
import { GalleryView, L } from '@anclient/anreact';
import '@puckeditor/core/puck.css';
import type { AnTreeNode, SessionClient, StreeTier } from '@anclient/semantier';

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
	synuri: string;
	height?: string;
}

export function AnStoryEditor(props: AnStoryEditorProps) {
	const { docId, doctier, albumtier, albumsk, synuri: docuri } = props;

	/** the page as loaded; undefined: loading */
	const [data, setData] = React.useState<Data>();
	/** error of loading / saving */
	const [msg, setMsg] = React.useState<string>();
	/** where the album tree is: in Puck's left side bar, or above the page */
	const [albumAt, setAlbumAt] = React.useState<'left' | 'top'>('left');

	/** Puck's state, got inside Puck (the header's actions) */
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
		// recorded: Puck doesn't keep setData in its history (undo) unless asked
		puck.dispatch({ type: 'setData', data: { content: next }, recordHistory: true });
	}, []);

	/**
	 * Media's src while editing: a session link of the album's doc, as AlbumDocview's
	 * (GalleryView.imgSrcReq()), its doc table as the album tree gives it (node.doctabl).
	 */
	const metadata = React.useMemo(() => editorMetadata((n) => albumtier
			? GalleryView.imgSrcReq(n.id, String(n.node?.doctabl ?? 'h_photos'), {
				uri: albumtier.uri, port: albumtier.port, client: albumtier.client as SessionClient,
				docuri: () => docuri })
			: '',
		onDrop), [albumtier, docuri, onDrop]);

	/** the album tree, scrolled on its own: in the left side bar, or above the page (at most a third of it) */
	const album = React.useMemo(() => (
		<div className='sdoc-story-album-scroll' style={albumAt === 'left'
				? { height: '100%', overflow: 'auto' }
				: { maxHeight: '33%', flex: 'none', overflow: 'auto', borderBottom: '1px solid #ddd' }}>
			<AlbumPanel albumtier={albumtier} albumsk={albumsk} docuri={docuri} />
		</div>), [albumAt, albumtier, albumsk, docuri]);

	/**
	 * Inside Puck: the album tree, left (drawer) or top (above Puck's ui); the toggles of it, beside Puck's
	 * side bar toggles (or in the header's actions), where Puck's state is got for onDrop; the drop targets shown
	 * while an album node is dragged. Top: the left side bar is closed, left: opened (Puck's ui).
	 */
	const overrides = React.useMemo(() => ({
		headerActions: function HeaderActions({ children }: { children: React.ReactNode }) {
			const get = useGetPuck();
			getPuck.current = get;

			/** in this Puck, where the toggles go: beside its header's side bar toggles, else here */
			const anchor = React.useRef<HTMLSpanElement>(null);
			const [controls, setControls] = React.useState<Element | null>(null);
			React.useEffect(() => {
				// Puck (0.24) has no override there (header replaces all of it): found by its css module's class name
				setControls(anchor.current?.closest('.Puck')?.querySelector('[class*="PuckHeader-toggle"]') ?? null);
			});

			/**
			 * While an album node is dragged (from the album panel; Puck's own drags aren't HTML5 drags),
			 * the page's blocks taking it are shown (story.css, sdoc-story-dragging on the page's body,
			 * in Puck's iframe). Capturing: the tree's dragstart stops its propagation.
			 */
			React.useEffect(() => {
				const doc = anchor.current?.ownerDocument;
				const root = anchor.current?.closest('.Puck');
				if (!doc || !root) return;
				const page = () => root.querySelector('iframe')?.contentDocument?.body;
				const on = (e: DragEvent) => {
					if ((e.target as Element)?.closest?.('.sdoc-story-album'))
						page()?.classList.add('sdoc-story-dragging');
				};
				const off = () => page()?.classList.remove('sdoc-story-dragging');
				doc.addEventListener('dragstart', on, true);
				doc.addEventListener('dragend', off, true);
				return () => {
					doc.removeEventListener('dragstart', on, true);
					doc.removeEventListener('dragend', off, true);
					off();
				};
			}, []);

			const toggles = (
				<ToggleButtonGroup size='small' exclusive value={albumAt} sx={{ ml: 0.5 }}
					onChange={(_e, at: 'left' | 'top' | null) => {
						if (!at) return;
						setAlbumAt(at);
						get().dispatch({ type: 'setUi', ui: { leftSideBarVisible: at === 'left' } });
					}} >
					<ToggleButton value='left' title={L('Album on the left')} sx={{ p: '3px' }}>
						<VerticalSplit sx={{ fontSize: 16 }} /></ToggleButton>
					<ToggleButton value='top' title={L('Album on top')} sx={{ p: '3px' }}>
						<HorizontalSplit sx={{ fontSize: 16 }} /></ToggleButton>
				</ToggleButtonGroup>);

			return (<>
				<span ref={anchor} hidden />
				{controls ? createPortal(toggles, controls) : toggles}
				{children}
			</>);
		},
		drawer: function Drawer() {
			return albumAt === 'left' ? album : <></>;
		},
		// top: above all of Puck's ui, not in its preview, which is zoomed
		puck: function Layout({ children }: { children: React.ReactNode }) {
			return albumAt === 'left' ? <>{children}</> : (
				<div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
					{album}
					<div style={{ flex: 1, minHeight: 0 }}>{children}</div>
				</div>);
		},
	}), [albumAt, album]);

	if (!data)
		return <div style={{ height: props.height }}>{msg ?? 'Loading the story ...'}</div>;

	return (
		<div style={{ height: props.height, display: 'flex', flexDirection: 'column' }}>
			{msg && <div className='sdoc-story-msg'>{msg}</div>}
			<div style={{ flex: 1, minHeight: 0 }}>
				<Puck config={storyConfig} data={data} metadata={metadata} height='100%'
					onChange={onChange} overrides={overrides} />
			</div>
		</div>);
}
