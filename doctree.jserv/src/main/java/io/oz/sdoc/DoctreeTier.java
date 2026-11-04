package io.oz.sdoc;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.ServPort;

/**
 * The doc-tree tier: the service semantic-doc's editor (AnDoctreeditor) talks to.
 *
 * <p>The port is the app's, e.g. jserv-album's AlbumPort.doctree, so this lib
 * doesn't depend on the app's port enum. Its url must be {@link #url}.</p>
 *
 * <p>Not wired to doc data yet: {@link DoctreeReq.A#docs} answers an empty list,
 * every other request an error.</p>
 *
 * TODO: verify sessions; no session check for now.
 *
 * @author odys-z@github.com
 */
@WebServlet(description = "semantic-doc doc-tree tier", urlPatterns = { "/doctree.less" })
public class DoctreeTier extends ServPort<DoctreeReq> {
	private static final long serialVersionUID = 1L;

	/** url of the port, without the leading '/' */
	public static final String url = "doctree.less";

	public DoctreeTier(IPort port) {
		super(port);
	}

	@Override
	protected void onGet(AnsonMsg<DoctreeReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		write(resp, handle(msg.body(0)));
	}

	@Override
	protected void onPost(AnsonMsg<DoctreeReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		write(resp, handle(msg.body(0)));
	}

	/**
	 * Serve a request.
	 *
	 * @param req
	 * @return response message
	 */
	public AnsonMsg<? extends AnsonResp> handle(DoctreeReq req) {
		String a = req == null ? null : req.a();

		if (DoctreeReq.A.docs.equals(a))
			return ok(new DoctreeResp().docs(new ArrayList<DocInf>()));

		return err(MsgCode.exGeneral, "Doc-tree tier: request %s is not served yet.", a);
	}
}
