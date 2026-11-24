package io.oz.sdoc.publish;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.oz.sdoc.docx.DocxInf;

/**
 * Response to {@link PuckPubReq}.
 * 
 * @since 0.1.2
 */
public class PuckPubResp extends AnsonResp {

	/**
	 * From html-service, {@link PuckPubReq.A#deploy}: the request's docs, with their
	 * {@link DocxInf#pubpath}; otherwise all the docs published there.<br>
	 * From the synode: all the docs with a pubpath.
	 */
	@AnsonField(valType="io.oz.sdoc.DocInf")
	public ArrayList<DocxInf> respubs;


	public PuckPubResp() { }

	public PuckPubResp respubs(ArrayList<DocxInf> docs) {
		this.respubs = docs;
		return this;
	}


}
