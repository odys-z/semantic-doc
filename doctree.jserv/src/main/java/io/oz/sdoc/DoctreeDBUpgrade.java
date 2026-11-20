package io.oz.sdoc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import io.odysz.common.Utils;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantic.meta.SemanticTableMeta;
import io.odysz.semantics.x.SemanticException;

/**
 * Upgrade a doc-tree db file ({@link DoctreeDBHelper#dbfile}) of an earlier version, the same way
 * as the synode upgrader's config picks (semanticshare ConfigPicks): the new version's db is the
 * template, with the installed values picked, as the app's picking list ({@link DbPicks},
 * e.g. &lt;install&gt;/bin/db-picks.json) says.
 *
 * <ol>
 * <li>A new db file, &lt;db&gt;.new, is created with the tables of the current metas' DDL.</li>
 * <li>Each table's rows are copied, column by column: a column's value is the old table's column of
 *     the first of its {@link DbPick#names} found (the newest first), else of its own name; a new
 *     column, found in none, takes its pick's default ({@link DbPick#dflt}, where {@link DbPick#where}
 *     holds), else the DDL's; old columns not picked are dropped (e.g. s_docs.nextNid).</li>
 * <li>Tables without a meta, e.g. oz_autoseq, the auto keys' sequences, are copied as they are
 *     ("insert or replace": a sequence the semantics has seeded is overwritten by the old one).</li>
 * <li>The records the current version needs ({@link DbPicks#inits}) are inserted if missing,
 *     e.g. the sequence of a new auto key.</li>
 * <li>The row counts are checked; the old file is renamed to &lt;db&gt;.&lt;time&gt;.bak,
 *     the new one to &lt;db&gt;.</li>
 * </ol>
 *
 * <p>The db is copied only if a meta's table has columns different from the db's, so no version of
 * the db is needed; otherwise only the missing records are inserted, in place. Done before the db is
 * connected ({@link DoctreeDBHelper#connect(String, String, String)}), with plain jdbc: the file is
 * renamed, which an open connection, e.g. on Windows, won't allow.</p>
 *
 * <p>Nodes keep their ids: those of an earlier version (docId + '.' + n, the doc root: the docId)
 * never equal an auto key, which has no '.'.</p>
 *
 * <p>When the tables change in a later version: change the metas' DDL; for a renamed column, a new
 * column needing a default, or a record a new semantics needs, add an item to the picking list,
 * marked as {@link DbPicks} says, merged with the earlier versions' items; a column whose values change meaning needs its own conversion here.</p>
 *
 * @since 0.1.4
 * @author ody
 */
public class DoctreeDBUpgrade {

	/**
	 * Upgrade the db file if its tables differ from the metas'.
	 *
	 * @param dbpath the db file; nothing is done if there is none (a new db)
	 * @param pickspath the picking list, e.g. &lt;install&gt;/bin/db-picks.json, see {@link DbPicks#load(String)}
	 * @param metas the tables of the current version, with ddlSqlite
	 * @return the backup of the old file; null if not copied
	 * @throws Exception the old file is kept as it is, the new one deleted
	 */
	public static File upgrade(String dbpath, String pickspath, SemanticTableMeta... metas) throws Exception {
		DbPicks picks = DbPicks.load(pickspath);
		if (picks == null && pickspath != null)
			Utils.warn("[DoctreeDBUpgrade] No picking list: %s", pickspath);
		return upgrade(dbpath, picks, metas);
	}

	/**
	 * Upgrade the db file if its tables differ from the metas'.
	 *
	 * @param dbpath the db file; nothing is done if there is none (a new db)
	 * @param picks null: none, a db needing to be copied isn't upgraded
	 * @param metas the tables of the current version, with ddlSqlite
	 * @return the backup of the old file; null if not copied
	 * @throws Exception the old file is kept as it is, the new one deleted
	 */
	public static File upgrade(String dbpath, DbPicks picks, SemanticTableMeta... metas) throws Exception {
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
				if (picks != null)
					initsInPlace(db, picks);
				return null;
			}

