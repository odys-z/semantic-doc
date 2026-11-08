package io.oz.sdoc;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.ext.AnDatasetReq;
import io.odysz.semantic.ext.TreeModify;

/**
 * Request body of the doc-tree tier, matching semantic-doc's TS DoctreeReq (doctree-tier.ts).
 *
 * <p>Tree changes are AnDatasetReq's {@link #mod}, a {@link TreeModify} as the user means it,
 * e.g. indent a node: the server works out where nodes go.</p>
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
		 * import a docx, {@link DoctreeReq#srcId}: answers the header (state parsing) at once,
		 * nodes are parsed in the background
		 */
		public static final String importDocx = "c/import";
	}

	public String docId;
	/** for {@link A#createDoc}, {@link A#importDocx}: title etc. */
	public DocInf doc;

	/** for {@link A#importDocx}: the docx's record id in the doc table (h_photos) */
	public String srcId;

	/** for {@link A#update}: the node's columns, json + rst */
	public DocNode node;

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
	public DoctreeReq node(DocNode n) {
		this.node = n;
		this.docId = n == null ? docId : n.docId;
		return this;
	}

	/** for {@link A#modify} */
	public DoctreeReq modify(TreeModify m) {
		this.mod = m;
		return this;
	}
}
