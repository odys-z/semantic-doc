package io.oz.sdoc.puck;

import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.DatasetCfg.AnTreeNode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.Resulving;
import io.odysz.transact.sql.parts.condition.Condit;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.DocRes;
import io.oz.sdoc.docx.DocxInf;

/**
 * The stories of the working copy (doctree.db): headers in s_stories ({@link S_StoriesMeta}), read as
 * {@link DocxInf} of kind story; pages as blocks in s_storynodes ({@link S_StoryNodesMeta}), read and
 * written as a {@link PuckNode} tree. All through semantic.DA, sql built by semantic.transact.
 *
 * <ul>
 * <li>{@link #save(String, String, PuckNode, IUser)}: a new story, or the page replacing its blocks;</li>
 * <li>{@link #commit(String, MediaSource, IUser)}: the media's pids mapped to their io_oz_synuid, the
 *     committed page written to {@link DocRes#storypage(String, String)} (the story's htmlUri);</li>
 * <li>{@link #mediaPaths(String, MediaSource)}: the committed page's media, synuid → file, the map
 *     html-service keeps of a published story.</li>
 * </ul>
 *
 * @since 0.1.5
 */
public class StoryPages {
	/** the viewer bundle of the pages written by this version, see {@link S_StoriesMeta#viewer} */
	public static final String viewer = "story-0.1";

	final String conn;
	final DATranscxt st;
	final DocRes res;
	final IUser robot;

	public final S_StoriesMeta storym;
	public final S_StoryNodesMeta nodem;

	public StoryPages(String conn, DATranscxt st, DocRes res, IUser robot,
			S_StoriesMeta storym, S_StoryNodesMeta nodem) {
		this.conn = conn;
		this.st = st;
		this.res = res;
		this.robot = robot;
		this.storym = storym;
		this.nodem = nodem;
	}

	/** The story's header, null if the id isn't a story's (stories' ids have an "S" part, docs' don't). */
	public DocxInf story(String storyId) throws TransException, SQLException {
		if (isblank(storyId))
			return null;
		AnResultset rs = (AnResultset) st.select(storym.tbl)
				.whereEq(storym.pk, storyId)
				.rs(st.instancontxt(conn, robot))
				.rs(0);
		return rs.next() ? story(rs) : null;
	}

	/** Headers of all stories. */
	public ArrayList<DocxInf> stories() throws TransException, SQLException {
		return stories((AnResultset) st.select(storym.tbl)
				.orderby(storym.pk)
				.rs(st.instancontxt(conn, robot))
				.rs(0));
	}

	/** Stories published at html-service, i. e. with a pubpath. */
	public ArrayList<DocxInf> published() throws TransException, SQLException {
		return stories((AnResultset) st.select(storym.tbl)
				.where(new Condit(op.isNotnull, storym.pubpath, "null"))
				.orderby(storym.pk)
				.rs(st.instancontxt(conn, robot))
				.rs(0));
	}

	ArrayList<DocxInf> stories(AnResultset rs) throws SQLException {
		ArrayList<DocxInf> docs = new ArrayList<DocxInf>();
		while (rs.next())
			docs.add(story(rs));
		return docs;
	}

	DocxInf story(AnResultset rs) throws SQLException {
		DocxInf d = new DocxInf(rs.getString(storym.pk), rs.getString(storym.title), rs.getString(storym.creator));
		d.kind     = DocxInf.Kind.story;
		d.subtitle = rs.getString(storym.subtitle);
		d.template = rs.getString(storym.template);
		d.cover    = rs.getString(storym.cover);
		d.htmlUri  = rs.getString(storym.htmlUri);
		d.builtAt  = rs.getString(storym.builtAt);
		d.builtBy  = rs.getString(storym.builtBy);
		d.pubpath  = rs.getString(storym.pubpath);
		d.dirty    = rs.getInt(storym.dirty, 0) != 0;
		d.committedAt = rs.getString(storym.committedAt);
		d.state    = DocxInf.State.ready;
		return d;
	}

