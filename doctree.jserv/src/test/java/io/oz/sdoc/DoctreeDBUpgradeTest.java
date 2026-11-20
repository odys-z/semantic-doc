package io.oz.sdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.ext.TreeModify;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.tier.DoctreeReq;
import io.oz.sdoc.tier.DoctreeResp;
import io.oz.sdoc.tier.DoctreeTier;
import io.oz.syn.dbpicks.DbInitRow;
import io.oz.syn.dbpicks.DbPicks;

/**
 * A doc-tree db of earlier versions, upgraded when connected:
 * s_docs as on a 0.8.x synode (dirty, committedAt, nextNid; no template, pubpath),
 * s_docnodes of the first version (json, rst, style), old nids (docId + '.' + n), oz_autoseq.
 *
 * <p>The old dbs are made through semantic.DA without semantics: the tables by their DDL of the
 * time, the rows by a basic transact builder ({@link DATranscxt#getBasicTrans(String)}).</p>
 */
class DoctreeDBUpgradeTest {
	static final String webinf = "src/test/res/WEB-INF";
	/** volumes, relative to webinf */
	static final String vol = "../upgrade";
	static final String volFail = "../upgrade-fail";
	static final String volInits = "../upgrade-inits";
	static final String volNopicks = "../upgrade-nopicks";
	/** the test copy of the picking list, as jserv-album's bin/doctree-picks.json */
	static final String picks = "src/test/res/doctree-picks.json";

	static final IUser robot = new JRobot();

	/** s_docs of 0.1.2, as pm-1-hub's */
	static final String ddlDocs012 = "create table s_docs (docId varchar(32) NOT NULL, synode varchar(12) NOT NULL, title varchar(256),"
			+ " creator varchar(32), state varchar(8), msg text, refId varchar(64), nextNid integer DEFAULT 1,"
			+ " htmlUri varchar(512), builtAt varchar(24), builtBy varchar(32), dirty integer DEFAULT 0,"
			+ " committedAt varchar(24), PRIMARY KEY (docId))";
	/** s_docnodes of the first version */
	static final String ddlNodes010 = "create table s_docnodes (nid varchar(48) NOT NULL, docId varchar(32) NOT NULL, parentId varchar(48),"
			+ " sort integer, fullpath varchar(512), kind varchar(8), json text, rst text, rstver integer DEFAULT 0,"
			+ " ptext text, src varchar(64), style varchar(64), resId varchar(64), refId varchar(64), PRIMARY KEY (nid))";

	@BeforeAll
	static void init() throws Exception {
		Configs.init(webinf);
		Connects.init(webinf);
		DatasetCfg.init(webinf);
	}

