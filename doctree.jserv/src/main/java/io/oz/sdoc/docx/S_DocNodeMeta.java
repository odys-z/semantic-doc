package io.oz.sdoc.docx;

import java.sql.SQLException;
import java.util.Set;

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
 * Table s_docnodes: nodes ({@link DocxNode}) of all docs in the working copy.
 * DDL built from the column names, as jserv-album's PhotoMeta does.
 *
 * <p>Semantics (semantics-doctree.xml): {@link #pk} is an auto key (autopk, oz_autoseq, Radix32 of
 * config.xml id-len), unique in the db; fullpath (parentId, sort, fullpath, sibling width).
 * The doc root's {@link #fullpath} is its own nid, so a subtree, selected by fullpath prefix, never
 * reaches into another doc; a doc's nodes are selected by {@link #docId}.</p>
 *
 * <p>Nodes imported before 0.1.4 keep their ids, docId + '.' + a node number (the doc root: the docId),
 * which never equal an auto key: auto keys have no '.'.</p>
 *
 * <p>A tree for SemanticTree.onModifyTree() ({@link ITreeMeta}): only the doc root and sections
 * have children; the doc roots (top level) are fixed; a new node's id is the next auto key.</p>
 *
 * @author ody
 */
public class S_DocNodeMeta extends SemanticTableMeta implements ITreeMeta {

	/** node kinds that may have children */
	static final Set<String> holders = Set.of(DocxNode.Kind.doc, DocxNode.Kind.section);

	public final String docId;
	public final String parentId;
	/** sibling sort */
	public final String sort;
	public final String fullpath;
	/** {@link DocxNode.Kind} */
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
			+ "  " + pk       + " varchar(48) NOT NULL, -- auto key; before 0.1.4: docId + '.' + n, the doc root: docId\n" // DB-PICKING-0.8.2 oz_autoseq:s_docnodes.nid
			+ "  " + docId    + " varchar(32) NOT NULL,\n"
			+ "  " + parentId + " varchar(48),\n"
			+ "  " + sort     + " integer,\n"
			+ "  " + fullpath + " varchar(512),         -- the doc root: its nid\n"
			+ "  " + kind     + " varchar(8),\n"
			+ "  " + tipjson  + " text,                 -- Tiptap JSON, \"type\" stored as \"contype\"\n" // DB-PICKING-0.8.2 s_docnodes.tipjson
			+ "  " + rstext   + " text,\n" // DB-PICKING-0.8.2 s_docnodes.rstext
			+ "  " + rstver   + " integer DEFAULT 0,\n"
			+ "  " + ptext    + " text,\n"
			+ "  " + facts    + " text,                 -- run facts, the client builds tipjson from them\n"
			+ "  " + src      + " varchar(64),          -- docx origin, e.g. body/2\n"
			+ "  " + docxstyle+ " varchar(64),\n" // DB-PICKING-0.8.2 s_docnodes.docxstyle
			+ "  " + resId    + " text,                 -- image: file name in the doc's resource folder\n"
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
	 * The next auto key of {@link #pk}, from the same sequence as the autopk semantics' (oz_autoseq),
	 * given to the insert, which the semantics then leaves as it is.
	 */
	@Override
	public String newId(String connId, String parent, IUser usr)
			throws TransException, SQLException {
		return new DATranscxt(connId).instancontxt(connId, usr).genId(connId, tbl, pk);
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
