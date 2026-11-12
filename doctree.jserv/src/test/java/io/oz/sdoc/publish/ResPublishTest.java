//package io.oz.sdoc.publish;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import java.io.File;
//import java.util.ArrayList;
//
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.Test;
//
//import io.odysz.common.Configs;
//import io.odysz.semantic.DA.Connects;
//import io.odysz.semantic.DA.DatasetCfg;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
//import io.odysz.semantic.jprotocol.AnsonMsg.Port;
//import io.odysz.semantic.jprotocol.AnsonResp;
//import io.odysz.semantic.jserv.JRobot;
//import io.odysz.semantics.IUser;
//import io.oz.sdoc.DocInf;
//import io.oz.sdoc.DoctreeDBHelper;
//import io.oz.sdoc.docx.DocxWalker;
//import io.oz.sdoc.tier.DoctreeReq;
//import io.oz.sdoc.tier.DoctreeResp;
//import io.oz.sdoc.tier.DoctreeTier;
//
///**
// * Publishing a doc at html-service, the synode side: c/publish, d/publish of {@link DoctreeTier},
// * and html-service's query to {@link ResPublishTier}. Html-service is played by {@link #htmlsrv}.
// * Its own db: src/test/res/rpub/doctree.db.
// */
//class ResPublishTest {
//	static final String webinf = "src/test/res/WEB-INF";
//
//	static DoctreeTier tier;
//	static ResPublishTier respub;
//	static String docId;
//	static final IUser ody = new JRobot() {
//		@Override public String uid() { return "ody"; }
//	};
//
//	/** What html-service was asked: "a pubpath htmlUri" */
//	static final ArrayList<String> asked = new ArrayList<String>();
//
//	/** Html-service: gives a first published doc the path "x7k2p0". */
//	static final ResPublisher htmlsrv = new ResPublisher("http://127.0.0.1:8900", Port.echo) {
//		@Override
//		protected ResPublishResp post(String a, DocInf doc) {
//			asked.add(a + " " + doc.pubpath + " " + doc.htmlUri);
//			if (doc.pubpath == null)
//				doc.pubpath = "x7k2p0";
//			return new ResPublishResp().respubs(new ResPublishReq().respubs(doc).respubs);
//		}
//	};
//
//	@BeforeAll
//	static void init() throws Exception {
//		new File("src/test/res/rpub", DoctreeDBHelper.dbfile).delete();
//
//		AnsonMsg.understandPorts(Port.echo);
//		Configs.init(webinf);
//		Connects.init(webinf);
//		String conn = DoctreeDBHelper.connect(webinf, "../rpub", "P");
//		DatasetCfg.init(webinf);
//		DoctreeDBHelper.setup(conn, true);
//
//		tier = new DoctreeTier(Port.echo)
//				.conn(conn, "P")
//				.importer((srcId) -> "src/test/res/media.docx", DocxWalker::blocks)
//				.publisher(htmlsrv);
//		respub = new ResPublishTier(Port.echo, tier);
//
//		DoctreeReq imp = new DoctreeReq("/sdoc");
//		imp.srcId = "media";
//		imp.doc = new DocInf(null, null, null);
//		imp.a(DoctreeReq.A.importDocx);
//		docId = ((DoctreeResp) tier.handle(imp, ody).body(0)).doc.docId;
//		tier.importer().await();
//	}
//
//	@Test
//	void testPublish() throws Exception {
//		// not built yet
//		assertEquals(MsgCode.exSemantic, doctree(DoctreeReq.A.publish).code());
//		assertEquals(0, published().size());
//
//		// built & published: the path is html-service's
//		tier.db().built(docId, "/vol/html/media.zip", ody);
//		DocInf doc = ((DoctreeResp) doctree(DoctreeReq.A.publish).body(0)).doc;
//		assertEquals("x7k2p0", doc.pubpath);
//		assertEquals("c null /vol/html/media.zip", asked.get(0));
//
//		// html-service's query
//		ArrayList<DocInf> pubs = published();
//		assertEquals(1, pubs.size());
//		assertEquals(docId, pubs.get(0).docId);
//		assertEquals("x7k2p0", pubs.get(0).pubpath);
//		assertEquals("/vol/html/media.zip", pubs.get(0).htmlUri);
//		assertNotNull(pubs.get(0).builtAt);
//
//		// rebuilt & published again: the same path
//		tier.db().built(docId, "/vol/html/media-2.zip", ody);
//		doc = ((DoctreeResp) doctree(DoctreeReq.A.publish).body(0)).doc;
//		assertEquals("x7k2p0", doc.pubpath);
//		assertEquals("c x7k2p0 /vol/html/media-2.zip", asked.get(1));
//
//		// removed
//		doc = ((DoctreeResp) doctree(DoctreeReq.A.unpublish).body(0)).doc;
//		assertNull(doc.pubpath);
//		assertTrue(asked.get(2).startsWith("d x7k2p0"));
//		assertEquals(0, published().size());
//	}
//
//	/** A request of the editor, to the doc-tree tier. */
//	static AnsonMsg<? extends AnsonResp> doctree(String a) {
//		DoctreeReq req = new DoctreeReq("/sdoc").docId(docId);
//		req.a(a);
//		return tier.handle(req, ody);
//	}
//
//	/** The query of html-service. */
//	static ArrayList<DocInf> published() {
//		ResPublishReq req = new ResPublishReq();
//		req.a(ResPublishReq.A.refresh);
//		AnsonMsg<? extends AnsonResp> msg = respub.handle(req);
//		assertEquals(MsgCode.ok, msg.code());
//		return ((ResPublishResp) msg.body(0)).respubs;
//	}
//}
