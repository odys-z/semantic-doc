package io.oz.sdoc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.odysz.common.Utils;
import io.odysz.semantic.meta.SemanticTableMeta;

/**
 * Upgrade a doc-tree db file ({@link DoctreeDBHelper#dbfile}) of an earlier version, the same way
 * as the synode upgrader's config picks (semanticshare ConfigPicks): the new version's db is the
 * template, with the installed values picked.
 *
 * <ol>
 * <li>A new db file, &lt;db&gt;.new, is created with the tables of the current metas' DDL.</li>
 * <li>Each table's rows are copied, column by column: a column's value is the old table's column of
 *     the first of its names ({@link #picks}, the newest first) found, else its own name; columns found
 *     in none are the new version's defaults; old columns not picked are dropped (e.g. s_docs.nextNid).</li>
 * <li>Tables without a meta, e.g. oz_autoseq, the auto keys' sequences, are copied as they are
 *     ("insert or replace": a sequence the semantics has seeded is overwritten by the old one).</li>
 * <li>The row counts are checked; the old file is renamed to &lt;db&gt;.&lt;time&gt;.bak,
 *     the new one to &lt;db&gt;.</li>
 * </ol>
 *
 * <p>The db is upgraded only if a meta's table has columns different from the db's, so no version of
 * the db is needed. Done before the db is connected ({@link DoctreeDBHelper#connect(String, String, String)}),
 * with plain jdbc: the file is renamed, which an open connection, e.g. on Windows, won't allow.</p>
 *
 * <p>Nodes keep their ids: those of an earlier version (docId + '.' + n, the doc root: the docId)
 * never equal an auto key, which has no '.'.</p>
 *
 * <p>When the tables change in a later version: change the metas' DDL; for a renamed column, add its
 * old name to {@link #picks}; a column whose values change meaning needs its own conversion here.</p>
 *
 * @since 0.1.4
 * @author ody
 */
public class DoctreeDBUpgrade {

	/**
	 * Renamed columns: {table: {column: the names it ever had, the newest first}}.
	 * Columns not listed are picked by their own names.
	 */
	public static final Map<String, Map<String, String[]>> picks = new HashMap<String, Map<String, String[]>>() {
		private static final long serialVersionUID = 1L;
		{
			put("s_docnodes", new HashMap<String, String[]>() {
				private static final long serialVersionUID = 1L;
				{
					put("tipjson",   new String[] { "tipjson", "json" });
					put("rstext",    new String[] { "rstext", "rst" });
					put("docxstyle", new String[] { "docxstyle", "style" });
				}
			});
		}
	};

