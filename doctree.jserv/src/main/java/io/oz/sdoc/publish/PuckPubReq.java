package io.oz.sdoc.publish;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.oz.sdoc.docx.DocxInf;

/**
 * Request between html-service and the local synode, for docs published at html-service:<ol>
 * <li>synode -&gt; html-service's port respaths (io.oz.srv.ResPathsTier):
 *     {@link A#deploy}, {@link A#remove}, {@link A#refresh};</li>
 * <li>html-service -&gt; synode's port {@link ResPublishTier}: {@link A#refresh}.</li>
 * </ol>
 * Both answer {@link PuckPubResp}. No session, localhost only.
 * 
 * @since 0.1.2
 */
public class PuckPubReq extends AnsonBody {

	public static class A {
		/**
		 * To html-service: query the synode for published docs now, and update the context paths.<br>
		 * To the synode: list the published docs.
		 */
		public static final String refresh = "r/list";
		
		/** Publish (or replace) {@link PuckPubReq#respubs}; a doc without pubpath gets one */
		public static final String deploy = "c";
		/** Remove {@link PuckPubReq#respubs}, by {@link DocxInf#pubpath} */
		public static final String remove = "d";
	}

	/** Docs to deploy / remove, with {@link DocxInf#pubpath}, {@link DocxInf#htmlUri} and {@link DocxInf#builtAt}. */
	@AnsonField(valType="io.oz.sdoc.DocInf")
	public ArrayList<PuckPage> respubs;


	public PuckPubReq respubs(PuckPage... docs) {
		respubs = new ArrayList<PuckPage>();
		for (PuckPage d : docs)
			respubs.add(d);
		return this;
	}


	public PuckPubReq() {
		super(null, null);
	}

	public PuckPubReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}
}
