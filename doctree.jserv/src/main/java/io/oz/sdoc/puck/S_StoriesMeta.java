package io.oz.sdoc.puck;

import io.odysz.semantic.meta.SemanticTableMeta;

/**
 * Table s_stories: story headers in the doc-tree db, as s_docs is of doc trees ({@link io.oz.sdoc.docx.S_DocsMeta}),
 * read as {@link io.oz.sdoc.SDocRef} of kind {@link io.oz.sdoc.SDocRef.Kind#story}. Created if missing,
 * when the db is set up.
 *
 * <p>Semantics (semantics-doctree.xml): {@link #pk} is an auto key prefixed with {@link #synode} and "S",
 * e.g. "X29.S.000A": never a doc's id, so a doc id tells which table it's of.</p>
 *
 * @since 0.1.5
 */
public class S_StoriesMeta extends SemanticTableMeta {

	/** the synode creating the story, the prefix of {@link #pk} */
	public final String synode;
	public final String title;
	public final String subtitle;
	public final String creator;
	/** the Puck template the story was started from, e.g. "pm-0" */
	public final String template;
	/** the viewer bundle the page needs at html-service, e.g. "story-0.1" */
	public final String viewer;
	/** io_oz_synuid of the cover media (the first hero's), set when committed */
	public final String cover;
	/**
	 * The album media referenced by the page's blocks, Anson json of a map, h_photos pid → Puck block
	 * ids, e.g. {"0001": ["Hero-1", "Gallery-1"]}; written with the blocks, passed through, read by the
	 * editor only ({@link PuckNode#refs}); null: none.
	 */
	public final String refs;
	/** the committed page, &lt;volume&gt;/&lt;uid&gt;/doctree.html/&lt;storyId&gt;.page.json */
	public final String htmlUri;
	/** time of htmlUri */
	public final String builtAt;
	public final String builtBy;
	/** url path published at html-service */
	public final String pubpath;
	/** 1: changed since the last commit */
	public final String dirty;
	public final String committedAt;

	public S_StoriesMeta(String... conn) {
		super("s_stories", conn);

		pk       = "storyId";
		synode   = "synode";
		title    = "title";
		subtitle = "subtitle";
		creator  = "creator";
		template = "template";
		viewer   = "viewer";
		cover    = "cover";
		refs     = "refs";
		htmlUri  = "htmlUri";
		builtAt  = "builtAt";
		builtBy  = "builtBy";
		pubpath  = "pubpath";
		dirty    = "dirty";
		committedAt = "committedAt";

		ddlSqlite = "CREATE TABLE if not exists " + tbl + " (\n"
			+ "  " + pk       + " varchar(32) NOT NULL, -- auto key: synode + '.S.' + radix 32\n"
			+ "  " + synode   + " varchar(12) NOT NULL,\n"
			+ "  " + title    + " varchar(256),\n"
			+ "  " + subtitle + " varchar(256),\n"
			+ "  " + creator  + " varchar(32),\n"
			+ "  " + template + " varchar(64),          -- the Puck template, e.g. pm-0\n"
			+ "  " + viewer   + " varchar(16),          -- the viewer bundle, e.g. story-0.1\n"
			+ "  " + cover    + " varchar(64),          -- io_oz_synuid of the cover media\n"
			+ "  " + refs     + " text,                 -- h_photos pid -> Puck block ids, Anson json\n" // IGNORE-PICKING-0.8.3 s_stories.refs -- new column, null by the DDL; rebuilt from the story's blocks at its next save (fromPuck())
			+ "  " + htmlUri  + " varchar(512),         -- the committed page\n"
			+ "  " + builtAt  + " varchar(24),\n"
			+ "  " + builtBy  + " varchar(32),\n"
			+ "  " + pubpath  + " varchar(32),          -- url path at html-service\n"
			+ "  " + dirty    + " integer DEFAULT 0,    -- 1: changed since the last commit\n"
			+ "  " + committedAt + " varchar(24),\n"
			+ "  PRIMARY KEY (" + pk + ")\n"
			+ ");";
	}
}
