package io.oz.sdoc;

import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantic.meta.SemanticTableMeta;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.docx.RstreeDBHelper;
import io.oz.syn.dbpicks.DbInitRow;
import io.oz.syn.dbpicks.DbPick;
import io.oz.syn.dbpicks.DbPicks;

/**
 * Upgrade a doc-tree db file ({@link RstreeDBHelper#dbfile}) of an earlier version, the same way
 * as the synode upgrader's config picks (semanticshare ConfigPicks): the new version's db is the
 * template, with the installed values picked, as the app's picking list ({@link DbPicks},
 * e.g. &lt;install&gt;/bin/doctree-picks.json) says. All through semantic.DA, the sql built by semantic.transact.
 *
 * <ol>
 * <li>The db file is connected as &lt;conn&gt;.old, read with a basic transact builder
 *     ({@link DATranscxt#getBasicTrans(String)}): its semantics never load, so nothing is written into it.</li>
 * <li>A new db, &lt;db&gt;.new, is connected as &lt;conn&gt;.new, its tables set up by the metas
 *     ({@link SemanticTableMeta#setupSqliTables(String, boolean, SemanticTableMeta...)}).</li>
 * <li>If a meta's table has other columns than the old db's (both of {@link Connects#getMeta(String, String)}),
 *     each table's rows are copied, page by page, with the new db's semantics, which keep the given keys:
 *     a column's value is the old table's column of the first of its {@link DbPick#names} found (the newest
 *     first), else of its own name; a new column, found in none, takes its pick's default
 *     ({@link DbPick#dflt}, when {@link DbPick#when} holds), else the DDL's; old columns not picked are
 *     dropped (e.g. s_docs.nextNid).</li>
 * <li>The auto keys' sequences, oz_autoseq, go on from the old ones.</li>
 * <li>The row counts are checked; both connections are removed ({@link Connects#removeSqlite(String)});
 *     the old file is renamed to &lt;db&gt;.&lt;time&gt;.bak, the new one to &lt;db&gt;.</li>
 * </ol>
 *
 * <p>The records the current version needs ({@link DbPicks#inits}) are inserted if missing by
 * {@link #inits(DATranscxt, String, DbPicks)}, when the db is set up, whether it was copied or not.</p>
 *
 * <p>Nodes keep their ids: those of an earlier version (docId + '.' + n, the doc root: the docId)
 * never equal an auto key, which has no '.'.</p>
 *
 * <p>When the tables change in a later version: change the metas' DDL; for a renamed column, a new
 * column needing a default, or a record a new semantics needs, add an item to the picking list,
 * marked as {@link DbPicks} says, merged with the earlier versions' items; a column whose values
 * change meaning needs its own conversion here.</p>
 *
 * @since 0.1.4
 * @author ody
 */
public class DoctreeDBUpgrade {
	/** rows copied by an insert */
	static int pgSize = 256;

	static final IUser robot = new JRobot();

