package io.oz.sdoc.publish;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.oz.sdoc.DocInf;

/**
 * Request to port {@link HtmlPort#respaths}.
 * 
 * @since 0.2.1
 */
public class ResPublishReq extends AnsonBody {

	public static class A {
		/** Ask html-service to query the published paths now, and update its context paths. */
		public static final String refresh = "r/list";
		
		public static final String deploy = "c";
		public static final String remove = "d";
	}

	public ArrayList<DocInf> respubs;

	public ResPublishReq() {
		super(null, null);
	}

	public ResPublishReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}
}
