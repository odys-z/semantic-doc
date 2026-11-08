package io.oz.sdoc.imports;

import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.LangExt.isblank;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.Update;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.DocNode;
import io.oz.sdoc.S_DocNodeMeta;
import io.oz.sdoc.S_DocsMeta;
import io.oz.sdoc.DoctreeDBHelper;
import io.oz.sdoc.imports.Outliner.Outline;

/**
 * Imports a docx into the working copy, in the background:
 *
 * <pre>
 * start():  resolve the docx → insert the header (state parsing) → answer it
 * worker:   parse → outline → insert the nodes, header ready (one commit)
 *           | header failed + msg
 * </pre>
 *
 * One worker thread: imports run one by one.
 *
 * @author ody
 */
public class Importer {

	final DoctreeDBHelper db;
	final String conn;
	final DocxSource source;
	final IBlockParser parser;
	final ExecutorService worker;
	final IUser robot;

	/** last submitted import, for tests */
	Future<?> last;

	public Importer(String conn, DoctreeDBHelper db, DocxSource source, IBlockParser parser) {
		this.conn = conn;
		this.db = db;
		this.source = source;
		this.parser = parser;
		this.robot = new JRobot();
		this.worker = Executors.newSingleThreadExecutor((r) -> {
			Thread t = new Thread(r, "sdoc-import");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Create the doc header and start parsing.
	 *
	 * @param synode for the doc id
	 * @param originRefId the docx's record id
	 * @param title null: the docx's Title, else its file name
	 * @param creator
	 * @return the header, state {@link DocInf.State#parsing}
	 * @throws Exception the docx can't be found (nothing created)
	 */
	public DocInf start(String synode, String originRefId, String title, String creator) throws Exception {
		String path = source.path(originRefId);
		if (isblank(path) || !Path.of(path).toFile().isFile())
			throw new IllegalArgumentException(String.format("Docx not found: %s, %s", originRefId, path));

		DATranscxt st = new DATranscxt(conn);
		S_DocsMeta docm = db.docm;
		SemanticObject res = (SemanticObject) st.insert(docm.tbl, robot)
			.nv(docm.synode, synode) // docId: autopk, prefixed with the synode
			.nv(docm.title, title)
			.nv(docm.creator, creator)
			.nv(docm.state, DocInf.State.parsing)
			.nv(docm.refId, originRefId)
			.nv(docm.nextNid, 1)
			.ins(st.instancontxt(conn, robot));
		String docId = res.resulve(docm.tbl, docm.pk, -1);

		String filename = Path.of(path).getFileName().toString();
		last = worker.submit(() -> run(docId, path, title, filename));

		return db.doc(docId);
	}

	/** Wait for the last submitted import, for tests. */
	public void await() throws Exception {
		if (last != null) last.get();
	}

	void run(String docId, String path, String title, String filename) {
		try {
			S_DocsMeta docm = db.docm;
			S_DocNodeMeta nodem = db.nodem;
			DATranscxt st = new DATranscxt(conn);

			List<Block> blocks = parser.parse(path);
			Outline o = Outliner.outline(docId, nodem.siblingWidth(), blocks);
			if (isblank(title))
				title = isblank(o.title) ? filename : o.title;
			o.nodes.get(0).ptext = title;

			Update u = st.update(docm.tbl, robot)
				.nv(docm.title, title)
				.nv(docm.state, DocInf.State.ready)
				.nv(docm.nextNid, o.nextNid)
				.whereEq(docm.pk, docId);

			for (DocNode n : o.nodes)
				u.post(st.insert(nodem.tbl, robot)
					.nv(nodem.pk, n.nid)
					.nv(nodem.docId, n.docId)
					.nv(nodem.parentId, n.parentId)
					.nv(nodem.sort, n.sort)
					.nv(nodem.fullpath, n.fullpath)
					.nv(nodem.kind, n.kind)
					.nv(nodem.ptext, n.ptext)
					.nv(nodem.facts, n.facts)
					.nv(nodem.src, n.src)
					.nv(nodem.style, n.docxstyle));

			u.u(st.instancontxt(conn, robot));
		} catch (Throwable e) {
			Utils.warnT(new Object() {}, "Importing %s failed: %s", path, e.getMessage());
			e.printStackTrace();
			try {
				DATranscxt st = new DATranscxt(conn);
				st.update(db.docm.tbl, robot)
					.nv(db.docm.state, DocInf.State.failed)
					.nv(db.docm.msg, ifnull(e.getMessage(), e.getClass().getName()))
					.whereEq(db.docm.pk, docId)
					.u(st.instancontxt(conn, robot));
			} catch (Exception x) {
				x.printStackTrace();
			}
		}
	}
}
