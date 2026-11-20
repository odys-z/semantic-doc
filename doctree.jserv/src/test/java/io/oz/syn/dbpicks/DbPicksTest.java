package io.oz.syn.dbpicks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Loading a picking list, the test copy of jserv-album's bin/doctree-picks.json.
 * The marks are checked both ways by jserv-album's package build (semanticshare.io.oz.syn.dbpicks),
 * against the sources.
 */
class DbPicksTest {
	static final String doctreePicks = "src/test/res/doctree-picks.json";

	@Test
	void load() throws Exception {
		DbPicks picks = DbPicks.load(doctreePicks);

		DbPick t = picks.pick("s_docs", "template");
		assertEquals("DB-PICKING-0.8.2 s_docs.template", t.mark);
		assertEquals("rtd-0.8", t.dflt);
		assertEquals("isNotnull", t.when.op);
		assertEquals("builtAt", t.when.l);
		String sql = t.when.predicate().sql(null);
		assertTrue(sql.matches("\\s*builtAt\\s+is\\s+not\\s+null\\s*"), sql);
		assertEquals(2, picks.pick("s_docnodes", "tipjson").names.length);

		DbInitRow seq = picks.inits.get(0);
		assertEquals("oz_autoseq", seq.tabl);
		assertEquals("s_docnodes.nid", seq.nvs.get(0)[1]);

		for (DbPick p : picks.picks)
			assertTrue(p.mark.startsWith(DbPicks.mark), p.mark);
		for (DbInitRow r : picks.inits)
			assertTrue(r.mark.startsWith(DbPicks.mark), r.mark);
	}

	@Test
	void none() throws Exception {
		assertNull(DbPicks.load(null));
		assertNull(DbPicks.load("src/test/res/no-such-picks.json"));
	}
}
