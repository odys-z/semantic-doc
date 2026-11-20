package io.oz.sdoc.imports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.odysz.transact.sql.parts.Resulving;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.DocNode;
import io.oz.sdoc.imports.Outliner.Outline;

/**
 * The outliner without a db: the inserts are collected, a line per statement.
 * The nids, auto keys, are k0, k1, ... in the order of generation, as the db's would be;
 * the fullpaths are the semantics handler's: see ImporterTest.
 */
class OutlinerTest {

	/** Collects what would be inserted. */
	static class Collecting extends Outliner {
		/** nids of each insert statement */
		final ArrayList<String> inserts = new ArrayList<String>();
		final ArrayList<DocNode> nodes = new ArrayList<DocNode>();
		/** each insert's parentIds, as given: a nid, or [i] for the insert's i-th row's key (a Resulving) */
		final ArrayList<String> parentRefs = new ArrayList<String>();
		int k = 0;

		@Override
		void insertRoot(DocNode root) {
			root.nid = "k" + k++;
			root.fullpath = root.nid;
			inserts.add(root.nid);
			nodes.add(root);
		}

		@Override
		void insert(ArrayList<DocNode> rows) throws TransException {
			if (rows.isEmpty())
				return;
			ArrayList<String> refs = new ArrayList<String>();
			for (DocNode n : rows) {
				ExprPart p = parentRef(n, rows);
				refs.add(p instanceof Resulving ? "[" + rows.indexOf(parents.get(n)) + "]" : p.sql(null));
			}
			parentRefs.add(String.join(" ", refs));

			ArrayList<String> nids = new ArrayList<String>();
			for (int i = 0; i < rows.size(); i++)
				nids.add("k" + k++);
			inserted(rows, nids);
			inserts.add(String.join(" ", nids));
			nodes.addAll(rows);
		}
	}

	/**
	 * <pre>
	 * k0 root
	 *   k1 section 第一章         [k2 p, k3 section 1.1 Scope, k4 section]
	 *     k4 section            [k5 section Clause 7]
	 *       k5 section          [k6 p, k7 p, k8 tabl]
	 *   k9 section 第二章         [k10 p]
	 * </pre>
	 */
	@Test
	void tricky() throws Exception {
		Collecting c = new Collecting();
		Outline o = c.outline("X.00000A", Tricky.blocks(), null, "tricky.docx");

		assertEquals("Annual Report", o.title);
		assertEquals("k0", o.root);
		assertEquals(11, c.nodes.size());

		// the doc root first, alone; then inserted when sections are popped: k3 by k4, then k5, k4, k1 by 第二章,
		// the rest by the end, popping the doc root; depth first, a row's parent is in an earlier insert or
		// earlier in the same one
		assertArrayEquals(new String[] {
				"k0",
				"k1 k2 k3",
				"k4 k5 k6 k7 k8",
				"k9 k10"},
			c.inserts.toArray(new String[0]));

		// parents inserted already: their nids; in the same insert: the key of their rows
		assertArrayEquals(new String[] {
				"k0 [0] [0]",
				"k1 [0] [1] [1] [1]",
				"k0 [0]"},
			c.parentRefs.toArray(new String[0]));

		// in tree order as they are; only the doc root's fullpath is given, its nid
		String[] tree = Tricky.tree("k0", "k1", "k2", "k3", "k4", "k5", "k6", "k7", "k8", "k9", "k10");
		for (int i = 0; i < tree.length; i++) {
			DocNode n = c.nodes.get(i);
			String[] expect = tree[i].split(" ", 6); // nid parentId sort fullpath kind text
			assertEquals(expect[0], n.nid);
			assertEquals(expect[1], String.valueOf(n.parentId));
			assertEquals(expect[2], String.valueOf(n.sort));
			assertEquals(i == 0 ? "k0" : null, n.fullpath);
			assertEquals(expect[4], n.kind);
			assertEquals(expect[5], n.ptext);
		}

		assertEquals("body/8", c.nodes.get(8).src);
		assertEquals("heading 1", c.nodes.get(9).docxstyle);
	}

	@Test
	void noTitle() throws Exception {
		Collecting c = new Collecting();
		Outline o = c.outline("X.00000B", Tricky.blocks().subList(1, 4), null, "b.docx");
		assertEquals("b.docx", o.title);
		assertEquals(4, c.nodes.size());
		assertArrayEquals(new String[] {"k0", "k1 k2 k3"}, c.inserts.toArray(new String[0]));
		assertEquals("b.docx", c.nodes.get(0).ptext);

		// a picture: an image node; without a resource folder the resId is the docx part
		ArrayList<Block> pic = new ArrayList<Block>(Tricky.blocks().subList(1, 3));
		pic.add(new Block(Block.image, "body/2/pic/0").res("word/media/image1.png").text("image1.png"));
		c = new Collecting();
		c.outline("X.00000D", pic, null, "d.docx");
		DocNode img = c.nodes.get(3);
		assertEquals(DocNode.Kind.image, img.kind);
		assertEquals("k1", img.parentId);
		assertEquals("word/media/image1.png", img.resId);
		assertEquals("image1.png", img.ptext);

		// the caller's title is the first choice
		assertEquals("Given", new Collecting().outline("X.00000C", Tricky.blocks(), "Given", "c.docx").title);
	}
}