	@Test
	void upgrade() throws Exception {
		File dir = fresh(vol);
		DATranscxt fx = fixture("fixture.U", new File(dir, DoctreeDBHelper.dbfile), ddlDocs012, ddlNodes010,
				new AutoSeqMeta().ddlSqlite);
		fx.insert("s_docs", robot)
			.nv("docId", "U.0001").nv("synode", "U").nv("title", "Old doc").nv("creator", "ody")
			.nv("state", "ready").nv("refId", "U,0401").nv("nextNid", "3").nv("dirty", "1")
			.post(fx.insert("s_docs", robot)
				.nv("docId", "U.0002").nv("synode", "U").nv("title", "Old doc 2").nv("creator", "admin")
				.nv("state", "ready").nv("refId", "U,0401").nv("nextNid", "1").nv("dirty", "0")
				// built: the template picked is the default (DB-PICKING-0.8.2 s_docs.template, when builtAt is not null)
				.nv("htmlUri", "U.0002.zip").nv("builtAt", "2026-11-12 10:00:00"))
			.post(fx.insert("s_docnodes", robot)
				.nv("nid", "U.0001").nv("docId", "U.0001").nv("sort", "1").nv("fullpath", "U.0001")
				.nv("kind", "doc").nv("ptext", "Old doc"))
			.post(fx.insert("s_docnodes", robot)
				.nv("nid", "U.0001.1").nv("docId", "U.0001").nv("parentId", "U.0001").nv("sort", "1")
				.nv("fullpath", "U.0001.0001").nv("kind", "section").nv("json", "{\"contype\":\"doc\"}")
				.nv("rst", "Chapter\n=======").nv("rstver", "1").nv("ptext", "Chapter").nv("style", "heading 1"))
			.post(fx.insert("s_docnodes", robot)
				.nv("nid", "U.0001.2").nv("docId", "U.0001").nv("parentId", "U.0001.1").nv("sort", "1")
				.nv("fullpath", "U.0001.0001.0001").nv("kind", "p").nv("ptext", "Text").nv("style", "Normal"))
			.post(fx.insert("oz_autoseq", robot)
				.nv("sid", "s_docs.docId").nv("seq", "2").nv("remarks", "by ShAutoKPrefix"))
			.ins(fx.basictx());
		Connects.removeSqlite("fixture.U");

		// 3 nodes in pages of 2: the last page is a short one
		int pg = DoctreeDBUpgrade.pgSize;
		DoctreeDBUpgrade.pgSize = 2;
		String conn;
		try { conn = DoctreeDBHelper.connect(webinf, vol, "U", picks); }
		finally { DoctreeDBUpgrade.pgSize = pg; }

		// the old file is kept as a backup
		File[] baks = dir.listFiles((d, n) -> n.startsWith(DoctreeDBHelper.dbfile + ".") && n.endsWith(".bak"));
		assertEquals(1, baks.length);
		assertFalse(new File(dir, DoctreeDBHelper.dbfile + ".new").exists());

		DoctreeDBHelper db = DoctreeDBHelper.setup(conn, false);

		// the current tables: new columns, renamed ones picked, nextNid dropped
		Set<String> dcols = Connects.getMeta(conn, "s_docs").ftypes().keySet();
		assertTrue(has(dcols, "template") && has(dcols, "pubpath"), dcols.toString());
		assertFalse(has(dcols, "nextNid"), dcols.toString());
		Set<String> ncols = Connects.getMeta(conn, "s_docnodes").ftypes().keySet();
		assertTrue(has(ncols, "tipjson") && has(ncols, "rstext") && has(ncols, "docxstyle") && has(ncols, "facts"), ncols.toString());
		assertFalse(has(ncols, "json") || has(ncols, "rst") || has(ncols, "style"), ncols.toString());

		// the sequences: docId's goes on (copied), nid's is the version's (DB-PICKING-0.8.2 oz_autoseq:s_docnodes.nid)
		assertEquals("2", seq(db.st, conn, "s_docs.docId"));
		assertEquals("0", seq(db.st, conn, "s_docnodes.nid"));

		// the docs and nodes, as they were
		ArrayList<DocInf> docs = db.docs();
		assertEquals(2, docs.size());
		assertEquals("U.0001", docs.get(0).docId);
		assertEquals("ody", docs.get(0).creator);
		assertEquals(true, docs.get(0).dirty);
		assertNull(docs.get(0).pubpath);
		assertNull(docs.get(0).template);   // never built: null
		assertEquals("rtd-0.8", docs.get(1).template); // built before the column

		AnResultset rs = db.nodes("U.0001");
		assertEquals(3, rs.total());
		rs.next();
		assertEquals("U.0001", rs.getString("nid"));
		rs.next();
		assertEquals("U.0001.1", rs.getString("nid"));
		assertEquals("{\"contype\":\"doc\"}", rs.getString("tipjson"));
		assertEquals("Chapter\n=======", rs.getString("rstext"));
		assertEquals(1, rs.getInt("rstver"));
		assertEquals("heading 1", rs.getString("docxstyle"));

		// a new node of an old doc: an auto key
		DoctreeTier tier = new DoctreeTier(Port.echo).conn(conn, "U");
		AnsonMsg<? extends AnsonResp> m = tier.handle(modify("U.0001", TreeModify.insert("U.0001.2", TreeModify.At.after, "kind", "p")));
		assertEquals(MsgCode.ok, m.code(), m.body(0).msg());
		String nid = ((DoctreeResp) m.body(0)).newId();
		assertNotNull(nid);
		assertFalse(nid.contains("."), nid);
		assertEquals(4, db.nodes("U.0001").total());
		assertEquals("2", seq(db.st, conn, "s_docs.docId"));

		// upgraded once: connected again, the file isn't copied
		DoctreeDBHelper.connect(webinf, vol, "U", picks);
		assertEquals(1, dir.listFiles((d, n) -> n.endsWith(".bak")).length);
	}

