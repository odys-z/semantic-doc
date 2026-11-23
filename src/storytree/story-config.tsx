/**
 * The Puck config of stories, the same for the editor (Puck) and the published page (Render).
 *
 * A story's page is a tree of PuckNode, {pucktype, id, ...props, children} (templates.ts):
 * "pucktype", not "type", which Anson reads as a class name. Puck takes its own Data,
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

import type { PuckNode } from './templates';
import { MediaDrop } from './MediaDrop';

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

const Hero: ComponentConfig<{ title?: string, subtitle?: string, media?: AnTreeNode }> = {
	fields: {},
	render: ({ id, title, subtitle, media, puck }) => (
		<MediaDrop onDrop={(dropped) => puck.metadata.onDrop?.(id, dropped)}>
			<div className='sdoc-story-hero'>
				{/* TODO WP2: the cover, withSrc([media], puck.metadata.mediaSrc)[0]; title, subtitle edited in place */}
				{media && <span>{String(media.node?.docname ?? media.id)}</span>}
				<h1>{title}</h1>{subtitle && <p>{subtitle}</p>}
			</div>
		</MediaDrop>),
};

const Text: ComponentConfig<{ content?: string }> = {
	fields: {},
	// TODO WP2: content edited in place
	render: ({ content }) => <div className='sdoc-story-text'>{content}</div>,
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

/** A story's page as Puck's Data: the root's fields as root props, its children as content. */
export function toPuck(page: PuckNode): Data {
	const { pucktype: _p, children, ...root } = page ?? {};
	return {
		root: { props: root },
		content: (children ?? []).map(({ pucktype, ...props }: PuckNode) => ({ type: pucktype, props })),
	};
}

/** Puck's Data as a story's page, see {@link toPuck} */
export function fromPuck(data: Data): PuckNode {
	return {
		pucktype: 'page',
		...(data.root?.props ?? {}),
		children: data.content.map(({ type, props }) => ({ pucktype: type, ...props })),
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

	if (pucktype === 'Gallery') {
		const items: AnTreeNode[] = [...(props.items ?? [])];
		const have = new Set(items.map((n) => n.id));
		for (const d of dropped)
			for (const s of isFolder(d) ? (d.node.children ?? []) : [d])
				if (!have.has(s.id)) { have.add(s.id); items.push(s); }
		return items.length === (props.items ?? []).length ? undefined : { ...props, items };
	}

	if (pucktype === 'Hero') {
		const media = dropped.find((d) => !isFolder(d));
		return media ? { ...props, media } : undefined;
	}

	return undefined;
}
