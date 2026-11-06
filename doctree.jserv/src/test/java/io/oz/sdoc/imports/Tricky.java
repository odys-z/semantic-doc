package io.oz.sdoc.imports;

import java.util.Arrays;
import java.util.List;

/**
 * Blocks of hello/docx4j's samples/tricky.docx, as FactsDemo prints them (see its README),
 * plus an empty paragraph (body/7a), a quote in "Second bullet, Ody's", and run facts in body/2.
 */
class Tricky {
	static List<Block> blocks() {
		Block tbl = new Block(Block.table, "body/8").text(
			"merged header {colspan=2} | r0c2 // r1c0 | r1c1 | vertical merge {vmerge-start} // r2c0 | r2c1 |  {vmerge-cont}");
		tbl.hasSpans = true;
		return Arrays.asList(
			new Block(Block.p, "body/0").style("Title", "Title").text("Annual Report").outline(-1),
			new Block(Block.p, "body/1").style("章标题", "章标题").text("第一章 总则").outline(0),
			body2(),
			new Block(Block.p, "body/3").style("Heading2", "heading 2").text("1.1 Scope").outline(1),
			new Block(Block.p, "body/4").style("Normal", "Normal").text("Direct outline level on a Normal paragraph").outline(1),
			new Block(Block.p, "body/5").style("Clause", "Clause").text("Clause 7: confidentiality").outline(2),
			new Block(Block.p, "body/6").style("ListNumber", "List Number").text("First bullet").num("1."),
			new Block(Block.p, "body/7").style("ListNumber", "List Number").text("Second bullet, Ody's").num("2."),
			new Block(Block.p, "body/7a").style("Normal", "Normal").text(""),
			tbl,
			new Block(Block.p, "body/9/sdt/0").style("Heading1", "heading 1").text("第二章 Inside a content control").outline(0),
			new Block(Block.p, "body/10").style("Normal", "Normal").text("Body text under chapter two."));
	}

	/** body/2, with run facts: "under" bold and green */
	static Block body2() {
		Block b = new Block(Block.p, "body/2").style("Normal", "Normal").text("Body text under chapter one.");
		Run under = new Run("under");
		under.b = true;
		under.color = "1E8449";
		b.runs.add(new Run("Body text "));
		b.runs.add(under);
		b.runs.add(new Run(" chapter one."));
		return b;
	}

	/** expected tree: "nid parentId sort fullpath kind text" in tree order; the root's nid and fullpath are the docId */
	static String[] tree(String docId) {
		return new String[] {
			String.format("%1$s null 1 %1$s doc Annual Report", docId),
			String.format("%1$s.1 %1$s 1 %1$s.0001 section 第一章 总则", docId),
			String.format("%1$s.2 %1$s.1 1 %1$s.0001.0001 p Body text under chapter one.", docId),
			String.format("%1$s.3 %1$s.1 2 %1$s.0001.0002 section 1.1 Scope", docId),
			String.format("%1$s.4 %1$s.1 3 %1$s.0001.0003 section Direct outline level on a Normal paragraph", docId),
			String.format("%1$s.5 %1$s.4 1 %1$s.0001.0003.0001 section Clause 7: confidentiality", docId),
			String.format("%1$s.6 %1$s.5 1 %1$s.0001.0003.0001.0001 p 1. First bullet", docId),
			String.format("%1$s.7 %1$s.5 2 %1$s.0001.0003.0001.0002 p 2. Second bullet, Ody's", docId),
			String.format("%1$s.8 %1$s.5 3 %1$s.0001.0003.0001.0003 tabl merged header {colspan=2} | r0c2 // r1c0 | r1c1 | vertical merge {vmerge-start} // r2c0 | r2c1 |  {vmerge-cont}", docId),
			String.format("%1$s.9 %1$s 2 %1$s.0002 section 第二章 Inside a content control", docId),
			String.format("%1$s.a %1$s.9 1 %1$s.0002.0001 p Body text under chapter two.", docId),
		};
	}
}
