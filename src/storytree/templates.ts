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

/**
 * A Puck template of stories: the page a new story starts from. Loaded by the app from its resources,
 * {@link loadTemplates}, e.g. album-web's res-vol/story/templates.json; {@link templates} if not there.
 */
export type StoryTemplate = {
	id: string;
	name: string;
	data: PuckNode;
	/**
	 * The template's illustration, a gif, relative to the templates' folder in templates.json,
	 * named [template id]-[width]x[height].gif, e.g. "pm-0-216x132.gif", so that a template's
	 * resources, in one folder with others', go together by name; as loaded, its path,
	 * e.g. "res-vol/story/pm-0-216x132.gif".
	 */
	gif?: string;
};

export const templates: StoryTemplate[] = [
	{
		id: 'pm-0',
		name: 'No.1 Seedings',
		data: new PuckNode({
			pucktype: PuckNode.Type.page, id: 'root', template: 'pm-0', title: 'No.1 PM Story', subtitle: '',
			children: [
				new PuckNode({ pucktype: PuckNode.Type.hero, id: 'Hero-1', title: 'No.1 PM Story - Banner', subtitle: '' }),
				new PuckNode({ pucktype: PuckNode.Type.text, id: 'Text-1', content: '' }),
				new PuckNode({ pucktype: PuckNode.Type.gallery, id: 'Gallery-1', items: [], layout: 'rows' }),
				new PuckNode({ pucktype: PuckNode.Type.text, id: 'Text-2', content: '' }),
			],
		}),
	},
];

/** the templates loaded, by folder: once a session, see {@link loadTemplates} */
const loaded: { [folder: string]: Promise<StoryTemplate[]> } = {};

/**
 * Load the templates of an app's resources, [folder]/templates.json, a json array of
 * {@link StoryTemplate}, the pages with their Anson types (io.oz.sdoc.puck.PuckNode).
 * Kept for the session: loaded once (the browser caches the files, and its gifs). If they can't be
 * loaded, the built-in {@link templates}.
 *
 * @param folder e.g. 'res-vol/story', as album-web's res-vol/lang.json
 * @returns the templates, their gif as a path, e.g. 'res-vol/story/pm-0.gif'
 */
export function loadTemplates(folder: string): Promise<StoryTemplate[]> {
	/** a page of the json as a PuckNode, with its blocks */
	const page = (n: PuckNode): PuckNode => new PuckNode({ ...n, children: n.children?.map(page) });

	return loaded[folder] ??= fetch(`${folder}/templates.json`)
		.then((r) => r.ok ? r.json() : Promise.reject(r))
		.then((ts: StoryTemplate[]) => ts.map((t) =>
			({ ...t, data: page(t.data), gif: t.gif && `${folder}/${t.gif}` })))
		.catch((e) => {
			console.warn(`Story templates can't be loaded from ${folder}, the built-in ones are used.`, e);
			delete loaded[folder]; // tried again next time
			return templates;
		});
}
