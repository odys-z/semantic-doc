package io.oz.sdoc.htmlsrv;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.odysz.anson.Anson;
import io.odysz.common.Utils;
import io.odysz.semantic.jserv.DynaResource;
import io.odysz.semantic.meta.DocRef;
import io.oz.sdoc.SDocRef;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The media of published stories, /{pubroot}/{story}/m/{synuid}.
 *
 * <p>Nothing is copied when a story is published, and the synode is not asked per request: the
 * synode gives each published story's resources, the album's records with their files' absolute
 * paths ({@link SDocRef#resources}), at publish ({@link HtmlPubReq.A#deploy}) and refresh (startup,
 * polling, io.oz.sdoc.tier.DoctreeReq.A#published), all by loopback. Each story's are kept as a json
 * file (Anson, the story's {@link SDocRef}), {@link #maps}/&lt;story&gt;.res.json, outside the served
 * folder (volume paths are never downloadable), replaced by a temp file moved over it, removed with
 * the story; kept when the service restarts.</p>
 *
 * <p>A media request reads the story's json and streams the file, with a range if asked (videos);
 * 404 if the story or the synuid isn't mapped (e.g. a file not synchronized when the map was
 * given). Other paths go on to the static holder of /{pubroot}/*. No cache:
 * {@link DynaResource#storyMediaCache} is reserved for a future LRU.</p>
 *
 * @since 0.2.2
 */
public class StoryMedia implements Filter {

	/** Warn at startup / refresh if all the maps have more media than this. */
	public static final int WARN_TOTAL = 100_000;
	/** Warn if a story has more media than this (a whole album dropped in by mistake?). */
	public static final int WARN_STORY = 5_000;

	/** {story}/m/{synuid} of the path info under /{pubroot} */
	static final Pattern regMedia = Pattern.compile("^/([^/]+)/m/([^/]+)$");

	static final String suffix = ".res.json";

	/** where the maps' json files are, e.g. &lt;resource&gt;.maps; not served */
	final Path maps;

	/** url root of the published docs, e.g. "pub" */
	final String pubroot;

	public StoryMedia(Path maps, String pubroot) throws IOException {
		this.maps = maps;
		this.pubroot = pubroot;
		Files.createDirectories(maps);
	}

	/** the resources' file of a story, by its pubpath, e.g. "pub/er3cou" → &lt;maps&gt;/er3cou.res.json */
	Path file(String pubpath) {
		String name = pubpath.substring(pubpath.lastIndexOf('/') + 1);
		return maps.resolve(name + suffix);
	}

	/**
	 * Write (replace) the story's resources, at deploy or refresh; unchanged ones are not rewritten.
	 * @param story the story, with its pubpath, e.g. "pub/er3cou", and {@link SDocRef#resources}
	 */
	public synchronized void write(SDocRef story) throws IOException {
		String pubpath = story.pubpath;
		SDocRef media = new SDocRef(story.docId, story.title, story.creator);
		media.pubpath = pubpath;
		media.mime = story.mime;
		media.resources = story.resources == null ? new ArrayList<DocRef>() : story.resources;
		String json = media.toBlock();

		Path f = file(pubpath);
		if (Files.isRegularFile(f) && json.equals(Files.readString(f, StandardCharsets.UTF_8)))
			return;

		Path tmp = Files.createTempFile(maps, f.getFileName().toString(), ".tmp");
		Files.writeString(tmp, json, StandardCharsets.UTF_8);
		Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

		if (media.resources.size() > WARN_STORY)
			Utils.warn("[StoryMedia] Story /%s has %s media, more than %s.", pubpath, media.resources.size(), WARN_STORY);
	}

	/** Remove the story's resources, with the story. */
	public synchronized void remove(String pubpath) throws IOException {
		Files.deleteIfExists(file(pubpath));
	}

	/** @return the story's, with its resources; null if there isn't one */
	SDocRef read(String pubpath) throws IOException {
		Path f = file(pubpath);
		if (!Files.isRegularFile(f))
			return null;
		try {
			return (SDocRef) Anson.fromJson(Files.readString(f, StandardCharsets.UTF_8));
		} catch (Exception e) {
			Utils.warn("[StoryMedia] Can't read %s: %s", f, e.getMessage());
			return null;
		}
	}

	/**
	 * The file of a story's media, read from the story's resources.
	 * @param pubpath the story's, e.g. "pub/er3cou"
	 * @param synuid the media's io_oz_synuid, {@link DocRef#uids}
	 * @return null if it's not a resource of the story
	 */
	public Path path(String pubpath, String synuid) throws IOException {
		SDocRef m = read(pubpath);
		if (m != null && m.resources != null)
			for (DocRef r : m.resources)
				if (r != null && synuid.equals(r.uids))
					return isblank(r.uri64) ? null : Path.of(r.uri64);
		return null;
	}

	/**
	 * Log the maps' size, at startup and after each refresh, e.g.
	 * "[StoryMedia] 12 stories, 1840 media mapped (maps 0.4 MB on disk)";
	 * warn if more than {@link #WARN_TOTAL} media in all, or {@link #WARN_STORY} in a story.
	 * The scan after each query of the synode: the references not resolved yet, the media not
	 * synchronized to the synode ({@link DocRef#uri64} null), are counted; the synode's next answer
	 * has their paths once it has them, without anyone doing anything.
	 * @return the references not resolved
	 */
	public int report() {
		int stories = 0, total = 0, refs = 0;
		long bytes = 0;
		try (DirectoryStream<Path> fs = Files.newDirectoryStream(maps, "*" + suffix)) {
			for (Path f : fs) {
				stories++;
				bytes += Files.size(f);
				String name = f.getFileName().toString();
				SDocRef m = read(pubroot + "/" + name.substring(0, name.length() - suffix.length()));
				int n = m == null || m.resources == null ? 0 : m.resources.size();
				total += n;
				if (m != null && m.resources != null)
					for (DocRef r : m.resources)
						if (r != null && isblank(r.uri64))
							refs++;
				if (n > WARN_STORY)
					Utils.warn("[StoryMedia] Story %s has %s media, more than %s.", name, n, WARN_STORY);
			}
		} catch (IOException e) {
			Utils.warn("[StoryMedia] Can't list %s: %s", maps, e.getMessage());
		}

		Utils.logi("[StoryMedia] %s stories, %s media mapped (maps %.1f KB on disk), %s",
				stories, total, bytes / 1024.0, maps);
		if (total > WARN_TOTAL)
			Utils.warn("[StoryMedia] %s media mapped, more than %s: consider unpublishing old stories.", total, WARN_TOTAL);
		if (refs > 0)
			Utils.logi("[StoryMedia] %s media not synchronized to the synode yet (404 until then), resolved by a later query.", refs);
		return refs;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;

		Matcher m = req.getPathInfo() == null ? null : regMedia.matcher(req.getPathInfo());
		if (m == null || !m.find() || !("GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod()))) {
			chain.doFilter(request, response);
			return;
		}

		Path f = path(pubroot + "/" + m.group(1), m.group(2));
		if (f == null || !Files.isRegularFile(f)) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		serve(req, resp, f);
	}

	static final Pattern regRange = Pattern.compile("^bytes=(\\d*)-(\\d*)$");

	/** Stream the file, or the one range asked (e.g. a video's), with its mime type. */
	static void serve(HttpServletRequest req, HttpServletResponse resp, Path f) throws IOException {
		long len = Files.size(f);
		long from = 0, to = len - 1;

		String range = req.getHeader("Range");
		if (!isblank(range)) {
			Matcher r = regRange.matcher(range.trim());
			if (r.find() && !(isblank(r.group(1)) && isblank(r.group(2)))) {
				if (isblank(r.group(1))) // bytes=-n: the last n
					from = Math.max(0, len - Long.parseLong(r.group(2)));
				else {
					from = Long.parseLong(r.group(1));
					if (!isblank(r.group(2)))
						to = Math.min(len - 1, Long.parseLong(r.group(2)));
				}
				if (from > to || from >= len) {
					resp.setHeader("Content-Range", "bytes */" + len);
					resp.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
					return;
				}
				resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
				resp.setHeader("Content-Range", "bytes " + from + "-" + to + "/" + len);
			}
		}

		String mime = req.getServletContext().getMimeType(f.getFileName().toString());
		resp.setContentType(isblank(mime) ? "application/octet-stream" : mime);
		resp.setHeader("Accept-Ranges", "bytes");
		resp.setHeader("Cache-Control", "max-age=3600");
		resp.setContentLengthLong(to - from + 1);
		if ("HEAD".equals(req.getMethod()))
			return;

		try (InputStream in = Files.newInputStream(f)) {
			in.skipNBytes(from);
			OutputStream out = resp.getOutputStream();
			byte[] buf = new byte[65536];
			long left = to - from + 1;
			while (left > 0) {
				int n = in.read(buf, 0, (int) Math.min(buf.length, left));
				if (n < 0) break;
				out.write(buf, 0, n);
				left -= n;
			}
		}
	}
}
