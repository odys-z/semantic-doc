package io.oz.sdoc;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.meta.DocRef;

/**
 * This is a helper. The data type is now DocRef.
 * 
 * Document header record, the "doc" part of semantic-doc's doc-a.json / doc-b.json.
 *
 * @author ody
 */
public class SDocRef extends DocRef {

	/** Values of {@link SDocRef#state}. */
	public static class State {
		/** importing: the header is there, nodes are being parsed */
		public static final String parsing = "parsing";
		public static final String ready = "ready";
		/** importing failed, see {@link SDocRef#msg} */
		public static final String failed = "failed";
	}

//	public String docId;
	public String title;
	public String creator;

	/** see {@link State} */
	public String state;
	/** why the import failed */
	public String msg;
//	/** the source docx, its record id in the doc table (h_photos); null if created in the editor */
//	public String srcId;

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

	/** Values of {@link SDocRef#kind}, the same as semantic-doc's TS DocInf.Kind. */
	public static class Kind {
		/** a doc tree, paragraphs in RST, published as a Sphinx html package */
		public static final String doctree = "doctree";
		/** a media story, a Puck page (page.json), published as a boilerplate at html-service */
		public static final String story = "story";
	}

	/**
	 * What the doc's content is, see {@link Kind}: {@link Kind#story} for the rows of s_stories
	 * (io.oz.sdoc.puck.S_StoriesMeta); null: a doc tree, of s_docs.
	 * @since 0.1.5
	 */
	public String mime;

	/** a story's subtitle; null for doc trees */
	public String subtitle;

	/** a story's cover media, its io_oz_synuid, set when committed; null for doc trees */
	public String cover;

	/**
	 * The page's resources on this synode, a story's media: the album's records (h_photos), each with
	 * {@link DocRef#uids}, the io_oz_synuid, and {@link DocRef#uri64}, resolved by the synode to the
	 * file's absolute path. Only exchanged with the local html-service (loopback), which serves them
	 * as m/&lt;synuid&gt; of the published page: deploying it, and its query r/published. Null otherwise,
	 * and for a doc tree, whose html package has its resources.
	 * @since 0.1.5
	 */
	@AnsonField(valType="io.odysz.semantic.meta.DocRef")
	public ArrayList<DocRef> resources;

	public SDocRef() { }

	public SDocRef(String docId, String title, String creator) {
		this.docId = docId;
		this.title = title;
		this.creator = creator;
	}
}
