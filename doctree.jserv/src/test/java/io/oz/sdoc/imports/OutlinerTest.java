package io.oz.sdoc.imports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.oz.sdoc.DocNode;
import io.oz.sdoc.imports.Outliner.Outline;

class OutlinerTest {

	@Test
	void tricky() {
		Outline o = Outliner.outline("X.00000A", 4, Tricky.blocks());

		assertEquals("Annual Report", o.title);
		assertEquals(11, o.nextNid);
		assertArrayEquals(Tricky.tree("X.00000A"), o.nodes.stream().map(OutlinerTest::line).toArray(String[]::new));

		DocNode table = o.nodes.get(8);
		assertEquals("body/8", table.src);
		assertEquals("heading 1", o.nodes.get(9).docxstyle);
	}

	@Test
	void noTitle() {
		Outline o = Outliner.outline("X.00000B", 4, Tricky.blocks().subList(1, 4));
		assertEquals(null, o.title);
		assertEquals(4, o.nodes.size());
		assertEquals("X.00000B", o.nodes.get(0).fullpath);
		assertEquals("X.00000B.0001.0002", o.nodes.get(3).fullpath);
	}

	static String line(DocNode n) {
		return String.join(" ", n.nid, String.valueOf(n.parentId), String.valueOf(n.sort), n.fullpath, n.kind, n.ptext);
	}
}
