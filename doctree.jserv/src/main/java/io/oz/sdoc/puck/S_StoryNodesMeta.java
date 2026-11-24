package io.oz.sdoc.puck;

import io.odysz.semantic.meta.SemanticTableMeta;

/**
 * Table s_storynodes: the blocks of the stories' pages, one row per {@link PuckNode} under a page
 * root (the page's own fields are the story's header, {@link S_StoriesMeta}). Created if missing,
 * when the db is set up.
 *
 * <p>{@link #media} and {@link #items} are the album nodes dropped (AnTreeNode), Anson json.</p>
 *
 * @since 0.1.5
 */
public class S_StoryNodesMeta extends SemanticTableMeta {

	public final String storyId;
	/** null: a block of the page root */
	public final String parentId;
	/** order among siblings */
	public final String sort;
	/** {@link PuckNode#pucktype}, e.g. Hero, Text, Gallery */
	public final String pucktype;
	/** Puck's id of the block, {@link PuckNode#id}, e.g. Hero-1 */
	public final String puckId;
	public final String title;
	public final String subtitle;
	public final String content;
	public final String layout;
	/** Anson json of an AnTreeNode */
	public final String media;
	/** Anson json of AnTreeNode[] */
	public final String items;

	/** the blocks of a story in order; committed after {@link #ddlSqlite} */
	public final String ddlIndex;

	public S_StoryNodesMeta(String... conn) {
		super("s_storynodes", conn);

		pk       = "nid";
		storyId  = "storyId";
		parentId = "parentId";
		sort     = "sort";
		pucktype = "pucktype";
		puckId   = "puckId";
		title    = "title";
		subtitle = "subtitle";
		content  = "content";
		layout   = "layout";
		media    = "media";
		items    = "items";

		ddlSqlite = "CREATE TABLE if not exists " + tbl + " (\n"
			+ "  " + pk       + " varchar(48) NOT NULL, -- auto key\n"
			+ "  " + storyId  + " varchar(32) NOT NULL,\n"
			+ "  " + parentId + " varchar(48),          -- null: a block of the page\n"
			+ "  " + sort     + " integer,\n"
			+ "  " + pucktype + " varchar(16),          -- Hero, Text, Gallery\n"
			+ "  " + puckId   + " varchar(32),          -- Puck's id, e.g. Hero-1\n"
			+ "  " + title    + " varchar(256),\n"
			+ "  " + subtitle + " varchar(256),\n"
			+ "  " + content  + " text,\n"
			+ "  " + layout   + " varchar(16),\n"
			+ "  " + media    + " text,                 -- Hero: AnTreeNode, Anson json\n"
			+ "  " + items    + " text,                 -- Gallery: AnTreeNode[], Anson json\n"
			+ "  PRIMARY KEY (" + pk + ")\n"
			+ ");";

		ddlIndex = "CREATE INDEX if not exists " + tbl + "_story on " + tbl + " (" + storyId + ", " + sort + ");";
	}
}
