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
import io.oz.sdoc.imports.DocxSource;
import io.oz.sdoc.imports.IBlockParser;
import io.oz.sdoc.imports.Importer;

/**
 * The doc-tree tier: the service semantic-doc's editor (AnDoctreeditor) talks to.
 *
 * <p>The port is the app's, e.g. jserv-album's AlbumPort.doctree, so this lib
 * doesn't depend on the app's port enum. Its url must be {@link #url}.</p>
 *
 * <p>The working copy is in its own db ({@link DoctreeDBHelper}), connection {@link #conn(String, String)},
 * set up at the first request.
 * Served: {@link DoctreeReq.A#docs}, {@link DoctreeReq.A#importDocx}
 * (with {@link #importer(DocxSource, IBlockParser)}); other requests are answered with an error.</p>
 *
 * TODO: verify sessions; no session check for now.
 *
 * @author ody
 */
@WebServlet(description = "semantic-doc doc-tree tier", urlPatterns = { "/doctree.less" })
public class DoctreeTier extends ServPort<DoctreeReq> {
	private static final long serialVersionUID = 1L;

	/** url of the port, without the leading '/' */
	public static final String url = "doctree.less";

	/** connection of the working copy, null: not configured (only {@link DoctreeReq.A#docs}, empty) */
	String conn;
	/** for doc ids */
	String synode;
	DocxSource source;
	IBlockParser parser;

	/** created by {@link #db()} at the first request needing it */
	DoctreeDBHelper db;
	Importer importer;

	public DoctreeTier(IPort port) {
		super(port);
	}

	/**
	 * Configure the working copy. Its tables are created / upgraded at the first request
	 * needing them ({@link #db()}), so a synode never editing doc trees keeps an empty db file,
	 * and a failure here never stops the synode.
	 * 
	 * @param conn connection of the doc-tree db, e.g. "doctree.VOLUME_HOME";
	 * its file must exist before Connects.init(), see {@link DoctreeDBHelper#touch(String, String)}
	 * @param synode id of this synode, the prefix of doc ids
	 * @return this
	 */
	public DoctreeTier conn(String conn, String synode) {
		this.conn = conn;
		this.synode = synode;
		return this;
	}

	/**
	 * Enable {@link DoctreeReq.A#importDocx}.
	 * 
	 * @param source where the docx files are, e.g. {@link DocxSource#extfiles(String, String)}
	 * @param parser e.g. {@link io.oz.sdoc.imports.DocxWalker#blocks(String)}
	 * @return this
	 */
	public DoctreeTier importer(DocxSource source, IBlockParser parser) {
		this.source = source;
		this.parser = parser;
		return this;
	}

	/**
	 * The working copy, set up at the first call.
	 * 
	 * @return null if not configured
	 * @throws Exception
	 */
	synchronized DoctreeDBHelper db() throws Exception {
		if (db == null && conn != null) {
			db = DoctreeDBHelper.setup(conn);
			if (parser != null)
				importer = new Importer(conn, db, source, parser);
		}
		return db;
	}

	public Importer importer() { return importer; }

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

		try {
			if (DoctreeReq.A.docs.equals(a))
				return ok(new DoctreeResp().docs(db() == null ? new ArrayList<DocInf>() : db.docs()));

			if (DoctreeReq.A.importDocx.equals(a)) {
				if (db() == null || importer == null)
					return err(MsgCode.exGeneral, "Doc-tree tier: importing isn't configured.");
				String title = req.doc == null ? null : req.doc.title;
				return ok(new DoctreeResp().doc(importer.start(synode, req.srcId, title, null)));
			}
		} catch (Exception e) {
			e.printStackTrace();
			return err(MsgCode.exGeneral, "Doc-tree tier: %s", e.getMessage());
		}

		return err(MsgCode.exGeneral, "Doc-tree tier: request %s is not served yet.", a);
	}
}
