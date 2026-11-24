package io.oz.sdoc.puck;

import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.sdoc.SDocRef;
import io.oz.sdoc.docx.RstreeDBHelper;
import io.oz.sdoc.docx.S_DocNodeMeta;
import io.oz.sdoc.docx.S_DocsMeta;

/**
 * The puck tree's working copy, the same db as the rst tree's ({@link RstreeDBHelper}), with the
 * headers in s_stories ({@link S_StoriesMeta}): the methods both trees have are answered from
 * there, by {@link #stories}, so publishing ({@link ResPublisher}) is the same for both.
 */
public class PucktreeDBHelper extends RstreeDBHelper {

	/** the stories' tables */
	public final StoryPages stories;

	PucktreeDBHelper(String conn, S_DocsMeta docm, S_DocNodeMeta nodem, S_StoriesMeta storym, S_StoryNodesMeta snodem)
			throws Exception {
		super(conn, docm, nodem, storym, snodem);
		stories = new StoryPages(conn, st, res, robot, storym, snodem);
	}

	/**
	 * The puck tree of the doc-tree db, its tables set up as the rst tree's, by
	 * {@link RstreeDBHelper#setup(String, boolean)}.
	 *
	 * @param conn see {@link RstreeDBHelper#connect(String, String, String)}
	 * @return the db
	 */
	public static PucktreeDBHelper setup(String conn) throws Exception {
		RstreeDBHelper.setup(conn, false);
		return new PucktreeDBHelper(conn, new S_DocsMeta(conn), new S_DocNodeMeta(conn),
				new S_StoriesMeta(conn), new S_StoryNodesMeta(conn));
	}

	/** Headers of all stories. */
	@Override
	public ArrayList<SDocRef> docs() throws TransException, SQLException {
		return stories.stories();
	}

	/** The story's header, null if not a story. */
	@Override
	public SDocRef doc(String storyId) throws TransException, SQLException {
		return stories.story(storyId);
	}

	/** Stories published at html-service. */
	@Override
	public ArrayList<SDocRef> published() throws TransException, SQLException {
		return stories.published();
	}

	@Override
	public SDocRef pubpath(String storyId, String pubpath, IUser usr) throws TransException, SQLException {
		return stories.pubpath(storyId, pubpath, usr);
	}

	/** see {@link StoryPages#commit(String, IUser)} */
	@Override
	public SDocRef commit(String storyId, IUser usr) throws Exception {
		return stories.commit(storyId, usr);
	}
}
