package io.oz.sdoc.htmlsrv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.odysz.anson.Anson;
import io.odysz.semantic.jserv.WebConfig;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.tier.DoctreeResp;

/**
 * The index page of the published docs. The synode's published docs, answered to the tier's
 * query, are {@link #synodocs}; null: the synode isn't reachable.
 */
class ResIndexTest {
	@TempDir
	static Path tmp;

	static Server server;
	static String root;
	static ResPathsTier tier;

	/** The docs published, as the synode keeps them. */
	static ArrayList<SDocRef> synodocs = new ArrayList<SDocRef>();

	@BeforeAll
	static void startServer() throws Exception {
		// a configured static path, as HtmlServer.multiReserver() does
		Path home = Files.createDirectories(tmp.resolve("home"));
		Files.writeString(home.resolve("index.html"), "home");

		server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setHost("127.0.0.1");
		connector.setPort(0);
		server.addConnector(connector);

		ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
		ctx.setContextPath("/");
		ctx.setWelcomeFiles(new String[] {"index.html"});
		ServletHolder holder = new ServletHolder("servlet0", DefaultServlet.class);
		holder.setInitParameter("resourceBase", home.toString());
		holder.setInitParameter("pathInfoOnly", "true");
		ctx.addServlet(holder, "/");
		server.setHandler(ctx);

		String tmpdir = tmp.toString().replace("\\", "/");
		WebConfig wcfg = (WebConfig) Anson.fromJson("{"
				+ "\"type\": \"io.odysz.semantic.jserv.WebConfig\", \"port\": 8901,"
				+ "\"paths\": [{\"path\": \"/*\", \"resource\": \"" + tmpdir + "/home\"}],"
				+ "\"dynaResource\": {\"handler\": \"io.oz.sdoc.htmlsrv.ResPathsTier\", \"syn_jserv\": \"http://127.0.0.1:8964/jserv-album\", \"pollInterval_snd\": 0,"
				+ "                   \"resource\": \"" + tmpdir + "/unzip\", \"danyroot\": \"pub\"}"
				+ "}");
		tier = new ResPathsTier(HtmlTestPort.respaths, HtmlTestPort.pucktree, server) {
			private static final long serialVersionUID = 1L;
			@Override
			DoctreeResp queryPublishs() throws IOException {
				if (synodocs == null)
					throw new IOException("Connection refused");
				return new DoctreeResp().docs(synodocs);
			}
		};
		tier.onUpdate(wcfg);
		server.start();

		root = "http://127.0.0.1:" + connector.getLocalPort();
	}

	@AfterAll
	static void stopServer() throws Exception {
		server.stop();
	}

	@Test
	void testIndex() throws Exception {
		// nothing published
		synodocs = docs();
		assertTrue(index().contains("No docs published."));
		assertEquals("home", get("/index.html").body());

		// published packages, with the synode's titles and creators; sorted by titles, texts escaped
		SDocRef db = doc("db", "Doc <b> & \"c\"", "pub/b001", zip("b-1.zip", "b 1"), "2026-11-16 02:00:00");
		SDocRef da = doc("da", "A doc 文档", "pub/a001", zip("a-1.zip", "a 1"), "2026-11-16 01:00:00");
		synodocs = docs(db, da);
		tier.reconcile(new DoctreeResp().docs(synodocs));
		String page = index();
		String a = "<li><a href=\"/pub/a001/\">A doc 文档</a><span class=\"creator\">ody</span></li>";
		String b = "<li><a href=\"/pub/b001/\">Doc &lt;b&gt; &amp; &quot;c&quot;</a><span class=\"creator\">ody</span></li>";
		assertTrue(page.contains(a), page);
		assertTrue(page.contains(b), page);
		assertTrue(page.indexOf(a) < page.indexOf(b), page);
		assertFalse(page.contains("No docs published."));

		// the docs themselves
		assertEquals("a 1", get("/pub/a001/").body());
		assertEquals("b 1", get("/pub/b001/index.html").body());

		// the page's urls
		HttpResponse<String> resp = get("/pub/index.html");
		assertEquals(200, resp.statusCode());
		assertTrue(resp.headers().firstValue("Content-Type").get().startsWith("text/html"));
		assertTrue(resp.body().contains(a));
		resp = get("/pub");
		assertEquals(302, resp.statusCode());
		assertTrue(resp.headers().firstValue("Location").get().endsWith("/pub/"));

		// a001 is no longer published, by the synode's records:
		// the synode isn't asked again within ResPathsTier.indexFresh
		synodocs = docs(db);
		assertTrue(get("/pub/").body().contains(a));

		// then it's not listed; the page doesn't change the paths, the next refresh does
		page = index();
		assertFalse(page.contains("a001"), page);
		assertTrue(page.contains(b), page);
		assertEquals("a 1", get("/pub/a001/").body());
		tier.reconcile(new DoctreeResp().docs(synodocs));
		assertEquals(404, get("/pub/a001/").statusCode());

		synodocs = docs();
		tier.reconcile(new DoctreeResp().docs(synodocs));
		assertTrue(index().contains("No docs published."));
	}

