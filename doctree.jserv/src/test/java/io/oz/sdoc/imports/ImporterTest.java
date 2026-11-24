package io.oz.sdoc.imports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.odysz.semantic.ext.SemanticTree.childIds;
import static java.util.Arrays.asList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantext;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetCfg.AnTreeNode;
import io.odysz.semantic.DA.DatasetHelper;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.ext.TreeModify;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantics.x.SemanticException;
import io.odysz.semantics.IUser;
import io.oz.sdoc.docx.Block;
import io.oz.sdoc.docx.DocxtreeDBHelper;
import io.oz.sdoc.docx.S_DocNodeMeta;
import io.oz.sdoc.docx.DocxInf;
import io.oz.sdoc.docx.DocxNode;
import io.oz.sdoc.docx.DocxWalker;
import io.oz.sdoc.tier.DoctreeReq;
import io.oz.sdoc.tier.DoctreeResp;
import io.oz.sdoc.tier.DoctreeTier;

/**
 * c/import through the tier, on a fresh src/test/res/doctree.db, with the tricky blocks as the parser.
 */
class ImporterTest {
	/** connected by {@link DocxtreeDBHelper#connect(String, String, String)}, volume: src/test/res */
	static String conn;
	static final String webinf = "src/test/res/WEB-INF";
	static final String docx = "src/test/res/tricky.docx";

	static DoctreeTier tier;
	/** set up once, with fresh tables; don't set up again, the docs imported by the tests would be dropped */
	static DocxtreeDBHelper db;
	static final JRobot robot = new JRobot();

	@BeforeAll
	static void init() throws Exception {
		// a fresh db: force-drop doesn't reach oz_autoseq (semantic-DA's), whose sequence would go on
		new File("src/test/res", DocxtreeDBHelper.dbfile).delete();
		Configs.init(webinf);
		Connects.init(webinf);
		conn = DocxtreeDBHelper.connect(webinf, "..", "T");
		DatasetCfg.init(webinf);

		db = DocxtreeDBHelper.setup(conn, true);

		// any file: the parser is the fake one
		tier = new DoctreeTier(Port.echo)
				.conn(conn, "T")
				.importer((srcId) -> "doc-a".equals(srcId) ? "pom.xml" : null,
						(path) -> Tricky.blocks());
	}

	@Test
	void importTricky() throws Exception {
		AnsonMsg<? extends AnsonResp> msg = tier.handle(importReq("doc-a"));

		assertEquals(MsgCode.ok, msg.code(), msg.body(0).msg());
		DocxInf doc = ((DoctreeResp) msg.body(0)).doc;
		String docId = doc.docId;
		assertEquals("T.0001", docId); // synode + autopk, Radix32 (file-sys = windows), id-len 4
		assertEquals(DocxInf.State.parsing, doc.state);
		assertEquals("doc-a", doc.srcId);

		tier.importer().await();

		DoctreeReq q = new DoctreeReq("/sdoc");
		q.a(DoctreeReq.A.docs);
		DoctreeResp docs = (DoctreeResp) tier.handle(q).body(0);
		doc = docs.docs.get(0);
		assertEquals(DocxInf.State.ready, doc.state);
		assertEquals("Annual Report", doc.title);

		// the nids: auto keys, in the order of generation, which is the tree order here
		String[] k = nids(docId);
		assertEquals(11, k.length);
		for (int i = 0; i < k.length; i++) {
			assertEquals(4, k[i].length(), k[i]); // Radix32, id-len 4 (src/test/res/WEB-INF/config.xml)
			if (i > 0) assertTrue(k[i - 1].compareTo(k[i]) < 0, k[i - 1] + " !< " + k[i]);
		}
		assertArrayEquals(Tricky.tree(k), lines(docId));
		String root = k[0];

		// a second doc: a new id, new nids; a subtree selected by fullpath prefix stays in its doc
		DocxInf doc2 = ((DoctreeResp) tier.handle(importReq("doc-a")).body(0)).doc;
		tier.importer().await();
		assertEquals("T.0002", doc2.docId);
		String[] k2 = nids(doc2.docId);
		assertArrayEquals(Tricky.tree(k2), lines(doc2.docId));
		assertTrue(k[10].compareTo(k2[0]) < 0, k[10] + " !< " + k2[0]);
		S_DocNodeMeta m = db.nodem;
		assertEquals(Tricky.tree(k).length, Connects.select(conn, String.format(
				"select %2$s from %3$s where %4$s = '%1$s' or %4$s like '%1$s.%%'",
				root, m.pk, m.tbl, m.fullpath)).total());

		// loads as one tree, the doc root
		List<AnTreeNode> forest = DatasetHelper.buildForest(db.nodes(docId), db.nodem.treeSemantics());
		assertEquals(1, forest.size());
		assertEquals(root, forest.get(0).id());
		assertEquals(2, forest.get(0).children().size());

		// r/tree: the doc and its forest, the nodes' fields as DocNode's
		DoctreeReq t = new DoctreeReq("/sdoc").docId(docId);
		t.a(DoctreeReq.A.tree);
		AnsonMsg<? extends AnsonResp> tmsg = tier.handle(t);
		assertEquals(MsgCode.ok, tmsg.code(), tmsg.body(0).msg());
		DoctreeResp tree = (DoctreeResp) tmsg.body(0);
		assertEquals("Annual Report", tree.doc.title);
		assertEquals(1, tree.forest().size());
		AnTreeNode rootn = (AnTreeNode) tree.forest().get(0);
		assertEquals(root, rootn.id());
		assertEquals("Annual Report", rootn.get("ptext"));
		AnTreeNode ch1 = (AnTreeNode) rootn.children().get(0);
		AnTreeNode p1 = (AnTreeNode) ch1.children().get(0);
		assertEquals("Body text under chapter one.", p1.get("ptext"));
		assertEquals("{\"v\":1,\"runs\":[{\"t\":\"Body text \"},{\"t\":\"under\",\"b\":1,\"color\":\"1E8449\"},{\"t\":\" chapter one.\"}]}",
				p1.get("facts"));
	}

