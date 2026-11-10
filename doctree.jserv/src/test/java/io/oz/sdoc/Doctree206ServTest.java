package io.oz.sdoc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.IUser;
import io.oz.sdoc.docx.DocxWalker;

/**
 * GETs of a doc's picture, imported from media.docx by "ody", through {@link Doctree206Serv},
 * with fake servlet requests / responses. Its own db: src/test/res/r206/doctree.db.
 */
class Doctree206ServTest {
	static final String webinf = "src/test/res/WEB-INF";

	static Doctree206Serv serv;
	static String docId;
	static byte[] png;

	/** sessions: ssid → user */
	static final HashMap<String, IUser> sessions = new HashMap<String, IUser>();

	@BeforeAll
	static void init() throws Exception {
		new File("src/test/res/r206", DoctreeDBHelper.dbfile).delete();
		AnsonMsg.understandPorts(Port.echo); // the app's ports, for parsing anson64
		Configs.init(webinf);
		Connects.init(webinf);
		String conn = DoctreeDBHelper.connect(webinf, "../r206", "R");
		DatasetCfg.init(webinf);
		DoctreeDBHelper.setup(conn, true);

		sessions.put("ss-ody", user("ody"));
		sessions.put("ss-zsu", user("zsu"));

		DoctreeTier tier = new DoctreeTier(Port.echo)
				.conn(conn, "R")
				.importer((srcId) -> "src/test/res/media.docx", DocxWalker::blocks);
		DoctreeReq imp = new DoctreeReq("/sdoc");
		imp.srcId = "media";
		imp.doc = new DocInf(null, null, null);
		imp.a(DoctreeReq.A.importDocx);
		docId = ((DoctreeResp) tier.handle(imp, sessions.get("ss-ody")).body(0)).doc.docId;
		tier.importer().await();

		png = Files.readAllBytes(tier.db().res.file("ody", docId, "image1.png").toPath());
		assertTrue(png.length > 20);

		serv = new Doctree206Serv(Port.echo, tier) {
			private static final long serialVersionUID = 1L;
			{
				verifier = new ISessionVerifier() {
					@Override
					public IUser verify(AnsonHeader h, int... seq) throws SsException {
						IUser u = h == null ? null : sessions.get(h.ssid());
						if (u == null) throw new SsException("No such session.");
						return u;
					}
				};
			}
		};
	}

	@Test
	void whole() throws Exception {
		Resp r = get("ss-ody", "image1.png", null);
		assertEquals(200, r.status);
		assertArrayEquals(png, r.body());
		assertEquals("image/png", r.contentType);
		assertEquals("bytes", r.headers.get("Accept-Ranges"));
		assertTrue(r.headers.containsKey("ETag"));
		assertEquals("Origin", r.headers.get("Vary"));
	}

	@Test
	void range() throws Exception {
		Resp r = get("ss-ody", "image1.png", "bytes=0-9");
		assertEquals(206, r.status);
		assertArrayEquals(Arrays.copyOfRange(png, 0, 10), r.body());
		assertEquals("bytes 0-9/" + png.length, r.headers.get("Content-Range"));

		// resuming from a break point
		r = get("ss-ody", "image1.png", "bytes=10-");
		assertEquals(206, r.status);
		assertArrayEquals(Arrays.copyOfRange(png, 10, png.length), r.body());

		// HEAD: the headers only
		r = call(true, "ss-ody", "image1.png", "bytes=0-9");
		assertEquals(206, r.status);
		assertEquals("bytes 0-9/" + png.length, r.headers.get("Content-Range"));
		assertEquals(0, r.body().length);

		assertEquals(416, get("ss-ody", "image1.png", "bytes=999999-").status);
	}

	@Test
	void refused() throws Exception {
		assertEquals(401, get("no-session", "image1.png", null).status);
		assertEquals(403, get("ss-zsu", "image1.png", null).status); // not the creator
		assertEquals(404, get("ss-ody", "none.png", null).status);
		assertEquals(400, get("ss-ody", "../doctree.db", null).status); // not a file name

		// not r/res
		DoctreeReq q = new DoctreeReq("/sdoc").docId(docId);
		q.a(DoctreeReq.A.docs);
		assertEquals(400, call(false, anson64(q, "ss-ody"), null).status);
		assertEquals(400, call(false, "bm90IGpzb24=", null).status); // "not json"
	}

