package io.oz.sdoc.imports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.oz.sdoc.DocNode;
import io.oz.sdoc.imports.Outliner.Outline;

/**
 * The outliner without a db: the inserts are collected, a line per statement.
 * The fullpaths are the semantics handler's: see ImporterTest.
 */
class OutlinerTest {

	/** Collects what would be inserted. */
	static class Collecting extends Outliner {
		/** nids of each insert statement */
		final ArrayList<String> inserts = new ArrayList<String>();
		final ArrayList<DocNode> nodes = new ArrayList<DocNode>();

		@Override
		void insert(ArrayList<DocNode> rows) {
			if (rows.isEmpty())
				return;
			inserts.add(String.join(" ", rows.stream().map((n) -> n.nid).toArray(String[]::new)));
			nodes.addAll(rows);
		}
	}

	/**
	 * <pre>
	 * root
	 *   .1 section 第一章         [.2 p, .3 section 1.1 Scope, .4 section]
	 *     .4 section            [.5 section Clause 7]
	 *       .5 section          [.6 p, .7 p, .8 tabl]
	 *   .9 section 第二章         [.a p]
	 * </pre>
	 */
	@Test
	void tricky() throws Exception {
		Collecting c = new Collecting();
		Outline o = c.outline("X.00000A", Tricky.blocks(), null, "tricky.docx");

		assertEquals("Annual Report", o.title);
		assertEquals(11, o.nextNid);
		assertEquals(11, c.nodes.size());

		// inserted when sections are popped: .3 by .4, then .5, .4, .1 by 第二章, the rest by the end, popping the doc root;
		// depth first, a row's parent is in an earlier insert or earlier in the same one
		assertArrayEquals(new String[] {
				"X.00000A X.00000A.1 X.00000A.2 X.00000A.3",
				"X.00000A.4 X.00000A.5 X.00000A.6 X.00000A.7 X.00000A.8",
				"X.00000A.9 X.00000A.a"},
			c.inserts.toArray(new String[0]));

		// in tree order as they are; only the doc root's fullpath is given
		String[] tree = Tricky.tree("X.00000A");
		for (int i = 0; i < tree.length; i++) {
			DocNode n = c.nodes.get(i);
			String[] expect = tree[i].split(" ", 6); // nid parentId sort fullpath kind text
			assertEquals(expect[0], n.nid);
			assertEquals(expect[1], String.valueOf(n.parentId));
			assertEquals(expect[2], String.valueOf(n.sort));
			assertEquals(i == 0 ? "X.00000A" : null, n.fullpath);
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
		assertArrayEquals(new String[] {"X.00000B X.00000B.1 X.00000B.2 X.00000B.3"},
				c.inserts.toArray(new String[0]));
		assertEquals("b.docx", c.nodes.get(0).ptext);

		// a picture: an image node; without a resource folder the resId is the docx part
		ArrayList<Block> pic = new ArrayList<Block>(Tricky.blocks().subList(1, 3));
		pic.add(new Block(Block.image, "body/2/pic/0").res("word/media/image1.png").text("image1.png"));
		c = new Collecting();
		c.outline("X.00000D", pic, null, "d.docx");
		DocNode img = c.nodes.get(3);
		assertEquals(DocNode.Kind.image, img.kind);
		assertEquals("X.00000D.1", img.parentId);
		assertEquals("word/media/image1.png", img.resId);
		assertEquals("image1.png", img.ptext);

		// the caller's title is the first choice
		assertEquals("Given", new Collecting().outline("X.00000C", Tricky.blocks(), "Given", "c.docx").title);
	}
}
