package io.oz.sdoc;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.UserReq;

/**
 * Request body of the doc-content tier (no ServPort yet).
 *
 * @author odys-z@github.com
 */
public class DoctreeReq extends UserReq {

	public static class A {
		/** list docs (headers only) */
		public static final String docs = "r/docs";
		/** load a doc: header + all nodes, i.e. the doc-a.json shape */
		public static final String tree = "r/tree";
		/** load a referenced subtree (read-only), rooted at {@link DoctreeReq#nid} */
		public static final String refTree = "r/ref";
		/** load one node */
		public static final String node = "r/node";

		/** create a doc header */
		public static final String createDoc = "c/doc";
		/** insert {@link DoctreeReq#node} */
		public static final String insert = "c/node";
		/** save a node's content: json + rst (+ style) */
		public static final String update = "u/node";
		/** move {@link DoctreeReq#nid} under {@link DoctreeReq#parentId} at {@link DoctreeReq#sort} */
		public static final String move = "u/move";
		/** delete {@link DoctreeReq#deletings} (with subtrees) */
		public static final String del = "d/nodes";
	}

	public String docId;
	public DocInf doc;

	public String nid;
	public DocNode node;

	/** for {@link A#move} */
	public String parentId;
	/** for {@link A#move} */
	public int sort;

	/** for {@link A#del} */
	public ArrayList<String> deletings;

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

	public DoctreeReq node(DocNode n) {
		this.node = n;
		this.nid = n == null ? null : n.nid;
		this.docId = n == null ? docId : n.docId;
		return this;
	}

	public DoctreeReq move(String nid, String parentId, int sort) {
		this.nid = nid;
		this.parentId = parentId;
		this.sort = sort;
		return this;
	}

	public DoctreeReq del(String... nids) {
		if (deletings == null)
			deletings = new ArrayList<String>();
		for (String n : nids)
			deletings.add(n);
		return this;
	}
}
