package io.oz.sdoc;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.DA.DatasetHelper;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
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
 * (with {@link #importer(DocxSource, IBlockParser)}), {@link DoctreeReq.A#tree}
 * (tree semantics {@link #sk} in the app's dataset.xml); the other actions of {@link DoctreeReq.A}
 * have their handlers, not implemented yet: they answer exSemantic, naming the action.</p>
 *
 * TODO: verify sessions; no session check for now.
 *
 * @author ody
 */
@WebServlet(description = "semantic-doc doc-tree tier", urlPatterns = { "/doctree.jserv" })
public class DoctreeTier extends ServPort<DoctreeReq> {
	private static final long serialVersionUID = 1L;

	/**
	 * The doc tree's semantics in the app's dataset.xml (only its s-tree is used):
	 * <pre>&lt;s-tree&gt;,s_docnodes,nid,parentId,fullpath,sort,ptext text,kind,false&lt;/s-tree&gt;</pre>
	 * ptext is sent as text, the field the client's tree shows.
	 */
	public static final String sk = "sdoc.tree";

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
	 * @param conn connection of the doc-tree db, see {@link DoctreeDBHelper#connect(String, String, String)}
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
			db = DoctreeDBHelper.setup(conn, false);
			if (parser != null)
				importer = new Importer(conn, db, source, parser);
		}
		return db;
	}

	public Importer importer() { return importer; }

	@Override
	protected void onGet(AnsonMsg<DoctreeReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		onPost(msg, resp);
	}

	@Override
	protected void onPost(AnsonMsg<DoctreeReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			write(resp, handle(msg.body(0)));
		} finally {
			resp.flushBuffer();
		}
	}

	/**
	 * Serve a request: the handler of its a() answers a {@link DoctreeResp}, or throws.
	 * Exceptions are answered as in docsync's ExpDoctier, for the client to report:
	 * SemanticException → exSemantic (a request that can't be served, the message for users),
	 * SQLException, TransException → exTransct, anything else → exGeneral.
	 *
	 * @param req
	 * @return response message
	 */
	public AnsonMsg<? extends AnsonResp> handle(DoctreeReq req) {
		try {
			String a = req == null ? null : req.a();
			DoctreeResp rsp;

			if (DoctreeReq.A.docs.equals(a))
				rsp = docs(req);
			else if (DoctreeReq.A.importDocx.equals(a))
				rsp = importDocx(req);
			else if (DoctreeReq.A.tree.equals(a))
				rsp = tree(req);
			else if (DoctreeReq.A.update.equals(a))
				rsp = update(req);
			else if (DoctreeReq.A.move.equals(a))
				rsp = move(req);
			else if (DoctreeReq.A.insert.equals(a))
				rsp = insert(req);
			else if (DoctreeReq.A.del.equals(a))
				rsp = del(req);
			else if (DoctreeReq.A.commit.equals(a))
				rsp = commit(req);
			else if (DoctreeReq.A.createDoc.equals(a))
				rsp = createDoc(req);
			else
				throw new SemanticException("Doc-tree tier: request.a, %s, can not be handled.", a);

			return ok(rsp);
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

	/** r/docs: the docs' headers → docs (empty if the working copy isn't configured) */
	DoctreeResp docs(DoctreeReq req) throws Exception {
		return new DoctreeResp().docs(db() == null ? new ArrayList<DocInf>() : db.docs());
	}

	/** c/import: the doc header (state parsing) at once; the nodes are parsed in the background → doc */
	DoctreeResp importDocx(DoctreeReq req) throws Exception {
		if (db() == null || importer == null)
			throw new SemanticException("Doc-tree tier: importing isn't configured.");
		String title = req.doc == null ? null : req.doc.title;
		return new DoctreeResp().doc(importer.start(synode, req.srcId, title, null));
	}

	/** r/tree: the doc root {@link DoctreeReq#docId} with its subtree → doc, forest */
	DoctreeResp tree(DoctreeReq req) throws Exception {
		if (db() == null)
			throw new SemanticException("Doc-tree tier: the working copy isn't configured.");
		DocInf doc = db.doc(req.docId);
		if (doc == null)
			throw new SemanticException("Doc-tree tier: no such doc: %s", req.docId);
		TreeSemantics sm = DatasetCfg.getTreeSemtcs(sk);
		if (sm == null)
			throw new SemanticException("Doc-tree tier: the tree semantics, %s, isn't configured in dataset.xml.", sk);

		// while importing (state parsing), the forest is empty; the client asks again
		DoctreeResp rsp = new DoctreeResp().doc(doc);
		rsp.forest(DatasetHelper.buildForest(db.nodes(req.docId), sm));
		rsp.root(null);
		return rsp;
	}

	/** u/node: save {@link DoctreeReq#node}'s json, rst, rstver; the doc gets dirty → doc */
	DoctreeResp update(DoctreeReq req) throws Exception {
		throw notyet(req);
	}

	/** u/move: rootId under toParent at toIndex → root, forest, rejected */
	DoctreeResp move(DoctreeReq req) throws Exception {
		throw notyet(req);
	}

	/** i: a new node under toParent at toIndex, columns nvs → newId, root, forest */
	DoctreeResp insert(DoctreeReq req) throws Exception {
		throw notyet(req);
	}

	/** d: rootId with its subtree → root, forest */
	DoctreeResp del(DoctreeReq req) throws Exception {
		throw notyet(req);
	}

	/** c/commit: the doc isn't dirty, committedAt = now → doc */
	DoctreeResp commit(DoctreeReq req) throws Exception {
		throw notyet(req);
	}

	/** c/doc: a new doc header with its root node → doc */
	DoctreeResp createDoc(DoctreeReq req) throws Exception {
		throw notyet(req);
	}

	/** the error of a handler not implemented yet, naming the request's a() */
	static SemanticException notyet(DoctreeReq req) {
		return new SemanticException("Doc-tree tier: %s is not implemented yet.", req.a());
	}
}
