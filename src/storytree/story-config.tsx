/**
 * The Puck config of stories, the same for the editor (Puck) and the published page (Render).
 *
 * A story's page is a tree of PuckNode, io.oz.sdoc.puck.PuckNode (templates.ts), {pucktype, id,
 * ...props, children}: "pucktype", not "type", which is Anson's. Puck takes its own Data,
 * {root: {props}, content: [{type, props}]}: toPuck() / fromPuck() at the boundary.
 *
 * Media are the album tree's AnTreeNodes, as dropped: a Hero's media, a Gallery's items. A
 * Gallery shows its items with anreact's GalleryView, opening Lightbox with them; their src is
 * Puck's metadata.mediaSrc(node), set as node.src:
 * - editor: a session link, GalleryView.imgSrcReq(node.id, ...), node.id the h_photos pid;
 * - published: html-service's m/<node.synuid>, synuid set by the synode when committing.
 */
import React from 'react';
import type { ComponentConfig, Config, Data } from '@puckeditor/core';
import type { AnTreeNode } from '@anclient/semantier';
import { regex } from '@anclient/anreact';

import { PuckNode } from './templates';
import { MediaDrop } from './MediaDrop';
import './story.css';

/** Puck's metadata of the editor: media as session links; drops applied by the editor */
export function editorMetadata(mediaSrc: (n: AnTreeNode) => string,
		onDrop: (id: string, dropped: AnTreeNode[]) => void) {
	return { mediaSrc, onDrop };
}

/** Puck's metadata of a published story: media by html-service's map, relative to the story */
export function viewerMetadata() {
	return {
		mediaSrc: (n: AnTreeNode) => `m/${encodeURIComponent(String(n.node?.synuid ?? ''))}`,
		onDrop: undefined as ((id: string, dropped: AnTreeNode[]) => void) | undefined,
	};
}

/** the nodes with their src, for GalleryView / Lightbox (which use node.src if set) */
const withSrc = (items: AnTreeNode[] | undefined, src: (n: AnTreeNode) => string) =>
	(items ?? []).map((n) => ({ ...n, node: { ...n.node, src: src(n) } }) as AnTreeNode);

/**
 * Text edited in place: Puck's contentEditable fields, also in the fields panel. In the editor, the
 * props are Puck's inline editors (ReactNodes, saved as strings); in a published page, the strings.
 * Elements with sdoc-story-editable keep a place to click into while empty (story.css).
 */
const Hero: ComponentConfig<{ title?: string, subtitle?: string, media?: AnTreeNode }> = {
	fields: {
		title:    { type: 'text', label: 'title', contentEditable: true },
		subtitle: { type: 'text', label: 'subtitle', contentEditable: true },
	},
	render: ({ id, title, subtitle, media, puck }) => {
	  /** the cover, the media dropped, with its src (Puck's metadata) */
	  const cover = media && withSrc([media as AnTreeNode], puck.metadata.mediaSrc)[0];
	  return (
		<MediaDrop onDrop={(dropped) => puck.metadata.onDrop?.(id, dropped)}>
			<div className='sdoc-story-hero'>
				{cover && (regex.mime2type(String(cover.node?.mime ?? '')) === 'video'
					? <video className='sdoc-story-cover' src={String(cover.node.src)} controls />
					: <img className='sdoc-story-cover' src={String(cover.node.src)}
						alt={String(cover.node?.docname ?? cover.id)} />)}
				<h1 className='sdoc-story-editable'>{title}</h1>
				{(puck.isEditing || subtitle) && <p className='sdoc-story-editable'>{subtitle}</p>}
			</div>
		</MediaDrop>);
	},
};

const Text: ComponentConfig<{ content?: string }> = {
	fields: {
		content: { type: 'textarea', label: 'text', contentEditable: true },
	},
	render: ({ content }) => <div className='sdoc-story-text sdoc-story-editable'>{content}</div>,
};

const Gallery: ComponentConfig<{ items?: AnTreeNode[], layout?: string }> = {
	fields: {},
	render: ({ id, items, puck }) => {
		const photos = withSrc(items as AnTreeNode[] | undefined, puck.metadata.mediaSrc);
		return (
			<MediaDrop onDrop={(dropped) => puck.metadata.onDrop?.(id, dropped)}>
				<div className='sdoc-story-gallery'>
					{/* TODO WP2: <GalleryView visible photos={photos} lightbox={(ps, opts) => <Lightbox {...opts} photos={ps} />} />,
					    after WP1: GalleryView / Lightbox use node.src, and pass props.photos to the lightbox */}
					{photos.length} media
				</div>
			</MediaDrop>);
	},
};

