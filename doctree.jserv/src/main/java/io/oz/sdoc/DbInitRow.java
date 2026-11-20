package io.oz.sdoc;

import java.util.ArrayList;

import io.odysz.anson.Anson;

/**
 * A record the current version needs in the doc-tree db, inserted by {@link DoctreeDBUpgrade}
 * if missing, an item of {@link DbPicks#inits}; e.g. the sequence of a new auto key in oz_autoseq.
 *
 * <p>A record is missing if no row of {@link #tabl} has the values of {@link #key} columns.
 * Existing records, e.g. a sequence copied from the old db, are never changed.</p>
 *
 * @since 0.1.4
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
	 * [column, sql], a value selected in the db after its tables are copied, e.g.
	 * a sequence starting past the rows a table has: "select count(*) from s_doctable3"
	 */
	public ArrayList<String[]> sqls;

	public DbInitRow() { }
}
