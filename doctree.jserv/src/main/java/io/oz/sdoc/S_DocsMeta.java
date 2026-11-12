package io.oz.sdoc;

import io.odysz.semantic.meta.SemanticTableMeta;

/**
 * Table s_docs: doc headers ({@link DocInf}) of the working copy, in the doc-tree db.
 * DDL built from the column names, as jserv-album's PhotoMeta does.
 *
 * <p>Semantics (semantics-doctree.xml): {@link #pk} is an auto key prefixed with {@link #synode},
 * e.g. "X.000A", unique across synodes.</p>
 *
 * @author ody
 */
public class S_DocsMeta extends SemanticTableMeta {

	/** the synode creating the doc, the prefix of {@link #pk} */
	public final String synode;
	public final String title;
	public final String creator;
	/** {@link DocInf.State} */
	public final String state;
	/** why the import failed */
	public final String msg;
	/** the source docx's record id in the doc table (h_photos) */
	public final String refId;
	/** next node number of the doc, see {@link DoctreeDBHelper#nid(String, int)} */
	public final String nextNid;
	public final String htmlUri;
	public final String builtAt;
	public final String builtBy;
	/** id of the template the html package was built with, see {@link DocInf#template} */
	public final String template;
	/** url path published at html-service, see {@link DocInf#pubpath} */
	public final String pubpath;
	/** 1: changed since the last commit */
	public final String dirty;
	/** time of the last commit */
	public final String committedAt;

	public S_DocsMeta(String... conn) {
		super("s_docs", conn);

		pk      = "docId";
		synode  = "synode";
		title   = "title";
		creator = "creator";
		state   = "state";
		msg     = "msg";
		refId   = "refId";
		nextNid = "nextNid";
		htmlUri = "htmlUri";
		builtAt = "builtAt";
		builtBy = "builtBy";
		template = "template";
		pubpath = "pubpath";
		dirty   = "dirty";
		committedAt = "committedAt";

		ddlSqlite = "CREATE TABLE if not exists " + tbl + " (\n"
			+ "  " + pk      + " varchar(32) NOT NULL, -- auto key: synode + '.' + radix 32\n"
			+ "  " + synode  + " varchar(12) NOT NULL,\n"
			+ "  " + title   + " varchar(256),\n"
			+ "  " + creator + " varchar(32),\n"
			+ "  " + state   + " varchar(8),           -- parsing, ready, failed\n"
			+ "  " + msg     + " text,\n"
			+ "  " + refId   + " varchar(64),          -- the docx's record id in h_photos\n"
			+ "  " + nextNid + " integer DEFAULT 1,\n"
			+ "  " + htmlUri + " varchar(512),\n"
			+ "  " + builtAt + " varchar(24),\n"
			+ "  " + builtBy + " varchar(32),\n"
			+ "  " + template + " varchar(64),         -- template id of the html package, e.g. rtd-0.8\n"
			+ "  " + pubpath + " varchar(32),          -- url path at html-service\n"
			+ "  " + dirty   + " integer DEFAULT 0,    -- 1: changed since the last commit\n"
			+ "  " + committedAt + " varchar(24),\n"
			+ "  PRIMARY KEY (" + pk + ")\n"
			+ ");";
	}
}
