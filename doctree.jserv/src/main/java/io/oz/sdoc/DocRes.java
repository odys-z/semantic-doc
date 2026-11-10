package io.oz.sdoc;

import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;

/**
 * Resources (pictures) of the docs in the working copy: files in
 * &lt;volume&gt;/&lt;uid&gt;/{@link #folder}/&lt;docId&gt;/, the volume: where the doc-tree db is.
 *
 * <p>The uid is the doc's creator ({@link DocInf#creator}), not a session id: the user goes on
 * with his work in another session, and the folder is found by the doc.</p>
 *
 * <p>A node's {@link DocNode#resId} is the file's name in its doc's folder, e.g. "image1.png":
 * a reference relative to the doc, as a relative src of a html page.</p>
 *
 * <p>Temporary but persisted: not a session's temp folder, the files are there for the user's
 * next visit. They are to be packed into the doc's zip and deleted when the doc is committed (TODO).</p>
 *
 * @author ody
 */
public class DocRes {
	/** the resources' folder of a user, in his folder of the synode's volume */
	public static final String folder = "doctree.temp";

	final File volume;

	/**
	 * @param volume the folder of the doc-tree db
	 */
	public DocRes(String volume) {
		this.volume = new File(volume);
	}

	/**
	 * The doc's folder, may not exist yet.
	 *
	 * @param uid the doc's creator
	 * @param docId
	 * @throws IOException uid or docId isn't a folder name
	 */
	public File dir(String uid, String docId) throws IOException {
		return new File(new File(new File(volume, name(uid)), folder), name(docId));
	}

	/**
	 * @param uid the doc's creator
	 * @param docId
	 * @param resId a file name, no path
	 * @return the resource's file, may not exist
	 * @throws IOException resId isn't a file name
	 */
	public File file(String uid, String docId, String resId) throws IOException {
		return new File(dir(uid, docId), name(resId));
	}

	/** a file / folder name, no path */
	static String name(String n) throws IOException {
		if (isblank(n) || !n.equals(new File(n).getName()) || n.startsWith("."))
			throw new IOException("Not a name of a resource, doc or user: " + n);
		return n;
	}

	/**
	 * Copy a part of a docx, e.g. a picture, into the doc's folder; a file of the name is replaced.
	 *
	 * @param uid the doc's creator
	 * @param docId
	 * @param docx path of the docx
	 * @param part zip entry in the docx, e.g. "word/media/image1.png"
	 * @return resId, the file's name: "image1.png"
	 * @throws IOException no such part, or the file can't be written
	 */
	public String saveDocxPart(String uid, String docId, String docx, String part) throws IOException {
		String resId = part.substring(part.lastIndexOf('/') + 1);
		File f = file(uid, docId, resId);

		try (ZipFile zip = new ZipFile(docx)) {
			ZipEntry e = zip.getEntry(part);
			if (e == null)
				throw new IOException(String.format("No part %s in %s.", part, docx));
			Files.createDirectories(f.getParentFile().toPath());
			try (InputStream in = zip.getInputStream(e)) {
				Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		return resId;
	}

	/** Delete the doc's folder with its resources, e.g. of a failed import. */
	public void delete(String uid, String docId) {
		try { FileUtils.deleteQuietly(dir(uid, docId)); }
		catch (IOException e) { } // not a folder: nothing to delete
	}
}
