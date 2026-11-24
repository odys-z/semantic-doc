package io.oz.sdoc.htmlsrv;

import static io.odysz.common.LangExt.isblank;
import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.head;
import static j2html.TagCreator.html;
import static j2html.TagCreator.iff;
import static j2html.TagCreator.link;
import static j2html.TagCreator.meta;
import static j2html.TagCreator.script;
import static j2html.TagCreator.title;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.oz.sdoc.SDocRef;

/**
 * The index.html of a published story: a shell, the same for every story but its title and cover,
 * written with j2html (as {@link ResIndex}), for {@link ResPathsTier}, which loads the story viewer and its page:
 *
 * <pre>
 * &lt;title&gt;{title}&lt;/title&gt;
 * &lt;meta property="og:title" content="{title}"&gt; &lt;meta property="og:image" content="m/{cover}"&gt;
 * &lt;link rel="stylesheet" href="/{pubroot}/_story/viewer.css"&gt;
 * &lt;div id="story"&gt;&lt;/div&gt;
 * &lt;script src="/{pubroot}/_story/viewer.js" data-page="page.json"&gt;&lt;/script&gt;
 * </pre>
 *
 * The viewer (viewer.js, built by semantic-doc/src/viewer) renders page.json; its media files,
 * m/&lt;synuid&gt;, are to be served from the story's DocRefs (TODO).
 *
 * @since 0.2.2
 */
public class StoryShell {

	/** the viewer's folder under the url root of published docs, e.g. /pub/_story/: never a doc's folder */
	public static final String viewer = "_story";

	/** the story's page in its folder */
	public static final String page = "page.json";

	/**
	 * Write the story's index.html into its folder, and copy its committed page as {@link #page}.
	 *
	 * @param dir the story's folder, before it's served
	 * @param doc the story: title, cover, htmlUri (the committed page)
	 * @param pubroot url root of the published docs, e.g. "pub"
	 */
	public static void write(Path dir, SDocRef doc, String pubroot) throws IOException {
		Files.createDirectories(dir);
		Files.copy(Path.of(doc.htmlUri), dir.resolve(page));
		Files.write(dir.resolve("index.html"), shell(doc, pubroot).getBytes(StandardCharsets.UTF_8));
	}

	static String shell(SDocRef doc, String pubroot) {
		String v = "/" + pubroot + "/" + viewer + "/";
		String t = isblank(doc.title) ? doc.docId : doc.title;
		return "<!DOCTYPE html>\n" + html(
			head(
				meta().withCharset("utf-8"),
				meta().withName("viewport").withContent("width=device-width, initial-scale=1"),
				title(t),
				meta().attr("property", "og:title").withContent(t),
				iff(!isblank(doc.subtitle), meta().attr("property", "og:description").withContent(doc.subtitle)),
				iff(!isblank(doc.cover), meta().attr("property", "og:image")
						.withContent("m/" + URLEncoder.encode(doc.cover == null ? "" : doc.cover, StandardCharsets.UTF_8))),
				link().withRel("stylesheet").withHref(v + "viewer.css")),
			body(
				div().withId("story"),
				script().withSrc(v + "viewer.js").attr("data-page", page))
		).render();
	}
}