			// without the picks, renamed columns would be lost
			if (picks == null)
				throw new IOException(String.format("The tables of %s are of an earlier version, but there's no picking list to upgrade it.", dbpath));

			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				Set<String> copied = new LinkedHashSet<String>();
				for (SemanticTableMeta m : metas) {
					s.execute(m.ddlSqlite);
					if (columns(c, "old", m.tbl).isEmpty())
						continue; // a new table
					String[] sel = select(picks, m.tbl, columns(c, "main", m.tbl), columns(c, "old", m.tbl));
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
			inits(c, "main", picks);
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
	static String[] select(DbPicks picks, String tbl, ArrayList<String> neucols, ArrayList<String> oldcols) {
		ArrayList<String> into = new ArrayList<String>();
		ArrayList<String> from = new ArrayList<String>();
		for (String col : neucols) {
			DbPick p = picks.pick(tbl, col);
			String[] names = p != null && p.names != null && p.names.length > 0 ? p.names : new String[] { col };
			String found = null;
			for (String n : names)
				if (containsIgnoreCase(oldcols, n)) {
					found = n;
					break;
				}

			if (found != null) {
				into.add(col);
				from.add(found);
			}
			else if (p != null && p.dflt != null) {
				// a new column with the pick's default
				String v = "'" + p.dflt.replace("'", "''") + "'";
				into.add(col);
				from.add(p.where == null ? v : String.format("case when (%s) then %s end", p.where, v));
			}
			// else the DDL's default
		}
		return new String[] { String.join(", ", into), String.join(", ", from) };
	}

	/**
	 * Insert the missing records of {@link DbPicks#inits}; oz_autoseq is created if there is none.
	 * @param schema e.g. "main"
	 * @return how many were inserted
	 */
	static int inits(Connection c, String schema, DbPicks picks) throws SQLException {
		int n = 0;
		for (DbInitRow r : picks.inits) {
			if (columns(c, schema, r.tabl).isEmpty()) {
				if (!"oz_autoseq".equalsIgnoreCase(r.tabl))
					throw new SQLException(String.format("%s: no table %s for the record.", r.mark, r.tabl));
				try (Statement s = c.createStatement()) {
					s.execute(new AutoSeqMeta().ddlSqlite);
				} catch (SemanticException e) { // only for a connection not sqlite
					throw new SQLException(e.getMessage(), e);
				}
			}

			ArrayList<String> cols = new ArrayList<String>();
			ArrayList<String> vals = new ArrayList<String>();
			ArrayList<String> args = new ArrayList<String>();
			if (r.nvs != null)
				for (String[] nv : r.nvs) {
					cols.add(nv[0]);
					vals.add("?");
					args.add(nv[1]);
				}
			if (r.sqls != null)
				for (String[] nv : r.sqls) {
					cols.add(nv[0]);
					vals.add("(" + nv[1] + ")");
				}

			ArrayList<String> where = new ArrayList<String>();
			ArrayList<String> keyargs = new ArrayList<String>();
			for (String k : r.key) {
				int x = cols.indexOf(k);
				if (x < 0 || x >= args.size())
					throw new SQLException(String.format("%s: key %s must be a constant of nvs.", r.mark, k));
				where.add(k + " = ?");
				keyargs.add(args.get(x));
			}

			try (PreparedStatement ps = c.prepareStatement(String.format(
					"insert into %1$s.%2$s (%3$s) select %4$s where not exists (select 1 from %1$s.%2$s where %5$s)",
					schema, r.tabl, String.join(", ", cols), String.join(", ", vals), String.join(" and ", where)))) {
				int i = 1;
				for (String a : args) ps.setString(i++, a);
				for (String a : keyargs) ps.setString(i++, a);
				n += ps.executeUpdate();
			}
		}
		return n;
	}

	/** The tables are the current ones: insert the missing records only, in place, if any is missing. */
	static void initsInPlace(File db, DbPicks picks) throws SQLException {
		if (picks.inits.isEmpty())
			return;
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath())) {
			c.setAutoCommit(false);
			int n = inits(c, "main", picks);
			c.commit();
			if (n > 0)
				Utils.logi("[DoctreeDBUpgrade] %s: %s missing records inserted.", db.getAbsolutePath(), n);
		}
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
