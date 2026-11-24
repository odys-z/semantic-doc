package io.oz.sdoc.puck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;
import io.odysz.common.Configs;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetCfg.AnTreeNode;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantics.IUser;
import io.odysz.semantic.meta.DocRef;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.docx.DocxWalker;
import io.oz.sdoc.docx.RstreeDBHelper;
import io.oz.sdoc.docx.T_PhotoMeta;
import io.oz.sdoc.tier.DoctreeReq;
import io.oz.sdoc.tier.DoctreeResp;
import io.oz.sdoc.tier.DoctreeTier;

/**
 * Stories in the working copy: a new story, its page saved and loaded, published (html-service's
 * query), committed (pid → synuid, the committed page).
 */
class StoryPagesTest {
	static final String webinf = "src/test/res/WEB-INF";
	static String conn;
	static RstreeDBHelper db;
	static DoctreeTier tier;
	static final IUser usr = new JRobot();

	@BeforeAll
	static void init() throws Exception {
		new File("src/test/res/story", RstreeDBHelper.dbfile).delete();
		Configs.init(webinf);
		Connects.init(webinf);
		conn = RstreeDBHelper.connect(webinf, "../story", "S");
		DatasetCfg.init(webinf);
		db = RstreeDBHelper.setup(conn, true);
		tier = new DoctreeTier(Port.echo).conn(conn, "X29")
				// the album: X29,00A3F7's file isn't synchronized here yet, its uri a doc-ref
				.importer(T_PhotoMeta.album(webinf, conn,
						"00A3F1", "X29,00A3F1", "pom.xml",
						"00A3F2", "X29,00A3F2", "README.md",
						"00A3F7", "X29,00A3F7", "{\"type\": \"io.odysz.semantic.meta.DocRef\", \"synoder\": \"X28\", \"uids\": \"X29,00A3F7\"}"),
						DocxWalker::blocks);
	}

	/** an album node, as the client drops it */
	static String albumNode(String pid, String mime) {
		return "{\"type\": \"io.odysz.semantic.DA.DatasetCfg$AnTreeNode\", \"id\": \"" + pid + "\", "
			+ "\"node\": {\"nodetype\": \"p\", \"mime\": \"" + mime + "\", \"css\": \"{\\\"wh\\\":[4,3]}\", \"doctabl\": \"h_photos\"}}";
	}

	/** a page as the editor sends it (fromPuck(): "type" on the root only) */
	static PuckNode page(String title, String... galleryPids) throws Exception {
		StringBuilder items = new StringBuilder();
		for (String p : galleryPids)
			items.append(items.length() > 0 ? ", " : "").append(albumNode(p, "image/jpeg"));
		return (PuckNode) Anson.fromJson("{\"type\": \"io.oz.sdoc.puck.PuckNode\", \"pucktype\": \"page\", \"id\": \"root\", "
			+ "\"title\": \"" + title + "\", \"template\": \"pm-0\", \"children\": ["
			+ "{\"pucktype\": \"Hero\", \"id\": \"Hero-1\", \"title\": \"" + title + "\", \"media\": " + albumNode("00A3F1", "image/jpeg") + "},"
			+ "{\"pucktype\": \"Text\", \"id\": \"Text-1\", \"content\": \"Three days in the tea hills\"},"
			+ "{\"pucktype\": \"Gallery\", \"id\": \"Gallery-1\", \"layout\": \"rows\", \"items\": [" + items + "]}]}");
	}

	DoctreeResp ok(DoctreeReq req) {
		AnsonMsg<? extends AnsonResp> msg = tier.handle(req);
		assertEquals(MsgCode.ok, msg.code(), msg.body(0).msg());
		return (DoctreeResp) msg.body(0);
	}

