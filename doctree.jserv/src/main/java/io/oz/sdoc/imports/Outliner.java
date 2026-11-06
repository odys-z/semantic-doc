package io.oz.sdoc.imports;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.leftPad;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;

import io.oz.sdoc.DocNode;
import io.oz.sdoc.DoctreeDBHelper;

/**
 * Builds a doc tree from {@link Block}s, in node records instead of json.
 *
 * <pre>
 * doc root (nid = fullpath = docId, text = title)
 *   a heading     → section, nested by outline level (a heading pops the stack until the top is shallower)
 *   anything else → p / table, under the current section
 * </pre>
 *
 * <ul>
 * <li>The first Title-styled block (outline -1) is the doc's title, not a node.</li>
 * <li>Empty paragraphs (spacing) are dropped; empty headings are kept.</li>
 * <li>A numbering label is put in front of the text, e.g. "1. First".</li>
 * <li>Nodes carry structure, text, src, style and the paragraphs' run facts ({@link Run}):
 *     no json / rst, the client builds them.</li>
 * </ul>
 *
 * @author ody
 */
public class Outliner {

	/** The tree as records, in tree order (parents first). */
	public static class Outline {
		public String title;
		public final ArrayList<DocNode> nodes = new ArrayList<DocNode>();
		/** next node number of the doc, after the nodes */
		public int nextNid;
	}

	/**
	 * @param docId
	 * @param siblingWidth digits of a sibling sort in fullpath, the fullpath semantics' args[3],
	 * see {@link io.oz.sdoc.S_DocNodeMeta#siblingWidth()}
	 * @param blocks in document order
	 * @return the tree, nodes[0] is the doc root
	 */
	public static Outline outline(String docId, int siblingWidth, List<Block> blocks) {
		Outline o = new Outline();

		DocNode root = new DocNode(docId, docId, DocNode.Kind.doc);
		root.sort = 1;
		root.fullpath = docId; // the prefix of the doc's subtree, unique in the nodes table
		o.nodes.add(root);

		HashMap<String, Integer> sorts = new HashMap<String, Integer>(); // children counted by parent nid
		HashMap<String, Integer> levels = new HashMap<String, Integer>(); // outline of the sections
		levels.put(docId, -2);

		Deque<DocNode> stack = new ArrayDeque<DocNode>();
		stack.push(root);

		int n = 1;
		for (Block b : blocks) {
			if (o.title == null && b.isHeading() && b.outline < 0) {
				o.title = b.text;
				continue;
			}
			if (!b.isHeading() && isblank(b.text))
				continue;

			String kind = b.isHeading() ? DocNode.Kind.section
						: Block.table.equals(b.kind) ? DocNode.Kind.table : DocNode.Kind.p;

			if (b.isHeading())
				while (stack.peek() != root && levels.get(stack.peek().nid) >= b.outline)
					stack.pop();

			DocNode parent = stack.peek();
			int sort = sorts.merge(parent.nid, 1, Integer::sum);

			DocNode node = new DocNode(docId, DoctreeDBHelper.nid(docId, n++), kind).parent(parent.nid, sort);
			node.fullpath = parent.fullpath + "." + leftPad(String.valueOf(sort), siblingWidth, '0');
			node.ptext = isblank(b.numLabel) ? b.text : b.numLabel + " " + b.text;
			node.src = b.src;
			if (!b.isHeading() && !b.runs.isEmpty()) {
				ArrayList<Run> runs = new ArrayList<Run>(b.runs);
				if (!isblank(b.numLabel))
					runs.add(0, new Run(b.numLabel + " "));
				node.facts = Run.json(runs);
			}
			node.style = b.styleName;
			o.nodes.add(node);

			if (b.isHeading()) {
				levels.put(node.nid, b.outline);
				stack.push(node);
			}
		}

		root.ptext = o.title;
		o.nextNid = n;
		return o;
	}
}
