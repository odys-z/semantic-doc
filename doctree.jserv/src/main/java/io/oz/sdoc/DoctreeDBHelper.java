package io.oz.sdoc;

import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.io.FilenameUtils;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.SemanticHandler;
import io.odysz.semantic.DASemantics.ShFullpath2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

/**
 * The working copy of doc trees: its own db file in the synode's volume ({@link #connect(String, String, String)},
 * not configured in connects.xml), its tables created by {@link #setup(String, boolean)} at the tier's first request.
 *
 * <p>Tables, DDL by the metas, semantics in the connection's semantics xml:</p>
 * <ul>
 * <li>{@link S_DocsMeta}: headers, docId: auto key (autopk) prefixed with the synode, e.g. "X.000A";
 *     its radix and length are the synode's config.xml file-sys (radix 32) and id-len</li>
 * <li>{@link S_DocNodeMeta}: nodes of all docs, fullpath; the doc root's nid and fullpath are the docId,
 *     so a subtree, selected by fullpath prefix, never reaches into another doc</li>
 * <li>oz_autoseq, the auto keys on sqlite: created by semantic-DA's autopk handler (1.5.27)</li>
 * </ul>
 *
 * <p>The docs' resources (pictures) are files beside the db file, see {@link DocRes}.</p>
 *
 * <p>Not a syntity: nothing here is synchronized.</p>
 *
 * <p>{@link #connect(String, String, String)} creates the db file, e.g. for a volume upgraded
 * from a version without doc trees.</p>
 *
 * @author ody
 */
public class DoctreeDBHelper {

	final String conn;
	final DATranscxt st;
	final IUser robot;

	public final S_DocsMeta docm;
	public final S_DocNodeMeta nodem;
	/** the docs' resource files, beside the db file; null if not connected by {@link #connect(String, String, String)} */
	public final DocRes res;

	/** conn id: folder of its db file, by {@link #connect(String, String, String)} */
	static final HashMap<String, String> volumes = new HashMap<String, String>();

	DoctreeDBHelper(String conn, S_DocsMeta docm, S_DocNodeMeta nodem) throws Exception {
		this.conn = conn;
		this.res = volumes.containsKey(conn) ? new DocRes(volumes.get(conn)) : null;
		this.docm = docm;
		this.nodem = nodem;
		this.st = new DATranscxt(conn);
		this.robot = new JRobot();
	}

	/** semantics of the doc-tree db, in the app's WEB-INF */
	public static final String smtcs = "semantics-doctree.xml";
	/** the doc-tree db in a synode's volume, beside its doc-jserv.db */
	public static final String dbfile = "doctree.db";

	/**
	 * Connect the doc-tree db of a synode, &lt;volume&gt;/{@link #dbfile}, with the semantics
	 * {@link #smtcs}; the db file is created if there is none. Not configured in connects.xml.
	 * Call after Connects.init().
	 *
	 * @param webinf
	 * @param volume settings.json's volume, relative to webinf or absolute
	 * @param synode makes the conn id unique when several synodes share a JVM (tests)
	 * @return conn id, "doctree." + synode
	 * @throws Exception
	 */
	public static String connect(String webinf, String volume, String synode) throws Exception {
		String volpath = FilenameUtils.concat(new File(webinf).getAbsolutePath(), volume);
		String dbpath = FilenameUtils.concat(volpath, dbfile);
		String conn = "doctree." + synode;
		Connects.addSqlite(conn, dbpath, smtcs);
		volumes.put(conn, volpath);
		return conn;
	}

	/**
	 * Create the missing tables (DDLs are "if not exists"), the same way as a synode's
	 * Syngleton.setupSyntables(), but at any time, e.g. a volume upgraded from a version without doc trees.
	 *
	 * @param conn
	 * @param forceDrop drop the tables first (tests only): all docs are lost
	 * @return the db
	 * @throws Exception
	 */
	public static DoctreeDBHelper setup(String conn, boolean forceDrop) throws Exception {
		S_DocsMeta docm = new S_DocsMeta(conn);
		S_DocNodeMeta nodem = new S_DocNodeMeta(conn);

		// oz_autoseq, for the autopk of docId, is created by semantic-DA (1.5.27) when the semantics is loaded
		setupSqliTables(conn, forceDrop, docm, nodem);
		Connects.commit(conn, new JRobot(), nodem.ddlIndex);

		DoctreeDBHelper db = new DoctreeDBHelper(conn, docm, nodem); // loads the connection's semantics
		checkSemantics(conn, docm, nodem);
		return db;
	}

