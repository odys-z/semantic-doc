package io.oz.sdoc.publish;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;

import java.io.IOException;

import io.odysz.anson.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.HttpServClient;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.DoctreeDBHelper;
import io.oz.sdoc.publish.ResPublishReq.A;

/**
 * Publishes / removes docs at the local html-service, by requests to its port respaths
 * (io.oz.srv.ResPathsTier), and keeps the docs' context paths ({@link DocInf#pubpath}).
 *
 * <p>The synode's records are what html-service finally follows (it queries {@link ResPublishTier}
 * when started and from time to time), so the requests here only make a change effective at once.</p>
 * 
 * @since 0.1.2
 */
public class ResPublisher {
	/** html-service's root, e.g. http://127.0.0.1:8900 */
	final String htmlroot;
	/** html-service's port, the app's port named "respaths", with url "respaths.serv" */
	final IPort respaths;

	/**
	 * @param htmlroot html-service's root, e.g. http://127.0.0.1:8900
	 * @param respaths the app's port for html-service's port, name "respaths", url "respaths.serv"
	 */
	public ResPublisher(String htmlroot, IPort respaths) {
		this.htmlroot = htmlroot;
		this.respaths = respaths;
	}

	/**
	 * Publish the doc's html package ({@link DocInf#htmlUri}) at html-service, or replace it
	 * if already published. A first published doc's pubpath is assigned by html-service.
	 * 
	 * @return the doc's header, with pubpath
	 * @throws SemanticException no such doc, the doc isn't built, or html-service refused
	 * @throws IOException html-service isn't reachable: nothing changed
	 */
	public DocInf publish(DoctreeDBHelper db, String docId, IUser usr) throws Exception {
		DocInf doc = db.doc(docId);
		if (doc == null)
			throw new SemanticException("Can't find doc %s.", docId);
		if (isblank(doc.htmlUri))
			throw new SemanticException("Doc %s has no html package. Build it first.", docId);

		ResPublishResp resp = post(A.deploy, doc);
		if (len(resp.respubs) != 1 || isblank(resp.respubs.get(0).pubpath))
			throw new SemanticException("Html-service doesn't reply the published path of doc %s.", docId);

		return db.pubpath(docId, resp.respubs.get(0).pubpath, usr);
	}

	/**
	 * The doc is no longer published. The record is changed first; if html-service isn't
	 * reachable, it removes the path at its next query.
	 * 
	 * @return the doc's header, without pubpath
	 */
	public DocInf unpublish(DoctreeDBHelper db, String docId, IUser usr) throws Exception {
		DocInf doc = db.doc(docId);
		if (doc == null)
			throw new SemanticException("Can't find doc %s.", docId);
		if (isblank(doc.pubpath))
			return doc;

		DocInf d = db.pubpath(docId, null, usr);
		try {
			post(A.remove, doc);
		} catch (IOException e) {
			Utils.warn("[ResPublisher] Html-service isn't reachable, /%s will be removed at its next query. %s",
					doc.pubpath, e.getMessage());
		}
		return d;
	}

	/** Post to html-service. */
	protected ResPublishResp post(String a, DocInf doc) throws IOException, SemanticException, AnsonException {
		AnsonMsg<ResPublishReq> req = new AnsonMsg<ResPublishReq>(respaths)
				.body(new ResPublishReq().respubs(doc).a(a));
		return (ResPublishResp) new HttpServClient()
				.post(respaths.url(htmlroot), req)
				.body(0);
	}
}