	@Test
	void story() throws Exception {
		// a new story
		DoctreeReq save = new DoctreeReq("/sdoc").page(null, page("Spring Trip", "00A3F1", "00A3F2"));
		save.a(DoctreeReq.A.savePage);
		SDocRef s = ok(save).doc;
		assertNotNull(s.docId);
		assertTrue(s.docId.startsWith("X29.S"), s.docId);
		assertEquals(SDocRef.Kind.story, s.mime);
		assertTrue(s.dirty);

		// listed as a story, not as a doc tree
		DoctreeReq list = new DoctreeReq("/sdoc");
		list.a(DoctreeReq.A.stories);
		final String id = s.docId;
		assertTrue(ok(list).docs.stream().anyMatch((d) -> id.equals(d.docId)));
		list.a(DoctreeReq.A.docs);
		assertEquals(0, ok(list).docs.size());

		// loaded as saved
		DoctreeReq load = new DoctreeReq("/sdoc").docId(s.docId);
		load.a(DoctreeReq.A.page);
		PuckNode p = ok(load).page;
		assertEquals("Spring Trip", p.title);
		assertEquals("pm-0", p.template);
		assertEquals(3, p.children.size());
		assertEquals("Hero", p.children.get(0).pucktype);
		assertEquals("00A3F1", p.children.get(0).media.id());
		assertEquals(2, p.children.get(2).items.size());
		assertEquals("image/jpeg", p.children.get(2).items.get(1).get("mime"));

		// a media appended, saved again: the blocks replaced
		save = new DoctreeReq("/sdoc").page(s.docId, page("Spring Trip", "00A3F1", "00A3F2", "00A3F7"));
		save.a(DoctreeReq.A.savePage);
		ok(save);
		assertEquals(3, ok(load).page.children.get(2).items.size());

		// published (pubpath kept as html-service would answer): in html-service's query
		tier.puck().pubpath(s.docId, "pub/er3cou", usr);
		DoctreeReq pubs = new DoctreeReq("/sdoc");
		pubs.a(DoctreeReq.A.published);
		ArrayList<SDocRef> published = ok(pubs).docs;
		assertEquals(1, published.size());
		assertEquals(s.docId, published.get(0).docId);
		assertEquals(SDocRef.Kind.story, published.get(0).mime);
		assertEquals("pub/er3cou", published.get(0).pubpath);
		tier.puck().pubpath(s.docId, null, usr);
	}

	/** committed: the media's pids mapped to their synuids by the album's records */
	@Test
	void commit() throws Exception {
		DoctreeReq save = new DoctreeReq("/sdoc").page(null, page("Spring Trip", "00A3F1", "00A3F2", "00A3F7"));
		save.a(DoctreeReq.A.savePage);
		SDocRef s = ok(save).doc;
		DoctreeReq load = new DoctreeReq("/sdoc").docId(s.docId);
		load.a(DoctreeReq.A.page);
		PuckNode p;
		final String id = s.docId;

		// committed: synuids, the committed page, the cover
		DoctreeReq commit = new DoctreeReq("/sdoc").docId(s.docId);
		commit.a(DoctreeReq.A.commit);
		s = ok(commit).doc;
		assertTrue(!s.dirty);
		assertNotNull(s.committedAt);
		assertEquals("X29,00A3F1", s.cover);
		assertTrue(new File(s.htmlUri).isFile(), s.htmlUri);
		p = ok(load).page;
		assertEquals("X29,00A3F7", p.children.get(2).items.get(2).get("synuid"));

		// the resources html-service serves: the files here, by the album; tea.mp4 isn't synchronized
		// yet, a reference, resolved by a later query
		ArrayList<DocRef> res = tier.puck().resources(s.docId);
		assertEquals(3, res.size());
		assertEquals("X29,00A3F1", res.get(0).uids);
		assertEquals(new File("pom.xml").getAbsolutePath(), res.get(0).uri64);
		assertEquals("X29,00A3F2", res.get(1).uids);
		assertEquals("X29,00A3F7", res.get(2).uids);
		assertNull(res.get(2).uri64);

		// they go to html-service, published (r/published), and survive Anson
		tier.puck().pubpath(s.docId, "pub/er3cou", usr);
		DoctreeReq pubs = new DoctreeReq("/sdoc");
		pubs.a(DoctreeReq.A.published);
		DoctreeResp back = (DoctreeResp) Anson.fromJson(ok(pubs).toBlock());
		SDocRef pub = back.docs.stream().filter((d) -> d.docId.equals(id)).findFirst().get();
		assertEquals(3, pub.resources.size());
		assertEquals(new File("README.md").getAbsolutePath(), pub.resources.get(1).uri64);
		tier.puck().pubpath(s.docId, null, usr);
	}

	@Test
	void missingMedia() throws Exception {
		DoctreeReq save = new DoctreeReq("/sdoc").page(null, page("Lost", "00A3F9"));
		save.a(DoctreeReq.A.savePage);
		SDocRef s = ok(save).doc;

		DoctreeReq commit = new DoctreeReq("/sdoc").docId(s.docId);
		commit.a(DoctreeReq.A.commit);
		AnsonMsg<? extends AnsonResp> msg = tier.handle(commit);
		assertEquals(MsgCode.exSemantic, msg.code());
		assertTrue(msg.body(0).msg().contains("00A3F9"), msg.body(0).msg());
	}

	@Test
	void albumNodes() throws Exception {
		// the items of a gallery round trip through their column
		ArrayList<AnTreeNode> items = page("x", "00A3F1", "00A3F2").children.get(2).items;
		ArrayList<AnTreeNode> back = StoryPages.items(StoryPages.json(items));
		assertEquals(2, back.size());
		assertEquals("00A3F2", back.get(1).id());
	}
}