	/**
	 * The semantics the doc tree needs, from {@link #smtcs}: a missing or stale file (e.g. a sandbox's
	 * WEB-INF copied before fullpath2) would import docs with no fullpaths, or none at all.
	 * <ul>
	 * <li>{@link S_DocsMeta}: autopk (smtype autoInc), the docIds</li>
	 * <li>{@link S_DocNodeMeta}: fullpath2 (ShFullpath2), the nodes' fullpaths in multi-row inserts</li>
	 * </ul>
	 *
	 * @throws SemanticException what's missing, for the user: the tier answers it, the synode keeps running
	 */
	static void checkSemantics(String conn, S_DocsMeta docm, S_DocNodeMeta nodem) throws SemanticException {
		String fix = String.format("Check WEB-INF/%s (connection %s), e.g. copy it from the package's WEB-INF.", smtcs, conn);

		if (!DATranscxt.hasSemantics(conn, docm.tbl, smtype.autoInc))
			throw new SemanticException("Doc-tree semantics: no autopk for %s. %s", docm.tbl, fix);

		SemanticHandler fp = DATranscxt.getHandler(conn, nodem.tbl, smtype.fullpath2);
		if (fp == null)
			fp = DATranscxt.getHandler(conn, nodem.tbl, smtype.fullpath);
		if (!(fp instanceof ShFullpath2))
			throw new SemanticException("Doc-tree semantics: %s for %s, expecting <smtc>fullpath2</smtc>. %s",
					fp == null ? "no fullpath" : "fullpath", nodem.tbl, fix);
	}

	/**
	 * Node id: unique in the db, as the doc id is unique across synodes.
	 * The doc root's nid is the doc id.
	 *
	 * @param docId
	 * @param n from the doc's {@link DocInf#nextNid}
	 * @return e.g. "X.000A.2f"
	 */
	public static String nid(String docId, int n) {
		return String.format("%s.%s", docId, Integer.toString(n, 36));
	}

	/**
	 * Headers of all docs.
	 *
	 * @return docs
	 */
	public ArrayList<DocInf> docs() throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(docm.tbl)
				.orderby(docm.pk)
				.rs(st.instancontxt(conn, robot))
				.rs(0);

		ArrayList<DocInf> docs = new ArrayList<DocInf>();
		while (rs.next())
			docs.add(doc(rs));
		return docs;
	}

	/**
	 * Select doc record, pk = docId, from table {@link S_DocNodeMeta#tbl}
	 * @param docId
	 * @return the header, null if not found
	 */
	public DocInf doc(String docId) throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(docm.tbl)
				.whereEq(docm.pk, docId)
				.rs(st.instancontxt(conn, robot))
				.rs(0);
		return rs.next() ? doc(rs) : null;
	}

	DocInf doc(AnResultset rs) throws SQLException {
		DocInf d = new DocInf(rs.getString(docm.pk), rs.getString(docm.title), rs.getString(docm.creator));
		d.state   = rs.getString(docm.state);
		d.msg     = rs.getString(docm.msg);
		d.srcId   = rs.getString(docm.refId);
		d.nextNid = rs.getInt(docm.nextNid, 1);
		d.htmlUri = rs.getString(docm.htmlUri);
		d.builtAt = rs.getString(docm.builtAt);
		d.builtBy = rs.getString(docm.builtBy);
		d.dirty   = rs.getInt(docm.dirty, 0) != 0;
		d.committedAt = rs.getString(docm.committedAt);
		return d;
	}

	/**
	 * Nodes of a doc, in tree order (fullpath), for building the forest.
	 *
	 * @param docId
	 * @return result set
	 */
	public AnResultset nodes(String docId) throws TransException, SQLException {
		return (AnResultset) st.select(nodem.tbl)
				.whereEq(nodem.docId, docId)
				.orderby(nodem.fullpath)
				.rs(st.instancontxt(conn, robot))
				.rs(0);
	}

	/**
	 * Save a node's content: tipjson, rstext, rstver; the doc gets dirty.
	 *
	 * @param n node, with nid and docId
	 * @return the doc's header
	 * @throws SemanticException the node isn't in the doc
	 */
	public DocInf update(DocNode n, IUser usr) throws TransException, SQLException {
		SemanticObject res = (SemanticObject) st.update(nodem.tbl, usr)
				.nv(nodem.tipjson, n.tipjson)
				.nv(nodem.rstext, n.rstext)
				.nv(nodem.rstver, n.rstver)
				.whereEq(nodem.pk, n.nid)
				.whereEq(nodem.docId, n.docId)
				.post(st.update(docm.tbl, usr)
					.nv(docm.dirty, 1)
					.whereEq(docm.pk, n.docId))
				.u(st.instancontxt(conn, usr));
		if (res == null || res.total() == 0)
			throw new SemanticException("Can't find node %s in doc %s.", n.nid, n.docId);
		return doc(n.docId);
	}

	/** The doc gets dirty, e.g. after a tree modification. */
	public void dirty(String docId, IUser usr) throws TransException, SQLException {
		st.update(docm.tbl, usr)
			.nv(docm.dirty, 1)
			.whereEq(docm.pk, docId)
			.u(st.instancontxt(conn, usr));
	}

	/**
	 * The doc's work is finished: not dirty, committedAt = now.
	 * @return the doc's header
	 */
	public DocInf commit(String docId, IUser usr) throws TransException, SQLException {
		st.update(docm.tbl, usr)
			.nv(docm.dirty, 0)
			.nv(docm.committedAt, Funcall.now())
			.whereEq(docm.pk, docId)
			.u(st.instancontxt(conn, usr));
		return doc(docId);
	}
}