	/**
	 * The synode isn't reachable, e.g. not started: the docs are the local folders, still
	 * served after html-service restarted, with the titles of their index pages.
	 */
	@Test
	void testIndexLocal() throws Exception {
		Path kept = Files.createDirectories(tier.resroot.resolve("k001"));
		Files.writeString(kept.resolve("index.html"),
				"<html><head><meta charset=\"utf-8\">\n<TITLE>Kept &amp; served\n  &lt;1&gt; &#8212; semantic-doc documentation</TITLE></head><body>kept 1</body></html>");
		Path untitled = Files.createDirectories(tier.resroot.resolve("k002"));
		Files.writeString(untitled.resolve("readme.txt"), "no index");

		ArrayList<SDocRef> docs = synodocs;
		try {
			synodocs = null;
			String page = index();
			assertTrue(page.contains("<li><a href=\"/pub/k001/\">Kept &amp; served &lt;1&gt;</a></li>"), page);
			assertTrue(page.contains("<li><a href=\"/pub/k002/\">pub/k002</a></li>"), page);
			assertTrue(get("/pub/k001/").body().contains("kept 1"));
		}
		finally {
			synodocs = docs;
		}

		// the synode is back, without these docs: not listed, and removed by the refresh
		String page = index();
		assertFalse(page.contains("k001"), page);
		assertFalse(page.contains("k002"), page);
		tier.reconcile(new DoctreeResp().docs(synodocs));
		assertEquals(404, get("/pub/k001/").statusCode());
	}

	/** @return the index page, with the synode asked */
	static String index() throws Exception {
		tier.indexAsked = 0;
		HttpResponse<String> resp = get("/pub/");
		assertEquals(200, resp.statusCode());
		return resp.body();
	}

	static HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(root + path)).GET().build(),
				BodyHandlers.ofString());
	}

	static SDocRef doc(String docId, String title, String pubpath, String htmlUri, String builtAt) {
		SDocRef d = new SDocRef(docId, title, "ody");
		d.pubpath = pubpath;
		d.htmlUri = htmlUri;
		d.builtAt = builtAt;
		return d;
	}

	static ArrayList<SDocRef> docs(SDocRef... docs) {
		return new HtmlPubReq().respubs(docs).respubs;
	}

	static String zip(String name, String indexHtml) throws Exception {
		Path z = tmp.resolve(name);
		try (OutputStream out = Files.newOutputStream(z);
			 ZipOutputStream zout = new ZipOutputStream(out)) {
			zout.putNextEntry(new ZipEntry("index.html"));
			zout.write(indexHtml.getBytes());
			zout.closeEntry();
		}
		return z.toString();
	}
}