	/** A db that can't be upgraded: kept as it is; the synode starts, the tier answers why. */
	@Test
	void upgradeFails() throws Exception {
		File dir = fresh(volFail);
		File dbf = new File(dir, DoctreeDBHelper.dbfile);
		DATranscxt fx = fixture("fixture.V", dbf,
				"create table s_docs (docId varchar(32), synode varchar(12), title varchar(256), nextNid integer)",
				// a node without a docId, now NOT NULL
				"create table s_docnodes (nid varchar(48), docId varchar(32), parentId varchar(48), sort integer, fullpath varchar(512), kind varchar(8))");
		fx.insert("s_docnodes", robot).nv("nid", "x").nv("kind", "p").ins(fx.basictx());
		Connects.removeSqlite("fixture.V");
		long size = dbf.length();

		expectTrace("upgradeFails", "NOT NULL constraint failed: s_docnodes.docId");
		String conn = DoctreeDBHelper.connect(webinf, volFail, "V", picks);

		assertEquals(0, dir.listFiles((d, n) -> n.endsWith(".bak") || n.endsWith(".new")).length);
		assertEquals(size, dbf.length());

		SemanticException e = assertThrows(SemanticException.class, () -> DoctreeDBHelper.setup(conn, false));
		assertTrue(e.getMessage().contains("can't be upgraded"), e.getMessage());

		AnsonMsg<? extends AnsonResp> m = new DoctreeTier(Port.echo).conn(conn, "V").handle(docs());
		assertEquals(MsgCode.exSemantic, m.code());
		assertTrue(m.body(0).msg().contains("can't be upgraded"), m.body(0).msg());
	}

	/** Tell the reader the stack trace printed next is the test's own, as ImporterTest does. */
	static void expectTrace(String test, String trace) {
		Utils.warn("\n**************************************************************\n"
				 + "* [DoctreeDBUpgradeTest.%s] A stack trace is expected next:\n"
				 + "*   %s\n"
				 + "* IGNORE it: the test makes the upgrade fail on purpose.\n"
				 + "**************************************************************", test, trace);
	}

	/**
	 * A db of the current tables, without some records the current version needs: the file isn't
	 * copied, the records are inserted when it's set up; a sequence can start past the rows a table has;
	 * existing records are never changed.
	 */
	@Test
	void initsInPlace() throws Exception {
		File dir = fresh(volInits);
		DATranscxt fx = fixture("fixture.I", new File(dir, DoctreeDBHelper.dbfile),
				new S_DocsMeta().ddlSqlite, new S_DocNodeMeta().ddlSqlite, new AutoSeqMeta().ddlSqlite);
		fx.insert("oz_autoseq", robot).nv("sid", "s_docs.docId").nv("seq", "3").nv("remarks", "by ShAutoKPrefix")
			.post(fx.insert("s_docs", robot).nv("docId", "I.0001").nv("synode", "I"))
			.post(fx.insert("s_docs", robot).nv("docId", "I.0002").nv("synode", "I"))
			.post(fx.insert("s_docs", robot).nv("docId", "I.0003").nv("synode", "I"))
			.ins(fx.basictx());
		Connects.removeSqlite("fixture.I");

		String conn = DoctreeDBHelper.connect(webinf, volInits, "I", picks);
		assertEquals(0, dir.listFiles((d, n) -> n.endsWith(".bak") || n.endsWith(".new")).length);

		DoctreeDBHelper db = DoctreeDBHelper.setup(conn, false);
		assertEquals("0", seq(db.st, conn, "s_docnodes.nid"));
		assertEquals("3", seq(db.st, conn, "s_docs.docId"));

		// e.g. a new auto key of a table with rows: its sequence starts past them
		DbPicks more = DbPicks.load(picks);
		DbInitRow ak = new DbInitRow();
		ak.mark = "DB-PICKING-0.8.2 oz_autoseq:s_docs.ak";
		ak.tabl = "oz_autoseq";
		ak.key = new String[] { "sid" };
		ak.nvs = new ArrayList<String[]>();
		ak.nvs.add(new String[] { "sid", "s_docs.ak" });
		ak.nvs.add(new String[] { "remarks", "test" });
		ak.counts = new ArrayList<String[]>();
		ak.counts.add(new String[] { "seq", "s_docs" });
		more.inits.add(ak);

		assertEquals(1, DoctreeDBUpgrade.inits(db.st, conn, more));
		assertEquals("3", seq(db.st, conn, "s_docs.ak"));

		// existing records are never changed
		DAHelper.updateFieldWhereEqs(db.st, conn, robot, new AutoSeqMeta(conn), "seq", 9, "sid", "s_docnodes.nid");
		assertEquals(0, DoctreeDBUpgrade.inits(db.st, conn, more));
		assertEquals("9", seq(db.st, conn, "s_docnodes.nid"));
	}

