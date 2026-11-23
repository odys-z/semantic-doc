package io.oz.sdoc.publish;

import java.util.HashMap;

import io.odysz.anson.Anson;

/**
 * STUB (media story): the media of a published story, as html-service maps them:
 * /{@link #pubpath}/m/&lt;synuid&gt; → a file of the synode's volume.
 *
 * <p>Only between the synode and the local html-service ({@link ResPublishReq.A#deploy},
 * {@link ResPublishReq.A#refresh}): volume paths never go to browsers, so this is not a field
 * of {@link io.oz.sdoc.DocInf}, which the doc-tree tier answers to editors.</p>
 *
 * @since media story (stub)
 */
public class PubMedia extends Anson {
	/** the story's {@link io.oz.sdoc.DocInf#pubpath}, e.g. "pub/er3cou" */
	public String pubpath;

	/** io_oz_synuid → the file's path on this machine */
	public HashMap<String, String> paths;

	public PubMedia() { }

	public PubMedia(String pubpath, HashMap<String, String> paths) {
		this.pubpath = pubpath;
		this.paths = paths;
	}
}
