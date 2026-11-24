package io.oz.sdoc.docx;

import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.io.FilenameUtils;

import io.odysz.common.Utils;
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
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.condition.Condit;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.DocResHelper;
import io.oz.sdoc.DoctreeDBUpgrade;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.puck.S_StoriesMeta;
import io.oz.sdoc.puck.S_StoryNodesMeta;
import io.oz.sdoc.puck.StoryPages;
import io.oz.syn.dbpicks.DbPicks;

/**
 * The working copy of doc trees: its own db file in the synode's volume ({@link #connect(String, String, String)},
 * not configured in connects.xml), its tables created by {@link #setup(String, boolean)} at the tier's first request.
 *
 * <p>Tables, DDL by the metas, semantics in the connection's semantics xml:</p>
 * <ul>
 * <li>{@link S_DocsMeta}: headers, docId: auto key (autopk) prefixed with the synode, e.g. "X.000A";
 *     its radix and length are the synode's config.xml file-sys (radix 32) and id-len</li>
 * <li>{@link S_DocNodeMeta}: nodes of all docs, nid: auto key; fullpath; the doc root's fullpath is its nid,
 *     so a subtree, selected by fullpath prefix, never reaches into another doc</li>
 * <li>oz_autoseq, the auto keys on sqlite: created by semantic-DA's autopk handler (1.5.27)</li>
 * </ul>
 *
 * <p>A db file of an earlier version is upgraded by {@link DoctreeDBUpgrade} when connected.</p>
 *
 * <p>The docs' resources (pictures) are files beside the db file, see {@link DocResHelper}.</p>
 *
 * <p>Not a syntity: nothing here is synchronized.</p>
 *
 * <p>{@link #connect(String, String, String)} creates the db file, e.g. for a volume upgraded
 * from a version without doc trees.</p>
 *
 * @author ody
 */
public class RstreeDBHelper {

	protected final String conn;
	public final DATranscxt st;
	protected final IUser robot;

	public final S_DocsMeta docm;
	public final S_DocNodeMeta nodem;
//	/** the stories (media stories, Puck pages), in their own tables; since 0.1.5 */
//	public final StoryPages stories;
	/** the docs' resource files, beside the db file; null if not connected by {@link #connect(String, String, String)} */
	public final DocResHelper res;

	/** conn id: folder of its db file, by {@link #connect(String, String, String)} */
	static final HashMap<String, String> volumes = new HashMap<String, String>();

	/** conn id: why its db file couldn't be upgraded, by {@link #connect(String, String, String, String)} */
	static final HashMap<String, String> upgradeErrors = new HashMap<String, String>();

	/** conn id: the picking list of its db, by {@link #connect(String, String, String, String)}, for {@link #setup(String, boolean)} */
	static final HashMap<String, DbPicks> picklists = new HashMap<String, DbPicks>();

