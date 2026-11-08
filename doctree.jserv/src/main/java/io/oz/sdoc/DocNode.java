package io.oz.sdoc;

import io.odysz.anson.Anson;

/**
 * Content record: one tree node of a doc, an element of "nodes" in
 * semantic-doc's doc-a.json / doc-b.json.
 *
 * <p>Note on {@link #tipjson}: the Tiptap document is carried as a serialized string,
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
		public static final String docref = "docref";
		/** docx table, only its text (cells joined) for now */
		public static final String tabl = "tabl";
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

	/** Tiptap JSON (source), serialized; null for image / docref */
	public String tipjson;
	/** RST derived from {@link #tipjson} */
	public String rstext;
	/** version of the tipjson -&gt; rstext serializer that produced {@link #rstext}; 0 = not yet */
	public int rstver;

	/** resource id (image / video on server), for {@link Kind#image} */
	public String resId;
	/** referenced node id (subtree root, may be in another doc), for {@link Kind#docref} */
	public String refId;

	/** docx origin, e.g. "body/2", from the importer; null if created in editor */
	public String src;
	/** docx paragraph style, e.g. "heading 1", "Normal" */
	public String docxstyle;

	/**
	 * Plain text, from the importer (a heading's title, a paragraph's text);
	 * the client builds {@link #tipjson} from it while json is null.
	 */
	public String ptext;

	/**
	 * The importer's facts of a paragraph's runs, {"v":1,"runs":[...]}, see {@link io.oz.sdoc.imports.Run};
	 * the client builds {@link #tipjson} from them.
	 */
	public String facts;

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

	public DocNode tipjson(String tiptap) {
		this.tipjson = tiptap;
		return this;
	}

	public DocNode rstext(String rst, int ver) {
		this.rstext = rst;
		this.rstver = ver;
		return this;
	}
}
