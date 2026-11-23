package io.oz.sdoc;

import io.odysz.anson.Anson;

/**
 * Document header record, the "doc" part of semantic-doc's doc-a.json / doc-b.json.
 *
 * @author ody
 */
public class DocInf extends Anson {

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

	/**
	 * The built html package (RST -&gt; html, zipped): a local file path, null if never built.
	 * This is the zip html-service unzips when the doc is published (see {@link #pubpath}).
	 */
	public String htmlUri;
	/** Time of last build. */
	public String builtAt;
	/** Who triggered the last build. */
	public String builtBy;
	/**
	 * Id of the template the html package was built with, name + version, e.g. "rtd-0.8":
	 * the package has no theme files, its pages link the template's, served by html-service at
	 * /pub/_t/&lt;template&gt;/_static/. Null if never built.
	 * @since 0.1.3
	 */
	public String template;

	/**
	 * Published url path at html-service, without the leading '/', e.g. "pub/er3cou": the url root
	 * of all published docs (html-service's configuration) and a short random name.
	 * It's assigned by html-service when the doc is first published, and kept by the synode
	 * (s_docs.pubpath), so the doc's url is the same after rebuilt or html-service restarted.
	 * Null if not published. The package's version is told by {@link #builtAt}.
	 * @since 0.1.2
	 */
	public String pubpath;

	/** true if changed since the last commit */
	public boolean dirty;
	/** time of the last commit, null if never committed */
	public String committedAt;

	/** Values of {@link DocInf#kind}, the same as semantic-doc's TS DocInf.Kind. */
	public static class Kind {
		/** a doc tree, paragraphs in RST, published as a Sphinx html package */
		public static final String doctree = "doctree";
		/** a media story, a Puck page (page.json), published as a boilerplate at html-service */
		public static final String story = "story";
	}

	/**
	 * What the doc's content is, see {@link Kind}; null: a doc tree.
	 * STUB (media story, WP3): s_docs.kind, to be added by DoctreeDBUpgrade (S_DocsMeta).
	 */
	public String kind;

	public DocInf() { }

	public DocInf(String docId, String title, String creator) {
		this.docId = docId;
		this.title = title;
		this.creator = creator;
	}
}
