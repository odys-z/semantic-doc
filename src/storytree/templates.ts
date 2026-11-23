

// TODO: define PuckNode type properly, currently it's any
export type PuckNode = any;

export type StoryTemplate = {
	id: string;
	name: string;
	data: PuckNode;
};

export const templates: PuckNode[] = [
	{
		id: 'pm-0',
		name: '12 Video Seedings',
		data: {
			pucktype: 'page', id: 'root', title: 'My PM Story by 12 Videos', subtitle: '',
			children: [
				{ pucktype: 'Hero', id: 'Hero-1', title: 'My PM Story by 12 Videos', subtitle: '' },
				{ pucktype: 'Text', id: 'Text-1', content: '' },
				{ pucktype: 'Gallery', id: 'Gallery-1', items: [], layout: 'rows' },
				{ pucktype: 'Text', id: 'Text-2', content: '' },
			],
		},
	},
];