	/** GET a resource of the doc */
	static Resp get(String ssid, String res, String range) throws Exception {
		return call(false, ssid, res, range);
	}

	static Resp call(boolean head, String ssid, String res, String range) throws Exception {
		DoctreeReq q = new DoctreeReq("/sdoc").res(docId, res);
		q.a(DoctreeReq.A.res);
		return call(head, anson64(q, ssid), range);
	}

	static Resp call(boolean head, String anson64, String range) throws Exception {
		HashMap<String, String> reqHeaders = new HashMap<String, String>();
		if (range != null) reqHeaders.put("Range", range);

		Resp r = new Resp();
		if (head) serv.doHead(request(anson64, reqHeaders), r.proxy());
		else serv.doGet(request(anson64, reqHeaders), r.proxy());
		return r;
	}

	/** the request as an img's src has it: ?anson64= */
	static String anson64(DoctreeReq q, String ssid) throws Exception {
		AnsonMsg<DoctreeReq> msg = new AnsonMsg<DoctreeReq>(Port.echo)
				.header(new AnsonHeader(ssid, null, null))
				.body(q);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.toBlock(bos);
		return Base64.getEncoder().encodeToString(bos.toByteArray());
	}

	static IUser user(String uid) {
		return new JRobot() {
			@Override public String uid() { return uid; }
		};
	}

	static HttpServletRequest request(String anson64, HashMap<String, String> headers) {
		ServletContext ctx = (ServletContext) Proxy.newProxyInstance(Doctree206ServTest.class.getClassLoader(),
			new Class<?>[] { ServletContext.class }, (p, m, args) ->
				"getMimeType".equals(m.getName()) && String.valueOf(args[0]).endsWith(".png") ? "image/png" : null);

		return (HttpServletRequest) Proxy.newProxyInstance(Doctree206ServTest.class.getClassLoader(),
			new Class<?>[] { HttpServletRequest.class }, (p, m, args) -> {
				switch (m.getName()) {
				case "getParameter": return "anson64".equals(args[0]) ? anson64 : null;
				case "getHeader": return headers.get(args[0]);
				case "getDateHeader": return -1L;
				case "getContentLength": return 0;
				case "getServletContext": return ctx;
				default: return defaultOf(m.getReturnType());
				}
			});
	}

	static Object defaultOf(Class<?> t) {
		if (t == boolean.class) return false;
		if (t == int.class) return 0;
		if (t == long.class) return 0L;
		return null;
	}

	/** A response, collecting status, headers and the body. */
	static class Resp {
		int status = 200;
		boolean committed;
		String contentType;
		final HashMap<String, String> headers = new HashMap<String, String>();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();

		byte[] body() { return out.toByteArray(); }

		HttpServletResponse proxy() {
			ServletOutputStream os = new ServletOutputStream() {
				@Override public void write(int b) { out.write(b); }
				@Override public boolean isReady() { return true; }
				@Override public void setWriteListener(WriteListener l) { }
			};
			return (HttpServletResponse) Proxy.newProxyInstance(Doctree206ServTest.class.getClassLoader(),
				new Class<?>[] { HttpServletResponse.class }, (p, m, args) -> {
					switch (m.getName()) {
					case "sendError": status = (Integer) args[0]; committed = true; return null;
					case "setStatus": status = (Integer) args[0]; return null;
					case "getStatus": return status;
					case "isCommitted": return committed;
					case "setHeader": case "addHeader": headers.put((String) args[0], String.valueOf(args[1])); return null;
					case "setDateHeader": case "addDateHeader": headers.put((String) args[0], String.valueOf(args[1])); return null;
					case "setContentType": contentType = (String) args[0]; return null;
					case "getOutputStream": return os;
					default: return defaultOf(m.getReturnType());
					}
				});
		}
	}
}
