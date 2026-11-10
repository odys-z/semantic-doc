package io.oz.sdoc;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;

/**
 * The docs' resource files (pictures, videos) for the doc-tree editor and its preview, with
 * break points (http 206): a GET of {@link DoctreeReq.A#res}, as anson64, e.g. an img's src.
 *
 * <p>For editing only: the files are in the creator's temp folder ({@link DocRes}), read by the
 * creator's sessions. A published doc's html links its resources as static, relative files.</p>
 *
 * <p>Independent of jserv's 206 service: doGet() and doHead() are this port's, a Range header
 * never reaches semantic.jserv's {@link io.odysz.semantic.tier.docs.Docs206#get206v2}.</p>
 *
 * <pre>
 * no / bad request  → 400, e.g. anson64 can't be parsed
 * no session        → 401
 * not the creator   → 403
 * no doc, no file   → 404
 * else              → {@link Doctree206#serve}: 200, 206, 304, 412, 416
 * </pre>
 *
 * The app registers it beside the {@link DoctreeTier} whose working copy it reads,
 * with a port of its own, url {@link #url}.
 *
 * @author ody
 */
@WebServlet(description = "semantic-doc doc resources, for editing", urlPatterns = { "/doctree206.serv" })
public class Doctree206Serv extends ServPort<DoctreeReq> {
	private static final long serialVersionUID = 1L;

	/** url of the port, without the leading '/' */
	public static final String url = "doctree206.serv";

	final DoctreeTier tier;

	/**
	 * @param port the app's port of this servlet
	 * @param tier the doc-tree tier, the working copy of which is read
	 */
	public Doctree206Serv(IPort port, DoctreeTier tier) {
		super(port);
		this.tier = tier;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		serve(req, resp, false);
	}

	@Override
	protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		serve(req, resp, true);
	}

	void serve(HttpServletRequest req, HttpServletResponse resp, boolean head) throws IOException {
		try {
			AnsonMsg<DoctreeReq> msg;
			try { msg = Doctree206.doctreeMsg(req); }
			catch (Exception e) { // anson64 can't be parsed, or isn't a doc-tree request
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
				return;
			}

			DoctreeReq r = msg == null || msg.body().isEmpty() ? null : msg.body(0);
			if (r == null || !DoctreeReq.A.res.equals(r.a()) || isblank(r.docId) || isblank(r.res)) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a request of a doc's resource.");
				return;
			}

			IUser usr;
			try { usr = verifier().verify(msg.header()); }
			catch (SsException e) {
				resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
				return;
			}

			DoctreeDBHelper db = tier.db();
			DocInf doc = db == null || db.res == null ? null : db.doc(r.docId);
			if (doc == null) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			if (!eq(usr.uid(), doc.creator)) {
				resp.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}

			File file;
			try { file = db.res.file(doc.creator, doc.docId, r.res); }
			catch (IOException e) { // not a file name
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
				return;
			}

			Doctree206.serve(req, resp, file, doc.docId + "/" + r.res, head);
		} catch (Exception e) {
			Utils.warnT(new Object() {}, "Serving a doc's resource failed: %s", e.getMessage());
			e.printStackTrace();
			if (!resp.isCommitted())
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	/** Only GETs of {@link DoctreeReq.A#res}, handled by {@link #doGet(HttpServletRequest, HttpServletResponse)}. */
	@Override
	protected void onGet(AnsonMsg<DoctreeReq> msg, HttpServletResponse resp) throws IOException {
		write(resp, err(MsgCode.exSemantic, "Doc resources: GET %s only.", DoctreeReq.A.res));
	}

	@Override
	protected void onPost(AnsonMsg<DoctreeReq> msg, HttpServletResponse resp) throws IOException {
		write(resp, err(MsgCode.exSemantic, "Doc resources: GET %s only.", DoctreeReq.A.res));
	}
}
