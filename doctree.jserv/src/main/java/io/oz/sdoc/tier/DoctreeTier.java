package io.oz.sdoc.tier;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.DA.DatasetHelper;
import io.odysz.semantic.ext.AnDatasetResp;
import io.odysz.semantic.ext.SemanticTree;
import io.odysz.semantic.ext.TreeModify;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.docx.RstreeDBHelper;
import io.oz.sdoc.docx.IBlockParser;
import io.oz.sdoc.docx.Importer;
import io.oz.sdoc.puck.PuckNode;
import io.oz.sdoc.puck.PucktreeDBHelper;
import io.oz.sdoc.puck.ResPublisher;
import io.oz.sdoc.rst.HtmlBuilder;

/**
 * The doc-tree tier: the service semantic-doc's editor (AnDoctreeditor) talks to.
 *
 * <p>The port is the app's, e.g. jserv-album's AlbumPort.doctree, so this lib
 * doesn't depend on the app's port enum. Its url must be {@link #url}.</p>
 *
 * <p>The working copy is in its own db ({@link RstreeDBHelper}), connection {@link #conn(String, String)},
 * set up at the first request.
 * Served: every action of {@link DoctreeReq.A}; {@link DoctreeReq.A#importDocx} with
 * {@link #importer(DocxSource, IBlockParser)}. Tree changes are SemanticTree.onModifyTree()'s,
 * with the doc's rules in {@link S_DocNodeMeta}.</p>
 *
 * <p>The session is verified ({@link #onPost(AnsonMsg, HttpServletResponse)}); its user is the
 * creator of the docs he imports, the owner of their resource folders ({@link DocResHelper}).</p>
 *
 * TODO Rename to RstreeTier
 * TODO
 * TODO
 * TODO
 * 
 * @author ody
 */
@WebServlet(description = "semantic-doc doc-tree tier", urlPatterns = { "/doctree.serv" })
public class DoctreeTier extends ServPort<DoctreeReq> {
	private static final long serialVersionUID = 1L;

	/** url of the port, without the leading '/' */
	public static final String url = "doctree.serv";

	/** connection of the working copy, null: not configured (only {@link DoctreeReq.A#docs}, empty) */
	String conn;
	/** for doc ids */
	String synode;
	SDocRef source;
	IBlockParser parser;

	/** created by {@link #db()} at the first request needing it */
	RstreeDBHelper db;
	Importer importer;

	/** the user of {@link #handle(DoctreeReq)}, without a session (tests), and of the db's logs */
	final IUser robot = new JRobot();

	public DoctreeTier(IPort port) {
		super(port);
	}

	/**
	 * Configure the working copy. Its tables are created / upgraded at the first request
	 * needing them ({@link #db()}), so a synode never editing doc trees keeps an empty db file,
	 * and a failure here never stops the synode.
	 * 
	 * @param conn connection of the doc-tree db, see {@link RstreeDBHelper#connect(String, String, String)}
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
	public DoctreeTier importer(SDocRef source, IBlockParser parser) {
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
	public synchronized RstreeDBHelper db() throws Exception {
		if (db == null && conn != null) {
			db = RstreeDBHelper.setup(conn, false);
			if (parser != null)
				importer = new Importer(conn, db, parser);
		}
		return db;
	}

	public Importer importer() { return importer; }

	/** the puck tree, the stories, in the same db: created by {@link #puck()} at the first request needing it */
	PucktreeDBHelper puck;

	/**
	 * The puck tree of the working copy, set up at the first call.
	 * 
	 * @return null if not configured
	 */
	public synchronized PucktreeDBHelper puck() throws Exception {
		if (puck == null && db() != null)
			puck = PucktreeDBHelper.setup(conn);
		return puck;
	}

	/**
	 * @return the tree the doc is of: the puck tree if it's a story, the rst tree otherwise
	 * @throws SemanticException the working copy isn't configured
	 */
	RstreeDBHelper tree(String docId) throws Exception {
		if (db() == null)
			throw new SemanticException("Doc-tree tier: the working copy isn't configured.");
		return puck().doc(docId) != null ? puck : db;
	}

	ResPublisher publisher;

	/**
	 * Enable {@link DoctreeReq.A#publish}, {@link DoctreeReq.A#unpublish}.
	 * @param publisher requests to the local html-service
	 * @return this
	 */
	public DoctreeTier publisher(ResPublisher publisher) {
		this.publisher = publisher;
		return this;
	}

	HtmlBuilder builder;