export const storyConfig: Config = {
	components: { Hero, Text, Gallery } as Config['components'],
};

/**
 * A story's page as Puck's Data: the root's fields as root props, its children as content.
 * Anson's "type" (the class name) is not a prop: fromPuck() gives it back.
 */
export function toPuck(page: PuckNode): Data {
	const { pucktype: _p, type: _t, children, ...root } = page;
	return {
		root: { props: root },
		content: (children ?? []).map(({ pucktype, type: _bt, ...props }) => ({ type: pucktype, props })),
	};
}

/**
 * Puck's Data as a story's page, io.oz.sdoc.puck.PuckNode, see {@link toPuck};
 * its refs, s_stories.refs, are the blocks' media, {@link pageRefs}.
 */
export function fromPuck(data: Data): PuckNode {
	const refs = pageRefs(data.content);
	return new PuckNode({
		...(data.root?.props ?? {}),
		pucktype: PuckNode.Type.page,
		id: 'root',
		refs: Object.keys(refs).length ? JSON.stringify(refs) : undefined,
		children: data.content.map(({ type, props }) => new PuckNode({ ...props, pucktype: type as string })),
	});
}

/**
 * Build the refs mape: {h_photos.pid → [referencing puck node id]}
 * @since 0.8.4
 */
export function pageRefs(content: Data['content'] | undefined): Record<string, string[]> {
	const refs: Record<string, string[]> = {};
	const ref = (pid: string | undefined, id: string) => {
		if (!pid) return;
		const ids = (refs[pid] ??= []);
		if (!ids.includes(id)) ids.push(id);
	};
	for (const { props } of content ?? []) {
		ref((props.media as AnTreeNode | undefined)?.id, props.id);
		for (const n of (props.items as AnTreeNode[] | undefined) ?? [])
			ref(n.id, props.id);
	}
	return refs;
}

/** The page without the media of pids: a Hero's media cleared, a Gallery's items removed. */
export function unref(data: Data, pids: string[]): Data {
	const gone = new Set(pids);
	return {
		...data,
		content: data.content.map((c) => {
			const { media, items } = c.props as { media?: AnTreeNode, items?: AnTreeNode[] };
			if (media && gone.has(media.id))
				return { ...c, props: { ...c.props, media: undefined } };
			if (items?.some((n) => gone.has(n.id)))
				return { ...c, props: { ...c.props, items: items.filter((n) => !gone.has(n.id)) } };
			return c;
		}),
	};
}

/**
 * Album nodes dropped on a block → the block's new props; undefined: ignored.
 *
 * - Gallery: a folder ('gallery' node) adds its children, the folder's images; a media ('p')
 *   adds itself; only those not in the gallery yet (by id), never replacing.
 * - Hero: a media replaces its media; a folder is ignored.
 * - anything else: ignored.
 */
export function dropTo(pucktype: string, props: any, dropped: AnTreeNode[]): any | undefined {
	const isFolder = (n: AnTreeNode) => n.node?.nodetype === 'gallery';
	/**
	 * A media as kept in the page: the album node's type, id (the pid) and node, without the tree's
	 * own fields (level, indents, parent), which the synode's AnTreeNode can't take as the client has them.
	 */
	const media = (n: AnTreeNode) => ({ type: n.type, id: n.id, node: n.node }) as AnTreeNode;

	if (pucktype === PuckNode.Type.gallery) {
		const items: AnTreeNode[] = [...(props.items ?? [])];
		const have = new Set(items.map((n) => n.id));
		for (const d of dropped)
			for (const s of isFolder(d) ? (d.node.children ?? []) : [d])
				if (!have.has(s.id)) { have.add(s.id); items.push(media(s)); }
		return items.length === (props.items ?? []).length ? undefined : { ...props, items };
	}

	if (pucktype === PuckNode.Type.hero) {
		const m = dropped.find((d) => !isFolder(d));
		return m ? { ...props, media: media(m) } : undefined;
	}

	return undefined;
}
