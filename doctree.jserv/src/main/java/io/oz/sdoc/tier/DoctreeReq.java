package io.oz.sdoc.tier;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.docx.DocxNode;
import io.oz.sdoc.puck.PuckNode;
import io.oz.sdoc.rst.RstFile;
import io.odysz.semantic.ext.AnDatasetReq;
import io.odysz.semantic.ext.TreeModify;

/**
 * Request body of the doc-tree tier, matching semantic-doc's TS DoctreeReq (doctree-tier.ts).
 *
 * <p>Tree changes are AnDatasetReq's {@link #mod}, a {@link TreeModify} as the user means it,
 * e.g. indent a node: the server works out where nodes go.</p>
 * 
 * 
 * TODO Rename to RstreeReq
 * TODO
 * TODO
 * TODO
 *
 * @author ody
 */
public class DoctreeReq extends AnDatasetReq {

	/** Values of a(), the same as semantic-doc's TS DoctreeReq.A. */
	public static class A {
		/** list docs (headers only) → {@link DoctreeResp#docs} */
		public static final String docs = "r/docs";
		/** load a doc: the doc root with its subtree → {@link DoctreeResp#doc}, forest */
		public static final String tree = "r/tree";
		/** save a node's content, {@link DoctreeReq#node}: tipjson + rstext → {@link DoctreeResp#doc} */
		public static final String update = "u/node";
		/**
		 * modify the doc's tree as {@link #mod} says (up, down, indent, outdent, insert, remove),
		 * the server works out where nodes go → doc, root, forest (the reloaded subtree), rejected, newId
		 */
		public static final String modify = "u/tree";
		/** mark the doc's work as finished → {@link DoctreeResp#doc} */
		public static final String commit = "c/commit";
		/**
		 * the state of the doc's html build, started by {@link #commit} → {@link DoctreeResp#doc},
		 * {@link DoctreeResp#build}, {@link DoctreeResp#buildlog}
		 */
		public static final String build = "r/build";
		/** publish the doc's html package at the local html-service, or replace it → {@link DoctreeResp#doc}, with pubpath */
		public static final String publish = "c/publish";
		/** the doc is no longer published at html-service → {@link DoctreeResp#doc} */
		public static final String unpublish = "d/publish";
		/**
		 * import a docx, {@link DoctreeReq#srcId}: answers the header (state parsing) at once,
		 * nodes are parsed in the background
		 */
		public static final String importDocx = "c/import";
		/**
		 * a doc's resource file, {@link DoctreeReq#res}, for editing: a GET of
		 * {@link Doctree206Serv}, e.g. an img's src, with break points (http 206)
		 */
		public static final String res = "r/res";

		/** the stories' headers (DocInf, kind story) → {@link DoctreeResp#docs}; since 0.1.5 */
		public static final String stories = "r/stories";
		/** load a story's page → {@link DoctreeResp#doc}, {@link DoctreeResp#page}; since 0.1.5 */
		public static final String page = "r/page";
		/** save a story's page, {@link DoctreeReq#puckpage}; a new story if docId is null → {@link DoctreeResp#doc}; since 0.1.5 */
		public static final String savePage = "u/page";

		/**
		 * The local html-service's query, by io.oz.sdoc.htmlsrv.ResPathsTier: the pages published there,
		 * of both trees, the rst tree's and the puck tree's → {@link DoctreeResp#docs}.
		 * Localhost only, no session.
		 */
		public static final String published = "r/published";
	}

	public String docId;
	/** for {@link A#createDoc}, {@link A#importDocx}: title etc. */
	public SDocRef doc;

	/** for {@link A#importDocx}: the docx's record id in the doc table (h_photos) */
	public String srcId;

	/** for {@link A#res}: the resource's name in the doc's folder, e.g. "image1.png" */
	public String res;

	/** for {@link A#update}: the node's columns, json + rst */
	public DocxNode node;

	/**
	 * for {@link A#commit}: the doc as RST files, assembled by the client; the synode builds the
	 * doc's html package from them, in the background. Null: committed without a build.
	 */
	@AnsonField(valType="io.oz.sdoc.rst.RstFile")
	public ArrayList<RstFile> rstFiles;

	/**
	 * for {@link A#savePage}: the story's page; docId null: a new story.
	 * Not named page, which would hide AnQueryReq.page, the paging index (an int) every client sends.
	 */
	public PuckNode puckpage;

	public DoctreeReq() {
		super(null, null);
	}

	public DoctreeReq(String uri) {
		super(null, uri);
	}

	public DoctreeReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

	public DoctreeReq docId(String docId) {
		this.docId = docId;
		return this;
	}

	/** for {@link A#update} */
	public DoctreeReq node(DocxNode n) {
		this.node = n;
		this.docId = n == null ? docId : n.docId;
		return this;
	}

	/** for {@link A#commit} */
	public DoctreeReq rstFiles(RstFile... files) {
		this.rstFiles = new ArrayList<RstFile>();
		for (RstFile f : files)
			this.rstFiles.add(f);
		return this;
	}

	/** for {@link A#res} */
	public DoctreeReq res(String docId, String name) {
		this.docId = docId;
		this.res = name;
		return this;
	}

	/** for {@link A#savePage} */
	public DoctreeReq puckPage(String docId, PuckNode page) {
		this.docId = docId;
		this.puckpage = page;
		return this;
	}

	/** for {@link A#modify} */
	public DoctreeReq modify(TreeModify m) {
		this.mod = m;
		return this;
	}
}
