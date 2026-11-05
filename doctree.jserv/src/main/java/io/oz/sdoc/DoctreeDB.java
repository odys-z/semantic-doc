package io.oz.sdoc;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import io.odysz.common.EnvPath;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.SemanticTree;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;

/**
 * The working copy of doc trees: its own db file (a connection of the app's connects.xml),
 * created and migrated by {@link #setup(String)} when the tier starts.
 *
 * <p>Tables, with semantics in the connection's semantics xml:</p>
 * <ul>
 * <li>{@link DocInf#tabl}: headers, docId: auto key (autopk) prefixed with the synode, e.g. "X.000A";
 *     its radix and length are the synode's config.xml file-sys (windows: Radix32) and id-len</li>
 * <li>{@link DocNode#tabl}: nodes of all docs, fullpath; the doc root's nid and fullpath are the docId,
 *     so a subtree, selected by fullpath prefix, never reaches into another doc</li>
 * <li>s_doctree_meta (k, v): the schema version; oz_autoseq: auto keys</li>
 * </ul>
 *
 * <p>Not a syntity: nothing here is synchronized.</p>
 *
 * <p>Connects requires a sqlite file to exist, so call {@link #touch(String, String)}
 * before Connects.init(), e.g. for a volume upgraded from a version without doc trees.</p>
 *
 * @author ody
 */
public class DoctreeDB {

	static final String meta = "s_doctree_meta";

	/** keys of s_doctree_meta */
	static final String kSchema = "schema";

	/**
	 * Width of a sibling sort in fullpath, e.g. "X.00000A.0001.0002": up to 9999 children of a node.
	 * Must be the same as the semantics xml's fullpath args[3]; registered to SemanticTree by {@link #setup(String)}.
	 */
	public static final int sortDigits = 4;

	/** DDL of each schema version; [i] upgrades version i to i + 1. */
	static final List<List<String>> migrations = Arrays.asList(
		Arrays.asList(
			"create table if not exists " + meta + " (k varchar(16) primary key, v varchar(64))",

			// semantic-DA's auto key sequences (autopk of s_docs.docId)
			"create table if not exists oz_autoseq (sid text(50), seq integer, remarks text(200), "
				+ "constraint oz_autoseq_pk primary key (sid))",

			"create table if not exists " + DocInf.tabl + " ("
				+ "docId varchar(32) primary key, synode varchar(12), title varchar(256), creator varchar(32), "
				+ "state varchar(8), msg text, srcId varchar(64), nextNid integer default 1, "
				+ "htmlUri varchar(512), builtAt varchar(24), builtBy varchar(32))",

			"create table if not exists " + DocNode.tabl + " ("
				+ "nid varchar(48) primary key, docId varchar(32) not null, parentId varchar(48), "
				+ "sort integer, fullpath varchar(512), kind varchar(8), "
				+ "json text, rst text, rstver integer default 0, "
				+ "text text, src varchar(64), style varchar(64), resId varchar(64), refId varchar(64))",

			"create index if not exists " + DocNode.tabl + "_doc on " + DocNode.tabl + " (docId, fullpath)"
		));

	public static int schemaVersion() { return migrations.size(); }

	final String conn;
	final DATranscxt st;
	final IUser robot;

	public DoctreeDB(String conn) throws Exception {
		this.conn = conn;
		this.st = new DATranscxt(conn);
		this.robot = new JRobot();
	}

	/**
	 * Create an empty db file if there is none, resolving the path as Connects does for sqlite.
	 * 
	 * @param webinf
	 * @param src the connection's src, e.g. "$VOLUME_HUB/doctree.db", relative to webinf
	 * @return the db file's path
	 * @throws IOException
	 */
	public static String touch(String webinf, String src) throws IOException {
		String dbpath = FilenameUtils.concat(new File(webinf).getAbsolutePath(), EnvPath.replaceEnv(src));
		File f = new File(dbpath);
		if (!f.exists()) {
			f.getParentFile().mkdirs();
			f.createNewFile();
		}
		return dbpath;
	}