	/**
	 * The story's page: its header as the root, its blocks as children, in order.
	 * @return null if there's no such story
	 */
	public PuckNode page(String storyId) throws Exception {
		DocxInf d = story(storyId);
		if (d == null)
			return null;

		PuckNode page = new PuckNode(PuckNode.Type.page, "root");
		page.title = d.title;
		page.subtitle = d.subtitle;
		page.template = d.template;
		page.children = new ArrayList<PuckNode>();

		AnResultset rs = (AnResultset) st.select(nodem.tbl)
				.whereEq(nodem.storyId, storyId)
				.orderby(nodem.sort)
				.rs(st.instancontxt(conn, robot))
				.rs(0);
		while (rs.next()) {
			PuckNode b = new PuckNode(rs.getString(nodem.pucktype), rs.getString(nodem.puckId));
			b.title    = rs.getString(nodem.title);
			b.subtitle = rs.getString(nodem.subtitle);
			b.content  = rs.getString(nodem.content);
			b.layout   = rs.getString(nodem.layout);
			b.media    = media(rs.getString(nodem.media));
			b.items    = items(rs.getString(nodem.items));
			page.children.add(b);
		}
		return page;
	}

	/**
	 * Save a story's page: a new story if storyId is blank, else its header updated and its blocks
	 * replaced, in one transaction. The story gets dirty.
	 *
	 * @param synode this synode, the prefix of a new story's id
	 * @param storyId null: a new story
	 * @param page the page root, its children the blocks
	 * @return the story's header
	 */
	public DocxInf save(String synode, String storyId, PuckNode page, IUser usr) throws Exception {
		if (page == null)
			throw new SemanticException("Saving story %s: no page.", storyId);

		if (isblank(storyId)) {
			SemanticObject res = (SemanticObject) st.insert(storym.tbl, usr)
				.nv(storym.synode, synode) // storyId: autopk, prefixed with the synode and S
				.nv(storym.title, page.title)
				.nv(storym.subtitle, page.subtitle)
				.nv(storym.template, page.template)
				.nv(storym.viewer, viewer)
				.nv(storym.creator, usr.uid())
				.nv(storym.dirty, 1)
				.post(blocks(null, page, usr))
				.ins(st.instancontxt(conn, usr));
			// the blocks refer to the story's id as resulved in the same transaction
			return story(res.resulve(storym.tbl, storym.pk, -1));
		}

		if (story(storyId) == null)
			throw new SemanticException("Can't find story %s.", storyId);

		st.update(storym.tbl, usr)
			.nv(storym.title, page.title)
			.nv(storym.subtitle, page.subtitle)
			.nv(storym.template, page.template)
			.nv(storym.dirty, 1)
			.whereEq(storym.pk, storyId)
			.post(st.delete(nodem.tbl, usr)
					.whereEq(nodem.storyId, storyId))
			.post(blocks(storyId, page, usr))
			.u(st.instancontxt(conn, usr));
		return story(storyId);
	}

	/**
	 * Inserts of the page's blocks.
	 * @param storyId null: the story's id, resulved from the story's insert of the same transaction
	 */
	ArrayList<Statement<?>> blocks(String storyId, PuckNode page, IUser usr) throws Exception {
		ArrayList<Statement<?>> ins = new ArrayList<Statement<?>>();
		if (page.children == null)
			return ins;

		int sort = 0;
		for (PuckNode b : page.children) {
			Insert i = st.insert(nodem.tbl, usr);
			if (storyId == null)
				i.nv(nodem.storyId, new Resulving(storym.tbl, storym.pk));
			else
				i.nv(nodem.storyId, storyId);
			i.nv(nodem.sort, sort++)
			 .nv(nodem.pucktype, b.pucktype)
			 .nv(nodem.puckId, b.id)
			 .nv(nodem.title, b.title)
			 .nv(nodem.subtitle, b.subtitle)
			 .nv(nodem.content, b.content)
			 .nv(nodem.layout, b.layout)
			 .nv(nodem.media, b.media == null ? null : b.media.toBlock())
			 .nv(nodem.items, json(b.items));
			ins.add(i);
		}
		return ins;
	}

