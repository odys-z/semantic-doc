package io.oz.sdoc.puck;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;

import java.io.IOException;

import io.odysz.anson.AnsonException;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.docx.DocxtreeDBHelper;
import io.oz.sdoc.docx.DocxInf;
import io.oz.sdoc.publish.PuckPubResp;
import io.oz.sdoc.tier.PucktreeReq;
import io.oz.sdoc.publish.PuckPubReq.A;

/**
 * Publishes / removes docs at the local html-service, by requests to its port respaths
 * (io.oz.srv.ResPathsTier), and keeps the docs' context paths ({@link DocxInf#pubpath}).
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
	 * Publish the doc's html package ({@link DocxInf#htmlUri}) at html-service, or replace it
	 * if already published. A first published doc's pubpath is assigned by html-service.
	 * 
	 * @return the doc's header, with pubpath
	 * @throws SemanticException no such doc, the doc isn't built, or html-service refused
	 * @throws IOException html-service isn't reachable: nothing changed
	 */
	public DocxInf publish(DocxtreeDBHelper db, String docId, IUser usr) throws Exception {
		return publish(db, docId, usr, null);
	}

	/**
	 * Publish a doc, or a story with its media's map ({@link PubMedia}), which html-service keeps
	 * as the story's json map for serving m/&lt;synuid&gt;.
	 *
	 * @param media the stories' media, see io.oz.sdoc.tier.DoctreeTier#media(MediaSource); for a story
	 * @return the doc's header, with pubpath
	 * @since 0.1.5
	 */
	public DocxInf publish(DocxtreeDBHelper db, String docId, IUser usr, PucktreeReq req) throws Exception {
		DocxInf doc = db.doc(docId);
		if (doc == null)
			throw new SemanticException("Can't find doc %s.", docId);
		if (isblank(doc.htmlUri))
			throw new SemanticException("Doc %s has no html package. Build it first.", docId);


		PuckPubResp resp = post(A.deploy, doc);
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
	public DocxInf unpublish(DocxtreeDBHelper db, String docId, IUser usr) throws Exception {
		DocxInf doc = db.doc(docId);
		if (doc == null)
			throw new SemanticException("Can't find doc %s.", docId);
		if (isblank(doc.pubpath))
			return doc;

		DocxInf d = db.pubpath(docId, null, usr);
		try {
			post(A.remove, doc);
		} catch (IOException e) {
			Utils.warn("[ResPublisher] Html-service isn't reachable, /%s will be removed at its next query. %s",
					doc.pubpath, e.getMessage());
		}
		return d;
	}

	/** Post to html-service. */
	protected PuckPubResp post(String a, DocxInf doc)
			throws IOException, SemanticException, AnsonException {
				return null;

	}
}
