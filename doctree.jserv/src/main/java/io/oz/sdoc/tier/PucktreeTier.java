package io.oz.sdoc.tier;

import static io.odysz.common.LangExt.eq;

import java.io.IOException;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.docx.DocxInf;
import io.oz.sdoc.docx.DocxtreeDBHelper;
import io.oz.sdoc.publish.PuckPubReq;
import io.oz.sdoc.publish.PuckPubResp;
import io.oz.sdoc.publish.PuckPubReq.A;
import io.oz.sdoc.puck.PucktreeDBHelper;

@WebServlet(description = "semantic-doc doc-tree tier", urlPatterns = { "/pucktree.serv" })
public class PucktreeTier extends ServPort<PucktreeReq> {

	public PucktreeTier(IPort port) {
		super(port);
		// TODO Auto-generated constructor stub
	}

	private static final long serialVersionUID = 1L;

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
	 * @param req
	 * @return response message, body: {@link PuckPubResp}
	 */
	public AnsonMsg<? extends AnsonResp> handle(PucktreeReq req) {
		try {
			if (!A.refresh.equals(req.a()))
				throw new SemanticException("Res-publish tier: request.a, %s, can not be handled.", req.a());

			PucktreeDBHelper db;
			if (db == null)
				return ok(new PuckPubResp().respubs(new ArrayList<PuckPage>()));

			// the published docs, and the media maps of the stories among them
			ArrayList<PuckPage> pubs = db.published();
			for (DocxInf d : pubs)
				if (eq(DocxInf.Kind.story, d.kind))
					; // media.add(new PubMedia(d.docId, d.pubpath, db.stories.mediaPaths(d.docId, doctree.media())));

			return ok(new PuckPubResp().respubs(pubs).media(media));
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
