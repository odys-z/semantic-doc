package io.oz.sdoc.publish;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.oz.sdoc.DocInf;

/**
 * Response to {@link ResPublishReq}.
 * 
 * @since 0.1.2
 */
public class ResPublishResp extends AnsonResp {

	/**
	 * From html-service, {@link ResPublishReq.A#deploy}: the request's docs, with their
	 * {@link DocInf#pubpath}; otherwise all the docs published there.<br>
	 * From the synode: all the docs with a pubpath.
	 */
	@AnsonField(valType="io.oz.sdoc.DocInf")
	public ArrayList<DocInf> respubs;

	public ResPublishResp() { }

	public ResPublishResp respubs(ArrayList<DocInf> docs) {
		this.respubs = docs;
		return this;
	}
}
