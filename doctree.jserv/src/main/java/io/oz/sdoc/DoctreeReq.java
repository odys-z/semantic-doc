package io.oz.sdoc;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.ext.AnDatasetReq;

/**
 * Request body of the doc-tree tier, matching semantic-doc's TS DoctreeReq (doctree-tier.ts).
 *
 * <p>Structural changes use AnDatasetReq's fields, as SemanticTree.onModifyTree() takes them:
 * {@link #rootId} (the node moved / deleted), {@link #toParent}, {@link #toIndex}
 * (position among toParent's children, counted after the node is detached),
 * {@link #nvs} (insert: other columns).</p>
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

		/** save a node's content, {@link DoctreeReq#node}: json + rst → {@link DoctreeResp#doc} */
		public static final String update = "u/node";
		/** move {@link #rootId} under {@link #toParent} at {@link #toIndex} → root, forest, rejected */
		public static final String move = AnDatasetReq.A.umove;
		/** insert a node under {@link #toParent} at {@link #toIndex}, columns {@link #nvs} → newId, root, forest */
		public static final String insert = AnDatasetReq.A.insrt;
		/** delete {@link #rootId} with its subtree → root, forest */
		public static final String del = AnDatasetReq.A.del;

		/** mark the doc's work as finished → {@link DoctreeResp#doc} */
		public static final String commit = "c/commit";
		/** create a doc header (and its root node) → {@link DoctreeResp#doc} */
		public static final String createDoc = "c/doc";
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

	/** for {@link A#move} */
	public DoctreeReq move(String nid, String toParent, int toIndex) {
		this.rootId = nid;
		this.toParent = toParent;
		this.toIndex = toIndex;
		return this;
	}

	/**
	 * For {@link A#insert}.
	 * @param nvs other columns: name, value, name, value, ...
	 */
	public DoctreeReq insert(String toParent, int toIndex, String... nvs) {
		this.toParent = toParent;
		this.toIndex = toIndex;
		this.nvs = new ArrayList<String[]>();
		for (int i = 0; i + 1 < nvs.length; i += 2)
			this.nvs.add(new String[] { nvs[i], nvs[i + 1] });
		return this;
	}

	/** for {@link A#del} */
	public DoctreeReq del(String nid) {
		this.rootId = nid;
		return this;
	}
}
