package io.oz.sdoc.imports;

/**
 * Facts of a block-level element of a docx (a paragraph, a table, or a picture of a paragraph),
 * in document order, as a parser ({@link IBlockParser}) finds them. {@link Outliner} builds the doc tree from them.
 *
 * @author ody
 */
public class Block {
	/** values of {@link Block#kind} */
	public static final String p = "p";
	public static final String table = "table";
	/** a picture, following the block of the paragraph it is in; its file: {@link #res} */
	public static final String image = "image";

	/** {@link #p}, {@link #table} or {@link #image} */
	public final String kind;
	/** path back into the source, e.g. "body/3/sdt/0" */
	public final String src;

	public String styleId;
	/** the style's display name, e.g. "heading 1" */
	public String styleName;
	public String text;
	/** numbering label as Word renders it, e.g. "1.", "a)"; null if not numbered */
	public String numLabel;

	/**
	 * Effective outline level: null for body text, 0 for heading 1, ...;
	 * -1 for the Title style (above heading 1).
	 */
	public Integer outline;

	/** image only: the picture's part (zip entry) in the docx, e.g. "word/media/image1.png" */
	public String res;

	/** table only: has merged cells (layout table) */
	public boolean hasSpans;

	/** paragraph only: the runs, text with direct formatting; empty if not collected */
	public final java.util.List<Run> runs = new java.util.ArrayList<Run>();

	public Block(String kind, String src) {
		this.kind = kind;
		this.src = src;
	}

	public boolean isHeading() { return outline != null; }

	public Block style(String id, String name) {
		this.styleId = id;
		this.styleName = name;
		return this;
	}

	public Block text(String t) {
		this.text = t;
		return this;
	}

	public Block outline(Integer lvl) {
		this.outline = lvl;
		return this;
	}

	public Block res(String part) {
		this.res = part;
		return this;
	}

	public Block num(String label) {
		this.numLabel = label;
		return this;
	}
}
