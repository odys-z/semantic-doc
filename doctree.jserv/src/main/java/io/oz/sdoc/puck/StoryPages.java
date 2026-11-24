package io.oz.sdoc.puck;

import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
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
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.oz.sdoc.DocResHelper;
import io.oz.sdoc.SDocRef;

/**
 * The stories of the working copy (doctree.db): headers in s_stories ({@link S_StoriesMeta}), read as
 * {@link SDocRef} of kind story; pages as blocks in s_storynodes ({@link S_StoryNodesMeta}), read and
 * written as a {@link PuckNode} tree. All through semantic.DA, sql built by semantic.transact.
 *
 * <ul>
 * <li>{@link #save(String, String, PuckNode, IUser)}: a new story, or the page replacing its blocks;</li>
 * <li>{@link #commit(String, IUser)}: the media's pids mapped to their io_oz_synuid, by the album's records
 *     ({@link #album}), the committed page written to {@link DocResHelper#storypage(String, String)} (the story's htmlUri);</li>
 * <li>{@link #resources(String)}: the committed page's media, synuid → file, the resources
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
	final DocResHelper res;
	final IUser robot;

	public final S_StoriesMeta storym;
	public final S_StoryNodesMeta nodem;

	/**
	 * The album, the doc table of the media, {@link DocRef#docm}, e.g. h_photos' meta, the same as the
	 * docx files' (io.oz.sdoc.tier.DoctreeTier#importer(DocRef, ...)); null: committing a story with
	 * media fails.
	 */
	DocRef album;

	public StoryPages album(DocRef album) {
		this.album = album;
		return this;
	}

	public StoryPages(String conn, DATranscxt st, DocResHelper res, IUser robot,
			S_StoriesMeta storym, S_StoryNodesMeta nodem) {
		this.conn = conn;
		this.st = st;
		this.res = res;
		this.robot = robot;
		this.storym = storym;
		this.nodem = nodem;
	}

	/** The story's header, null if the id isn't a story's (stories' ids have an "S" part, docs' don't). */
	public SDocRef story(String storyId) throws TransException, SQLException {
		if (isblank(storyId))
			return null;
		AnResultset rs = (AnResultset) st.select(storym.tbl)
				.whereEq(storym.pk, storyId)
				.rs(st.instancontxt(conn, robot))
				.rs(0);
		return rs.next() ? story(rs) : null;
	}

	/** Headers of all stories. */
	public ArrayList<SDocRef> stories() throws TransException, SQLException {
		return stories((AnResultset) st.select(storym.tbl)
				.orderby(storym.pk)
				.rs(st.instancontxt(conn, robot))
				.rs(0));
	}

	/** Stories published at html-service, i. e. with a pubpath. */
	public ArrayList<SDocRef> published() throws TransException, SQLException {
		return stories((AnResultset) st.select(storym.tbl)
				.where(new Condit(op.isNotnull, storym.pubpath, "null"))
				.orderby(storym.pk)
				.rs(st.instancontxt(conn, robot))
				.rs(0));
	}

	ArrayList<SDocRef> stories(AnResultset rs) throws SQLException {
		ArrayList<SDocRef> docs = new ArrayList<SDocRef>();
		while (rs.next())
			docs.add(story(rs));
		return docs;
	}

	SDocRef story(AnResultset rs) throws SQLException {
		SDocRef d = new SDocRef(rs.getString(storym.pk), rs.getString(storym.title), rs.getString(storym.creator));
		d.mime     = SDocRef.Kind.story;
		d.subtitle = rs.getString(storym.subtitle);
		d.template = rs.getString(storym.template);
		d.cover    = rs.getString(storym.cover);
		d.htmlUri  = rs.getString(storym.htmlUri);
		d.builtAt  = rs.getString(storym.builtAt);
		d.builtBy  = rs.getString(storym.builtBy);
		d.pubpath  = rs.getString(storym.pubpath);
		d.dirty    = rs.getInt(storym.dirty, 0) != 0;
		d.committedAt = rs.getString(storym.committedAt);
		d.state    = SDocRef.State.ready;
		return d;
	}

	/**
	 * The story's page: its header as the root, its blocks as children, in order.
	 * @return null if there's no such story
	 */
	public PuckNode page(String storyId) throws Exception {
		SDocRef d = story(storyId);
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
	public SDocRef save(String synode, String storyId, PuckNode page, IUser usr) throws Exception {
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
	 * {@link DocResHelper#storypage(String, String)}, the story's htmlUri; the cover is the first hero's media.
	 *
	 * @param media where the media are, see {@link MediaSource#extfiles(String, String)}
	 * @return the story's header
	 * @throws SemanticException a media not found, listing them
	 */
	public SDocRef commit(String storyId, IUser usr) throws Exception {

		SDocRef d = story(storyId);
		PuckNode page = page(storyId);
		if (d == null || page == null)
			throw new SemanticException("Can't find story %s.", storyId);

		ArrayList<AnTreeNode> all = media(page);
		LinkedHashSet<String> pids = new LinkedHashSet<String>();
		for (AnTreeNode n : all)
			pids.add(n.id());

		HashMap<String, String> ids = synuids(pids);
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
	 * The resources of a story's committed page on this synode, its media: the album's records, each
	 * with its synuid ({@link DocRef#uids}) and file, the uri resolved to an absolute path
	 * ({@link DocRef#uri64}), what html-service serves as m/&lt;synuid&gt;.
	 * 
	 * <p>A media not synchronized to this synode yet, its record's uri still a doc-ref (an envelope),
	 * or whose record isn't here at all yet, is kept as a reference, the synuid without a path
	 * ({@link DocRef#uri64} null), never resolved here: as html-service queries
	 * again (r/published, by its polling), this is called again and the path is there once the
	 * synchronizing has resolved the record. Nothing else is needed, from the user or the synode.</p>
	 *
	 * @return null if the story isn't committed
	 */
	public ArrayList<DocRef> resources(String storyId) throws Exception {
		SDocRef d = story(storyId);
		if (d == null || isblank(d.htmlUri) || !new File(d.htmlUri).isFile())
			return null;

		PuckNode page = (PuckNode) Anson.fromJson(new String(
				Files.readAllBytes(new File(d.htmlUri).toPath()), StandardCharsets.UTF_8));
		LinkedHashSet<String> uids = new LinkedHashSet<String>();
		for (AnTreeNode n : media(page))
			if (!isblank((String) n.get("synuid")))
				uids.add((String) n.get("synuid"));

		ArrayList<DocRef> res = new ArrayList<DocRef>();
		if (uids.isEmpty() || album == null || album.docm == null)
			return res;

		ExpDocTableMeta m = album.docm;
		DATranscxt ast = new DATranscxt(m.conn());
		AnResultset rs = (AnResultset) ast.select(m.tbl)
				.col(m.pk).col(m.io_oz_synuid).col(m.resname).col(m.uri)
				.col(Funcall.isEnvelope(m.uri), ExpDocTableMeta.isDocRef)
				.whereIn(m.io_oz_synuid, new ArrayList<String>(uids))
				.orderby(m.pk)
				.rs(ast.instancontxt(m.conn(), robot))
				.rs(0);
		while (rs.next()) {
			DocRef r = new DocRef()
					.syntabl(m.tbl)
					.uids(rs.getString(m.io_oz_synuid))
					.docId(rs.getString(m.pk))
					.resname(rs.getString(m.resname));
			// a doc-ref, not synchronized yet: the reference only
			if (rs.getInt(ExpDocTableMeta.isDocRef, 0) == 0) {
				String path = ShExtFilev2.resolvUri(rs.getString(m.uri));
				r.uri64 = !isblank(path) && new File(path).isFile() ? path : null;
			}
			res.add(r);
			uids.remove(r.uids);
		}
		// a media whose record isn't here yet: the reference only, too
		for (String uid : uids)
			res.add(new DocRef().syntabl(m.tbl).uids(uid));
		return res;
	}

	/**
	 * The album's records of the media, by the pids of the album tree's nodes.
	 * @return pid → io_oz_synuid; a pid without a record isn't in it
	 * @throws SemanticException there are media but no {@link #album}
	 */
	HashMap<String, String> synuids(Collection<String> pids) throws Exception {
		HashMap<String, String> ids = new HashMap<String, String>();
		if (pids.isEmpty())
			return ids;
		if (album == null || album.docm == null)
			throw new SemanticException("The album (the media's doc table) isn't configured for stories.");

		ExpDocTableMeta m = album.docm;
		DATranscxt ast = new DATranscxt(m.conn());
		AnResultset rs = (AnResultset) ast.select(m.tbl)
				.col(m.pk).col(m.io_oz_synuid)
				.whereIn(m.pk, new ArrayList<String>(pids))
				.rs(ast.instancontxt(m.conn(), robot))
				.rs(0);
		while (rs.next())
			ids.put(rs.getString(m.pk), rs.getString(m.io_oz_synuid));
		return ids;
	}

	/** Keep the story's url path at html-service; null: no longer published. */
	public SDocRef pubpath(String storyId, String pubpath, IUser usr) throws TransException, SQLException {
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
