package io.oz.sdoc.story;

import java.util.HashMap;

import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.DoctreeDBHelper;
import io.oz.sdoc.imports.MediaSource;

/**
 * STUB (media story, WP3): the pages of stories in the working copy (doctree.db).
 *
 * <p>A story is a doc (s_docs) of kind {@link DocInf.Kind#story}; its content is one page, the
 * json of semantic-doc/story StoryData (Puck's Data), kept as is while editing, its media
 * referring to h_photos by pid (local, as the album tree gives them).</p>
 *
 * <p>Committing maps each pid to the record's io_oz_synuid (the page's items get uid), writes
 * the committed page (the file of the story's h_photos record, as a doc tree's html package),
 * and the story's media into s_docmedia(docId, synuid). Publishing gives html-service the
 * committed page and the map of the story's media, {@link #mediaPaths(String, MediaSource)}.</p>
 *
 * <p>Tables, all through semantic.DA (DATranscxt, DAHelper), built with semantic.transact:<ul>
 * <li>s_docs.kind, and the page's column (or table: versions per commit is open);</li>
 * <li>s_docmedia(docId, synuid).</li></ul>
 * TODO WP3: both added by DoctreeDBUpgrade, with the picking list's items.</p>
 *
 * @since media story (stub)
 */
public class StoryPages {

	final DoctreeDBHelper db;

	public StoryPages(DoctreeDBHelper db) {
		this.db = db;
	}

	/**
	 * STUB: the story's page, as being edited.
	 * @return json of StoryData; null: a new story (the editor starts from a template)
	 */
	public String page(String docId) throws Exception {
		throw new SemanticException("TODO: StoryPages.page()");
	}

	/**
	 * STUB: save the story's page, as being edited (items by pid).
	 * @return the doc's header (dirty)
	 */
	public DocInf save(String docId, String page, IUser usr) throws Exception {
		throw new SemanticException("TODO: StoryPages.save()");
	}

	/**
	 * STUB: commit the story: map its items' pid → io_oz_synuid (the record's, of the album's
	 * connection), write the committed page, and s_docmedia, in one transaction.
	 * Fails, listing them, if some media's records aren't found.
	 *
	 * @return the doc's header (committed), its htmlUri the committed page's path
	 */
	public DocInf commit(String docId, IUser usr) throws Exception {
		throw new SemanticException("TODO: StoryPages.commit()");
	}

	/**
	 * STUB: the map html-service keeps of a published story's media: synuid → path on this
	 * synode's volume, from s_docmedia and the source. Media whose files aren't here are left out
	 * (html-service answers 404 until its next refresh).
	 *
	 * @param docId the story
	 * @param media where the files are
	 * @return synuid → path
	 */
	public HashMap<String, String> mediaPaths(String docId, MediaSource media) throws Exception {
		throw new SemanticException("TODO: StoryPages.mediaPaths()");
	}
}
