import { Anson, type AnTreeNode } from '@anclient/semantier';

/**
 * io.oz.sdoc.puck.PuckNode: a story's page, and each block of it, {pucktype, id, ...props, children}.
 * "pucktype", not "type", which is Anson's (the class name).
 *
 * Media are the album tree's nodes as dropped, AnTreeNode: id the h_photos pid while editing;
 * node.synuid, the record's io_oz_synuid, set by the synode when the story is committed.
 */
export class PuckNode extends Anson {
	static __type__ = 'io.oz.sdoc.puck.PuckNode';

	/** io.oz.sdoc.puck.PuckNode.Type, values of {@link PuckNode.pucktype} */
	static Type = {
		page: 'page',
		hero: 'Hero',
		text: 'Text',
		gallery: 'Gallery',
	} as const;

	/** see {@link PuckNode.Type} */
	pucktype!: string;
	/** Puck's id: "root" of the page, e.g. "Hero-1" of a block */
	id!: string;

	/** page: the template started from, e.g. "pm-0" */
	template?: string;
	/** page, Hero */
	title?: string;
	/** page, Hero */
	subtitle?: string;
	/** Text */
	content?: string;
	/** Gallery */
	layout?: string;

	/** Hero: the album node dropped (a media) */
	media?: AnTreeNode;
	/** Gallery: the album nodes dropped, a folder's children */
	items?: AnTreeNode[];

	/**
	 * page: the album media the blocks use, json of a map, h_photos pid → block ids, e.g.
	 * {"0001": ["Hero-1", "Gallery-1"]}; s_stories.refs, set by fromPuck() (story-config.tsx).
	 */
	refs?: string;

	/** page: the blocks */
	children?: PuckNode[];

	constructor(opts: Partial<Omit<PuckNode, 'type'>> & { pucktype: string, id: string }) {
		super(PuckNode.__type__);
		Object.assign(this, opts);
	}
}

export type StoryTemplate = {
	id: string;
	name: string;
	data: PuckNode;
};

export const templates: StoryTemplate[] = [
	{
		id: 'pm-0',
		name: '12 Video Seedings',
		data: new PuckNode({
			pucktype: PuckNode.Type.page, id: 'root', template: 'pm-0', title: 'My PM Story by 12 Videos', subtitle: '',
			children: [
				new PuckNode({ pucktype: PuckNode.Type.hero, id: 'Hero-1', title: 'My PM Story by 12 Videos', subtitle: '' }),
				new PuckNode({ pucktype: PuckNode.Type.text, id: 'Text-1', content: '' }),
				new PuckNode({ pucktype: PuckNode.Type.gallery, id: 'Gallery-1', items: [], layout: 'rows' }),
				new PuckNode({ pucktype: PuckNode.Type.text, id: 'Text-2', content: '' }),
			],
		}),
	},
];
