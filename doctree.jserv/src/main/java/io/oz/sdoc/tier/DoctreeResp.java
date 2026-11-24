package io.oz.sdoc.tier;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.ext.AnDatasetResp;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.puck.PuckNode;

/**
 * Response of the doc-tree tier, matching semantic-doc's TS DoctreeResp (doctree-tier.ts).
 *
 * <p>Trees use AnDatasetResp's fields, as SemanticTree.onModifyTree() answers them:
 * forest (AnTreeNodes, by DatasetHelper.buildForest), root (the reloaded subtree's root;
 * null: forest is the whole doc), rejected (the change is refused, nothing changed).</p>
 *
 * @author ody
 */
public class DoctreeResp extends AnDatasetResp {

	/** the doc's header */
	public SDocRef doc;

	/** for {@link DoctreeReq.A#docs} */
	@AnsonField(valType="io.oz.sdoc.SDocRef")
	public ArrayList<SDocRef> docs;

	/**
	 * for {@link DoctreeReq.A#commit}, {@link DoctreeReq.A#build}: the state of the doc's html build,
	 * a value of io.oz.sdoc.rst.HtmlBuilder.State; null: no build since the synode started.
	 */
	public String build;

	/** output of the build tool (warnings, or why it failed) */
	public String buildlog;

	/** for {@link DoctreeReq.A#page}: the story's page, see io.oz.sdoc.puck.StoryPages#page() */
	public PuckNode page;

	public DoctreeResp() { }

	/** for {@link DoctreeReq.A#page} */
	public DoctreeResp page(PuckNode page) {
		this.page = page;
		return this;
	}

	public DoctreeResp doc(SDocRef d) {
		this.doc = d;
		return this;
	}

	public DoctreeResp build(String state, String log) {
		this.build = state;
		this.buildlog = log;
		return this;
	}

	public DoctreeResp docs(ArrayList<SDocRef> ds) {
		this.docs = ds;
		return this;
	}

}
