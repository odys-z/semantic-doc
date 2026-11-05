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
import io.oz.sdoc.DoctreeDB;
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

	final DoctreeDB db;
	final String conn;
	final DocxSource source;
	final IBlockParser parser;
	final ExecutorService worker;
	final IUser robot;

	/** last submitted import, for tests */
	Future<?> last;

	public Importer(String conn, DoctreeDB db, DocxSource source, IBlockParser parser) {
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
	 * @param srcId the docx's record id
	 * @param title null: the docx's Title, else its file name
	 * @param creator
	 * @return the header, state {@link DocInf.State#parsing}
	 * @throws Exception the docx can't be found (nothing created)
	 */
	public DocInf start(String synode, String srcId, String title, String creator) throws Exception {
		String path = source.path(srcId);
		if (isblank(path) || !Path.of(path).toFile().isFile())
			throw new IllegalArgumentException(String.format("Docx not found: %s, %s", srcId, path));

		DATranscxt st = new DATranscxt(conn);
		SemanticObject res = (SemanticObject) st.insert(DocInf.tabl, robot)
			.nv("synode", synode) // docId: autopk, prefixed with the synode
			.nv("title", title)
			.nv("creator", creator)
			.nv("state", DocInf.State.parsing)
			.nv("srcId", srcId)
			.nv("nextNid", 1)
			.ins(st.instancontxt(conn, robot));
		String docId = res.resulve(DocInf.tabl, "docId", -1);

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
			List<Block> blocks = parser.parse(path);
			Outline o = Outliner.outline(docId, blocks);
			if (isblank(title))
				title = isblank(o.title) ? filename : o.title;
			o.nodes.get(0).ptext = title;

			DATranscxt st = new DATranscxt(conn);
			Update u = st.update(DocInf.tabl, robot)
				.nv("title", title)
				.nv("state", DocInf.State.ready)
				.nv("nextNid", o.nextNid)
				.whereEq("docId", docId);

			for (DocNode n : o.nodes)
				u.post(st.insert(DocNode.tabl, robot)
					.nv("nid", n.nid)
					.nv("docId", n.docId)
					.nv("parentId", n.parentId)
					.nv("sort", n.sort)
					.nv("fullpath", n.fullpath)
					.nv("kind", n.kind)
					.nv("text", n.ptext)
					.nv("src", n.src)
					.nv("style", n.style));

			u.u(st.instancontxt(conn, robot));
		} catch (Throwable e) {
			Utils.warnT(new Object() {}, "Importing %s failed: %s", path, e.getMessage());
			e.printStackTrace();
			try {
				DATranscxt st = new DATranscxt(conn);
				st.update(DocInf.tabl, robot)
					.nv("state", DocInf.State.failed)
					.nv("msg", ifnull(e.getMessage(), e.getClass().getName()))
					.whereEq("docId", docId)
					.u(st.instancontxt(conn, robot));
			} catch (Exception x) {
				x.printStackTrace();
			}
		}
	}
}