	/**
	 * u/tree, u/node, c/commit on an imported tricky doc, k: the nids in tree order:
	 * <pre>
	 * k0 root
	 *   k1 section 第一章         [k2 p, k3 section 1.1 Scope, k4 section]
	 *     k4 section            [k5 section Clause 7]
	 *       k5 section          [k6 p, k7 p, k8 tabl]
	 *   k9 section 第二章         [k10 p]
	 * </pre>
	 */
	@Test
	void editTree() throws Exception {
		DocxInf doc = ((DoctreeResp) tier.handle(importReq("doc-a")).body(0)).doc;
		tier.importer().await();
		String d = doc.docId;
		String[] k = nids(d);
		String root = k[0], n1 = k[1], n2 = k[2], n3 = k[3], n5 = k[5], n6 = k[6], n7 = k[7], n8 = k[8], n9 = k[9];
		assertEquals(false, db.doc(d).dirty);

		// up: the server works out where it goes; the reloaded subtree is the parent's
		DoctreeResp r = modify(d, TreeModify.up(n3));
		assertEquals(null, r.rejected());
		assertEquals(n1, r.root());
		assertEquals(1, r.forest().size());
		assertEquals(n1, ((AnTreeNode) r.forest().get(0)).id());
		assertEquals(asList(n3, n2, k[4]), childIds(conn, db.nodem.treeSemantics(), n1, robot));
		assertEquals(true, r.doc.dirty);

		// indent: under the previous sibling, now the section .3
		modify(d, TreeModify.indent(n2));
		assertEquals(asList(n2), childIds(conn, db.nodem.treeSemantics(), n3, robot));

		// a paragraph can't have children; the doc root is fixed
		assertEquals(TreeModify.Rejected.holder, modify(d, TreeModify.indent(n7)).rejected());
		assertEquals(TreeModify.Rejected.fixed, modify(d, TreeModify.outdent(n1)).rejected());
		assertEquals(TreeModify.Rejected.fixed, modify(d, TreeModify.remove(root)).rejected());

		// insert: the id is the next auto key, the docId is the server's
		r = modify(d, TreeModify.insert(n7, TreeModify.At.after, "kind", "p", "docId", "not-this"));
		String nb = r.newId();
		assertEquals(4, nb.length(), nb);
		assertTrue(k[10].compareTo(nb) < 0, k[10] + " !< " + nb);
		assertEquals(n5, r.root());
		assertEquals(asList(n6, n7, nb, n8), childIds(conn, db.nodem.treeSemantics(), n5, robot));
		assertEquals(1, Connects.select(conn, String.format(
				"select nid from s_docnodes where nid = '%s' and docId = '%s'", nb, d)).total());

		// remove, with the subtree
		modify(d, TreeModify.remove(n9));
		assertEquals(asList(n1), childIds(conn, db.nodem.treeSemantics(), root, robot));
		assertEquals(0, Connects.select(conn, String.format(
				"select nid from s_docnodes where nid in ('%s', '%s')", n9, k[10])).total());

		// u/node: tipjson, rstext; the fullpath is not touched (fullpath2, semantic.DA ShFullpath2)
		AnResultset fp0 = Connects.select(conn, String.format(
				"select fullpath from s_docnodes where nid = '%s'", n6));
		fp0.next();
		DoctreeReq u = new DoctreeReq("/sdoc").docId(d).node(new DocxNode(d, n6, DocxNode.Kind.p)
				.tipjson("{\"contype\":\"doc\"}").rstext("{\"body\":\"x\"}", 1));
		u.a(DoctreeReq.A.update);
		AnsonMsg<? extends AnsonResp> m = tier.handle(u);
		assertEquals(MsgCode.ok, m.code(), m.body(0).msg());
		AnResultset rs = Connects.select(conn, String.format(
				"select tipjson, rstext, rstver from s_docnodes where nid = '%s'", n6));
		rs.next();
		assertEquals("{\"contype\":\"doc\"}", rs.getString("tipjson"));
		assertEquals("{\"body\":\"x\"}", rs.getString("rstext"));
		assertEquals(1, rs.getInt("rstver"));
		rs = Connects.select(conn, String.format(
				"select fullpath from s_docnodes where nid = '%s'", n6));
		rs.next();
		assertTrue(fp0.getString("fullpath").startsWith(root + "."), fp0.getString("fullpath"));
		assertEquals(fp0.getString("fullpath"), rs.getString("fullpath"));

		// c/commit
		DoctreeReq c = new DoctreeReq("/sdoc").docId(d);
		c.a(DoctreeReq.A.commit);
		DocxInf committed = ((DoctreeResp) tier.handle(c).body(0)).doc;
		assertEquals(false, committed.dirty);
		assertTrue(committed.committedAt != null);
	}