	/** A db of an earlier version, but no picking list: kept as it is, the tier answers why. */
	@Test
	void noPicks() throws Exception {
		File dir = fresh(volNopicks);
		File dbf = new File(dir, DoctreeDBHelper.dbfile);
		fixture("fixture.N", dbf, "create table s_docs (docId varchar(32), synode varchar(12), title varchar(256), nextNid integer)");
		Connects.removeSqlite("fixture.N");
		long size = dbf.length();

		expectTrace("noPicks", "IOException: The tables of ... are of an earlier version, but there's no picking list to upgrade it.");
		String conn = DoctreeDBHelper.connect(webinf, volNopicks, "N", "src/test/res/no-such-picks.json");

		assertEquals(0, dir.listFiles((d, n) -> n.endsWith(".bak") || n.endsWith(".new")).length);
		assertEquals(size, dbf.length());
		SemanticException e = assertThrows(SemanticException.class, () -> DoctreeDBHelper.setup(conn, false));
		assertTrue(e.getMessage().contains("no picking list"), e.getMessage());
		assertTrue(e.getMessage().contains("no-such-picks.json"), e.getMessage());
	}

	/**
	 * An old db, through semantic.DA without semantics: connected, its tables created by the DDL of the time.
	 * Remove the connection ({@link Connects#removeSqlite(String)}) before the db is connected for the doc tree.
	 *
	 * @return a basic transact builder (no semantics) for the rows
	 */
	static DATranscxt fixture(String conn, File dbf, String... ddls) throws Exception {
		Connects.addSqlite(conn, dbf.getAbsolutePath(), DoctreeDBHelper.smtcs);
		for (String ddl : ddls)
			Connects.commit(conn, robot, ddl);
		return (DATranscxt) DATranscxt.getBasicTrans(conn);
	}

	/** the sequence of an auto key, oz_autoseq.seq of sid */
	static String seq(DATranscxt st, String conn, String sid) throws Exception {
		return DAHelper.getValstr(st, conn, new AutoSeqMeta(conn), "seq", "sid", sid);
	}

	static boolean has(Set<String> cols, String col) {
		return DoctreeDBUpgrade.find(cols, col) != null;
	}

	static File fresh(String volume) {
		File dir = new File(webinf, volume);
		if (dir.isDirectory())
			for (File f : dir.listFiles())
				f.delete();
		dir.mkdirs();
		return dir;
	}

	static DoctreeReq modify(String docId, TreeModify mod) {
		DoctreeReq req = new DoctreeReq("/sdoc").docId(docId).modify(mod);
		req.a(DoctreeReq.A.modify);
		return req;
	}

	static DoctreeReq docs() {
		DoctreeReq req = new DoctreeReq("/sdoc");
		req.a(DoctreeReq.A.docs);
		return req;
	}
}
