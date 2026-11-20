package io.oz.sdoc;

import io.odysz.anson.Anson;

/**
 * A column of the current tables, picked by {@link DoctreeDBUpgrade} from an earlier version's db,
 * an item of {@link DbPicks#picks}.
 *
 * <p>Its value is the old table's column of the first of {@link #names} found; if none is found,
 * i.e. the column is new, {@link #dflt} where {@link #when} holds, else the DDL's default.
 * A column without a pick is picked by its own name.</p>
 *
 * @since 0.1.4
 * @author ody
 */
public class DbPick extends Anson {
	/**
	 * "DB-PICKING-&lt;package version&gt; &lt;table&gt;.&lt;column&gt;", also in a comment of the source line that needs this pick,
	 * e.g. the meta's DDL column; see {@link DbPicks}.
	 */
	public String mark;

	public String tabl;
	public String col;

	/** the names the column ever had in earlier versions, the newest first; null: its own name */
	public String[] names;

	/** default of a new column, a string constant; null: the DDL's */
	public String dflt;

	/**
	 * the condition on the old table's row for {@link #dflt}, e.g. builtAt is not null,
	 * { "op": "isNotnull", "l": "builtAt" }; null: every row. Rows not matching take null.
	 */
	/** @since 0.1.5, replaced the sql text, where */
	public DbLogic when;

	public DbPick() { }
}
