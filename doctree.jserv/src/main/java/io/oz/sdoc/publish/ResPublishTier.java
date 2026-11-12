package io.oz.sdoc.publish;

import java.io.IOException;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.DoctreeDBHelper;
import io.oz.sdoc.publish.ResPublishReq.A;
import io.oz.sdoc.tier.DoctreeTier;

/**
 * The synode's port for the local html-service: {@link A#refresh} answers the docs published
 * for html-service to restore / update its context paths.
 * 
 * No session, localhost only.
 *
 * <p>The port is the app's, as {@link DoctreeTier}'s. Its url must be {@link #url}.</p>
 * 
 * @since 0.1.2
 */
@WebServlet(description = "semantic-doc, docs published at html-service", urlPatterns = { "/respub.less" })
public class ResPublishTier extends ServPort<ResPublishReq> {
	private static final long serialVersionUID = 1L;

	/** url of the port, without the leading '/' */
	public static final String url = "respub.less";

	final DoctreeTier doctree;

	/**
	 * @param port the app's port, with {@link #url}
	 * @param doctree the tier with the working copy
	 */
	public ResPublishTier(IPort port, DoctreeTier doctree) {
		super(port);
		this.doctree = doctree;
	}

	@Override
	protected void onGet(AnsonMsg<ResPublishReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {
		onPost(msg, resp);
	}

	@Override
	protected void onPost(AnsonMsg<ResPublishReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {
		if (!InetAddress.getByName(msg.addr()).isLoopbackAddress())
			throw new SemanticException("Hello, %s !", msg.addr());

		write(resp, handle(msg.body(0)));
	}

	/**
	 * Serve a request from the local html-service.
	 * @param req
	 * @return response message, body: {@link ResPublishResp}
	 */
	public AnsonMsg<? extends AnsonResp> handle(ResPublishReq req) {
		try {
			if (!A.refresh.equals(req.a()))
				throw new SemanticException("Res-publish tier: request.a, %s, can not be handled.", req.a());

			DoctreeDBHelper db = doctree.db();
			return ok(new ResPublishResp().respubs(db == null ? new ArrayList<DocInf>() : db.published()));
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
