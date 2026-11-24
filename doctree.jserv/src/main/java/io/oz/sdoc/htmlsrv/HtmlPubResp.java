package io.oz.sdoc.htmlsrv;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.meta.DocRef;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.puck.PuckNode;

/**
 * Response to {@link HtmlPubReq}, from synode to html-service/ResPathTier.
 * 
 * @since 0.1.2
 */
public class HtmlPubResp extends AnsonResp {

	/**
	 * To synode 
	 */
	public ArrayList<SDocRef> respubs;

	public HtmlPubResp respubs(SDocRef... docs) {
		respubs = new ArrayList<SDocRef>();
		for (SDocRef d : docs)
			respubs.add(d);
		return this;
	}

	public HtmlPubResp() { }

	public HtmlPubResp respubs(ArrayList<SDocRef> page) {
		this.respubs = page;
		return this;
	}
}
