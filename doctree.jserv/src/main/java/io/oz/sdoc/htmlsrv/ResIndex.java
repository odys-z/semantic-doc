package io.oz.sdoc.htmlsrv;

import static io.odysz.common.LangExt.isblank;
import static j2html.TagCreator.a;
import static j2html.TagCreator.body;
import static j2html.TagCreator.each;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.head;
import static j2html.TagCreator.iff;
import static j2html.TagCreator.li;
import static j2html.TagCreator.meta;
import static j2html.TagCreator.p;
import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.span;
import static j2html.TagCreator.style;
import static j2html.TagCreator.title;
import static j2html.TagCreator.ul;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import io.odysz.semantic.jserv.DynaResource;
import io.oz.sdoc.SDocRef;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The index page of the published html &amp; puck packages, /{@link DynaResource#danyroot}/, e. g. /pub/:
 * for each package, a topic, its title (the link) and creator, of {@link ResPathsTier#listed()},
 * written for each request (no file), with j2html, the html generator of semantic.jserv.
 * A package isn't a docx: it's what's committed of a doc tree, which can include or refer to
 * any docx.
 * {@link DynaResource#allowDir} is only for the folders in a doc.
 *
 * @since 0.2.2
 */
public class ResIndex extends HttpServlet {
	private static final long serialVersionUID = 1L;

	static final String title = "Published Docs";

	final ResPathsTier tier;

	ResIndex(ResPathsTier tier) {
		this.tier = tier;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String root = req.getContextPath() + "/" + tier.pubroot + "/";

		// "/pub": the docs' links are relative to "/pub/"
		if (!req.getRequestURI().startsWith(root)) {
			resp.sendRedirect(root);
			return;
		}

		byte[] page = html(tier.listed(), req.getContextPath()).getBytes(StandardCharsets.UTF_8);
		resp.setContentType("text/html;charset=utf-8");
		resp.setHeader("Cache-Control", "no-cache");
		resp.setContentLength(page.length);
		resp.getOutputStream().write(page);
	}

	static final String css =
		  "body { font-family: sans-serif; max-width: 48em; margin: 2em auto; padding: 0 1em; color: #333; }\n"
		+ "h1 { font-size: 1.4em; border-bottom: 1px solid #ddd; padding-bottom: .4em; }\n"
		+ "ul { list-style: none; padding: 0; }\n"
		+ "li { padding: .5em 0; border-bottom: 1px solid #eee; }\n"
		+ "a { color: #1565c0; text-decoration: none; }\n"
		+ "a:hover { text-decoration: underline; }\n"
		+ ".creator, .none { color: #888; font-size: .85em; }\n"
		+ ".creator { float: right; }\n";

	// TODO now need also list puck pages.
	// TODO this return may finally be chaged a resource list (json) for a template page, like the one of odys-z.github.io/landing pages. 
	/**
	 * Write the page with j2html, as semantic.jserv's helper, io.odysz.semantic.jserv.helper.Html,
	 * does: texts and attributes are escaped by it.
	 * 
	 * @param docs headers of the packages published, each with pubpath, e.g. "pub/er3cou"
	 * @param ctxpath the context path, "" for the root
	 * @return the page
	 */
	static String html(ArrayList<SDocRef> docs, String ctxpath) {
		docs.sort((a, b) -> {
			int c = name(a).compareToIgnoreCase(name(b));
			return c != 0 ? c : a.committedAt.compareTo(b.committedAt);
		});

		return "<!DOCTYPE HTML>" + j2html.TagCreator.html(
			head(
				meta().withCharset("utf-8"),
				meta().withName("viewport").withContent("width=device-width, initial-scale=1"),
				title(title),
				style().with(rawHtml(css))),
			body(
				h1(title),
				docs.isEmpty()
				? p("No docs published.").withClass("none")
				: ul(each(docs, d -> li(
					a(name(d)).withHref(ctxpath + "/" + d.pubpath + "/"),
					iff(!isblank(d.creator), span(d.creator).withClass("creator")))))
			)).render();
	}

	/** @return the package's title, or its path if without a title */
	static String name(SDocRef d) {
		return isblank(d.title) ? d.pubpath : d.title;
	}
}
