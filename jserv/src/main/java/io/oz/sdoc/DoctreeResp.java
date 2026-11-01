package io.oz.sdoc;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.jprotocol.AnsonResp;

/**
 * Response of the doc-content tier; for {@link DoctreeReq.A#tree}
 * it has the same shape as semantic-doc's doc-a.json: { doc, nodes }.
 *
 * @author odys-z@github.com
 */
public class DoctreeResp extends AnsonResp {

	public DocInf doc;

	@AnsonField(valType="io.oz.sdoc.DocNode")
	public ArrayList<DocNode> nodes;

	/** for {@link DoctreeReq.A#docs} */
	@AnsonField(valType="io.oz.sdoc.DocRec")
	public ArrayList<DocInf> docs;

	public DoctreeResp() { }

	public DoctreeResp doc(DocInf d) {
		this.doc = d;
		return this;
	}

	public DoctreeResp nodes(ArrayList<DocNode> ns) {
		this.nodes = ns;
		return this;
	}

	public DoctreeResp node(DocNode n) {
		if (nodes == null)
			nodes = new ArrayList<DocNode>();
		nodes.add(n);
		return this;
	}

	public DoctreeResp docs(ArrayList<DocInf> ds) {
		this.docs = ds;
		return this;
	}
}
