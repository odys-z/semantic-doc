package io.oz.sdoc.puck;

import java.util.ArrayList;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.semantic.DA.DatasetCfg.AnTreeNode;

/**
 * A story's page, and each block of it: semantic-doc's TS PuckNode (storytree/templates.ts),
 * {pucktype, id, ...props, children}, "pucktype", not "type", which Anson reads as the class.
 * Only the page root carries "type" (the envelope); blocks and media are typed by the fields.
 *
 * <p>Media are the album tree's nodes as dropped, AnTreeNode: id the h_photos pid while editing;
 * node.synuid, the record's io_oz_synuid, set when the story is committed.</p>
 *
 * @since 0.1.5
 */
public class PuckNode extends Anson {
	/** Values of {@link PuckNode#pucktype}. */
	public static class Type {
		public static final String page = "page";
		public static final String hero = "Hero";
		public static final String text = "Text";
		public static final String gallery = "Gallery";
	}

	/** see {@link Type} */
	public String pucktype;
	/** Puck's id: "root" of the page, e.g. "Hero-1" of a block */
	public String id;

	/** page: the template started from, e.g. "pm-0" */
	public String template;
	/** page, Hero */
	public String title;
	/** page, Hero */
	public String subtitle;
	/** Text */
	public String content;
	/** Gallery */
	public String layout;

	/** Hero: the album node dropped (a media) */
	public AnTreeNode media;

	/** Gallery: the album nodes dropped, a folder's children */
	@AnsonField(valType="io.odysz.semantic.DA.DatasetCfg$AnTreeNode")
	public ArrayList<AnTreeNode> items;

	/** page: the blocks */
	@AnsonField(valType="io.oz.sdoc.story.PuckNode")
	public ArrayList<PuckNode> children;

	public PuckNode() { }

	public PuckNode(String pucktype, String id) {
		this.pucktype = pucktype;
		this.id = id;
	}
}