	/**
	 * Upgrade the db file if its tables differ from the metas'. Call before the db is connected.
	 *
	 * @param conn the db's connection id, e.g. "doctree.X"; the old and new dbs are connected
	 * as conn + ".old", conn + ".new" while upgrading
	 * @param dbpath the db file; nothing is done if there is none (a new db)
	 * @param smtcs the semantics of the db, e.g. {@link RstreeDBHelper#smtcs}
	 * @param picks null: none, a db needing to be copied isn't upgraded
	 * @param metas the tables of the current version, with ddlSqlite
	 * @return the backup of the old file; null if not copied
	 * @throws Exception the old file is kept as it is, the new one deleted
	 */
	public static File upgrade(String conn, String dbpath, String smtcs, DbPicks picks,
			SemanticTableMeta... metas) throws Exception {
		File db = new File(dbpath);
		if (!db.isFile() || db.length() == 0)
			return null;

		String old = conn + ".old", neu = conn + ".new";
		File nf = new File(dbpath + ".new");
		Files.deleteIfExists(nf.toPath());

		boolean copied = false;
		try {
			Connects.addSqlite(old, db.getAbsolutePath(), smtcs);
			Connects.addSqlite(neu, nf.getAbsolutePath(), smtcs);
			setupSqliTables(neu, false, metas);

			if (!differs(old, neu, metas))
				return null;

			// without the picks, renamed columns would be lost
			if (picks == null)
				throw new IOException(String.format(
					"The tables of %s are of an earlier version, but there's no picking list to upgrade it.", dbpath));

			DATranscxt sto = (DATranscxt) DATranscxt.getBasicTrans(old);
			DATranscxt stn = new DATranscxt(neu);

			ArrayList<String> tables = new ArrayList<String>();
			for (SemanticTableMeta m : metas)
				if (copy(sto, old, stn, neu, m, picks))
					tables.add(m.tbl);

			// oz_autoseq is merged, not copied: semantics of the new connection
			// may have seeded their own rows (ShAutoKPrefix), so it's not counted.
			String seqs = copySeqs(sto, old, stn, neu) ? new AutoSeqMeta(neu).tbl : null;

			for (String t : tables) {
				long n0 = count(sto, t), n1 = DAHelper.count(stn, neu, t);
				if (n0 != n1)
					throw new SQLException(String.format("Upgrading %s: %s rows copied of %s.", t, n1, n0));
			}

			for (String t : Connects.getMeta(old).keySet())
				if (!tables.contains(t) && !t.equals(seqs) && Connects.getMeta(neu, t) == null)
					Utils.warn("[DoctreeDBUpgrade] Table %s of %s isn't of this version, not copied.", t, dbpath);

			copied = true;
		} finally {
			Connects.removeSqlite(old);
			Connects.removeSqlite(neu);
			if (!copied)
				Files.deleteIfExists(nf.toPath());
		}

		File bak = new File(String.format("%s.%s.bak", dbpath, new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())));
		Files.move(db.toPath(), bak.toPath());
		Files.move(nf.toPath(), db.toPath(), StandardCopyOption.REPLACE_EXISTING);
		Utils.logi("[DoctreeDBUpgrade] %s upgraded; the old file: %s", dbpath, bak.getName());
		return bak;
	}

	/** whether any meta's table, existing in the old db, has columns other than the new db's */
	static boolean differs(String old, String neu, SemanticTableMeta... metas) throws TransException, SQLException {
		for (SemanticTableMeta m : metas) {
			TableMeta om = Connects.getMeta(old, m.tbl);
			if (om == null)
				continue; // a new table
			Set<String> oc = om.ftypes().keySet();
			Set<String> nc = Connects.getMeta(neu, m.tbl).ftypes().keySet();
			if (oc.size() != nc.size())
				return true;
			for (String c : nc)
				if (find(oc, c) == null)
					return true;
		}
		return false;
	}

	/**
	 * Copy a table's rows, page by page: select (picked column [as column], ...) from the old db,
	 * insert into the new one with its semantics.
	 * @return false if the old db has no such table
	 */
	static boolean copy(DATranscxt sto, String old, DATranscxt stn, String neu, SemanticTableMeta m, DbPicks picks)
			throws TransException, SQLException {
		TableMeta om = Connects.getMeta(old, m.tbl);
		if (om == null)
			return false;
		Set<String> oldcols = om.ftypes().keySet();

		Query q = sto.select(m.tbl);
		ArrayList<String> cols = new ArrayList<String>();
		for (String col : Connects.getMeta(neu, m.tbl).ftypes().keySet()) {
			DbPick p = picks.pick(m.tbl, col);
			String[] names = p != null && p.names != null && p.names.length > 0 ? p.names : new String[] { col };
			String found = null;
			for (String n : names)
				if ((found = find(oldcols, n)) != null)
					break;

			if (found != null)
				q.col(found, col);
			else if (p != null && p.dflt != null)
				// a new column with the pick's default
				q.col(p.when == null
						? ExprPart.constr(p.dflt)
						: Funcall.ifElse(p.when.predicate(), ExprPart.constr(p.dflt), new ExprPart()),
					col);
			else continue; // the DDL's default
			cols.add(col);
		}
		String pk = find(oldcols, m.pk);
		q.orderby(pk != null ? pk : cols.get(0));

		for (int page = 0; ; page++) {
			AnResultset rs = (AnResultset) q.page(page, pgSize).rs(sto.basictx()).rs(0);
			if (rs.getRowCount() == 0)
				break;

			ArrayList<ArrayList<Object[]>> vals = new ArrayList<ArrayList<Object[]>>(rs.getRowCount());
			rs.beforeFirst();
			while (rs.next()) {
				ArrayList<Object[]> row = new ArrayList<Object[]>(cols.size());
				for (String c : cols)
					row.add(new Object[] { c, rs.getObject(c) });
				vals.add(row);
			}

			// the keys are given: the semantics keep them, e.g. autopk, fullpath2
			stn.insert(m.tbl, robot)
				.cols(cols.toArray(new String[0]))
				.values(vals)
				.ins(stn.instancontxt(neu, robot));

			if (rs.getRowCount() < pgSize)
				break;
		}
		return true;
	}

	/**
	 * The old db's sequences of auto keys go on in the new one, where the semantics may have seeded them.
	 * @return false if the old db has no oz_autoseq
	 */
	static boolean copySeqs(DATranscxt sto, String old, DATranscxt stn, String neu)
			throws TransException, SQLException {
		AutoSeqMeta seqm = new AutoSeqMeta(neu);
		if (Connects.getMeta(old, seqm.tbl) == null)
			return false;

		AnResultset rs = (AnResultset) sto.select(seqm.tbl)
				.rs(sto.basictx()).rs(0);
		rs.beforeFirst();
		while (rs.next()) {
			String sid = rs.getString("sid");
			long seq = rs.getLong("seq");
			if (DAHelper.count(stn, neu, seqm.tbl, "sid", sid) > 0) {
				// seeded by the new semantics: never roll a sequence back
				String seeded = DAHelper.getValstr(stn, neu, seqm, "seq", "sid", sid);
				if (seeded == null || Long.valueOf(seeded) < seq)
					DAHelper.updateFieldWhereEqs(stn, neu, robot, seqm, "seq", seq, "sid", sid);
			}
			else
				DAHelper.insert(robot, stn, neu, seqm,
						"sid", sid, "seq", seq, "remarks", rs.getString("remarks"));

			if (DAHelper.count(stn, neu, seqm.tbl, "sid", sid) != 1)
				throw new SQLException(String.format("Upgrading %s: sequence %s is not merged.", seqm.tbl, sid));
		}
		return true;
	}

	/**
	 * Insert the missing records of {@link DbPicks#inits}: a record is missing if no row has its key
	 * columns' values; existing records, e.g. a sequence copied from the old db, are never changed.
	 *
	 * @param st the db's transact builder, with its semantics
	 * @param conn
	 * @param picks null: nothing to do
	 * @return how many were inserted
	 */
	public static int inits(DATranscxt st, String conn, DbPicks picks) throws TransException, SQLException {
		if (picks == null)
			return 0;

		int n = 0;
		for (DbInitRow r : picks.inits) {
			TableMeta m = "oz_autoseq".equalsIgnoreCase(r.tabl)
					? new AutoSeqMeta(conn) : Connects.getMeta(conn, r.tabl);
			if (m == null)
				throw new TransException("%s: no table %s for the record.", r.mark, r.tabl);

			ArrayList<Object> kvs = new ArrayList<Object>();
			for (String k : r.key) {
				String v = value(r.nvs, k);
				if (v == null)
					throw new TransException("%s: key %s must be a constant of nvs.", r.mark, k);
				kvs.add(k);
				kvs.add(v);
			}
			if (DAHelper.count(st, conn, r.tabl, kvs.toArray()) > 0)
				continue;

			ArrayList<Object> nvs = new ArrayList<Object>();
			if (r.nvs != null)
				for (String[] nv : r.nvs) {
					nvs.add(nv[0]);
					nvs.add(nv[1]);
				}
			if (r.counts != null)
				for (String[] ct : r.counts) {
					nvs.add(ct[0]);
					nvs.add(DAHelper.count(st, conn, ct[1]));
				}
			DAHelper.insert(robot, st, conn, m, nvs.toArray());
			n++;
		}
		if (n > 0)
			Utils.logi("[DoctreeDBUpgrade] %s: %s missing records inserted.", conn, n);
		return n;
	}

	/** count the rows with a basic transact builder (no semantics) */
	static long count(DATranscxt basic, String tbl) throws TransException, SQLException {
		return ((AnResultset) basic.select(tbl)
				.col(Funcall.count("*"), "cnt")
				.rs(basic.basictx()).rs(0)).nxt().getLong("cnt");
	}

	static String value(ArrayList<String[]> nvs, String col) {
		if (nvs != null)
			for (String[] nv : nvs)
				if (nv[0].equals(col))
					return nv[1];
		return null;
	}

	/** the name in cols, ignoring case; null if not found */
	static String find(Set<String> cols, String col) {
		for (String c : cols)
			if (c.equalsIgnoreCase(col))
				return c;
		return null;
	}
}
