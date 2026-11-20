package io.oz.syn.dbpicks;

import java.util.ArrayList;

import io.odysz.anson.Anson;

/**
 * A record the current version needs in a db, inserted if missing, e.g. by
 * {@link io.oz.sdoc.DoctreeDBUpgrade}; an item of {@link DbPicks#inits}, e.g. the sequence of a
 * new auto key in oz_autoseq.
 *
 * <p>A record is missing if no row of {@link #tabl} has the values of {@link #key} columns.
 * Existing records, e.g. a sequence copied from the old db, are never changed.</p>
 *
 * @since doctree.jserv 0.1.4
 * @author ody
 */
public class DbInitRow extends Anson {
	/** "DB-PICKING-&lt;package version&gt; &lt;table&gt;:&lt;key values&gt;", also in a comment of the source line that needs it; see {@link DbPicks} */
	public String mark;

	public String tabl;

	/** columns identifying the record, of {@link #nvs} */
	public String[] key;

	/** [column, string constant] */
	public ArrayList<String[]> nvs;

	/**
	 * [column, table], the column's value is the count of the table's rows, e.g. a sequence
	 * starting past the rows a table has: ["seq", "s_doctable3"]
	 */
	public ArrayList<String[]> counts;

	public DbInitRow() { }
}
