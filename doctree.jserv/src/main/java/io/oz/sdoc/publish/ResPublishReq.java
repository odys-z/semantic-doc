package io.oz.sdoc.publish;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.oz.sdoc.DocInf;

/**
 * Request between html-service and the local synode, for docs published at html-service:<ol>
 * <li>synode -&gt; html-service's port respaths (io.oz.srv.ResPathsTier):
 *     {@link A#deploy}, {@link A#remove}, {@link A#refresh};</li>
 * <li>html-service -&gt; synode's port {@link ResPublishTier}: {@link A#refresh}.</li>
 * </ol>
 * Both answer {@link ResPublishResp}. No session, localhost only.
 * 
 * @since 0.1.2
 */
public class ResPublishReq extends AnsonBody {

	public static class A {
		/**
		 * To html-service: query the synode for published docs now, and update the context paths.<br>
		 * To the synode: list the published docs.
		 */
		public static final String refresh = "r/list";
		
		/** Publish (or replace) {@link ResPublishReq#respubs}; a doc without pubpath gets one */
		public static final String deploy = "c";
		/** Remove {@link ResPublishReq#respubs}, by {@link DocInf#pubpath} */
		public static final String remove = "d";
	}

	/** Docs to deploy / remove, with {@link DocInf#pubpath}, {@link DocInf#htmlUri} and {@link DocInf#builtAt}. */
	@AnsonField(valType="io.oz.sdoc.DocInf")
	public ArrayList<DocInf> respubs;

	public ResPublishReq respubs(DocInf... docs) {
		respubs = new ArrayList<DocInf>();
		for (DocInf d : docs)
			respubs.add(d);
		return this;
	}

	public ResPublishReq() {
		super(null, null);
	}

	public ResPublishReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}
}
