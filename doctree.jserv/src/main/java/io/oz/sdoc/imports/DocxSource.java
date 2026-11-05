package io.oz.sdoc.imports;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.tier.docs.DocUtils;

/**
 * Where the docx of an import is: resolves a record id to the file's path.
 *
 * @author odys
 */
@FunctionalInterface
public interface DocxSource {
	String path(String srcId) throws Exception;

	/**
	 * Docx files uploaded as records of a doc table, e.g. a synode's h_photos.
	 * 
	 * @param conn connection of the doc table
	 * @param docTabl e.g. "h_photos"
	 * @return the source
	 */
	static DocxSource extfiles(String conn, String docTabl) {
		return (srcId) -> {
			ExpDocTableMeta m = (ExpDocTableMeta) Connects.getMeta(conn, docTabl);
			return DocUtils.resolvExtpath_byeid(new DATranscxt(conn), conn, srcId, new JRobot(), m);
		};
	}
}
