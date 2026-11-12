package io.oz.sdoc.build;

import io.odysz.anson.Anson;

/**
 * One file of a doc's Sphinx source, assembled by the client (semantic-doc's rst-assembler.ts,
 * RstFile): index.rst for the doc root, &lt;nid&gt;.rst for a section.
 * 
 * <p>The synode doesn't generate RST: a node's rstext is written by clients, and the files
 * are what the client assembled from the tree when the doc was committed.</p>
 * 
 * @since 0.1.3
 */
public class RstFile extends Anson {
	/** the section (or doc root) the file is built from */
	public String nid;
	/** file name in the Sphinx source folder, "index.rst" for the doc root; no folders */
	public String path;
	/** docname for toctrees: the path without ".rst" */
	public String name;
	public String rst;
	/** names of the resources the file's images link, in media/ beside it, e.g. ["image1.png"] */
	public String[] media;

	public RstFile() { }

	public RstFile(String path, String rst, String... media) {
		this.path = path;
		this.name = path == null ? null : path.replaceAll("\\.rst$", "");
		this.rst = rst;
		this.media = media;
	}
}
