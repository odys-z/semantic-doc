package io.oz.sdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.ext.TreeModify;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.tier.DoctreeReq;
import io.oz.sdoc.tier.DoctreeResp;
import io.oz.sdoc.tier.DoctreeTier;

/**
 * A doc-tree db of earlier versions, upgraded when connected:
 * s_docs as on a 0.8.x synode (dirty, committedAt, nextNid; no template, pubpath),
 * s_docnodes of the first version (json, rst, style), old nids (docId + '.' + n), oz_autoseq.
 */
class DoctreeDBUpgradeTest {
	static final String webinf = "src/test/res/WEB-INF";
	/** volumes, relative to webinf */
	static final String vol = "../upgrade";
	static final String volFail = "../upgrade-fail";
	static final String volInits = "../upgrade-inits";
	static final String volNopicks = "../upgrade-nopicks";
	/** the test copy of the picking list, as jserv-album's bin/db-picks.json */
	static final String picks = "src/test/res/db-picks.json";

	@BeforeAll
	static void init() throws Exception {
		Configs.init(webinf);
		Connects.init(webinf);
		DatasetCfg.init(webinf);
	}

	@Test
	void upgrade() throws Exception {
		File dir = fresh(vol);
		File dbf = new File(dir, DoctreeDBHelper.dbfile);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbf.getAbsolutePath());
			 Statement s = c.createStatement()) {
			s.execute("create table s_docs (docId varchar(32) NOT NULL, synode varchar(12) NOT NULL, title varchar(256),"
					+ " creator varchar(32), state varchar(8), msg text, refId varchar(64), nextNid integer DEFAULT 1,"
					+ " htmlUri varchar(512), builtAt varchar(24), builtBy varchar(32), dirty integer DEFAULT 0,"
					+ " committedAt varchar(24), PRIMARY KEY (docId))");
			s.execute("create table s_docnodes (nid varchar(48) NOT NULL, docId varchar(32) NOT NULL, parentId varchar(48),"
					+ " sort integer, fullpath varchar(512), kind varchar(8), json text, rst text, rstver integer DEFAULT 0,"
					+ " ptext text, src varchar(64), style varchar(64), resId varchar(64), refId varchar(64), PRIMARY KEY (nid))");
			s.execute("create table oz_autoseq (sid text(50), seq INTEGER, remarks text(200), CONSTRAINT oz_autoseq_pk PRIMARY KEY (sid))");

			s.execute("insert into s_docs (docId, synode, title, creator, state, refId, nextNid, dirty)"
					+ " values ('U.0001', 'U', 'Old doc', 'ody', 'ready', 'U,0401', 3, 1),"
					+ " ('U.0002', 'U', 'Old doc 2', 'admin', 'ready', 'U,0401', 1, 0)");
			// built: the template picked is the default (DBPICK s_docs.template, where builtAt is not null)
			s.execute("update s_docs set htmlUri = 'U.0002.zip', builtAt = '2026-11-12 10:00:00' where docId = 'U.0002'");
			s.execute("insert into s_docnodes (nid, docId, parentId, sort, fullpath, kind, json, rst, rstver, ptext, style) values"
					+ " ('U.0001', 'U.0001', null, 1, 'U.0001', 'doc', null, null, 0, 'Old doc', null),"
					+ " ('U.0001.1', 'U.0001', 'U.0001', 1, 'U.0001.0001', 'section', '{\"contype\":\"doc\"}', 'Chapter\n=======', 1, 'Chapter', 'heading 1'),"
					+ " ('U.0001.2', 'U.0001', 'U.0001.1', 1, 'U.0001.0001.0001', 'p', null, null, 0, 'Text', 'Normal')");
			s.execute("insert into oz_autoseq (sid, seq, remarks) values ('s_docs.docId', 2, 'by ShAutoKPrefix')");
		}

		String conn = DoctreeDBHelper.connect(webinf, vol, "U", picks);

		// the old file is kept as a backup
		File[] baks = dir.listFiles((d, n) -> n.startsWith(DoctreeDBHelper.dbfile + ".") && n.endsWith(".bak"));
		assertEquals(1, baks.length);
		assertFalse(new File(dir, DoctreeDBHelper.dbfile + ".new").exists());

		// the current tables: new columns, renamed ones picked, nextNid dropped
		ArrayList<String> dcols = columns(dbf, "s_docs");
		assertTrue(dcols.contains("template") && dcols.contains("pubpath"), dcols.toString());
		assertFalse(dcols.contains("nextNid"), dcols.toString());
		// the sequence of the new auto key nid, inserted by the upgrade (DBPICK oz_autoseq:s_docnodes.nid)
		assertEquals("0", select(dbf, "select seq from oz_autoseq where sid = 's_docnodes.nid'"));
		assertEquals("2", select(dbf, "select seq from oz_autoseq where sid = 's_docs.docId'"));

		ArrayList<String> ncols = columns(dbf, "s_docnodes");
		assertTrue(ncols.contains("tipjson") && ncols.contains("rstext") && ncols.contains("docxstyle") && ncols.contains("facts"), ncols.toString());
		assertFalse(ncols.contains("json") || ncols.contains("rst") || ncols.contains("style"), ncols.toString());

		DoctreeDBHelper db = DoctreeDBHelper.setup(conn, false);

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

		// the sequences go on: docId from the old seq, nid by the semantics
		DoctreeTier tier = new DoctreeTier(Port.echo).conn(conn, "U");
		AnsonMsg<? extends AnsonResp> m = tier.handle(modify("U.0001", TreeModify.insert("U.0001.2", TreeModify.At.after, "kind", "p")));
		assertEquals(MsgCode.ok, m.code(), m.body(0).msg());
		String nid = ((DoctreeResp) m.body(0)).newId();
		assertNotNull(nid);
		assertFalse(nid.contains("."), nid);
		assertEquals(4, db.nodes("U.0001").total());

		AnResultset seq = Connects.select(conn, "select sid, seq from oz_autoseq order by sid");
		seq.next();
		assertEquals("s_docnodes.nid", seq.getString("sid"));
		seq.next();
		assertEquals("s_docs.docId", seq.getString("sid"));
		assertEquals(2, seq.getInt("seq"));

		// upgraded once: connected again, the file isn't touched
		DoctreeDBHelper.connect(webinf, vol, "U", picks);
		assertEquals(1, dir.listFiles((d, n) -> n.endsWith(".bak")).length);
	}

	/** A db that can't be upgraded: kept as it is; the synode starts, the tier answers why. */
	@Test
	void upgradeFails() throws Exception {
		File dir = fresh(volFail);
		File dbf = new File(dir, DoctreeDBHelper.dbfile);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbf.getAbsolutePath());
			 Statement s = c.createStatement()) {
			s.execute("create table s_docs (docId varchar(32), synode varchar(12), title varchar(256), nextNid integer)");
			// a node without a docId, now NOT NULL
			s.execute("create table s_docnodes (nid varchar(48), docId varchar(32), parentId varchar(48), sort integer, fullpath varchar(512), kind varchar(8))");
			s.execute("insert into s_docnodes (nid, docId, kind) values ('x', null, 'p')");
		}
		long size = dbf.length();

		expectTrace("upgradeFails", "SQLiteException: [SQLITE_CONSTRAINT_NOTNULL] ... s_docnodes.docId");
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
	 * A db of the current tables, without some records the current version needs: they are inserted
	 * in place, the file isn't copied; a sequence can start past the rows a table has.
	 */
	@Test
	void initsInPlace() throws Exception {
		File dir = fresh(volInits);
		File dbf = new File(dir, DoctreeDBHelper.dbfile);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbf.getAbsolutePath());
			 Statement s = c.createStatement()) {
			s.execute(new S_DocsMeta().ddlSqlite);
			s.execute(new S_DocNodeMeta().ddlSqlite);
			s.execute("create table oz_autoseq (sid text(50), seq INTEGER, remarks text(200), CONSTRAINT oz_autoseq_pk PRIMARY KEY (sid))");
			s.execute("insert into oz_autoseq (sid, seq, remarks) values ('s_docs.docId', 3, 'by ShAutoKPrefix')");
			s.execute("insert into s_docs (docId, synode) values ('I.0001', 'I'), ('I.0002', 'I'), ('I.0003', 'I')");
		}

		// e.g. a new auto key of a table with rows: its sequence starts past them
		DbPicks picks = DbPicks.load(DoctreeDBUpgradeTest.picks);
		DbInitRow ak = new DbInitRow();
		ak.mark = "DB-PICKING-0.8.2 oz_autoseq:s_docs.ak";
		ak.tabl = "oz_autoseq";
		ak.key = new String[] { "sid" };
		ak.nvs = new ArrayList<String[]>();
		ak.nvs.add(new String[] { "sid", "s_docs.ak" });
		ak.nvs.add(new String[] { "remarks", "test" });
		ak.sqls = new ArrayList<String[]>();
		ak.sqls.add(new String[] { "seq", "select count(*) from s_docs" });
		picks.inits.add(ak);

		assertNull(DoctreeDBUpgrade.upgrade(dbf.getAbsolutePath(), picks, new S_DocsMeta(), new S_DocNodeMeta()));
		assertEquals(0, dir.listFiles((d, n) -> n.endsWith(".bak") || n.endsWith(".new")).length);

		assertEquals("0", select(dbf, "select seq from oz_autoseq where sid = 's_docnodes.nid'"));
		assertEquals("3", select(dbf, "select seq from oz_autoseq where sid = 's_docs.ak'"));
		assertEquals("3", select(dbf, "select seq from oz_autoseq where sid = 's_docs.docId'"));

		// existing records are never changed
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbf.getAbsolutePath());
			 Statement s = c.createStatement()) {
			s.execute("update oz_autoseq set seq = 9 where sid = 's_docnodes.nid'");
		}
		DoctreeDBUpgrade.upgrade(dbf.getAbsolutePath(), picks, new S_DocsMeta(), new S_DocNodeMeta());
		assertEquals("9", select(dbf, "select seq from oz_autoseq where sid = 's_docnodes.nid'"));
	}

	/** A db of an earlier version, but no picking list: kept as it is, the tier answers why. */
	@Test
	void noPicks() throws Exception {
		File dir = fresh(volNopicks);
		File dbf = new File(dir, DoctreeDBHelper.dbfile);
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbf.getAbsolutePath());
			 Statement s = c.createStatement()) {
			s.execute("create table s_docs (docId varchar(32), synode varchar(12), title varchar(256), nextNid integer)");
		}
		long size = dbf.length();

		expectTrace("noPicks", "IOException: The tables of ... are of an earlier version, but there's no picking list to upgrade it.");
		String conn = DoctreeDBHelper.connect(webinf, volNopicks, "N", "src/test/res/no-such-picks.json");

		assertEquals(0, dir.listFiles((d, n) -> n.endsWith(".bak") || n.endsWith(".new")).length);
		assertEquals(size, dbf.length());
		SemanticException e = assertThrows(SemanticException.class, () -> DoctreeDBHelper.setup(conn, false));
		assertTrue(e.getMessage().contains("no picking list"), e.getMessage());
		assertTrue(e.getMessage().contains("no-such-picks.json"), e.getMessage());
	}

	static String select(File dbf, String sql) throws Exception {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbf.getAbsolutePath());
			 Statement s = c.createStatement();
			 ResultSet rs = s.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	static File fresh(String volume) {
		File dir = new File(webinf, volume);
		if (dir.isDirectory())
			for (File f : dir.listFiles())
				f.delete();
		dir.mkdirs();
		return dir;
	}

	static ArrayList<String> columns(File dbf, String tbl) throws Exception {
		ArrayList<String> cols = new ArrayList<String>();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbf.getAbsolutePath());
			 Statement s = c.createStatement();
			 ResultSet rs = s.executeQuery("pragma table_info(" + tbl + ")")) {
			while (rs.next())
				cols.add(rs.getString("name"));
		}
		return cols;
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
