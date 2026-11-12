package io.oz.sdoc.tier;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.tier.docs.Docs206;

/**
 * Range (http 206) serving of a doc's resource files, for {@link Doctree206Serv}:
 * semantic.jserv's {@link Docs206} with the file given, not resolved from a doc table.
 *
 * <pre>
 * file missing     → 404
 * If-Match fails   → 412
 * not modified     → 304
 * Range invalid    → 416
 * Range            → 206, the ranges
 * no Range         → 200, the whole file
 * </pre>
 *
 * @author ody
 */
public abstract class Doctree206 extends Docs206 {

	/**
	 * The request message, from the anson64 parameter, or the body.
	 *
	 * @return null if there is none
	 * @throws ClassCastException the body isn't a {@link DoctreeReq}
	 */
	@SuppressWarnings("unchecked")
	public static AnsonMsg<DoctreeReq> doctreeMsg(HttpServletRequest req) throws IOException {
		AnsonMsg<?> msg = ansonMsg(req);
		if (msg != null && !msg.body().isEmpty() && !(msg.body(0) instanceof DoctreeReq))
			throw new ClassCastException("Not a doc-tree request: " + msg.body(0).getClass().getName());
		return (AnsonMsg<DoctreeReq>) msg;
	}

	/**
	 * Answer the file, or the ranges of it the request asks for.
	 *
	 * @param req
	 * @param resp
	 * @param file
	 * @param tag identifies the file in its ETag, e.g. docId/name
	 * @param head headers only (http HEAD)
	 */
	public static void serve(HttpServletRequest req, HttpServletResponse resp, File file, String tag, boolean head)
			throws IOException {
		// an img (no-cors, no Origin) and a fetch (cors) of the same url get different answers:
		// the CORS filter adds Access-Control-Allow-Origin only with an Origin
		resp.addHeader("Vary", "Origin");

		Resource r = new Resource(file, tag);
		if (r.file == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if (preconditionFailed(req, r)) {
			resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return;
		}

		setCacheHeaders(resp, r);
		if (notModified(req, r)) {
			resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}

		List<Range> ranges = getRanges(req, r);
		if (ranges == null) {
			resp.setHeader(JProtocol.Headers.Content_range, "bytes */" + r.length);
			resp.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
			return;
		}

		if (!ranges.isEmpty())
			resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
		else
			ranges.add(new Range(0, r.length - 1));

		String contentType = setContentHeaders(req, resp, r, ranges);
		if (!head)
			writeContent(resp, r, ranges, contentType);
	}
}