	/**
	 * Upgrade the db file if its tables differ from the metas'.
	 *
	 * @param dbpath the db file; nothing is done if there is none (a new db)
	 * @param metas the tables of the current version, with ddlSqlite
	 * @return the backup of the old file; null if not upgraded
	 * @throws Exception the old file is kept as it is, the new one deleted
	 */
	public static File upgrade(String dbpath, SemanticTableMeta... metas) throws Exception {
		File db = new File(dbpath);
		if (!db.isFile() || db.length() == 0)
			return null;

		File neu = new File(dbpath + ".new");
		Files.deleteIfExists(neu.toPath());

		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + neu.getAbsolutePath())) {
			try (Statement s = c.createStatement()) {
				s.execute(String.format("attach database '%s' as old", db.getAbsolutePath().replace("'", "''")));
			}

			if (!differs(c, metas)) {
				detach(c);
				c.close();
				Files.deleteIfExists(neu.toPath());
				return null;
			}

			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				Set<String> copied = new LinkedHashSet<String>();
				for (SemanticTableMeta m : metas) {
					s.execute(m.ddlSqlite);
					if (columns(c, "old", m.tbl).isEmpty())
						continue; // a new table
					String[] sel = select(m.tbl, columns(c, "main", m.tbl), columns(c, "old", m.tbl));
					s.execute(String.format("insert into main.%s (%s) select %s from old.%s",
							m.tbl, sel[0], sel[1], m.tbl));
					copied.add(m.tbl);
				}

				// tables without metas, e.g. oz_autoseq
				for (String[] t : others(c, metas)) {
					s.execute(t[1]);
					s.execute(String.format("insert or replace into main.%1$s select * from old.%1$s", t[0]));
					copied.add(t[0]);
				}

				for (String t : copied) {
					long n0 = count(c, "old", t), n1 = count(c, "main", t);
					if (n0 != n1)
						throw new SQLException(String.format("Upgrading %s: %s rows copied of %s.", t, n1, n0));
				}
			}
			c.commit();
			c.setAutoCommit(true);
			detach(c);
		} catch (Exception e) {
			Files.deleteIfExists(neu.toPath());
			throw e;
		}

		File bak = new File(String.format("%s.%s.bak", dbpath, new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())));
		Files.move(db.toPath(), bak.toPath());
		Files.move(neu.toPath(), db.toPath(), StandardCopyOption.REPLACE_EXISTING);
		Utils.logi("[DoctreeDBUpgrade] %s upgraded; the old file: %s", dbpath, bak.getName());
		return bak;
	}

	/**
	 * The columns of the new table, and what's selected from the old one for each of them.
	 * @return [column list, select list], comma separated
	 */
	static String[] select(String tbl, ArrayList<String> neucols, ArrayList<String> oldcols) {
		Map<String, String[]> renamed = picks.get(tbl);
		ArrayList<String> into = new ArrayList<String>();
		ArrayList<String> from = new ArrayList<String>();
		for (String col : neucols) {
			String[] names = renamed != null && renamed.containsKey(col) ? renamed.get(col) : new String[] { col };
			for (String n : names)
				if (containsIgnoreCase(oldcols, n)) {
					into.add(col);
					from.add(n);
					break;
				}
		}
		return new String[] { String.join(", ", into), String.join(", ", from) };
	}

	/** whether any meta's table, existing in the old db, has columns other than the meta's */
	static boolean differs(Connection c, SemanticTableMeta... metas) throws SQLException {
		for (SemanticTableMeta m : metas) {
			ArrayList<String> old = columns(c, "old", m.tbl);
			if (old.isEmpty())
				continue;

			// the meta's columns, of its DDL in a scratch table
			String scratch = "upgrade_" + m.tbl;
			try (Statement s = c.createStatement()) {
				s.execute(m.ddlSqlite.replaceFirst("(?i)(CREATE TABLE\\s+(if not exists\\s+)?)\\w+", "$1temp." + scratch));
			}
			ArrayList<String> neu = columns(c, "temp", scratch);
			try (Statement s = c.createStatement()) {
				s.execute("drop table temp." + scratch);
			}

			if (neu.size() != old.size())
				return true;
			for (String col : neu)
				if (!containsIgnoreCase(old, col))
					return true;
		}
		return false;
	}

	/** tables of the old db without metas: [name, its create sql] */
	static ArrayList<String[]> others(Connection c, SemanticTableMeta... metas) throws SQLException {
		Set<String> mt = new LinkedHashSet<String>();
		for (SemanticTableMeta m : metas)
			mt.add(m.tbl.toLowerCase());

		ArrayList<String[]> ts = new ArrayList<String[]>();
		try (Statement s = c.createStatement();
			 ResultSet rs = s.executeQuery("select name, sql from old.sqlite_master where type = 'table' and name not like 'sqlite_%'")) {
			while (rs.next())
				if (!mt.contains(rs.getString(1).toLowerCase()))
					ts.add(new String[] { rs.getString(1),
						rs.getString(2).replaceFirst("(?i)^CREATE TABLE\\s+(if not exists\\s+)?", "CREATE TABLE if not exists ") });
		}
		return ts;
	}

	static ArrayList<String> columns(Connection c, String schema, String tbl) throws SQLException {
		ArrayList<String> cols = new ArrayList<String>();
		try (Statement s = c.createStatement();
			 ResultSet rs = s.executeQuery(String.format("pragma %s.table_info(%s)", schema, tbl))) {
			while (rs.next())
				cols.add(rs.getString("name"));
		}
		return cols;
	}

	static long count(Connection c, String schema, String tbl) throws SQLException {
		try (Statement s = c.createStatement();
			 ResultSet rs = s.executeQuery(String.format("select count(*) from %s.%s", schema, tbl))) {
			return rs.next() ? rs.getLong(1) : 0;
		}
	}

	static void detach(Connection c) throws SQLException {
		try (Statement s = c.createStatement()) {
			s.execute("detach database old");
		}
	}

	static boolean containsIgnoreCase(ArrayList<String> cols, String col) {
		for (String c : cols)
			if (c.equalsIgnoreCase(col))
				return true;
		return false;
	}
}
