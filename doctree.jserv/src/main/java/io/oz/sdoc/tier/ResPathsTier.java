// REMOVED

package io.oz.sdoc.tier;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.mustnonull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;

import io.odysz.common.EnvPath;
import io.odysz.common.Radix32;
import io.odysz.common.Utils;
import io.odysz.jclient.HttpServClient;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort6;
import io.odysz.semantic.jserv.WebConfig;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.docx.DocxInf;
import io.oz.sdoc.publish.PuckPubReq;
import io.oz.sdoc.publish.PuckPubReq.A;
import io.oz.sdoc.publish.PuckPubResp;
import io.oz.srv.respaths.HtmlPort;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Dynamic resource paths: publishes docs' html packages (zip) at this (running) server, and
 * removes them. All the docs are under one url root, {@link DynaResource#danyroot}, e. g. /pub/er3cou,
 * served from one folder, {@link DynaResource#resource}: publishing a doc is unzipping its package into a sub folder,
 * removing is deleting the folder. It is the port {@link HtmlPort#respaths}, for the local synode,
 * no session, localhost only:<ol>
 * <li>{@link A#deploy}, {@link A#remove}: publish / replace, or remove the docs of the request.
 *     A doc first published gets its context path here ({@link DocxInf#pubpath}), replied
 *     for the synode to keep;</li>
 * <li>{@link A#refresh}: query the synode for all the published docs ({@link #req2synode()}),
 *     and make context paths the same ({@link #reconcile(ArrayList)}).</li>
 * </ol>
 * The refresh is also triggered when the server started, then every {@link DynaResource#pollInterval_snd} seconds
 * (sooner after a failure, e.g. the synode isn't started yet): the paths are restored with
 * the synode's records.
 * 
 * <p>The docs are listed at the url root, e. g. /pub/ or /pub/index.html, by {@link ResIndex}:
 * the page is written for each request, of {@link #listed()}, the local folders matched with
 * the synode's docs, or all the local folders if the synode isn't reachable.</p>
 * 
 * <p>Requires semantic.jserv, doctree.jserv and anclient.java, see {@link WebConfig#dynaResource}.</p>
 * 
 * @since 0.2.1
 */
public class ResPathsTier extends ServPort6<PuckPubReq> {
	private static final long serialVersionUID = 1L;

	/** A published doc. */
	static class DynaPublish extends DocxInf {
		DynaPublish(DocxInf doc) {
			docId   = doc.docId;
			title   = doc.title;
			creator = doc.creator;
			pubpath = doc.pubpath;
			htmlUri = doc.htmlUri;
			builtAt = doc.builtAt;
			kind    = doc.kind;
		}

		/** @return the doc's header, a type the synode knows */
		DocxInf inf() {
			DocxInf d = new DocxInf(docId, title, creator);
			d.pubpath = pubpath;
			d.htmlUri = htmlUri;
			d.builtAt = builtAt;
			d.kind    = kind;
			return d;
		}
	}

	/** the folder of all the published docs, {@link DynaResource#resource}, served at /{@link #pubroot} */
	Path resroot;
	/** where packages are unzipped before published, and removed docs are moved to; not served */
	Path staging;

	/** url root of all the published docs, {@link DynaResource#danyroot}, e.g. "pub" */
	String pubroot;
	/** the local synode's jserv root, {@link DynaResource#syn_jserv} */
	String synode;
	/** polling interval, {@link DynaResource#pollInterval_snd}; 0: no polling */
	long seconds;
	/** polling again after a failure */
	static final long retry = 60;
	/** time of the next polling, ms */
	long nextpoll;

	/** the index page asks the synode again if it's asked longer ago than this, ms */
	static final long indexFresh = 30000;
	/** time of the index page's last asking the synode, ms */
	long indexAsked;
	/** the synode's last answer to the index page, [pubpath, doc]; null: it can't be reached */
	HashMap<String, DocxInf> indexDocs;

	/** [pubpath, published doc] */
	HashMap<String, DynaPublish> published = new HashMap<String, DynaPublish>();

	ScheduledExecutorService scheduler;

	/** the media of the published stories, /{pubroot}/{story}/m/{synuid}; since 0.2.2 */
	StoryMedia storyMedia;

	public ResPathsTier() {
		super(HtmlPort.respaths);
	}

	/**
	 * Called once before the server is started, by {@link HtmlServer}.
	 */
	@Override
	public void onUpdate(WebConfig wcfg) throws IOException {
		start(wcfg, wcfg.server);
	}

	void start(WebConfig wcfg, Server server) throws IOException {
		DynaResource res = wcfg.dynaResource;
		mustnonull(res, "html-service.json/dynaResource is required by ResPathsTier.");
		mustnonull(res.resource, "html-service.json/dynaResource.resource, the folder of published docs, is required.");
		mustnonull(res.danyroot, "html-service.json/dynaResource.danyroot, the url root of published docs, is required.");

		synode    = res.syn_jserv;
		seconds   = res.pollInterval_snd;
		resroot   = Paths.get(EnvPath.decodeUri(Paths.get(".").toAbsolutePath().toString(), res.resource)).normalize();
		pubroot   = res.danyroot.replaceAll("^/+|/\\*$|/+$", "");
		if (!pubroot.matches("[0-9A-Za-z_\\-\\.]+"))
			throw new IOException("html-service.json/dynaResource.danyroot must be a folder name: " + res.danyroot);

		// the docs published before are kept, and served, until the synode's records tell otherwise:
		// a synode not reachable doesn't take them down (reconcile())
		staging = resroot.resolveSibling(resroot.getFileName() + ".staging");
		clean(staging);
		Files.createDirectories(resroot);
		Files.createDirectories(staging);
		// the viewer of stories, installed with the web assets: never a published doc's folder
		Files.createDirectories(resroot.resolve(StoryShell.viewer));
		// the stories' media maps, kept when restarted (the staging folder is not), not served
		storyMedia = new StoryMedia(resroot.resolveSibling(resroot.getFileName() + ".maps"), pubroot);

		AnsonMsg.understandPorts(HtmlPort.respaths);

		// 1. this port, in the configured context ("/")
		ServletContextHandler ctx0 = (ServletContextHandler) server.getHandler();
		ctx0.addServlet(new ServletHolder(this), "/" + HtmlPort.respaths.url());

		// 2. the published docs, a static resource path as the configured ones (HtmlServer.multiReserver()),
		// after the stories' media, /pub/<story>/m/<synuid> (a servlet mapping can't match the middle of a path)
		ctx0.addFilter(new FilterHolder(storyMedia), "/" + pubroot + "/*", EnumSet.of(DispatcherType.REQUEST));
		ServletHolder holder = HtmlServer.staticHolder("respaths-pub", resroot.toString());
		holder.setInitParameter("dirAllowed", String.valueOf(res.allowDir));
		ctx0.addServlet(holder, "/" + pubroot + "/*");

		// 2.1 the index page of the published docs, written whenever the root or its index.html
		// is requested: exact paths, matched before "/pub/*"
		ServletHolder index = new ServletHolder("respaths-index", new ResIndex(this));
		for (String path : new String[] {"", "/", "/index.html"})
			ctx0.addServlet(index, "/" + pubroot + path);

		// the stories' maps kept from before a restart
		storyMedia.report();

		// 3. polling, also restores the docs when started
		if (seconds > 0 && !isblank(synode))
		server.addEventListener(new LifeCycle.Listener() {
			@Override
			public void lifeCycleStarted(LifeCycle event) {
				scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
					Thread t = new Thread(r, "respaths-polling");
					t.setDaemon(true);
					return t;
				});
				scheduler.scheduleWithFixedDelay(() -> {
					if (System.currentTimeMillis() < nextpoll)
						return;
					try {
						reconcile(req2synode());
						nextpoll = System.currentTimeMillis() + seconds * 1000;
					}
					catch (Exception e) {
						Utils.warn("[ResPathsTier] Updating paths failed. Will try again in %s seconds. %s: %s",
								Math.min(retry, seconds), e.getClass().getName(), e.getMessage());
					}
				}, 0, Math.min(retry, seconds), TimeUnit.SECONDS);
			}

			@Override
			public void lifeCycleStopping(LifeCycle event) {
				if (scheduler != null)
					scheduler.shutdownNow();
			}
		});

		Utils.logi("[ResPathsTier] dynamic paths: /%s/* -> %s, %s", pubroot, resroot,
				seconds > 0 && !isblank(synode) ? f("polling %s per %s seconds", synode, seconds) : "no polling");
	}

	@Override
	protected void onGet(AnsonMsg<PuckPubReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {
		throw new SemanticException("Res-path-tier: only POST method available.");
	}

	@Override
	protected void onPost(AnsonMsg<PuckPubReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {

		if (!InetAddress.getByName(msg.addr()).isLoopbackAddress())
			throw new SemanticException("Hello, %s !", msg.addr());

		PuckPubReq req = msg.body(0);
		try {
			ArrayList<DocxInf> docs;
			if (A.refresh.equals(req.a())) {
				reconcile(req2synode());
				docs = published();
			}
			else if (A.deploy.equals(req.a()))
				docs = deploy(req);
			else if (A.remove.equals(req.a())) {
				remove(req);
				docs = published();
			}
			else
				throw new SemanticException("Unknown request: a = %s", req.a());

			write(resp, ok(new PuckPubResp().respubs(docs)));
		} catch (SemanticException e) {
			throw e;
		} catch (Exception e) {
			write(resp, err(MsgCode.exGeneral, "%s: %s", e.getClass().getName(), e.getMessage()));
		}
	}

	/**
	 * Query the local synode ({@link HtmlPort#respub}) for all the published docs, and the media maps
	 * of the stories among them.
	 * @return respubs: docs, media: the stories' maps
	 * @throws IOException the synode isn't reachable
	 * @throws SemanticException the synode refused
	 */
	PuckPubResp req2synode() throws Exception {
		if (isblank(synode))
			throw new SemanticException("The synode's jserv root, html-service.json/dynaResource.syn_jserv, is not configured.");

		AnsonMsg<PuckPubReq> req = new AnsonMsg<PuckPubReq>(HtmlPort.respub)
				.body(new PuckPubReq().a(A.refresh));
		PuckPubResp resp = (PuckPubResp) new HttpServClient()
				.post(HtmlPort.respub.url(synode), req)
				.body(0);
		if (resp.respubs == null)
			resp.respubs = new ArrayList<DocxInf>();
		return resp;
	}

	/** @return published paths, sorted */
	public synchronized String[] paths() {
		return published.keySet().stream().sorted().toArray(String[]::new);
	}

	/** @return published docs, sorted by paths */
	synchronized ArrayList<DocxInf> published() {
		ArrayList<DocxInf> docs = new ArrayList<DocxInf>();
		for (String path : paths())
			docs.add(published.get(path).inf());
		return docs;
	}

	/**
	 * The docs of the index page ({@link ResIndex}): the folders here, in {@link #resroot}, matched
	 * with the synode's published docs ({@link #req2synode()}, asked not more often than per
	 * {@link #indexFresh}) for the packages' titles and creators. A folder the synode doesn't publish
	 * is not listed; it's removed by the next refresh. If the synode can't be reached, all the folders
	 * are listed, as they are still served, each with the title of its index.html.
	 * Nothing is changed here: paths are only deployed or removed by the synode's requests and
	 * the polling ({@link #reconcile(ArrayList)}).
	 * 
	 * @return a new list of the packages' headers, each with pubpath, title and creator
	 */
	ArrayList<DocxInf> listed() {
		boolean ask;
		synchronized (this) {
			long now = System.currentTimeMillis();
			ask = now - indexAsked >= indexFresh;
			if (ask) indexAsked = now;
		}

		if (ask) {
			HashMap<String, DocxInf> docs = null;
			try {
				docs = new HashMap<String, DocxInf>();
				for (DocxInf d : req2synode().respubs)
					if (!isblank(d.pubpath))
						docs.put(d.pubpath, d);
			} catch (Exception e) {
				docs = null;
				Utils.warn("[ResPathsTier] Listing the local docs, as the synode can't be asked. %s: %s",
						e.getClass().getName(), e.getMessage());
			}
			synchronized (this) { indexDocs = docs; }
		}

		synchronized (this) {
			ArrayList<DocxInf> docs = new ArrayList<DocxInf>();
			try (DirectoryStream<Path> dirs = Files.newDirectoryStream(resroot, Files::isDirectory)) {
				for (Path d : dirs) {
					if (StoryShell.viewer.equals(d.getFileName().toString()))
						continue;
					String path = pubroot + "/" + d.getFileName();
					DocxInf doc = indexDocs != null ? indexDocs.get(path) : published.get(path);
					if (doc == null && indexDocs != null)
						continue;

					DocxInf inf = new DocxInf(doc == null ? null : doc.docId,
							doc == null ? htmltitle(d.resolve("index.html")) : doc.title,
							doc == null ? null : doc.creator);
					inf.pubpath = path;
					docs.add(inf);
				}
			} catch (IOException e) {
				Utils.warn("[ResPathsTier] Can't list %s: %s", resroot, e.getMessage());
			}
			return docs;
		}
	}

	static final Pattern regTitle = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	/**
	 * @param html a html file
	 * @return the text of its title element (in the first 64k), without Sphinx's project
	 * suffix (" &amp;#8212; semantic-doc documentation"); null if there isn't one
	 */
	static String htmltitle(Path html) {
		byte[] head = new byte[65536];
		try (InputStream in = Files.newInputStream(html)) {
			int len = in.readNBytes(head, 0, head.length);
			Matcher m = regTitle.matcher(new String(head, 0, len, StandardCharsets.UTF_8));
			if (!m.find())
				return null;
			String t = m.group(1).replaceAll("\\s+", " ").replaceAll(" (&#8212;|\u2014) .* documentation$", "").trim();
			t = t.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
			return isblank(t) ? null : t;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Make context paths the same as the published docs: deploy the new and the rebuilt, remove the
	 * others; the stories' media maps rewritten (a file synchronized since gets mapped).
	 * @param resp the synode's answer: respubs, all the published docs, each with its pubpath; media,
	 * the stories' maps
	 */
	synchronized void reconcile(PuckPubResp resp) throws Exception {
		HashMap<String, PubMedia> media = mediaByDoc(resp.media);
		HashSet<String> pubs = new HashSet<String>();
		for (DocxInf doc : resp.respubs) {
			if (isblank(doc.pubpath))
				continue;
			pubs.add(doc.pubpath);
			try {
				deploy(doc, false);
				if (eq(DocxInf.Kind.story, doc.kind))
					storyMedia.write(doc.pubpath, media.getOrDefault(doc.docId, new PubMedia(doc.docId, doc.pubpath, null)));
			}
			catch (Exception e) {
				// a bad record must not block the others
				Utils.warn("[ResPathsTier] Ignoring path '%s'. %s: %s", doc.pubpath, e.getClass().getName(), e.getMessage());
			}
		}

		for (String path : paths())
			if (!pubs.contains(path))
				undeploy(path);

		// folders kept from before a restart, of docs no longer published
		try (DirectoryStream<Path> dirs = Files.newDirectoryStream(resroot, Files::isDirectory)) {
			for (Path d : dirs) {
				if (StoryShell.viewer.equals(d.getFileName().toString()))
					continue;
				String path = pubroot + "/" + d.getFileName();
				if (!pubs.contains(path)) {
					retire(d);
					storyMedia.remove(path);
					Utils.logi("[ResPathsTier] removed /%s", path);
				}
			}
		}
		storyMedia.report();
	}

	/** the stories' maps by their docIds */
	static HashMap<String, PubMedia> mediaByDoc(ArrayList<PubMedia> media) {
		HashMap<String, PubMedia> m = new HashMap<String, PubMedia>();
		if (media != null)
			for (PubMedia p : media)
				if (p != null && !isblank(p.docId))
					m.put(p.docId, p);
		return m;
	}

	/** @return the request's docs, with pubpaths */
	synchronized ArrayList<DocxInf> deploy(PuckPubReq req) throws Exception {
		HashMap<String, PubMedia> media = mediaByDoc(req.media);
		ArrayList<DocxInf> docs = new ArrayList<DocxInf>();
		if (req.respubs != null)
			for (DocxInf doc : req.respubs) {
				deploy(doc, true);
				// a story's media, its map at the pubpath just assigned
				if (eq(DocxInf.Kind.story, doc.kind))
					storyMedia.write(doc.pubpath, media.getOrDefault(doc.docId, new PubMedia(doc.docId, doc.pubpath, null)));
				docs.add(doc);
			}
		return docs;
	}

	synchronized void remove(PuckPubReq req) throws Exception {
		if (req.respubs != null)
			for (DocxInf doc : req.respubs)
				undeploy(doc.pubpath);
	}

	/**
	 * Publish the doc's html package ({@link DocxInf#htmlUri}) at /{@link DocxInf#pubpath}, e.g. /pub/er3cou.
	 * A doc without pubpath gets a new one, set to {@code doc}.
	 * 
	 * @param force false: a published doc is replaced only if rebuilt, i. e. {@link DocxInf#builtAt}
	 * or {@link DocxInf#htmlUri} changed; true: always, as the synode asked for it.
	 */
	void deploy(DocxInf doc, boolean force) throws Exception {
		if (isblank(doc.pubpath))
			doc.pubpath = getCtxUrlPath(doc);

		DynaPublish old = published.get(doc.pubpath);
		if (!force && old != null && eq(old.builtAt, doc.builtAt) && eq(old.htmlUri, doc.htmlUri))
			return;

		Path dir = folder(doc.pubpath);

		// unzip first, where it's not served: a broken package must not take the published one down
		Path unzipped = Files.createTempDirectory(staging, dir.getFileName() + "-");
		try {
			if (isblank(doc.htmlUri))
				throw new SemanticException("No html package (htmlUri) of doc %s.", doc.docId);
			// a story has no package: its shell, and its committed page (htmlUri) as page.json
			if (eq(DocxInf.Kind.story, doc.kind))
				StoryShell.write(unzipped, doc, pubroot);
			else
				unzip(Paths.get(doc.htmlUri), unzipped);

			// then swap the folders
			retire(dir);
			Files.move(unzipped, dir);
		} catch (Exception e) {
			clean(unzipped);
			throw e;
		}

		published.put(doc.pubpath, new DynaPublish(doc));
		Utils.logi("[ResPathsTier] published /%s <- %s", doc.pubpath, doc.htmlUri);
	}

	void undeploy(String pubpath) throws Exception {
		if (published.remove(pubpath) == null)
			return;

		retire(folder(pubpath));
		storyMedia.remove(pubpath);
		Utils.logi("[ResPathsTier] removed /%s", pubpath);
	}

	/**
	 * Take a published folder away: moved out of the served folder at once, then deleted.
	 * If it can't be moved, e.g. a file is in use (Windows), nothing is changed.
	 */
	void retire(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		Path trash = Files.createTempDirectory(staging, "retired-");
		Files.move(dir, trash.resolve(dir.getFileName()));
		clean(trash);
	}

	/**
	 * @param pubpath e.g. "pub/er3cou"
	 * @return the doc's folder, e.g. respaths/er3cou
	 * @throws SemanticException the path is not the url root and one path segment
	 */
	Path folder(String pubpath) throws SemanticException {
		if (isblank(pubpath) || !pubpath.startsWith(pubroot + "/"))
			throw new SemanticException("The path is not in the root of published docs, /%s.", pubroot);
		String name = pubpath.substring(pubroot.length() + 1);
		if (isblank(name) || name.matches(".*[/\\\\].*|\\.+") || StoryShell.viewer.equals(name))
			throw new SemanticException("The path is not a path segment in /%s.", pubroot);
		return resroot.resolve(name);
	}

	/**
	 * Get the url path of a doc first published: the root and a short, random name, radix 32
	 * (as file names are case insensitive on Windows), e.g. "pub/er3cou".
	 * If a doc already published, e.g. the synode missed the reply, keeps its path.
	 */
	String getCtxUrlPath(DocxInf doc) {
		for (DynaPublish p : published.values())
			if (!isblank(doc.docId) && eq(p.docId, doc.docId))
				return p.pubpath;

		String path;
		do {
			path = pubroot + "/" + Radix32.toString(ThreadLocalRandom.current().nextLong(1L << 30), 6).toLowerCase();
		} while (published.containsKey(path) || Files.exists(resroot.resolve(path.substring(pubroot.length() + 1))));
		return path;
	}

	static void unzip(Path zip, Path dir) throws IOException {
		try (InputStream fin = Files.newInputStream(zip);
			 ZipInputStream zin = new ZipInputStream(fin)) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				Path f = dir.resolve(e.getName()).normalize();
				if (!f.startsWith(dir))
					throw new IOException("Zip entry is out of the target folder: " + e.getName());
				if (e.isDirectory())
					Files.createDirectories(f);
				else {
					Files.createDirectories(f.getParent());
					Files.copy(zin, f, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	/** Delete the folder. Files still in use (Windows) are left to the next start. */
	static void clean(Path dir) {
		if (!Files.exists(dir)) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		} catch (IOException e) {
			Utils.warn("[ResPathsTier] Can't clean %s: %s", dir, e.getMessage());
		}
	}
}