	static DoctreeResp modify(String docId, TreeModify mod) {
		DoctreeReq req = new DoctreeReq("/sdoc").docId(docId).modify(mod);
		req.a(DoctreeReq.A.modify);
		AnsonMsg<? extends AnsonResp> msg = tier.handle(req);
		assertEquals(MsgCode.ok, msg.code(), msg.body(0).msg());
		return (DoctreeResp) msg.body(0);
	}

	/**
	 * file-sys = windows: auto keys in Radix32, 32 = "0010" (Radix64: "000W"),
	 * and sortable: string order is number order over all id-len (4) digit keys.
	 */
	@Test
	void radix32() {
		assertEquals("0010", DASemantext.radix64_32(32));

		String prev = DASemantext.radix64_32(0);
		for (long v = 1; v < 1L << 20; v++) {
			String k = DASemantext.radix64_32(v);
			assertTrue(prev.compareTo(k) < 0, prev + " !< " + k);
			prev = k;
		}
		assertEquals("VVVV", prev);
	}

	@Test
	void docxNotFound() throws Exception {
		expectTrace("docxNotFound", "IllegalArgumentException: Docx not found: no-such");
		AnsonMsg<? extends AnsonResp> msg = tier.handle(importReq("no-such"));
		assertEquals(MsgCode.exGeneral, msg.code());
		assertTrue(msg.body(0).msg().contains("no-such"));
	}

	@Test
	void parseFails() throws Exception {
		DoctreeTier t = new DoctreeTier(Port.echo)
				.conn(conn, "F")
				.importer((srcId) -> "pom.xml", (path) -> { throw new Exception("not a docx"); });

		expectTrace("parseFails", "Exception: not a docx");

		DocxInf doc = ((DoctreeResp) t.handle(importReq("x")).body(0)).doc;
		t.importer().await();

		doc = db.doc(doc.docId);
		assertEquals(DocxInf.State.failed, doc.state);
		assertEquals("not a docx", doc.msg);
	}

	/**
	 * The nodes are inserted section by section, not in a transaction:
	 * a failure after some sections leaves no nodes, the header is failed.
	 */
	@Test
	void failsHalfway() throws Exception {
		ArrayList<Block> blocks = new ArrayList<Block>(Tricky.blocks());
		blocks.add(null); // 第一章's sections are inserted by 第二章, then a NullPointerException

		DoctreeTier t = new DoctreeTier(Port.echo)
				.conn(conn, "H")
				.importer((srcId) -> "pom.xml", (path) -> blocks);

		expectTrace("failsHalfway", "NullPointerException");

		DocxInf doc = ((DoctreeResp) t.handle(importReq("x")).body(0)).doc;
		t.importer().await();

		assertEquals(DocxInf.State.failed, db.doc(doc.docId).state);
		assertEquals(0, db.nodes(doc.docId).total());
	}

