package io.oz.sdoc.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;
import io.odysz.common.Configs;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.docx.RstreeDBHelper;
import io.oz.sdoc.docx.S_DocsMeta;
import io.oz.sdoc.htmlsrv.HtmlPubReq;
import io.oz.sdoc.htmlsrv.HtmlPubResp;
import io.oz.sdoc.htmlsrv.HtmlTestPort;
import io.oz.sdoc.puck.PuckNode;
import io.oz.sdoc.puck.ResPublisher;
import io.oz.sdoc.puck.S_StoriesMeta;

/**
 * Publishing at html-service, the synode side: c/publish, d/publish of {@link DoctreeTier}, of a
 * doc tree and a story, and html-service's query to {@link PucktreeTier}. Html-service is played by
 * {@link #htmlsrv}. Its own db: src/test/res/rpub/doctree.db.
 */
class PucktreeTierTest {
	static final String webinf = "src/test/res/WEB-INF";

	static String conn;
	static DoctreeTier tier;
	static PucktreeTier pucktree;
	static String docId;
	static String storyId;
	static final IUser ody = new JRobot() {
		@Override public String uid() { return "ody"; }
	};

	/** What html-service was asked: "a pubpath htmlUri" */
	static final ArrayList<String> asked = new ArrayList<String>();

	/** Html-service: gives a first published doc the path "pub/x7k2p" + the count. */
	static final ResPublisher htmlsrv = new ResPublisher("http://127.0.0.1:8900", HtmlTestPort.respaths) {
		@Override
		protected HtmlPubResp post(String a, SDocRef doc) {
			asked.add(a + " " + doc.pubpath + " " + doc.htmlUri);
			if (doc.pubpath == null)
				doc.pubpath = "pub/x7k2p" + asked.size();
			return new HtmlPubResp().respubs(new HtmlPubReq().respubs(doc).respubs);
		}
	};

	@BeforeAll
	static void init() throws Exception {
		new File("src/test/res/rpub", RstreeDBHelper.dbfile).delete();

		Configs.init(webinf);
		Connects.init(webinf);
		conn = RstreeDBHelper.connect(webinf, "../rpub", "P");
		DatasetCfg.init(webinf);
		RstreeDBHelper db = RstreeDBHelper.setup(conn, true);

		tier = new DoctreeTier(HtmlTestPort.wrong)
				.conn(conn, "P")
				.publisher(htmlsrv);
		pucktree = new PucktreeTier(HtmlTestPort.pucktree, tier);

		// a doc tree, built (the importer isn't needed here)
		DATranscxt st = new DATranscxt(conn);
		S_DocsMeta docm = db.docm;
		SemanticObject res = (SemanticObject) st.insert(docm.tbl, ody)
				.nv(docm.synode, "P")
				.nv(docm.title, "Doc tree")
				.nv(docm.creator, ody.uid())
				.nv(docm.state, SDocRef.State.ready)
				.ins(st.instancontxt(conn, ody));
		docId = res.resulve(docm.tbl, docm.pk, -1);
		db.built(docId, "/vol/html/doc.zip", "rtd-0.8", ody);

		// a story, saved by the editor, committed (its page file)
		DoctreeReq save = new DoctreeReq("/sdoc");
		save.a(DoctreeReq.A.savePage);
		save.page = (PuckNode) Anson.fromJson("{\"type\": \"io.oz.sdoc.puck.PuckNode\", \"pucktype\": \"page\", \"id\": \"root\", "
				+ "\"title\": \"Tea hills\", \"template\": \"pm-0\", \"children\": ["
				+ "{\"pucktype\": \"Text\", \"id\": \"Text-1\", \"content\": \"Three days in the tea hills\"}]}");
		storyId = ok(tier.handle(save, ody)).doc.docId;

		S_StoriesMeta storym = tier.puck().stories.storym;
		st.update(storym.tbl, ody)
			.nv(storym.htmlUri, "/vol/html/story.page.json")
			.whereEq(storym.pk, storyId)
			.u(st.instancontxt(conn, ody));
	}

	@Test
	void testPublished() throws Exception {
		assertEquals(0, published().size());

		// the doc tree and the story published: the paths are html-service's
		SDocRef doc = ok(doctree(DoctreeReq.A.publish, docId)).doc;
		assertEquals("pub/x7k2p1", doc.pubpath);
		assertEquals("c null /vol/html/doc.zip", asked.get(0));

		SDocRef story = ok(doctree(DoctreeReq.A.publish, storyId)).doc;
		assertEquals("pub/x7k2p2", story.pubpath);
		assertEquals(SDocRef.Kind.story, story.mime);
		assertEquals("c null /vol/html/story.page.json", asked.get(1));

		// html-service's query: both trees' pages
		ArrayList<SDocRef> pubs = published();
		assertEquals(2, pubs.size());
		assertEquals(storyId, pubs.get(0).docId);
		assertEquals("pub/x7k2p2", pubs.get(0).pubpath);
		assertEquals(docId, pubs.get(1).docId);
		assertEquals("/vol/html/doc.zip", pubs.get(1).htmlUri);

		// the story removed
		story = ok(doctree(DoctreeReq.A.unpublish, storyId)).doc;
		assertNull(story.pubpath);
		assertTrue(asked.get(2).startsWith("d pub/x7k2p2"), asked.get(2));
		pubs = published();
		assertEquals(1, pubs.size());
		assertEquals(docId, pubs.get(0).docId);

		// not a query of html-service
		PucktreeReq req = new PucktreeReq();
		req.a(DoctreeReq.A.publish);
		assertEquals(MsgCode.exSemantic, pucktree.handle(req).code());
	}

	/** The port's url is checked. */
	@Test
	void testPortUrl() {
		SemanticException e = assertThrows(SemanticException.class, () -> new PucktreeTier(HtmlTestPort.respaths, tier));
		assertTrue(e.getMessage().contains(PucktreeTier.url), e.getMessage());
	}

	/** A request of the editor, to the doc-tree tier. */
	static AnsonMsg<? extends AnsonResp> doctree(String a, String id) {
		DoctreeReq req = new DoctreeReq("/sdoc").docId(id);
		req.a(a);
		return tier.handle(req, ody);
	}

	static DoctreeResp ok(AnsonMsg<? extends AnsonResp> msg) {
		assertEquals(MsgCode.ok, msg.code(), msg.body(0).msg());
		return (DoctreeResp) msg.body(0);
	}

	/** The query of html-service. */
	static ArrayList<SDocRef> published() {
		PucktreeReq req = new PucktreeReq();
		req.a(DoctreeReq.A.docs);
		return ok(pucktree.handle(req)).docs;
	}
}
