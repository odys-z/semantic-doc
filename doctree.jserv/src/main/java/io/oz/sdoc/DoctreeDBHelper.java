package io.oz.sdoc;

import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.commons.io.FilenameUtils;

import io.odysz.common.EnvPath;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;

/**
 * The working copy of doc trees: its own db file (a connection of the app's connects.xml),
 * its tables created by {@link #setup(String, boolean)} at the tier's first request.
 *
 * <p>Tables, DDL by the metas, semantics in the connection's semantics xml:</p>
 * <ul>
 * <li>{@link S_DocsMeta}: headers, docId: auto key (autopk) prefixed with the synode, e.g. "X.000A";
 *     its radix and length are the synode's config.xml file-sys (radix 32) and id-len</li>
 * <li>{@link S_DocNodeMeta}: nodes of all docs, fullpath; the doc root's nid and fullpath are the docId,
 *     so a subtree, selected by fullpath prefix, never reaches into another doc</li>
 * <li>{@link AutoSeqMeta}: oz_autoseq, the auto keys</li>
 * </ul>
 *
 * <p>Not a syntity: nothing here is synchronized.</p>
 *
 * <p>Connects requires a sqlite file to exist, so call {@link #touch(String, String)}
 * before Connects.init(), e.g. for a volume upgraded from a version without doc trees.</p>
 *
 * @author ody
 */
public class DoctreeDBHelper {

	final String conn;
	final DATranscxt st;
	final IUser robot;

	public final S_DocsMeta docm;
	public final S_DocNodeMeta nodem;

	DoctreeDBHelper(String conn, S_DocsMeta docm, S_DocNodeMeta nodem) throws Exception {
		this.conn = conn;
		this.docm = docm;
		this.nodem = nodem;
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

		// before the connection's first DATranscxt: loading the autopk semantics needs oz_autoseq
		setupSqliTables(conn, forceDrop, new AutoSeqMeta(conn), docm, nodem);
		Connects.commit(conn, new JRobot(), nodem.ddlIndex);

		return new DoctreeDBHelper(conn, docm, nodem);
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
}