	/**
	 * media.docx, by the docx walker: the picture is an image node, its file in the doc's resource
	 * folder (src/test/res/[uid]/doctree.temp/[docId]); the Excel object is a paragraph of text.
	 */
	@Test
	void importMedia() throws Exception {
		DoctreeTier t = new DoctreeTier(Port.echo)
				.conn(conn, "H")
				.importer((srcId) -> "src/test/res/media.docx", DocxWalker::blocks);

		// the session's user is the doc's creator, the owner of its resource folder
		IUser ody = new JRobot() {
			@Override public String uid() { return "ody"; }
		};

		DocxInf doc = ((DoctreeResp) t.handle(importReq("media"), ody).body(0)).doc;
		t.importer().await();
		String d = doc.docId;
		assertEquals(DocxInf.State.ready, db.doc(d).state, db.doc(d).msg);
		assertEquals("ody", db.doc(d).creator);

		// the picture and the Excel object, the 11th and 12th nodes in tree order, under 第二章, the 9th
		S_DocNodeMeta m = db.nodem;
		String[] k = nids(d);
		AnResultset rs = Connects.select(conn, String.format(
				"select %s, %s, %s, %s from %s where %s in ('%s', '%s') order by %s",
				m.parentId, m.kind, m.ptext, m.resId, m.tbl, m.pk, k[11], k[12], m.fullpath));
		rs.next();
		assertEquals(k[9], rs.getString(m.parentId)); // 第二章
		assertEquals(DocxNode.Kind.image, rs.getString(m.kind));
		assertEquals("image1.png", rs.getString(m.resId));
		rs.next();
		assertEquals(DocxNode.Kind.p, rs.getString(m.kind));
		assertEquals("[excel object is ignored in semantic-doc 0.1.1]", rs.getString(m.ptext));

		File png = db.res.file("ody", d, "image1.png");
		assertEquals(new File("src/test/res/ody/doctree.temp/" + d + "/image1.png").getAbsolutePath(), png.getAbsolutePath());
		assertTrue(png.length() > 0);

		// a failed import leaves no resources: the picture is saved, then a part is missing
		DoctreeTier f = new DoctreeTier(Port.echo)
				.conn(conn, "H")
				.importer((srcId) -> "src/test/res/media.docx", (path) -> {
					List<Block> blocks = DocxWalker.blocks(path);
					blocks.add(new Block(Block.image, "body/13/pic/0").res("word/media/none.png").text("none.png"));
					return blocks;
				});
		expectTrace("importMedia", "IOException: No part word/media/none.png");
		doc = ((DoctreeResp) f.handle(importReq("media"), ody).body(0)).doc;
		f.importer().await();
		assertEquals(DocxInf.State.failed, db.doc(doc.docId).state);
		assertEquals(0, db.nodes(doc.docId).total());
		assertEquals(false, db.res.dir("ody", doc.docId).exists());
	}

	/**
	 * A stale semantics file (fullpath, not fullpath2), e.g. a sandbox's old WEB-INF: the working copy
	 * isn't set up, with a message saying what to fix, instead of importing docs without fullpaths.
	 */
	@Test
	void staleSemantics() throws Exception {
		File dbf = new File("src/test/res/r206/stale.db");
		dbf.delete();
		Connects.addSqlite("doctree.stale", dbf.getAbsolutePath(), "semantics-doctree-stale.xml");

		SemanticException e = assertThrows(SemanticException.class, () -> DocxtreeDBHelper.setup("doctree.stale", true));
		assertTrue(e.getMessage().contains("expecting <smtc>fullpath2</smtc>"), e.getMessage());
		assertTrue(e.getMessage().contains("WEB-INF/semantics-doctree.xml"), e.getMessage());
	}

	/** the doc's nids, in tree order */
	static String[] nids(String docId) throws Exception {
		AnResultset rs = db.nodes(docId);
		ArrayList<String> k = new ArrayList<String>();
		while (rs.next())
			k.add(rs.getString(db.nodem.pk));
		return k.toArray(new String[0]);
	}

	static String[] lines(String docId) throws Exception {
		S_DocNodeMeta m = db.nodem;
		AnResultset rs = db.nodes(docId);
		ArrayList<String> lines = new ArrayList<String>();
		while (rs.next())
			lines.add(String.join(" ", rs.getString(m.pk), String.valueOf(rs.getString(m.parentId)),
				rs.getString(m.sort), rs.getString(m.fullpath), rs.getString(m.kind), rs.getString(m.ptext)));
		return lines.toArray(new String[0]);
	}

	/** Tell the reader the stack trace printed next is the test's own. */
	static void expectTrace(String test, String trace) {
		Utils.warn("\n**************************************************************\n"
				 + "* [ImporterTest.%s] A stack trace is expected next:\n"
				 + "*   %s\n"
				 + "* IGNORE it: the test makes the import fail on purpose.\n"
				 + "**************************************************************", test, trace);
	}

	static DoctreeReq importReq(String srcId) {
		DoctreeReq req = new DoctreeReq("/sdoc");
		req.srcId = srcId;
		req.doc = new DocxInf(null, null, null);
		req.a(DoctreeReq.A.importDocx);
		return req;
	}
}
