package io.oz.sdoc.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

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
import io.oz.sdoc.docx.DocxtreeDBHelper;
import io.oz.sdoc.docx.DocxInf;
import io.oz.sdoc.docx.MediaSource;
import io.oz.sdoc.publish.PubMedia;
import io.oz.sdoc.publish.PuckPubReq;
import io.oz.sdoc.publish.PuckPubResp;
import io.oz.sdoc.publish.ResPublishTier;
import io.oz.sdoc.puck.PuckNode;
import io.oz.sdoc.puck.StoryPages;
import io.oz.sdoc.tier.DoctreeReq;
import io.oz.sdoc.tier.DoctreeResp;
import io.oz.sdoc.tier.DoctreeTier;

/**
 * Stories in the working copy: a new story, its page saved and loaded, committed (pid → synuid,
 * the committed page), and the media maps html-service gets at refresh.
 */
class StoryPagesTest {
	static final String webinf = "src/test/res/WEB-INF";
	static String conn;
	static DocxtreeDBHelper db;
	static DoctreeTier tier;
	static final IUser usr = new JRobot();

	/** the album: pid → synuid, and the files of those here */
	static final HashMap<String, String> album = new HashMap<String, String>() {{
		put("00A3F1", "X29,00A3F1");
		put("00A3F2", "X29,00A3F2");
		put("00A3F7", "X29,00A3F7");
	}};
	static final HashMap<String, String> files = new HashMap<String, String>() {{
		put("X29,00A3F1", new File("pom.xml").getAbsolutePath());
		put("X29,00A3F2", new File("README.md").getAbsolutePath());
		// X29,00A3F7: not synchronized yet
	}};

	static final MediaSource media = new MediaSource() {
		@Override
		public HashMap<String, String> synuids(Collection<String> pids) {
			HashMap<String, String> ids = new HashMap<String, String>();
			for (String p : pids)
				if (album.containsKey(p))
					ids.put(p, album.get(p));
			return ids;
		}

		@Override
		public String path(String synuid) {
			return files.get(synuid);
		}
	};

	@BeforeAll
	static void init() throws Exception {
		new File("src/test/res/story", DocxtreeDBHelper.dbfile).delete();
		Configs.init(webinf);
		Connects.init(webinf);
		conn = DocxtreeDBHelper.connect(webinf, "../story", "S");
		DatasetCfg.init(webinf);
		db = DocxtreeDBHelper.setup(conn, true);
		tier = new DoctreeTier(Port.echo).conn(conn, "X29").media(media);
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
		return (PuckNode) Anson.fromJson("{\"type\": \"io.oz.sdoc.story.PuckNode\", \"pucktype\": \"page\", \"id\": \"root\", "
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
		DocxInf s = ok(save).doc;
		assertNotNull(s.docId);
		assertTrue(s.docId.startsWith("X29.S"), s.docId);
		assertEquals(DocxInf.Kind.story, s.kind);
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

		// published (pubpath kept as html-service would answer): the map html-service gets at refresh
		db.pubpath(s.docId, "pub/er3cou", usr);
		assertEquals(DocxInf.Kind.story, db.doc(s.docId).kind);
		PuckPubReq refresh = new PuckPubReq();
		refresh.a(PuckPubReq.A.refresh);
		PuckPubResp resp = (PuckPubResp) new ResPublishTier(Port.echo, tier).handle(refresh).body(0);
		assertEquals(1, resp.respubs.size());
		assertEquals(1, resp.media.size());
		PubMedia m = resp.media.get(0);
		assertEquals(s.docId, m.docId);
		assertEquals("pub/er3cou", m.pubpath);
		assertEquals(2, m.paths.size()); // tea.mp4's file isn't here
		assertEquals(files.get("X29,00A3F1"), m.paths.get("X29,00A3F1"));
		assertNull(m.paths.get("X29,00A3F7"));

		// the map survives Anson, as it goes to html-service
		PubMedia back = (PubMedia) Anson.fromJson(m.toBlock());
		assertEquals(m.paths, back.paths);
	}

	@Test
	void missingMedia() throws Exception {
		DoctreeReq save = new DoctreeReq("/sdoc").page(null, page("Lost", "00A3F9"));
		save.a(DoctreeReq.A.savePage);
		DocxInf s = ok(save).doc;

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