	/**
	 * Create the tables, or upgrade them to {@link #schemaVersion()}.
	 *
	 * @param conn
	 * @return this
	 * @throws Exception
	 */
	public static DoctreeDB setup(String conn) throws Exception {
		SemanticTree.sortDigits(DocNode.tabl, sortDigits);

		// version 0: nothing, not even the meta table.
		// Before the first DATranscxt of the connection: loading the autopk semantics needs oz_autoseq.
		ArrayList<String> sqls = new ArrayList<String>(migrations.get(0));
		Connects.commit(conn, new JRobot(), sqls);

		DoctreeDB db = new DoctreeDB(conn);
		int ver = db.metaInt(kSchema, 0);
		if (ver == 0) {
			db.insertMeta(kSchema, "1");
			ver = 1;
		}
		for (; ver < schemaVersion(); ver++) {
			Connects.commit(conn, db.robot, new ArrayList<String>(migrations.get(ver)));
			db.st.update(meta, db.robot)
				.nv("v", String.valueOf(ver + 1))
				.whereEq("k", kSchema)
				.u(db.st.instancontxt(conn, db.robot));
		}
		return db;
	}

	int metaInt(String k, int deflt) throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(meta)
				.col("v")
				.whereEq("k", k)
				.rs(st.instancontxt(conn, robot))
				.rs(0);
		return rs.next() ? Integer.valueOf(rs.getString("v")) : deflt;
	}

	void insertMeta(String k, String v) throws TransException, SQLException {
		st.insert(meta, robot)
			.nv("k", k).nv("v", v)
			.ins(st.instancontxt(conn, robot));
	}

	/**
	 * Node id: unique in the db, as the doc id is unique across synodes.
	 * The doc root's nid is the doc id.
	 *
	 * @param docId
	 * @param n from the doc's {@link DocInf#nextNid}
	 * @return e.g. "X.1a.2f"
	 */
	public static String nid(String docId, int n) {
		return String.format("%s.%s", docId, Integer.toString(n, 36));
	}

	/**
	 * A sibling sort in fullpath.
	 *
	 * @param sort
	 * @return e.g. "02"
	 */
	public static String sortStr(int sort) {
		return String.format("%0" + sortDigits + "d", sort);
	}

	/**
	 * Headers of all docs.
	 *
	 * @return docs
	 */
	public ArrayList<DocInf> docs() throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(DocInf.tabl)
				.cols("docId", "title", "creator", "state", "msg", "srcId", "nextNid", "htmlUri", "builtAt", "builtBy")
				.orderby("docId")
				.rs(st.instancontxt(conn, robot))
				.rs(0);

		ArrayList<DocInf> docs = new ArrayList<DocInf>();
		while (rs.next())
			docs.add(doc(rs));
		return docs;
	}

	/**
	 * @param docId
	 * @return the header, null if not found
	 */
	public DocInf doc(String docId) throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(DocInf.tabl)
				.cols("docId", "title", "creator", "state", "msg", "srcId", "nextNid", "htmlUri", "builtAt", "builtBy")
				.whereEq("docId", docId)
				.rs(st.instancontxt(conn, robot))
				.rs(0);
		return rs.next() ? doc(rs) : null;
	}

	static DocInf doc(AnResultset rs) throws SQLException {
		DocInf d = new DocInf(rs.getString("docId"), rs.getString("title"), rs.getString("creator"));
		d.state   = rs.getString("state");
		d.msg     = rs.getString("msg");
		d.srcId   = rs.getString("srcId");
		d.nextNid = rs.getInt("nextNid", 1);
		d.htmlUri = rs.getString("htmlUri");
		d.builtAt = rs.getString("builtAt");
		d.builtBy = rs.getString("builtBy");
		return d;
	}

	/**
	 * Nodes of a doc, in tree order (fullpath), for building the forest.
	 *
	 * @param docId
	 * @return result set
	 */
	public AnResultset nodes(String docId) throws TransException, SQLException {
		return (AnResultset) st.select(DocNode.tabl)
				.whereEq("docId", docId)
				.orderby("fullpath")
				.rs(st.instancontxt(conn, robot))
				.rs(0);
	}
}
