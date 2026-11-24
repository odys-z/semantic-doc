package io.oz.sdoc.docx;

import java.io.File;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SemanticTableMeta;
import io.odysz.semantics.IUser;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

/**
 * A stub of the album's doc table, h_photos (jserv-album-lib's PhotoMeta, the same DDL), in a
 * test's own db: the docx files are its records, found by their io_oz_synuid, as on a synode.
 */
public class T_PhotoMeta extends ExpDocTableMeta {

	public T_PhotoMeta(String conn) throws TransException {
		super("h_photos", "pid", "device", conn);
		ddlSqlite = "CREATE TABLE if not exists h_photos (\n"
				+ "  pid        varchar(12)   NOT NULL,\n"
				+ "  family     varchar2(12)  NOT NULL,\n"
				+ "  folder     varchar2(256) NOT NULL,\n"
				+ "  docname    varchar2(256),\n"
				+ "  uri        varchar2(512) NOT NULL,\n"
				+ "  pdate      datetime,\n"
				+ "  device     varchar(12)   NOT NULL,\n"
				+ "  clientpath text DEFAULT '' NOT NULL,\n"
				+ "  shareby    varchar(12),\n"
				+ "  sharedate  datetime     NOT NULL,\n"
				+ "  exif       text,\n"
				+ "  mime       varchar2(64),\n"
				+ "  filesize   integer,\n"
				+ "  shareflag  varchar2(12) default 'prv' NOT NULL,\n"
				+ "  oper       varchar(12)  NOT NULL,\n"
				+ "  opertime   datetime     NOT NULL,\n"
				+ "  io_oz_synuid varchar2(25),\n"
				+ "  PRIMARY KEY (pid)\n"
				+ ");";
	}

	/**
	 * The album, h_photos created anew in the db, with the docx files' records.
	 *
	 * @param webinf the test's WEB-INF, the config root of the ext-file paths, as a synode's
	 * @param conn the test's db
	 * @param pid_synuid_paths triples of a record's pid, io_oz_synuid and file, relative to the module;
	 * a file starting with '{' is a doc-ref's envelope, the uri of a record not synchronized yet
	 * @return the importer's source, the doc table's DocRef
	 */
	public static DocRef album(String webinf, String conn, String... pid_synuid_paths) throws Exception {
		DATranscxt.configRoot(webinf, new File(".").getAbsolutePath());

		T_PhotoMeta m = new T_PhotoMeta(conn);
		SemanticTableMeta.setupSqliTables(conn, true, m);
		// a table created after the connection's metas were loaded, e.g. by the doc-tree's setup
		Connects.clearMeta(conn);

		IUser robot = new JRobot();
		DATranscxt st = new DATranscxt(conn);
		for (int i = 0; i + 2 < pid_synuid_paths.length; i += 3) {
			String p = pid_synuid_paths[i + 2];
			boolean docref = p.startsWith("{");
			File f = new File(docref ? "docref" : p);
			st.insert(m.tbl, robot)
				.nv(m.pk, pid_synuid_paths[i])
				.nv(m.org, "zsu")
				.nv(m.folder, "docs")
				.nv(m.resname, f.getName())
				.nv(m.uri, docref ? p : f.getAbsolutePath())
				.nv(m.device, "X29")
				.nv(m.fullpath, f.getPath())
				.nv(m.shareDate, Funcall.now())
				.nv("oper", robot.uid())
				.nv("opertime", Funcall.now())
				.nv(m.io_oz_synuid, pid_synuid_paths[i + 1])
				.ins(st.instancontxt(conn, robot));
		}
		return new DocRef().docm(m);
	}
}
