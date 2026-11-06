package io.oz.sdoc.imports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantext;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetCfg.AnTreeNode;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.DA.DatasetHelper;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.S_DocNodeMeta;
import io.oz.sdoc.DoctreeDBHelper;
import io.oz.sdoc.DoctreeReq;
import io.oz.sdoc.DoctreeResp;
import io.oz.sdoc.DoctreeTier;

/**
 * c/import through the tier, on a fresh doctree-test.db, with the tricky blocks as the parser.
 */
class ImporterTest {
	static final String conn = "doctree-test";
	static final String webinf = "src/test/res/WEB-INF";
	static final String docx = "src/test/res/tricky.docx";

	static DoctreeTier tier;
	static TreeSemantics sm;

	@BeforeAll
	static void init() throws Exception {
		new File("src/test/res/doctree-test.db").delete();
		DoctreeDBHelper.touch(webinf, "../doctree-test.db");
		Configs.init(webinf);
		Connects.init(webinf);
		DatasetCfg.init(webinf);
		sm = DatasetCfg.getTreeSemtcs("sdoc.tree");

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
		DocInf doc = ((DoctreeResp) msg.body(0)).doc;
		String docId = doc.docId;
		assertEquals("T.0001", docId); // synode + autopk, Radix32 (file-sys = windows), id-len 4
		assertEquals(DocInf.State.parsing, doc.state);
		assertEquals("doc-a", doc.srcId);

		tier.importer().await();

		DoctreeReq q = new DoctreeReq("/sdoc");
		q.a(DoctreeReq.A.docs);
		DoctreeResp docs = (DoctreeResp) tier.handle(q).body(0);
		doc = docs.docs.get(0);
		assertEquals(DocInf.State.ready, doc.state);
		assertEquals("Annual Report", doc.title);
		assertEquals(11, doc.nextNid);

		assertArrayEquals(Tricky.tree(docId), lines(docId));

		// a second doc: a new id; a subtree selected by fullpath prefix stays in its doc
		DocInf doc2 = ((DoctreeResp) tier.handle(importReq("doc-a")).body(0)).doc;
		tier.importer().await();
		assertEquals("T.0002", doc2.docId);
		assertArrayEquals(Tricky.tree(doc2.docId), lines(doc2.docId));
		S_DocNodeMeta m = DoctreeDBHelper.setup(conn).nodem;
		assertEquals(Tricky.tree(docId).length, Connects.select(conn, String.format(
				"select %2$s from %3$s where %4$s = '%1$s' or %4$s like '%1$s.%%'",
				docId, m.pk, m.tbl, m.fullpath)).total());

		// loads as one tree, the doc root
		List<AnTreeNode> forest = DatasetHelper.buildForest(DoctreeDBHelper.setup(conn).nodes(docId), sm);
		assertEquals(1, forest.size());
		assertEquals(docId, forest.get(0).id());
		assertEquals(2, forest.get(0).children().size());
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
		AnsonMsg<? extends AnsonResp> msg = tier.handle(importReq("no-such"));
		assertEquals(MsgCode.exGeneral, msg.code());
		assertTrue(msg.body(0).msg().contains("no-such"));
	}

	@Test
	void parseFails() throws Exception {
		DoctreeTier t = new DoctreeTier(Port.echo)
				.conn(conn, "F")
				.importer((srcId) -> "pom.xml", (path) -> { throw new Exception("not a docx"); });

		DocInf doc = ((DoctreeResp) t.handle(importReq("x")).body(0)).doc;
		t.importer().await();

		doc = DoctreeDBHelper.setup(conn).doc(doc.docId);
		assertEquals(DocInf.State.failed, doc.state);
		assertEquals("not a docx", doc.msg);
	}

	static String[] lines(String docId) throws Exception {
		DoctreeDBHelper db = DoctreeDBHelper.setup(conn);
		S_DocNodeMeta m = db.nodem;
		AnResultset rs = db.nodes(docId);
		ArrayList<String> lines = new ArrayList<String>();
		while (rs.next())
			lines.add(String.join(" ", rs.getString(m.pk), String.valueOf(rs.getString(m.parentId)),
				rs.getString(m.sort), rs.getString(m.fullpath), rs.getString(m.kind), rs.getString(m.ptext)));
		return lines.toArray(new String[0]);
	}

	static DoctreeReq importReq(String srcId) {
		DoctreeReq req = new DoctreeReq("/sdoc");
		req.srcId = srcId;
		req.doc = new DocInf(null, null, null);
		req.a(DoctreeReq.A.importDocx);
		return req;
	}
}
