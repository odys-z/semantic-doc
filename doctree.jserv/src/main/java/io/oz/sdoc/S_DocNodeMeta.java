package io.oz.sdoc;

import java.sql.SQLException;
import java.util.Set;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.DASemantics.ShFullpath;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.ext.ITreeMeta;
import io.odysz.semantic.meta.SemanticTableMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**
 * Table s_docnodes: nodes ({@link DocNode}) of all docs in the working copy.
 * DDL built from the column names, as jserv-album's PhotoMeta does.
 *
 * <p>Semantics (semantics-doctree.xml): fullpath (parentId, sort, fullpath, sibling width).
 * The doc root's {@link #pk} and {@link #fullpath} are the docId, so a subtree, selected by
 * fullpath prefix, never reaches into another doc.</p>
 *
 * <p>A tree for SemanticTree.onModifyTree() ({@link ITreeMeta}): only the doc root and sections
 * have children; the doc roots (top level) are fixed; a new node's id is docId + '.' + the doc's
 * next node number.</p>
 *
 * @author ody
 */
public class S_DocNodeMeta extends SemanticTableMeta implements ITreeMeta {

	/** node kinds that may have children */
	static final Set<String> holders = Set.of(DocNode.Kind.doc, DocNode.Kind.section);

	public final String docId;
	public final String parentId;
	/** sibling sort */
	public final String sort;
	public final String fullpath;
	/** {@link DocNode.Kind} */
	public final String kind;
	/** Tiptap json, "type" stored as "contype" */
	public final String tipjson;
	public final String rstext;
	public final String rstver;
	/** plain text, from the importer */
	public final String ptext;
	/** the importer's run facts, {"v":1,"runs":[...]}, see imports.Run */
	public final String facts;
	public final String src;
	public final String docxstyle;
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
		tipjson  = "tipjson";
		rstext   = "rstext";
		rstver   = "rstver";
		ptext    = "ptext";
		facts    = "facts";
		src      = "src";
		docxstyle= "docxstyle";
		resId    = "resId";
		refId    = "refId";

		ddlSqlite = "CREATE TABLE if not exists " + tbl + " (\n"
			+ "  " + pk       + " varchar(48) NOT NULL, -- docId + '.' + n, the doc root: docId\n"
			+ "  " + docId    + " varchar(32) NOT NULL,\n"
			+ "  " + parentId + " varchar(48),\n"
			+ "  " + sort     + " integer,\n"
			+ "  " + fullpath + " varchar(512),         -- the doc root: docId\n"
			+ "  " + kind     + " varchar(8),\n"
			+ "  " + tipjson  + " text,                 -- Tiptap JSON, \"type\" stored as \"contype\"\n"
			+ "  " + rstext   + " text,\n"
			+ "  " + rstver   + " integer DEFAULT 0,\n"
			+ "  " + ptext    + " text,\n"
			+ "  " + facts    + " text,                 -- run facts, the client builds tipjson from them\n"
			+ "  " + src      + " varchar(64),          -- docx origin, e.g. body/2\n"
			+ "  " + docxstyle+ " varchar(64),\n"
			+ "  " + resId    + " varchar(64),\n"
			+ "  " + refId    + " varchar(64),\n"
			+ "  PRIMARY KEY (" + pk + ")\n"
			+ ");";

		ddlIndex = "CREATE INDEX if not exists " + tbl + "_doc on " + tbl + " (" + docId + ", " + fullpath + ");";
	}

	TreeSemantics stree;

	@Override
	public TreeSemantics treeSemantics() {
		if (stree == null)
			try { stree = ITreeMeta.stree(tbl, pk, parentId, fullpath, sort, ptext, kind); }
			catch (Exception e) { throw new IllegalStateException(e); }
		return stree;
	}

	@Override
	public String nodetype() { return kind; }

	@Override
	public boolean canHold(String nodekind) { return holders.contains(nodekind); }

	@Override
	public boolean fixedRoots() { return true; }

	/**
	 * docId + '.' + the doc's next node number, which is counted up ({@link S_DocsMeta#nextNid}).
	 */
	@Override
	public synchronized String newId(String connId, String parent, IUser usr)
			throws TransException, SQLException {
		DATranscxt st = new DATranscxt(connId);
		AnResultset rs = (AnResultset) st.select(tbl)
				.col(docId)
				.whereEq(pk, parent)
				.rs(st.instancontxt(connId, usr))
				.rs(0);
		if (!rs.next())
			throw new SemanticException("Can't find the parent node %s.", parent);
		String doc = rs.getString(docId);

		S_DocsMeta docm = new S_DocsMeta(connId);
		rs = (AnResultset) st.select(docm.tbl)
				.col(docm.nextNid)
				.whereEq(docm.pk, doc)
				.rs(st.instancontxt(connId, usr))
				.rs(0);
		int n = rs.next() ? rs.getInt(docm.nextNid, 1) : 1;

		st.update(docm.tbl, usr)
			.nv(docm.nextNid, n + 1)
			.whereEq(docm.pk, doc)
			.u(st.instancontxt(connId, usr));
		return DoctreeDBHelper.nid(doc, n);
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
