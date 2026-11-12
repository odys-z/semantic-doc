package io.oz.sdoc;

import io.odysz.anson.Anson;

/**
 * Document header record, the "doc" part of semantic-doc's doc-a.json / doc-b.json.
 *
 * @author odys-z@github.com
 */
public class DocInf extends Anson {

	public static final String tabl = "s_docs";

	public String docId;
	public String title;
	public String creator;

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
