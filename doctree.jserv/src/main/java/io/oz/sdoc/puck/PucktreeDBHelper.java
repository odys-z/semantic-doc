package io.oz.sdoc.puck;

import io.oz.sdoc.docx.RstreeDBHelper;
import io.oz.sdoc.docx.S_DocNodeMeta;
import io.oz.sdoc.docx.S_DocsMeta;

public class PucktreeDBHelper extends RstreeDBHelper {

	PucktreeDBHelper(String conn, S_DocsMeta docm, S_DocNodeMeta nodem, S_StoriesMeta storym, S_StoryNodesMeta snodem)
			throws Exception {
		super(conn, docm, nodem, storym, snodem);
	}

}
