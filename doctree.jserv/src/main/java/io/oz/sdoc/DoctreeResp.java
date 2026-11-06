package io.oz.sdoc;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.ext.AnDatasetResp;

/**
 * Response of the doc-tree tier, matching semantic-doc's TS DoctreeResp (doctree-tier.ts).
 *
 * <p>Trees use AnDatasetResp's fields, as SemanticTree.onModifyTree() answers them:
 * forest (AnTreeNodes, by DatasetHelper.buildForest), root (the reloaded subtree's root;
 * null: forest is the whole doc), rejected (the change is refused, nothing changed).</p>
 *
 * @author ody
 */
public class DoctreeResp extends AnDatasetResp {

	/** the doc's header */
	public DocInf doc;

	/** for {@link DoctreeReq.A#docs} */
	@AnsonField(valType="io.oz.sdoc.DocInf")
	public ArrayList<DocInf> docs;

	/** for {@link DoctreeReq.A#insert}: the new node's id */
	public String newId;

	public DoctreeResp() { }

	public DoctreeResp doc(DocInf d) {
		this.doc = d;
		return this;
	}

	public DoctreeResp docs(ArrayList<DocInf> ds) {
		this.docs = ds;
		return this;
	}

	public DoctreeResp newId(String nid) {
		this.newId = nid;
		return this;
	}
}
