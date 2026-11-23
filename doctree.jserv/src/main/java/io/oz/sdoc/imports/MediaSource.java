package io.oz.sdoc.imports;

/**
 * STUB (media story, WP3): where a story's media files are, as {@link DocxSource} is for docx
 * imports: resolves a media record's io_oz_synuid, the same on every synode, to the file's path
 * on this synode's volume.
 *
 * <p>Used when a story is committed / published, and for html-service's map of published media
 * (see {@link io.oz.sdoc.publish.PubMedia}). Injected by the app, e.g. jserv-album's
 * SynotierJettyApp: {@code doctree.media(MediaSource.extfiles(cfg.synconn, "h_photos"))}.</p>
 *
 * @since media story (stub)
 */
@FunctionalInterface
public interface MediaSource {
	/**
	 * @param synuid the media record's io_oz_synuid, "&lt;synode&gt;,&lt;pid&gt;"
	 * @return the file's path; null: no such record, or its file isn't here (not synchronized yet)
	 * @throws Exception
	 */
	String path(String synuid) throws Exception;

	/**
	 * STUB: media files uploaded as records of a doc table, e.g. a synode's h_photos.
	 *
	 * TODO WP3: as {@link DocxSource#extfiles(String, String)}, DocUtils.resolvExtpath_byuid();
	 * null instead of an exception for a missing record or file.
	 *
	 * @param conn connection of the doc table
	 * @param docTabl e.g. "h_photos"
	 * @return the source
	 */
	static MediaSource extfiles(String conn, String docTabl) {
		return (synuid) -> null;
	}
}
