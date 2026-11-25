/**
 * semantic-doc media stories: import from 'semantic-doc/storytree'.
 * Needs @anclient/anreact and @puckeditor/core; loaded by apps lazily (e.g. album-web's chunk story).
 *
 * STUB (media story): see claude/plans/media-story-plan.md of the Album-web project.
 */
export { AnStoryEditor } from './AnStoryEditor';
export type { AnStoryEditorProps } from './AnStoryEditor';
export { templates, loadTemplates } from './templates';
export type { StoryTemplate, PuckNode } from './templates';
export { storyConfig, toPuck, fromPuck, dropTo, pageRefs, unref, editorMetadata, viewerMetadata } from './story-config';
