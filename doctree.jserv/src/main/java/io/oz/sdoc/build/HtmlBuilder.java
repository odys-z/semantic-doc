package io.oz.sdoc.build;

import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.odysz.common.Utils;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.DocRes;
import io.oz.sdoc.DoctreeDBHelper;

/**
 * Builds a doc's html package on the synode, RST -&gt; html, by Sphinx: the only step compiled
 * here. The RST files are the client's ({@link RstFile}), the configuration is the template's.
 * 
 * <p>A build runs in the background, one at a time:</p><ol>
 * <li>&lt;volume&gt;/&lt;creator&gt;/{@link DocRes#buildfolder}/&lt;docId&gt;/source: the template's files
 *     (conf.py, static/), the RST files, and media/ with the resources the files link;</li>
 * <li>sphinx-build -b html source html;</li>
 * <li>html is zipped as {@link DocRes#htmlzip(String, String)}, the doc's {@link DocInf#htmlUri},
 *     with builtAt = now ({@link DoctreeDBHelper#built(String, String, IUser)}).</li>
 * </ol>
 * The state of a doc's last build, {@link #state(String)}, is kept in memory: a build that
 * finished is told by the doc's builtAt, not earlier than its committedAt.
 * 
 * @since 0.1.3
 */
public class HtmlBuilder {
	/** Values of {@link #state(String)} */
	public static class State {
		public static final String building = "building";
		public static final String built = "built";
		public static final String failed = "failed";
	}

	/** the folder of resources in the Sphinx source, as the client's rst-assembler.ts links them */
	public static final String media = "media";

	/** the template's source folder: conf.py, static/ */
	final File template;
	/**
	 * The template's id, name + version, e.g. "rtd-0.8": "id" of its manifest.json (beside the
	 * source folder, written by make-template.py), or the template folder's name.
	 */
	public final String templateId;
	/** the build tool, e.g. ["sphinx-build"] or ["python3", "-m", "sphinx"] */
	final String[] sphinx;
	/** a build is killed after this, seconds */
	public long timeout = 300;

	final ExecutorService worker;
	/** [docId, [state, log]] */
	final Map<String, String[]> states = Collections.synchronizedMap(new HashMap<String, String[]>());
	/** last submitted build, for tests */
	Future<?> last;

	/**
	 * @param template the Sphinx template's source folder (with conf.py), e.g.
	 * jserv-album's sdoc-template-0.8/source
	 * @param sphinx the command of sphinx-build, with leading arguments if any;
	 * default "sphinx-build", found by PATH
	 */
	public HtmlBuilder(String template, String... sphinx) {
		this.template = new File(template);
		this.templateId = templateId(this.template);
		this.sphinx = sphinx == null || sphinx.length == 0 ? new String[] { "sphinx-build" } : sphinx;
		this.worker = Executors.newSingleThreadExecutor((r) -> {
			Thread t = new Thread(r, "sdoc-build");
			t.setDaemon(true);
			return t;
		});
	}

	/** @return state of the doc's last build, see {@link State}; null: no build since started */
	public String state(String docId) {
		String[] s = states.get(docId);
		return s == null ? null : s[0];
	}

	/** @return output of the build tool, or why the build failed */
	public String log(String docId) {
		String[] s = states.get(docId);
		return s == null ? null : s[1];
	}

	/**
	 * Build the doc's html package in the background. Builds of a doc are done in order,
	 * the last one is the doc's package.
	 * 
	 * @param db the working copy, with the doc's resources
	 * @param doc the doc's header
	 * @param files the doc as RST files
	 * @param usr who committed
	 */
	public synchronized void start(DoctreeDBHelper db, DocInf doc, ArrayList<RstFile> files, IUser usr) {
		states.put(doc.docId, new String[] { State.building, null });
		last = worker.submit(() -> {
			try {
				String log = build(db, doc, files, usr);
				states.put(doc.docId, new String[] { State.built, log });
			} catch (Throwable e) {
				String why = e instanceof SemanticException || e instanceof IOException
						? e.getMessage() : e.getClass().getName() + ": " + e.getMessage();
				states.put(doc.docId, new String[] { State.failed, why });
				Utils.warn("[HtmlBuilder] Building doc %s failed. %s", doc.docId, why);
			}
		});
	}

	/** The doc is committed without a build: the state of its last build tells nothing now. */
	public void forget(String docId) {
		states.remove(docId);
	}

	/** Wait for the last build, for tests. */
	public void await() throws Exception {
		if (last != null) last.get();
	}

