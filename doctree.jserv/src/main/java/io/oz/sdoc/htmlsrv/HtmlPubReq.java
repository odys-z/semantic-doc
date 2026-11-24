package io.oz.sdoc.htmlsrv;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.oz.sdoc.SDocRef;

/**
 * Request by html-service to the local synode, for puck pages published at html-service:<ol>
 * <li>synode -&gt; html-service's port respaths (io.oz.srv.ResPathsTier):
 *     {@link A#deploy}, {@link A#remove}, {@link A#refresh};</li>
 * <li>html-service -&gt; synode's port {@link ResPublishTier}: {@link A#refresh}.</li>
 * </ol>
 * Both answer {@link HtmlPubResp}. No session, localhost only.
 * 
 * @since 0.1.2
 */
public class HtmlPubReq extends AnsonBody {

	public static class A {
		/**
		 * To synode by html-service: query the synode for published html pages now, and update the context paths.<br>
		 * 
		 * To the synode: list the published docx-rst pages.
		public static final String listdocx = "r/rst";
		 */

		/**
		 * To the synode: list the published puck pages.
		public static final String listpuck = "r/puck";
		 */
		
		/** Publish (or replace) {@link HtmlPubReq#respubs}; a doc without pubpath gets one */
		public static final String deploy = "c";

		/** Remove {@link HtmlPubReq#respubs}, by {@link SDocRef#pubpath} */
		public static final String remove = "d";
	}
	
	public String synode;
	public ArrayList<SDocRef> respubs;

	public HtmlPubReq respubs(SDocRef... docs) {
		respubs = new ArrayList<SDocRef>();
		for (SDocRef d : docs)
			respubs.add(d);
		return this;
	}

	public HtmlPubReq() {
		super(null, null);
	}

	public HtmlPubReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}
}
