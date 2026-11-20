package io.oz.syn.dbpicks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import io.odysz.anson.Anson;

/**
 * A db's picking list: how an upgrade picks an earlier version's db into the current tables
 * ({@link #picks}), and the records the current version needs ({@link #inits}).
 *
 * <p>One list per db, a file of the app's package named after the db, beside bin/resource.json,
 * not a resource in a jar (text resources in jars don't load the same on every SDK, e.g. Android's),
 * e.g. the doc-tree db's &lt;install&gt;/bin/doctree-picks.json (source: jserv-album/src/main/doctree-picks.json),
 * read by {@link io.oz.sdoc.DoctreeDBUpgrade}.</p>
 *
 * <p>The items are generated (by Claude) and merged with the earlier versions' at each upgrade,
 * never edited by hand. Each item's {@code mark}, "DB-PICKING-&lt;package version&gt; ...", e.g.
 * "DB-PICKING-0.8.2 s_docs.template", is also in a comment of the source line that needs it, e.g.
 * the meta's DDL column; the app's package build checks both ways (semantics.py3,
 * semanticshare.io.oz.syn.dbpicks.DbPicks.check_marks(), the Python type of the same json).</p>
 *
 * @since doctree.jserv 0.1.4, Portolio-0.8.2
 * @author ody
 */
public class DbPicks extends Anson {
	/** the marks' prefix, followed by the package version */
	public static final String mark = "DB-PICKING-";

	public ArrayList<DbPick> picks;
	public ArrayList<DbInitRow> inits;

	public DbPicks() { }

	/**
	 * @param path the picking list, e.g. &lt;install&gt;/bin/doctree-picks.json
	 * @return the picks; null if path is null or there is no such file
	 * @throws IOException the file can't be parsed
	 */
	public static DbPicks load(String path) throws IOException {
		if (path == null || !new File(path).isFile())
			return null;
		try {
			DbPicks p = Anson.fromPath(new File(path).getAbsolutePath());
			if (p.picks == null) p.picks = new ArrayList<DbPick>();
			if (p.inits == null) p.inits = new ArrayList<DbInitRow>();
			return p;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("Can't load " + path + ": " + e.getMessage(), e);
		}
	}

	/** the pick of a column, null if none */
	public DbPick pick(String tabl, String col) {
		for (DbPick p : picks)
			if (p.tabl.equalsIgnoreCase(tabl) && p.col.equalsIgnoreCase(col))
				return p;
		return null;
	}
}