	/**
	 * Build now.
	 * @return output of sphinx-build (warnings)
	 * @throws SemanticException the files can't be built, or sphinx-build failed: its output
	 * @throws IOException sphinx-build can't be run, or files can't be written
	 */
	String build(DoctreeDBHelper db, DocInf doc, ArrayList<RstFile> files, IUser usr) throws Exception {
		if (db.res == null)
			throw new SemanticException("The doc-tree db has no volume for building docs.");
		if (!new File(template, "conf.py").isFile())
			throw new IOException("No Sphinx template (conf.py) in " + template.getAbsolutePath());

		String uid = isblank(doc.creator) ? usr.uid() : doc.creator;
		Path dir    = db.res.builddir(uid, doc.docId).toPath().toAbsolutePath();
		Path source = dir.resolve("source");
		Path html   = dir.resolve("html");

		// 1. the source, always from nothing: files of deleted sections must not be built
		clean(dir);
		Files.createDirectories(source);
		copy(template.toPath(), source);

		boolean index = false;
		for (RstFile f : files) {
			if (f == null || isblank(f.path) || !f.path.matches("[\\w.\\-]+\\.rst") || f.path.startsWith("."))
				throw new SemanticException("Not a name of a RST file: %s", f == null ? null : f.path);
			Files.write(source.resolve(f.path), (f.rst == null ? "" : f.rst).getBytes(StandardCharsets.UTF_8));
			index |= "index.rst".equals(f.path);

			if (f.media != null)
				for (String m : f.media) {
					File res = db.res.file(uid, doc.docId, m);
					// a missing resource: Sphinx warns, the page is built without the picture
					if (res.isFile()) {
						Files.createDirectories(source.resolve(media));
						Files.copy(res.toPath(), source.resolve(media).resolve(m), StandardCopyOption.REPLACE_EXISTING);
					}
				}
		}
		if (!index)
			throw new SemanticException("The doc's RST files have no index.rst.");

		// 2. RST -> html
		String log = sphinx(dir, source, html);

		// 3. the package
		File zip = db.res.htmlzip(uid, doc.docId);
		Files.createDirectories(zip.getParentFile().toPath());
		Path tmp = zip.toPath().resolveSibling(zip.getName() + ".building");
		zip(html, tmp);
		Files.move(tmp, zip.toPath(), StandardCopyOption.REPLACE_EXISTING);

		db.built(doc.docId, zip.getAbsolutePath(), templateId, usr);
		Utils.logi("[HtmlBuilder] built doc %s -> %s", doc.docId, zip.getAbsolutePath());
		return log;
	}

	/**
	 * @param source a template's source folder, &lt;template&gt;/source
	 * @return "id" of &lt;template&gt;/manifest.json; without it, the template folder's name
	 */
	static String templateId(File source) {
		File tpl = source.getAbsoluteFile().getParentFile();
		try {
			String manifest = new String(Files.readAllBytes(new File(tpl, "manifest.json").toPath()), StandardCharsets.UTF_8);
			Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(manifest);
			if (m.find())
				return m.group(1);
		} catch (IOException e) { }
		return tpl == null ? null : tpl.getName();
	}

	/** @return the tool's output */
	String sphinx(Path dir, Path source, Path html) throws Exception {
		ArrayList<String> cmd = new ArrayList<String>();
		Collections.addAll(cmd, sphinx);
		Collections.addAll(cmd, "-b", "html", "-q", "-d", dir.resolve("doctrees").toString(),
				source.toString(), html.toString());

		File out = dir.resolve("sphinx.log").toFile();
		Process p;
		try {
			p = new ProcessBuilder(cmd).directory(dir.toFile())
					.redirectErrorStream(true).redirectOutput(out).start();
		} catch (IOException e) {
			throw new IOException("Can't run the html build tool, " + sphinx[0] + ". " + e.getMessage());
		}

		if (!p.waitFor(timeout, TimeUnit.SECONDS)) {
			p.destroyForcibly();
			throw new SemanticException("The html build is stopped, not finished in %s seconds.", timeout);
		}

		String log = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8).trim();
		if (p.exitValue() != 0)
			throw new SemanticException("The html build failed (exit code %s).\n%s", p.exitValue(), log);
		return log;
	}

	static void copy(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path f : (Iterable<Path>) walk::iterator) {
				Path t = to.resolve(from.relativize(f).toString());
				if (Files.isDirectory(f))
					Files.createDirectories(t);
				else
					Files.copy(f, t, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	static void zip(Path dir, Path zip) throws IOException {
		try (OutputStream fos = Files.newOutputStream(zip);
			 ZipOutputStream zos = new ZipOutputStream(fos);
			 Stream<Path> walk = Files.walk(dir)) {
			for (Path f : (Iterable<Path>) walk.sorted()::iterator) {
				if (Files.isDirectory(f))
					continue;
				zos.putNextEntry(new ZipEntry(dir.relativize(f).toString().replace('\\', '/')));
				Files.copy(f, zos);
				zos.closeEntry();
			}
		}
	}

	static void clean(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
	}
}
