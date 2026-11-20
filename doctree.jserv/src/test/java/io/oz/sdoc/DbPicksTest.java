package io.oz.sdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Loading a picking list, the test copy of jserv-album's bin/db-picks.json.
 * The marks are checked both ways by jserv-album's package build (picking.py), against the sources.
 */
class DbPicksTest {
	@Test
	void load() throws Exception {
		DbPicks picks = DbPicks.load(DoctreeDBUpgradeTest.picks);

		DbPick t = picks.pick("s_docs", "template");
		assertEquals("DB-PICKING-0.8.2 s_docs.template", t.mark);
		assertEquals("rtd-0.8", t.dflt);
		assertEquals("builtAt is not null", t.where);
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
