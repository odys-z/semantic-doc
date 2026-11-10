package io.oz.sdoc.imports;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.DocNode;
import io.oz.sdoc.DocRes;
import io.oz.sdoc.DoctreeDBHelper;
import io.oz.sdoc.S_DocNodeMeta;

/**
 * Builds a doc tree from {@link Block}s and inserts it into the nodes table, subtree by subtree.
 *
 * <pre>
 * doc root (nid = fullpath = docId, text = title)
 *   a heading     → section, nested by outline level (a heading pops the stack until the top is shallower)
 *   anything else → p / table / image, under the current section
 *   a picture     → image, its file saved into the doc's resource folder, of its creator; resId: the file's name
 *
 * sections popped → the rows not inserted yet, in depth first order (the document's order),
 *                   are inserted at once, as one multi-row insert
 * end of blocks   → all the open sections are popped, the doc root too
 * </pre>
 *
 * <ul>
 * <li>The first Title-styled block (outline -1) is the doc's title, not a node.</li>
 * <li>Empty paragraphs (spacing) are dropped; empty headings are kept.</li>
 * <li>A numbering label is put in front of the text, e.g. "1. First".</li>
 * <li>Nodes carry structure, text, src, style and the paragraphs' run facts ({@link Run}):
 *     no json / rst, the client builds them.</li>
 * <li>The fullpath is the fullpath semantics' (semantic.DA ShFullpath2), not composed here; only the
 *     doc root's is given, the docId. The handler needs a row's parent in the db or earlier in the
 *     same insert: the rows are inserted in depth first order, parents first.</li>
 * <li>Not a transaction: a statement each time sections are popped; no rows are returned.</li>
 * </ul>
 *
 * @author ody
 */
public class Outliner {

	/** What {@link Outliner#outline(String, List, String, String)} has done. */
	public static class Outline {
		/** the doc root's text */
		public String title;
		/** next node number of the doc, after the nodes */
		public int nextNid;
	}

	/** An open section: on the stack until a heading of its level or upper comes. */
	static class Section {
		final DocNode node;
		/** outline level, the doc root: -2 */
		final int level;
		/** children so far, the next one's sibling sort is + 1 */
		int children;

		Section(DocNode node, int level) {
			this.node = node;
			this.level = level;
		}
	}

	final String conn;
	final S_DocNodeMeta nodem;
	final IUser usr;
	final DATranscxt st;

	/** where the pictures are saved; null: not saved, an image's resId is its part in the docx */
	DocRes res;
	/** the doc's creator, the owner of the resource folder */
	String uid;
	/** path of the docx being imported */
	String docx;

	/**
	 * Save the pictures of the docx as the doc's resources.
	 *
	 * @param res null: not saved
	 * @param uid the doc's creator
	 * @param docx path of the docx the blocks are parsed from
	 * @return this
	 */
	public Outliner resources(DocRes res, String uid, String docx) {
		this.res = res;
		this.uid = uid;
		this.docx = docx;
		return this;
	}

	/**
	 * @param conn the doc-tree db, with the fullpath semantics of the nodes table
	 * @param nodem
	 * @param usr
	 */
	public Outliner(String conn, S_DocNodeMeta nodem, IUser usr) throws Exception {
		this.conn = conn;
		this.nodem = nodem;
		this.usr = usr;
		this.st = new DATranscxt(conn);
	}

	/** Without a db, for tests overriding {@link #insert(ArrayList)}. */
	Outliner() {
		this.conn = null;
		this.nodem = null;
		this.usr = null;
		this.st = null;
	}

