package io.oz.sdoc;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DASemantics.ShFullpath;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.meta.SemanticTableMeta;
import io.odysz.semantics.x.SemanticException;

/**
 * Table s_docnodes: nodes ({@link DocNode}) of all docs in the working copy.
 * DDL built from the column names, as jserv-album's PhotoMeta does.
 *
 * <p>Semantics (semantics-doctree.xml): fullpath (parentId, sort, fullpath, sibling width).
 * The doc root's {@link #pk} and {@link #fullpath} are the docId, so a subtree, selected by
 * fullpath prefix, never reaches into another doc.</p>
 *
 * @author ody
 */
public class S_DocNodeMeta extends SemanticTableMeta {

	public final String docId;
	public final String parentId;
	/** sibling sort */
	public final String sort;
	public final String fullpath;
	/** {@link DocNode.Kind} */
	public final String kind;
	/** Tiptap json, "type" stored as "contype" */
	public final String json;
	public final String rst;
	public final String rstver;
	/** plain text, from the importer */
	public final String ptext;
	/** the importer's run facts, {"v":1,"runs":[...]}, see imports.Run */
	public final String facts;
	public final String src;
	public final String style;
	public final String resId;
	public final String refId;

	/** index of a doc's nodes in tree order; committed after {@link #ddlSqlite} */
	public final String ddlIndex;

	public S_DocNodeMeta(String... conn) {
		super("s_docnodes", conn);

		pk       = "nid";
		docId    = "docId";
		parentId = "parentId";
		sort     = "sort";
		fullpath = "fullpath";
		kind     = "kind";
		json     = "json";
		rst      = "rst";
		rstver   = "rstver";
		ptext    = "ptext";
		facts    = "facts";
		src      = "src";
		style    = "style";
		resId    = "resId";
		refId    = "refId";

		ddlSqlite = "CREATE TABLE if not exists " + tbl + " (\n"
			+ "  " + pk       + " varchar(48) NOT NULL, -- docId + '.' + n, the doc root: docId\n"
			+ "  " + docId    + " varchar(32) NOT NULL,\n"
			+ "  " + parentId + " varchar(48),\n"
			+ "  " + sort     + " integer,\n"
			+ "  " + fullpath + " varchar(512),         -- the doc root: docId\n"
			+ "  " + kind     + " varchar(8),\n"
			+ "  " + json     + " text,\n"
			+ "  " + rst      + " text,\n"
			+ "  " + rstver   + " integer DEFAULT 0,\n"
			+ "  " + ptext    + " text,\n"
			+ "  " + facts    + " text,                 -- run facts, the client builds json from them\n"
			+ "  " + src      + " varchar(64),          -- docx origin, e.g. body/2\n"
			+ "  " + style    + " varchar(64),\n"
			+ "  " + resId    + " varchar(64),\n"
			+ "  " + refId    + " varchar(64),\n"
			+ "  PRIMARY KEY (" + pk + ")\n"
			+ ");";

		ddlIndex = "CREATE INDEX if not exists " + tbl + "_doc on " + tbl + " (" + docId + ", " + fullpath + ");";
	}

	/**
	 * Sibling sort width of the fullpath, configured only by the fullpath semantics' args[3].
	 * The connection's semantics must have been loaded (a DATranscxt of it created).
	 * 
	 * @return e.g. 4 for "X.000A.0001.0002"
	 * @throws SemanticException no fullpath semantics configured for the table
	 */
	public int siblingWidth() throws SemanticException {
		ShFullpath sh = (ShFullpath) DATranscxt.getHandler(conn, tbl, smtype.fullpath);
		if (sh == null)
			throw new SemanticException("No fullpath semantics configured for %s, connection %s.", tbl, conn);
		return sh.siblinSize();
	}
}
