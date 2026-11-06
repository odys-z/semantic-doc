package io.oz.sdoc.imports;

import java.util.List;

/**
 * Parses a docx into {@link Block}s, in document order.
 * The docx4j implementation is {@link io.oz.sdoc.imports.DocxWalker#blocks(String)};
 * tests can use their own.
 *
 * @author ody
 */
@FunctionalInterface
public interface IBlockParser {
	List<Block> parse(String docxPath) throws Exception;
}
