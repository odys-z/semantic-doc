package io.oz.sdoc;

import io.odysz.anson.Anson;

/**
 * Document header record, the "doc" part of semantic-doc's doc-a.json / doc-b.json.
 *
 * @author ody
 */
public class DocInf extends Anson {

	public static final String tabl = "s_docs";

	/** Values of {@link DocInf#state}. */
	public static class State {
		/** importing: the header is there, nodes are being parsed */
		public static final String parsing = "parsing";
		public static final String ready = "ready";
		/** importing failed, see {@link DocInf#msg} */
		public static final String failed = "failed";
	}

	public String docId;
	public String title;
	public String creator;

	/** see {@link State} */
	public String state;
	/** why the import failed */
	public String msg;
	/** the source docx, its record id in the doc table (h_photos); null if created in the editor */
	public String srcId;
	/** next node id of this doc, see {@link DoctreeDB#nid(String, int)} */
	public int nextNid;

	/** Compiled html (RST -&gt; html) uri, null if never built. */
	public String htmlUri;
	/** Time of last build. */
	public String builtAt;
	/** Who triggered the last build. */
	public String builtBy;

	public DocInf() { }

	public DocInf(String docId, String title, String creator) {
		this.docId = docId;
		this.title = title;
		this.creator = creator;
	}
}
