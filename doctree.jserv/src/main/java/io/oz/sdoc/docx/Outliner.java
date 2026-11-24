package io.oz.sdoc.docx;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.parts.Resulving;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.DocResHelper;

/**
 * Builds a doc tree from {@link Block}s and inserts it into the nodes table, subtree by subtree.
 *
 * <pre>
 * doc root (fullpath = its nid, text = title), inserted first, alone
 *   a heading     → section, nested by outline level (a heading pops the stack until the top is shallower)
 *   anything else → p / table / image, under the current section
 *   a picture     → image, its file saved into the doc's resource folder, of its creator; resId: the file's name
 *
 * sections popped → the rows not inserted yet, in depth first order (the document's order),
 *                   are inserted at once, as one multi-row insert
 * end of blocks   → all the open sections are popped, the doc root too
 * </pre>
 *
 * <p>The nids are auto keys (the autopk semantics of the nodes table), generated in an insert,
 * in the order of its rows. A row's parentId is the parent's nid if the parent is inserted already,
 * or, if the parent is earlier in the same insert, {@code new Resulving(nodes, nid, i)}: the key of
 * the insert's i-th row. Rows leave the nid out: it's the last column, appended by the semantics.</p>
 *
 * <ul>
 * <li>The first Title-styled block (outline -1) is the doc's title, not a node.</li>
 * <li>Empty paragraphs (spacing) are dropped; empty headings are kept.</li>
 * <li>A numbering label is put in front of the text, e.g. "1. First".</li>
 * <li>Nodes carry structure, text, src, style and the paragraphs' run facts ({@link Facts}):
 *     no json / rst, the client builds them.</li>
 * <li>The fullpath is the fullpath semantics' (semantic.DA ShFullpath2), not composed here; only the
 *     doc root's is given, its own nid ({@code new Resulving(nodes, nid)}). The handler needs a row's
 *     parent in the db or earlier in the same insert: the rows are inserted in depth first order,
 *     parents first.</li>
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
		/** the doc root's nid */
		public String root;
	}

	/** An open section: on the stack until a heading of its level or upper comes. */
	static class Section {
		final DocxNode node;
		/** outline level, the doc root: -2 */
		final int level;
		/** children so far, the next one's sibling sort is + 1 */
		int children;

		Section(DocxNode node, int level) {
			this.node = node;
			this.level = level;
		}
	}

	final String conn;
	final S_DocNodeMeta nodem;
	final IUser usr;
	final DATranscxt st;

	/** each node's parent, until the node is inserted and its parentId known */
	final IdentityHashMap<DocxNode, DocxNode> parents = new IdentityHashMap<DocxNode, DocxNode>();

	/** where the pictures are saved; null: not saved, an image's resId is its part in the docx */
	DocResHelper res;
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
	public Outliner resources(DocResHelper res, String uid, String docx) {
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

	/** Without a db, for tests overriding {@link #insertRoot(DocxNode)} and {@link #insert(ArrayList)}. */
	Outliner() {
		this.conn = null;
		this.nodem = new S_DocNodeMeta();
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
	 * @return title and the doc root's nid; the nodes are in the db
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

		DocxNode root = new DocxNode(docId, null, DocxNode.Kind.doc);
		root.sort = 1;
		root.ptext = o.title;
		// its fullpath, its own nid, is the prefix of the doc's subtree, unique in the nodes table
		insertRoot(root);
		o.root = root.nid;

		Deque<Section> stack = new ArrayDeque<Section>();
		stack.push(new Section(root, -2));

		// rows not inserted yet, in the document's order: depth first, parents first
		ArrayList<DocxNode> rows = new ArrayList<DocxNode>();

		for (Block b : blocks) {
			if (b == docxTitle)
				continue;
			if (!b.isHeading() && isblank(b.text))
				continue;

			String kind = b.isHeading() ? DocxNode.Kind.section
						: Block.table.equals(b.kind) ? DocxNode.Kind.tabl
						: Block.image.equals(b.kind) ? DocxNode.Kind.image : DocxNode.Kind.p;

			// an upper (or the same) level: the sections down to it are finished, insert them
			if (b.isHeading())
				pop2db(stack, b.outline, rows);

			Section parent = stack.peek();

			// nid: the auto key; parentId: when inserted; fullpath: the semantics', by parentId & sort
			DocxNode node = new DocxNode(docId, null, kind).parent(null, ++parent.children);
			parents.put(node, parent.node);
			node.ptext = isblank(b.numLabel) ? b.text : b.numLabel + " " + b.text;
			node.src = b.src;
			if (!b.isHeading() && !b.runs.isEmpty()) {
				ArrayList<Facts> runs = new ArrayList<Facts>(b.runs);
				if (!isblank(b.numLabel))
					runs.add(0, new Facts(b.numLabel + " "));
				node.facts = Facts.factsOf(runs);
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
	void pop2db(Deque<Section> stack, int level, ArrayList<DocxNode> rows) throws TransException, SQLException {
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
	 * Insert the doc root, alone: its fullpath is its own nid, the latest key generated
	 * ({@code new Resulving(nodes, nid)}), resolved by the fullpath semantics after the autopk's.
	 * Sets the root's nid and fullpath.
	 *
	 * @param root
	 */
	void insertRoot(DocxNode root) throws TransException, SQLException {
		SemanticObject res = (SemanticObject) st.insert(nodem.tbl, usr)
			.nv(nodem.docId, root.docId)
			.nv(nodem.sort, root.sort)
			.nv(nodem.kind, root.kind)
			.nv(nodem.ptext, root.ptext)
			.nv(nodem.fullpath, new Resulving(nodem.tbl, nodem.pk))
			.ins(st.instancontxt(conn, usr));
		root.nid = res.resulve(nodem.tbl, nodem.pk, -1);
		root.fullpath = root.nid;
	}

	/**
	 * A row's parentId: the parent's nid if inserted, else the key of the parent's row in the same insert.
	 * The rows are in depth first order, parents first, so a parent in the insert is an earlier row,
	 * already indexed when its children come.
	 *
	 * @param n
	 * @param ix index of the insert's rows before n
	 * @return nid or {@link Resulving}
	 */
	ExprPart parentRef(DocxNode n, IdentityHashMap<DocxNode, Integer> ix) throws TransException {
		DocxNode p = parents.get(n);
		if (p.nid != null)
			return ExprPart.constr(p.nid); // a string, quoted: constVal() isn't
		Integer i = ix.get(p);
		if (i == null)
			throw new TransException("Outliner: the parent of %s isn't inserted before it.", n.ptext);
		return new Resulving(nodem.tbl, nodem.pk, i);
	}

	/**
	 * The rows are inserted: their nids, in the order of the rows, and then their parentIds.
	 *
	 * @param rows
	 * @param nids generated by the insert
	 */
	void inserted(ArrayList<DocxNode> rows, List<String> nids) throws TransException {
		if (nids == null || nids.size() != rows.size())
			throw new TransException("Outliner: %s nodes inserted, but %s keys generated.",
					rows.size(), nids == null ? 0 : nids.size());
		for (int i = 0; i < rows.size(); i++)
			rows.get(i).nid = nids.get(i);
		for (DocxNode n : rows)
			n.parentId = parents.remove(n).nid;
	}

	/**
	 * Insert the nodes as one multi-row statement, cols() &amp; values(), then {@link #inserted(ArrayList, List)}.
	 * The nid, the last column, is left out by every row, generated by the autopk semantics; the fullpath
	 * is composed by the fullpath semantics, so a row's parent must be in the db or earlier in the rows.
	 *
	 * @param nodes in depth first order; nothing is done if empty
	 */
	void insert(ArrayList<DocxNode> nodes) throws TransException, SQLException {
		if (nodes.isEmpty())
			return;

		ArrayList<ArrayList<Object[]>> vals = new ArrayList<ArrayList<Object[]>>(nodes.size());
		IdentityHashMap<DocxNode, Integer> ix = new IdentityHashMap<DocxNode, Integer>(nodes.size());
		for (int i = 0; i < nodes.size(); i++) {
			DocxNode n = nodes.get(i);
			ArrayList<Object[]> row = new ArrayList<Object[]>();
			row.add(new Object[] { nodem.docId, n.docId });
			row.add(new Object[] { nodem.parentId, parentRef(n, ix) });
			ix.put(n, i);
			row.add(new Object[] { nodem.sort, n.sort });
			row.add(new Object[] { nodem.kind, n.kind });
			row.add(new Object[] { nodem.ptext, n.ptext });
			row.add(new Object[] { nodem.facts, n.facts });
			row.add(new Object[] { nodem.src, n.src });
			row.add(new Object[] { nodem.docxstyle, n.docxstyle });
			row.add(new Object[] { nodem.resId, n.resId });
			vals.add(row);
		}

		SemanticObject res = (SemanticObject) st.insert(nodem.tbl, usr)
			.cols(new String[] { nodem.docId, nodem.parentId, nodem.sort, nodem.kind,
				nodem.ptext, nodem.facts, nodem.src, nodem.docxstyle, nodem.resId, nodem.pk })
			.values(vals)
			.ins(st.instancontxt(conn, usr));
		inserted(nodes, res.resulve(nodem.tbl, nodem.pk));
	}
}