	/**
	 * Build the tree and insert the nodes: each time sections are popped
	 * ({@link #pop2db(Deque, int, ArrayList)}), the rows not inserted yet, in depth first order.
	 *
	 * @param docId
	 * @param blocks in document order
	 * @param title the doc root's text; blank: the docx's Title, else deftitle
	 * @param deftitle e.g. the file name
	 * @return title and next node number; the nodes are in the db
	 */
	public Outline outline(String docId, List<Block> blocks, String title, String deftitle)
			throws TransException, SQLException, IOException {
		Outline o = new Outline();

		// the first Title-styled block; found first: the doc root is in the first insert
		Block docxTitle = null;
		for (Block b : blocks)
			if (b.isHeading() && b.outline < 0) {
				docxTitle = b;
				break;
			}
		o.title = !isblank(title) ? title
				: docxTitle != null && !isblank(docxTitle.text) ? docxTitle.text : deftitle;

		DocNode root = new DocNode(docId, docId, DocNode.Kind.doc);
		root.sort = 1;
		root.fullpath = docId; // the prefix of the doc's subtree, unique in the nodes table
		root.ptext = o.title;

		Deque<Section> stack = new ArrayDeque<Section>();
		stack.push(new Section(root, -2));

		// rows not inserted yet, in the document's order: depth first, parents first
		ArrayList<DocNode> rows = new ArrayList<DocNode>();
		rows.add(root);

		int n = 1;
		for (Block b : blocks) {
			if (b == docxTitle)
				continue;
			if (!b.isHeading() && isblank(b.text))
				continue;

			String kind = b.isHeading() ? DocNode.Kind.section
						: Block.table.equals(b.kind) ? DocNode.Kind.tabl
						: Block.image.equals(b.kind) ? DocNode.Kind.image : DocNode.Kind.p;

			// an upper (or the same) level: the sections down to it are finished, insert them
			if (b.isHeading())
				pop2db(stack, b.outline, rows);

			Section parent = stack.peek();

			// fullpath: left to the fullpath semantics, by parentId & sort
			DocNode node = new DocNode(docId, DoctreeDBHelper.nid(docId, n++), kind)
					.parent(parent.node.nid, ++parent.children);
			node.ptext = isblank(b.numLabel) ? b.text : b.numLabel + " " + b.text;
			node.src = b.src;
			if (!b.isHeading() && !b.runs.isEmpty()) {
				ArrayList<Run> runs = new ArrayList<Run>(b.runs);
				if (!isblank(b.numLabel))
					runs.add(0, new Run(b.numLabel + " "));
				node.facts = Run.factsOf(runs);
			}
			node.docxstyle = b.styleName;
			if (Block.image.equals(b.kind))
				node.resId = res == null ? b.res : res.saveDocxPart(uid, docId, docx, b.res);
			rows.add(node);

			if (b.isHeading())
				stack.push(new Section(node, b.outline));
		}

		// end of the doc: every open section is finished, the doc root (the upmost level) too
		pop2db(stack, Integer.MIN_VALUE, rows);

		o.nextNid = n;
		return o;
	}

	/**
	 * Pop the sections of the level or deeper. If any is popped, insert the rows at once and
	 * clear them: the popped subtrees, after their ancestors not inserted yet (still on the stack).
	 *
	 * @param stack
	 * @param level outline level of the coming heading; the doc root (-2) is popped only by the end
	 * @param rows not inserted yet, in depth first order; cleared when inserted
	 */
	void pop2db(Deque<Section> stack, int level, ArrayList<DocNode> rows) throws TransException, SQLException {
		boolean popped = false;
		while (!stack.isEmpty() && stack.peek().level >= level) {
			stack.pop();
			popped = true;
		}

		if (popped) {
			insert(rows);
			rows.clear();
		}
	}

	/**
	 * Insert the nodes as one multi-row statement, cols() &amp; values().
	 * The fullpath, the last column, is given only by the doc root; the others' are composed by the
	 * fullpath semantics, so a row's parent must be in the db or earlier in the rows.
	 *
	 * @param nodes in depth first order; nothing is done if empty
	 */
	void insert(ArrayList<DocNode> nodes) throws TransException, SQLException {
		if (nodes.isEmpty())
			return;

		ArrayList<ArrayList<Object[]>> vals = new ArrayList<ArrayList<Object[]>>(nodes.size());
		for (DocNode n : nodes) {
			ArrayList<Object[]> row = new ArrayList<Object[]>();
			row.add(new Object[] { nodem.pk, n.nid });
			row.add(new Object[] { nodem.docId, n.docId });
			row.add(new Object[] { nodem.parentId, n.parentId });
			row.add(new Object[] { nodem.sort, n.sort });
			row.add(new Object[] { nodem.kind, n.kind });
			row.add(new Object[] { nodem.ptext, n.ptext });
			row.add(new Object[] { nodem.facts, n.facts });
			row.add(new Object[] { nodem.src, n.src });
			row.add(new Object[] { nodem.docxstyle, n.docxstyle });
			row.add(new Object[] { nodem.resId, n.resId });
			if (n.fullpath != null)
				row.add(new Object[] { nodem.fullpath, n.fullpath });
			vals.add(row);
		}

		st.insert(nodem.tbl, usr)
			.cols(new String[] { nodem.pk, nodem.docId, nodem.parentId, nodem.sort, nodem.kind,
				nodem.ptext, nodem.facts, nodem.src, nodem.docxstyle, nodem.resId, nodem.fullpath })
			.values(vals)
			.ins(st.instancontxt(conn, usr));
	}
}
