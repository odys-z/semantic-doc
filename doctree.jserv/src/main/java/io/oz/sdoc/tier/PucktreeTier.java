package io.oz.sdoc.tier;

import static io.odysz.common.LangExt.eq;

import java.io.IOException;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.docx.RstreeDBHelper;

/**
 * The synode's port for the local html-service, queried for the pages published there
 * ({@link DoctreeReq.A#docs}, by io.oz.sdoc.htmlsrv.ResPathsTier#queryPublishs()), when html-service
 * started and from time to time. No session, localhost only.
 *
 * <p>The answer is all the published pages, the puck tree's and the rst tree's, as html-service
 * removes the pages not in it. Both are of the working copy of {@link DoctreeTier}.</p>
 *
 * <p>The port is the app's, e.g. SynDocollPort.pucktree. Its url must be {@link #url}.</p>
 */
@WebServlet(description = "semantic-doc puck-tree tier", urlPatterns = { "/pucktree.serv" })
public class PucktreeTier extends ServPort<PucktreeReq> {
	private static final long serialVersionUID = 1L;

	/** url of the port, without the leading '/' */
	public static final String url = "pucktree.serv";

	final DoctreeTier doctree;

	/**
	 * @param port the app's port, with {@link #url}
	 * @param doctree the tier with the working copy
	 * @throws SemanticException the port's url is not {@link #url}
	 */
	public PucktreeTier(IPort port, DoctreeTier doctree) throws SemanticException {
		super(port);
		if (port == null || !eq(url, port.url()))
			throw new SemanticException("The port of PucktreeTier must be at url %s, but is %s.",
					url, port == null ? null : port.url());
		this.doctree = doctree;
	}

	@Override
	protected void onGet(AnsonMsg<PucktreeReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {
		onPost(msg, resp);
	}

	@Override
	protected void onPost(AnsonMsg<PucktreeReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {
		if (!InetAddress.getByName(msg.addr()).isLoopbackAddress())
			throw new SemanticException("Hello, %s !", msg.addr());

		write(resp, handle(msg.body(0)));
	}

	/**
	 * Serve a request from the local html-service.
	 * @param req a = {@link DoctreeReq.A#docs}
	 * @return response message, body: {@link DoctreeResp#docs}, the published pages; empty if the
	 * working copy isn't configured
	 */
	public AnsonMsg<? extends AnsonResp> handle(PucktreeReq req) {
		try {
			if (req == null || !eq(DoctreeReq.A.docs, req.a()))
				throw new SemanticException("Puck-tree tier: request.a, %s, can not be handled.", req == null ? null : req.a());

			ArrayList<SDocRef> pubs = new ArrayList<SDocRef>();
			RstreeDBHelper rst = doctree.db();
			if (rst != null) {
				pubs.addAll(doctree.puck().published());
				pubs.addAll(rst.published());
			}
			return ok(new DoctreeResp().docs(pubs));
		} catch (SemanticException e) {
			return err(MsgCode.exSemantic, e.getMessage());
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			return err(MsgCode.exTransct, e.getMessage());
		} catch (Throwable e) {
			e.printStackTrace();
			return err(MsgCode.exGeneral, "%s\n%s", e.getClass().getName(), e.getMessage());
		}
	}
}