	protected RstreeDBHelper(String conn, S_DocsMeta docm, S_DocNodeMeta nodem,
			S_StoriesMeta storym, S_StoryNodesMeta snodem) throws Exception {
		this.conn = conn;
		this.res = volumes.containsKey(conn) ? new DocResHelper(volumes.get(conn)) : null;
		this.docm = docm;
		this.nodem = nodem;
		this.st = new DATranscxt(conn);
		this.robot = new JRobot();
//		this.stories = new StoryPages(conn, st, res, robot, storym, snodem);
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
	 * <p>Without a picking list: a db file of an earlier version can't be upgraded, see
	 * {@link #connect(String, String, String, String)}.</p>
	 *
	 * @param webinf
	 * @param volume settings.json's volume, relative to webinf or absolute
	 * @param synode makes the conn id unique when several synodes share a JVM (tests)
	 * @return conn id, "doctree." + synode
	 * @throws Exception
	 */
	public static String connect(String webinf, String volume, String synode) throws Exception {
		return connect(webinf, volume, synode, null);
	}

	/**
	 * Connect the doc-tree db of a synode, &lt;volume&gt;/{@link #dbfile}, with the semantics
	 * {@link #smtcs}; the db file is created if there is none. Not configured in connects.xml.
	 * Call after Connects.init().
	 *
	 * <p>A db file of an earlier version is upgraded first ({@link DoctreeDBUpgrade}), with the picking
	 * list of the app's package. If that fails, the file is kept as it is, the synode starts, and
	 * {@link #setup(String, boolean)} answers why.</p>
	 *
	 * @param webinf
	 * @param volume settings.json's volume, relative to webinf or absolute
	 * @param synode makes the conn id unique when several synodes share a JVM (tests)
	 * @param picks the picking list, e.g. &lt;install&gt;/bin/doctree-picks.json, see {@link DbPicks}; null: none
	 * @return conn id, "doctree." + synode
	 * @throws Exception
	 * @since 0.1.4
	 */
	public static String connect(String webinf, String volume, String synode, String picks) throws Exception {
		String volpath = FilenameUtils.concat(new File(webinf).getAbsolutePath(), volume);
		String dbpath = FilenameUtils.concat(volpath, dbfile);
		String conn = "doctree." + synode;

		upgradeErrors.remove(conn);
		picklists.remove(conn);
		try {
			DbPicks picklist = DbPicks.load(picks);
			if (picklist == null && picks != null)
				Utils.warn("[DoctreeDBHelper] No picking list: %s", picks);
			else if (picklist != null)
				picklists.put(conn, picklist);
			DoctreeDBUpgrade.upgrade(conn, dbpath, smtcs, picklist, new S_DocsMeta(), new S_DocNodeMeta(),
					new S_StoriesMeta(), new S_StoryNodesMeta());
		}
		catch (Exception e) {
			e.printStackTrace();
			upgradeErrors.put(conn, String.format("Doc-tree db %s can't be upgraded: %s. "
					+ "Check the package's picking list (%s), or restore a backup of the db.",
					dbpath, e.getMessage(), picks));
		}

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
	public static RstreeDBHelper setup(String conn, boolean forceDrop) throws Exception {
		if (upgradeErrors.containsKey(conn))
			throw new SemanticException(upgradeErrors.get(conn));

		S_DocsMeta docm = new S_DocsMeta(conn);
		S_DocNodeMeta nodem = new S_DocNodeMeta(conn);
		S_StoriesMeta storym = new S_StoriesMeta(conn);
		S_StoryNodesMeta snodem = new S_StoryNodesMeta(conn);

		// oz_autoseq, for the autopks of docId and nid, is created by semantic-DA (1.5.27) when the semantics is loaded
		// the stories' tables, since 0.1.5, are created here if missing, as s_docs on a volume without doc trees
		setupSqliTables(conn, forceDrop, docm, nodem, storym, snodem);
		Connects.commit(conn, new JRobot(), nodem.ddlIndex);
		Connects.commit(conn, new JRobot(), snodem.ddlIndex);

		// loads the connection's semantics
		RstreeDBHelper db = new RstreeDBHelper(conn, docm, nodem, storym, snodem);
		checkSemantics(conn, docm, nodem);
		checkSemantics(conn, storym, snodem);

		// the records this version needs, e.g. the sequence of nid, if missing
		DoctreeDBUpgrade.inits(db.st, conn, picklists.get(conn));
		return db;
	}

	/**
	 * The semantics the doc tree needs, from {@link #smtcs}: a missing or stale file (e.g. a sandbox's
	 * WEB-INF copied before fullpath2) would import docs with no fullpaths, or none at all.
	 * <ul>
	 * <li>{@link S_DocsMeta}: autopk (smtype autoInc), the docIds</li>
	 * <li>{@link S_DocNodeMeta}: autopk, the nids (since 0.1.4); fullpath2 (ShFullpath2), the nodes' fullpaths
	 *     in multi-row inserts</li>
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
		if (!DATranscxt.hasSemantics(conn, nodem.tbl, smtype.autoInc))
			throw new SemanticException("Doc-tree semantics: no autopk for %s. %s", nodem.tbl, fix);
	}

	/**
	 * The semantics the stories need (since 0.1.5): autopks of {@link S_StoriesMeta} (prefixed with the
	 * synode and "S") and {@link S_StoryNodesMeta}.
	 *
	 * @throws SemanticException what's missing, for the user
	 */
	static void checkSemantics(String conn, S_StoriesMeta storym, S_StoryNodesMeta snodem) throws SemanticException {
		String fix = String.format("Check WEB-INF/%s (connection %s), e.g. copy it from the package's WEB-INF.", smtcs, conn);

		if (!DATranscxt.hasSemantics(conn, storym.tbl, smtype.autoInc))
			throw new SemanticException("Doc-tree semantics: no autopk for %s. %s", storym.tbl, fix);
		if (!DATranscxt.hasSemantics(conn, snodem.tbl, smtype.autoInc))
			throw new SemanticException("Doc-tree semantics: no autopk for %s. %s", snodem.tbl, fix);
	}

	/**
	 * Headers of all docs.
	 *
	 * @return docs
	 */
	public ArrayList<SDocRef> docs() throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(docm.tbl)
				.orderby(docm.pk)
				.rs(st.instancontxt(conn, robot))
				.rs(0);

		ArrayList<SDocRef> docs = new ArrayList<SDocRef>();
		while (rs.next())
			docs.add(doc(rs));
		return docs;
	}

	/**
	 * Select doc record, pk = docId, from table {@link S_DocNodeMeta#tbl}
	 * @param docId
	 * @return the header, null if not found
	 */
	public SDocRef doc(String docId) throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(docm.tbl)
				.whereEq(docm.pk, docId)
				.rs(st.instancontxt(conn, robot))
				.rs(0);
		// a story's, of kind story (since 0.1.5), if not a doc tree's
		return rs.next() ? doc(rs) : null;// stories.story(docId);
	}

	SDocRef doc(AnResultset rs) throws SQLException {
		SDocRef d = new SDocRef(rs.getString(docm.pk), rs.getString(docm.title), rs.getString(docm.creator));
		d.state   = rs.getString(docm.state);
		d.msg     = rs.getString(docm.msg);
//		d.srcId   = rs.getString(docm.refId);
		d.htmlUri = rs.getString(docm.htmlUri);
		d.builtAt = rs.getString(docm.builtAt);
		d.builtBy = rs.getString(docm.builtBy);
		// a db created before 0.1.3 has no such column, until upgraded
		d.template = rs.getString(docm.template, (String) null);
		d.pubpath = rs.getString(docm.pubpath);
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
	public SDocRef update(DocxNode n, IUser usr) throws TransException, SQLException {
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
	public SDocRef commit(String docId, IUser usr) throws Exception {
		st.update(docm.tbl, usr)
			.nv(docm.dirty, 0)
			.nv(docm.committedAt, Funcall.now())
			.whereEq(docm.pk, docId)
			.u(st.instancontxt(conn, usr));
		return doc(docId);
	}

	/**
	 * The doc's html package is (re)built: htmlUri, builtAt = now, builtBy, template.
	 * @param htmlUri the zip's local path, see {@link SDocRef#htmlUri}
	 * @param template id of the template it was built with, see {@link SDocRef#template}
	 * @return the doc's header
	 */
	public SDocRef built(String docId, String htmlUri, String template, IUser usr) throws TransException, SQLException {
		st.update(docm.tbl, usr)
			.nv(docm.htmlUri, htmlUri)
			.nv(docm.builtAt, Funcall.now())
			.nv(docm.builtBy, usr.uid())
			.nv(docm.template, template)
			.whereEq(docm.pk, docId)
			.u(st.instancontxt(conn, usr));
		return doc(docId);
	}

	/**
	 * Docs published at html-service, i. e. with a {@link SDocRef#pubpath}.
	 * The puck tree's are its own, see io.oz.sdoc.puck.PucktreeDBHelper#published().
	 * @return docs
	 */
	public ArrayList<SDocRef> published() throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(docm.tbl)
				.where(new Condit(op.isNotnull, docm.pubpath, "null"))
				.orderby(docm.pk)
				.rs(st.instancontxt(conn, robot))
				.rs(0);

		ArrayList<SDocRef> docs = new ArrayList<SDocRef>();
		while (rs.next())
			docs.add(doc(rs));
		return docs;
	}

	/**
	 * Keep the doc's context path at html-service.
	 * @param pubpath null: the doc is no longer published
	 * @return the doc's header
	 */
	public SDocRef pubpath(String docId, String pubpath, IUser usr) throws TransException, SQLException {
		st.update(docm.tbl, usr)
			.nv(docm.pubpath, pubpath)
			.whereEq(docm.pk, docId)
			.u(st.instancontxt(conn, usr));
		return doc(docId);
	}
}
