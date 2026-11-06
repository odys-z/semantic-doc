package io.oz.sdoc;

import io.odysz.anson.Anson;

/**
 * Content record: one tree node of a doc, an element of "nodes" in
 * semantic-doc's doc-a.json / doc-b.json.
 *
 * <p>Note on {@link #json}: the Tiptap document is carried as a serialized string,
 * not as a nested object, because every Tiptap node has a "type" key
 * ("doc", "paragraph", ...) which collides with Anson's "type" (class name) key.
 * The client stringifies / parses it.</p>
 *
 * @author ody
 */
public class DocNode extends Anson {

	/** Values of {@link DocNode#kind}. */
	public static class Kind {
		/** the doc root, nid = docId */
		public static final String doc = "doc";
		public static final String section = "section";
		public static final String p = "p";
		public static final String math = "math";
		/** media node, content is in {@link DocNode#resId} */
		public static final String image = "image";
		/** live, read-only reference to another doc's subtree, root is {@link DocNode#refId} */
		public static final String ref = "ref";
		/** docx table, only its text (cells joined) for now */
		public static final String table = "tabl";
	}

	public String nid;
	public String docId;
	/** null for root-level nodes */
	public String parentId;
	/** order among siblings */
	public int sort;
	/** e.g. "02.01.03", sibling sorts joined by '.' */
	public String fullpath;
	/** see {@link Kind} */
	public String kind;

	/** Tiptap JSON (source), serialized; null for image / ref */
	public String json;
	/** RST derived from {@link #json} */
	public String rst;
	/** version of the json -&gt; rst serializer that produced {@link #rst}; 0 = not yet */
	public int rstver;

	/** resource id (image / video on server), for {@link Kind#image} */
	public String resId;
	/** referenced node id (subtree root, may be in another doc), for {@link Kind#ref} */
	public String refId;

	/** docx origin, e.g. "body/2", from the importer; null if created in editor */
	public String src;
	/** docx paragraph style, e.g. "heading 1", "Normal" */
	public String style;

	/**
	 * Plain text, from the importer (a heading's title, a paragraph's text);
	 * the client builds {@link #json} from it while json is null.
	 */
	public String ptext;

	public DocNode() { }

	public DocNode(String docId, String nid, String kind) {
		this.docId = docId;
		this.nid = nid;
		this.kind = kind;
	}

	public DocNode parent(String parentId, int sort) {
		this.parentId = parentId;
		this.sort = sort;
		return this;
	}

	public DocNode json(String tiptap) {
		this.json = tiptap;
		return this;
	}

	public DocNode rst(String rst, int ver) {
		this.rst = rst;
		this.rstver = ver;
		return this;
	}
}
