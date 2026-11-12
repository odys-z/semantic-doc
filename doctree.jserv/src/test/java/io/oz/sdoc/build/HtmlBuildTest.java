package io.oz.sdoc.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantics.IUser;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.DoctreeDBHelper;
import io.oz.sdoc.docx.DocxWalker;
import io.oz.sdoc.publish.ResPublishReq;
import io.oz.sdoc.publish.ResPublishResp;
import io.oz.sdoc.publish.ResPublisher;
import io.oz.sdoc.tier.DoctreeReq;
import io.oz.sdoc.tier.DoctreeResp;
import io.oz.sdoc.tier.DoctreeTier;

/**
 * Committing a doc with its RST files builds its html package: c/commit, r/build, then c/publish.
 * Needs sphinx-build (the template's manifest.json tells the versions), skipped without it.
 * The template is the demo's, semantic-doc/src/web-dist/sdoc-template. Its own db: src/test/res/rbuild/doctree.db.
 */
class HtmlBuildTest {
	static final String webinf = "src/test/res/WEB-INF";
	static final String template = "../src/web-dist/sdoc-template/source";

	static DoctreeTier tier;
	static HtmlBuilder builder;
	static String docId;
	/** a picture of media.docx, in the doc's resource folder */
	static String picture;

	static final IUser ody = new JRobot() {
		@Override public String uid() { return "ody"; }
	};

	/** Html-service: gives a first published doc the path "pub/x7k2p0". */
	static final ResPublisher htmlsrv = new ResPublisher("http://127.0.0.1:8900", Port.echo) {
		@Override
		protected ResPublishResp post(String a, DocInf doc) {
			if (doc.pubpath == null)
				doc.pubpath = "pub/x7k2p0";
			return new ResPublishResp().respubs(new ResPublishReq().respubs(doc).respubs);
		}
	};

	static boolean hasSphinx() {
		try {
			return new ProcessBuilder("sphinx-build", "--version").redirectErrorStream(true).start().waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	@BeforeAll
	static void init() throws Exception {
		assumeTrue(hasSphinx(), "sphinx-build is not installed");

		new File("src/test/res/rbuild", DoctreeDBHelper.dbfile).delete();

		AnsonMsg.understandPorts(Port.echo);
		Configs.init(webinf);
		Connects.init(webinf);
		String conn = DoctreeDBHelper.connect(webinf, "../rbuild", "B");
		DatasetCfg.init(webinf);
		DoctreeDBHelper.setup(conn, true);

		builder = new HtmlBuilder(template);
		tier = new DoctreeTier(Port.echo)
				.conn(conn, "B")
				.importer((srcId) -> "src/test/res/media.docx", DocxWalker::blocks)
				.publisher(htmlsrv)
				.builder(builder);

		DoctreeReq imp = new DoctreeReq("/sdoc");
		imp.srcId = "media";
		imp.doc = new DocInf(null, null, null);
		imp.a(DoctreeReq.A.importDocx);
		docId = ((DoctreeResp) tier.handle(imp, ody).body(0)).doc.docId;
		tier.importer().await();

		picture = tier.db().res.dir("ody", docId).list()[0];
	}

	@Test
	void testCommitBuildPublish() throws Exception {
		// RST files that can't be built: no index.rst, and a file out of the source folder
		DoctreeReq bad = req(DoctreeReq.A.commit).rstFiles(new RstFile("../s1.rst", "Section 1\n=========\n"));
		assertEquals(HtmlBuilder.State.building, resp(tier.handle(bad, ody)).build);
		builder.await();
		DoctreeResp failed = resp(tier.handle(req(DoctreeReq.A.build), ody));
		assertEquals(HtmlBuilder.State.failed, failed.build);
		assertTrue(failed.buildlog.contains("Not a name of a RST file"), failed.buildlog);

		// committed without RST files: no build, nothing to publish
		assertNull(resp(tier.handle(req(DoctreeReq.A.commit), ody)).build);
		AnsonMsg<? extends AnsonResp> refused = tier.handle(req(DoctreeReq.A.publish), ody);
		assertEquals(MsgCode.exSemantic, refused.code());
		assertTrue(refused.body(0).msg().contains("isn't built"), refused.body(0).msg());

		// committed with the client's RST files: building in the background
		DoctreeReq commit = req(DoctreeReq.A.commit).rstFiles(
			new RstFile("index.rst", "Media doc\n=========\n\nHello.\n\n.. toctree::\n\n   s1\n"),
			new RstFile("s1.rst", "Section 1\n=========\n\n.. image:: media/" + picture + "\n", picture));
		assertEquals(HtmlBuilder.State.building, resp(tier.handle(commit, ody)).build);

		builder.await();

		DoctreeResp built = resp(tier.handle(req(DoctreeReq.A.build), ody));
		assertEquals(HtmlBuilder.State.built, built.build, built.buildlog);
		assertEquals("ody", built.doc.builtBy);
		// the template's id, of its manifest.json
		assertEquals("rtd-0.8", built.doc.template);
		assertTrue(built.doc.builtAt.compareTo(built.doc.committedAt) >= 0);

		// the package: the pages, the picture and the theme
		HashSet<String> entries = new HashSet<String>();
		try (ZipFile zip = new ZipFile(built.doc.htmlUri)) {
			Enumeration<? extends ZipEntry> es = zip.entries();
			while (es.hasMoreElements())
				entries.add(es.nextElement().getName());
		}
		assertTrue(entries.contains("index.html"));
		assertTrue(entries.contains("s1.html"));
		assertTrue(entries.contains("_images/" + picture), entries.toString());
		assertTrue(entries.contains("_static/theme-roles.css"));

		// and it can be published
		DocInf doc = resp(tier.handle(req(DoctreeReq.A.publish), ody)).doc;
		assertEquals("pub/x7k2p0", doc.pubpath);
		assertNotNull(doc.htmlUri);
	}

	static DoctreeReq req(String a) {
		DoctreeReq req = new DoctreeReq("/sdoc").docId(docId);
		req.a(a);
		return req;
	}

	static DoctreeResp resp(AnsonMsg<? extends AnsonResp> msg) {
		assertEquals(MsgCode.ok, msg.code(), msg.body(0).msg());
		return (DoctreeResp) msg.body(0);
	}
}