	/**
	 * Enable building a doc's html package when it's committed with its RST files
	 * ({@link DoctreeReq#rstFiles}), and {@link DoctreeReq.A#build}.
	 * 
	 * @param builder
	 * @return this
	 */
	public DoctreeTier builder(HtmlBuilder builder) {
		this.builder = builder;
		return this;
	}

	@Override
	protected void onGet(AnsonMsg<DoctreeReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		onPost(msg, resp);
	}

	@Override
	protected void onPost(AnsonMsg<DoctreeReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			IUser usr = verifier().verify(msg.header());
			write(resp, handle(msg.body(0), usr));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/** Serve a request without a session, as the robot: for tests. */
	public AnsonMsg<? extends AnsonResp> handle(DoctreeReq req) {
		return handle(req, robot);
	}

	/**
	 * Serve a request: the handler of its a() answers a {@link DoctreeResp}, or throws.
	 * Exceptions are answered as in docsync's ExpDoctier, for the client to report:
	 * SemanticException → exSemantic (a request that can't be served, the message for users),
	 * SQLException, TransException → exTransct, anything else → exGeneral.
	 *
	 * @param req
	 * @param usr the session's user
	 * @return response message
	 */
	public AnsonMsg<? extends AnsonResp> handle(DoctreeReq req, IUser usr) {
		try {
			String a = req == null ? null : req.a();
			DoctreeResp rsp;

			if (DoctreeReq.A.docs.equals(a))
				rsp = docs(req);
			else if (DoctreeReq.A.importDocx.equals(a))
				rsp = importDocx(req, usr);
			else if (DoctreeReq.A.tree.equals(a))
				rsp = tree(req);
			else if (DoctreeReq.A.update.equals(a))
				rsp = update(req);
			else if (DoctreeReq.A.modify.equals(a))
				rsp = modify(req);
			else if (DoctreeReq.A.commit.equals(a))
				rsp = commitDoc(req, usr);
			else if (DoctreeReq.A.build.equals(a))
				rsp = build(req);
			else if (DoctreeReq.A.publish.equals(a))
				rsp = publish(req, usr);
			else if (DoctreeReq.A.unpublish.equals(a))
				rsp = unpublish(req, usr);
			else if (DoctreeReq.A.stories.equals(a))
				rsp = stories(req);
			else if (DoctreeReq.A.page.equals(a))
				rsp = page(req);
			else if (DoctreeReq.A.savePage.equals(a))
				rsp = savePage(req, usr);
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
		return new DoctreeResp().docs(db() == null ? new ArrayList<SDocRef>() : db.docs());
	}

	/** c/import: the doc header (state parsing) at once; the nodes are parsed in the background → doc */
	DoctreeResp importDocx(DoctreeReq req, IUser usr) throws Exception {
		if (db() == null || importer == null)
			throw new SemanticException("Doc-tree tier: importing isn't configured.");
		String title = req.doc == null ? null : req.doc.title;
		return new DoctreeResp().doc(importer.start(synode, req.srcId, title, usr.uid()));
	}

	/** r/tree: the doc root {@link DoctreeReq#docId} with its subtree → doc, forest */
	DoctreeResp tree(DoctreeReq req) throws Exception {
		SDocRef doc = doc(req);

		// while importing (state parsing), the forest is empty; the client asks again
		DoctreeResp rsp = new DoctreeResp().doc(doc);
		rsp.forest(DatasetHelper.buildForest(db.nodes(req.docId), db.nodem.treeSemantics()));
		rsp.root(null);
		return rsp;
	}

	/** u/node: save {@link DoctreeReq#node}'s tipjson, rstext, rstver; the doc gets dirty → doc */
	DoctreeResp update(DoctreeReq req) throws Exception {
		doc(req);
		if (req.node == null || isblank(req.node.nid))
			throw new SemanticException("Doc-tree tier: no node to save.");
		req.node.docId = req.docId;
		return new DoctreeResp().doc(db.update(req.node, robot));
	}

	/**
	 * u/tree: modify the doc's tree as req.mod says, by SemanticTree.onModifyTree(), with the
	 * doc's rules ({@link S_DocNodeMeta}) → doc, root, forest (the reloaded subtree), rejected, newId
	 */
	DoctreeResp modify(DoctreeReq req) throws Exception {
		doc(req);
		if (req.mod == null)
			throw new SemanticException("Doc-tree tier: no tree modification.");
		if (req.mod.op == TreeModify.Op.insert) {
			// the new node is in the doc, whatever the client says
			if (req.mod.nvs == null) req.mod.nvs = new ArrayList<String[]>();
			req.mod.nvs.removeIf((nv) -> eq(nv[0], db.nodem.docId));
			req.mod.nvs.add(new String[] { db.nodem.docId, req.docId });
		}

		AnDatasetResp r = new SemanticTree().onModifyTree(conn, db.nodem, req.mod, robot);
		if (r.rejected() == null)
			db.dirty(req.docId, robot);

		DoctreeResp rsp = new DoctreeResp().doc(db.doc(req.docId));
		rsp.root(r.root()).rejected(r.rejected()).newId(r.newId()).forest(r.forest());
		return rsp;
	}


	/**
	 * c/commit: the doc's work is finished → doc;
	 * a doc tree's html package is built with its {@link DoctreeReq#rstFiles};
	 * a story's page is committed by its tree, {@link PucktreeDBHelper#commit(String, IUser)}.
	 */
	DoctreeResp commitDoc(DoctreeReq req, IUser usr) throws Exception {
		RstreeDBHelper tree = tree(req.docId);
		if (tree.doc(req.docId) == null)
			throw new SemanticException("Doc-tree tier: no such doc: %s", req.docId);

		SDocRef doc = tree.commit(req.docId, robot);
		DoctreeResp rsp = new DoctreeResp().doc(doc);
		if (tree == puck)
			return rsp;

		if (builder != null && !isNull(req.rstFiles)) {
			builder.start(db, doc, req.rstFiles, usr);
			rsp.build(builder.state(req.docId), null);
		}
		else if (builder != null)
			builder.forget(req.docId);
		return rsp;
	}

	/** c/publish: publish the doc at html-service → doc, with pubpath */
	DoctreeResp publish(DoctreeReq req, IUser usr) throws Exception {
		if (db() == null || publisher == null)
			throw new SemanticException("Doc-tree tier: publishing isn't configured.");

		RstreeDBHelper tree = tree(req.docId);
		if (builder != null && tree == db) {
			SDocRef doc = doc(req);
			String state = builder.state(req.docId);
			if (eq(HtmlBuilder.State.building, state))
				throw new SemanticException("The doc's html is still building. Try again later.");
			if (isblank(doc.builtAt) || !isblank(doc.committedAt) && doc.builtAt.compareTo(doc.committedAt) < 0)
				throw new SemanticException(eq(HtmlBuilder.State.failed, state)
					? "The doc's html build failed. " + builder.log(req.docId)
					: "The doc's html isn't built since it's committed. Commit it again.");
		}
		return new DoctreeResp().doc(publisher.publish(tree, req.docId, usr));
	}

	/** d/publish: the doc is no longer published → doc, without pubpath */
	DoctreeResp unpublish(DoctreeReq req, IUser usr) throws Exception {
		if (db() == null || publisher == null)
			throw new SemanticException("Doc-tree tier: publishing isn't configured.");
		return new DoctreeResp().doc(publisher.unpublish(tree(req.docId), req.docId, usr));
	}

	/** r/stories: the stories' headers → docs (empty if the working copy isn't configured) */
	DoctreeResp stories(DoctreeReq req) throws Exception {
		return new DoctreeResp().docs(puck() == null ? new ArrayList<SDocRef>() : puck.docs());
	}

	/** r/page: the story's page → doc, page */
	DoctreeResp page(DoctreeReq req) throws Exception {
		if (puck() == null)
			throw new SemanticException("Doc-tree tier: the working copy isn't configured.");
		PuckNode page = puck.stories.page(req.docId);
		if (page == null)
			throw new SemanticException("Doc-tree tier: no such story: %s", req.docId);
		return new DoctreeResp().doc(puck.doc(req.docId)).page(page);
	}

	/** u/page: save the story's page, {@link DoctreeReq#page}; docId null: a new story → doc */
	DoctreeResp savePage(DoctreeReq req, IUser usr) throws Exception {
		if (puck() == null)
			throw new SemanticException("Doc-tree tier: the working copy isn't configured.");
		return new DoctreeResp().doc(puck.stories.save(synode, req.docId, req.page, usr));
	}


	/** r/build: the state of the doc's html build → doc (with builtAt, htmlUri), build, buildlog */
	DoctreeResp build(DoctreeReq req) throws Exception {
		SDocRef doc = doc(req);
		DoctreeResp rsp = new DoctreeResp().doc(doc);
		if (builder != null)
			rsp.build(builder.state(req.docId), builder.log(req.docId));
		return rsp;
	}

	/** the doc of the request; the working copy must be configured */
	SDocRef doc(DoctreeReq req) throws Exception {
		if (db() == null)
			throw new SemanticException("Doc-tree tier: the working copy isn't configured.");
		SDocRef doc = db.doc(req.docId);
		if (doc == null)
			throw new SemanticException("Doc-tree tier: no such doc: %s", req.docId);
		return doc;
	}

}