	/**
	 * Commit a story: every media's pid (the album node's id) mapped to the record's io_oz_synuid
	 * (node.synuid), the blocks saved so, and the committed page written to
	 * {@link DocRes#storypage(String, String)}, the story's htmlUri; the cover is the first hero's media.
	 *
	 * @param media where the media are, see {@link MediaSource#extfiles(String, String)}
	 * @return the story's header
	 * @throws SemanticException a media not found, listing them
	 */
	public DocxInf commit(String storyId, IUser usr) throws Exception {

		DocxInf d = story(storyId);
		PuckNode page = page(storyId);
		if (d == null || page == null)
			throw new SemanticException("Can't find story %s.", storyId);

		ArrayList<AnTreeNode> all = media(page);
		LinkedHashSet<String> pids = new LinkedHashSet<String>();
		for (AnTreeNode n : all)
			pids.add(n.id());

		HashMap<String, String> ids = null; //media.synuids(pids);
		ArrayList<String> missing = new ArrayList<String>();
		for (String pid : pids)
			if (!ids.containsKey(pid))
				missing.add(pid);
		if (missing.size() > 0)
			throw new SemanticException("Media not found: %s", String.join(", ", missing));

		String cover = null;
		for (AnTreeNode n : all) {
			n.put("synuid", ids.get(n.id()));
			if (cover == null)
				cover = ids.get(n.id());
		}

		File f = res.storypage(d.creator, storyId);
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), page.toBlock().getBytes(StandardCharsets.UTF_8));

		st.update(storym.tbl, usr)
			.nv(storym.htmlUri, f.getAbsolutePath())
			.nv(storym.builtAt, Funcall.now())
			.nv(storym.builtBy, usr.uid())
			.nv(storym.cover, cover)
			.nv(storym.dirty, 0)
			.nv(storym.committedAt, Funcall.now())
			.whereEq(storym.pk, storyId)
			.post(st.delete(nodem.tbl, usr)
					.whereEq(nodem.storyId, storyId))
			.post(blocks(storyId, page, usr))
			.u(st.instancontxt(conn, usr));
		return story(storyId);
	}

	/**
	 * The media of a story's committed page, synuid → the file on this synode: the map html-service
	 * keeps of the published story. Media whose files aren't here are left out.
	 */
	public HashMap<String, String> mediaPaths(String storyId) throws Exception {
		HashMap<String, String> paths = new HashMap<String, String>();
		DocxInf d = story(storyId);

		PuckNode page = (PuckNode) Anson.fromJson(new String(
				Files.readAllBytes(new File(d.htmlUri).toPath()), StandardCharsets.UTF_8));
		for (AnTreeNode n : media(page)) {
			String uid = (String) n.get("synuid");
			if (!isblank(uid) && !paths.containsKey(uid)) {
				String p = null; // media.path(uid);
				if (p != null)
					paths.put(uid, p);
			}
		}
		return paths;
	}

	/** Keep the story's url path at html-service; null: no longer published. */
	public DocxInf pubpath(String storyId, String pubpath, IUser usr) throws TransException, SQLException {
		st.update(storym.tbl, usr)
			.nv(storym.pubpath, pubpath)
			.whereEq(storym.pk, storyId)
			.u(st.instancontxt(conn, usr));
		return story(storyId);
	}

	/** all the media of a page: heroes' media, galleries' items */
	static ArrayList<AnTreeNode> media(PuckNode page) {
		ArrayList<AnTreeNode> all = new ArrayList<AnTreeNode>();
		if (page.children != null)
			for (PuckNode b : page.children) {
				if (b.media != null)
					all.add(b.media);
				if (b.items != null)
					all.addAll(b.items);
			}
		return all;
	}

	/** Anson json of the items, typed by PuckNode's field: '[' ... ']' */
	static String json(ArrayList<AnTreeNode> items) throws IOException {
		if (items == null)
			return null;
		StringBuilder j = new StringBuilder("[");
		for (AnTreeNode n : items) {
			if (j.length() > 1)
				j.append(",\n");
			j.append(n.toBlock());
		}
		return j.append("]").toString();
	}

	static AnTreeNode media(String json) throws Exception {
		return isblank(json) ? null : (AnTreeNode) Anson.fromJson(json);
	}

	static ArrayList<AnTreeNode> items(String json) throws Exception {
		if (isblank(json))
			return null;
		// parsed as a PuckNode's field, which types the elements
		PuckNode holder = (PuckNode) Anson.fromJson(
				"{\"type\": \"" + PuckNode.class.getName() + "\", \"items\": " + json + "}");
		return holder.items;
	}
}
