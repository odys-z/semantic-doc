package io.oz.sdoc.docx;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.docx4j.XmlUtils;
import org.docx4j.model.PropertyResolver;
import org.docx4j.model.listnumbering.Emulator;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.SdtBlock;
import org.docx4j.wml.SdtElement;
import org.docx4j.wml.Style;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.TcPr;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

import io.oz.sdoc.imports.Block;

/**
 * The "facts first" walker of hello/docx4j (FactsDemo), producing doctree.jserv's {@link Block}s:
 * per block, the source path, style, effective outline level and numbering label.
 *
 * <p>Usage: {@code tier.importer(source, DocxWalker::blocks)}.</p>
 *
 * <p>A walker is stateful (numbering counters), so {@link #blocks(String)} uses a new one per file.</p>
 *
 * @author ody
 */
public class DocxWalker {

	/**
	 * {@link IBlockParser} of docx files.
	 *
	 * @param docxPath
	 * @return blocks in document order
	 * @throws Exception
	 */
	public static List<Block> blocks(String docxPath) throws Exception {
		WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new File(docxPath));
		DocxWalker w = new DocxWalker(pkg);
		w.walk(pkg.getMainDocumentPart().getContent(), "body");
		return w.blocks;
	}

	final WordprocessingMLPackage pkg;
	final PropertyResolver resolver;
	final Map<String, Style> stylesById = new HashMap<>();
	final List<Block> blocks = new ArrayList<>();

	DocxWalker(WordprocessingMLPackage pkg) throws Exception {
		this.pkg = pkg;
		MainDocumentPart mdp = pkg.getMainDocumentPart();
		this.resolver = mdp.getPropertyResolver();
		for (Style s : mdp.getStyleDefinitionsPart().getJaxbElement().getStyle())
			stylesById.put(s.getStyleId(), s);
	}

	/** Walk block-level content in document order. src = path back into the source. */
	void walk(List<Object> content, String src) {
		for (int i = 0; i < content.size(); i++) {
			Object o = XmlUtils.unwrap(content.get(i)); // strips JAXBElement wrappers
			String here = src + "/" + i;
			if (o instanceof P p)
				blocks.add(paragraph(p, here));
			else if (o instanceof Tbl tbl)
				blocks.add(table(tbl, here));
			else if (o instanceof SdtBlock sdt) // content control: recurse
				walk(sdt.getSdtContent().getContent(), here + "/sdt");
			// sectPr, bookmarks, altChunk (CTAltChunk) etc. would be handled here
		}
	}

	Block paragraph(P p, String src) {
		PPr direct = p.getPPr();
		String styleId = (direct != null && direct.getPStyle() != null)
				? direct.getPStyle().getVal() : "Normal";
		Style st = stylesById.get(styleId);
		Block b = new Block(Block.p, src)
				.style(styleId, (st != null && st.getName() != null) ? st.getName().getVal() : styleId)
				.text(text(p));

		// explicit resolution: direct pPr, then the style's basedOn chain;
		// docx4j's resolver (doc defaults + styles + direct) where that finds nothing
		PPr eff = resolver.getEffectivePPr(direct);
		Integer outline = outline(direct);
		String chain = styleId;
		for (int guard = 0; outline == null && chain != null && guard < 20; guard++) {
			Style s = stylesById.get(chain);
			if (s == null) break;
			outline = outline(s.getPPr());
			chain = s.getBasedOn() != null ? s.getBasedOn().getVal() : null;
		}
		if (outline == null) outline = outline(eff);
		if (outline == null && "Title".equals(styleId)) outline = -1; // above level 1
		b.outline(outline);

		// numbering label as Word would render it ("1.", "a)", ...); stateful: call in order
		PPrBase.NumPr numPr = eff != null ? eff.getNumPr() : null;
		if (numPr != null && numPr.getNumId() != null) {
			String ilvl = numPr.getIlvl() != null ? numPr.getIlvl().getVal().toString() : "0";
			try {
				Emulator.ResultTriple rt = Emulator.getNumber(pkg, styleId,
						numPr.getNumId().getVal().toString(), ilvl);
				if (rt != null) b.num(rt.getNumString());
			} catch (Exception e) { b.num("?"); }
		}
		return b;
	}

	static Integer outline(PPr ppr) {
		if (ppr == null || ppr.getOutlineLvl() == null) return null;
		BigInteger v = ppr.getOutlineLvl().getVal();
		return (v == null || v.intValue() >= 9) ? null : v.intValue(); // 9 = body text
	}

	Block table(Tbl tbl, String src) {
		Block b = new Block(Block.table, src);
		List<String> rows = new ArrayList<>();
		for (Object ro : tbl.getContent()) {
			if (!(XmlUtils.unwrap(ro) instanceof Tr tr)) continue;
			List<String> cells = new ArrayList<>();
			for (Object co : tr.getContent()) {
				if (!(XmlUtils.unwrap(co) instanceof Tc tc)) continue;
				String cell = text(tc);
				TcPr pr = tc.getTcPr();
				if (pr != null && pr.getGridSpan() != null)
					cell += " {colspan=" + pr.getGridSpan().getVal() + "}";
				if (pr != null && pr.getVMerge() != null) // val "restart" starts; null continues
					cell += pr.getVMerge().getVal() == null ? " {vmerge-cont}" : " {vmerge-start}";
				cells.add(cell);
			}
			rows.add(String.join(" | ", cells));
		}
		b.text(String.join(" // ", rows));
		b.hasSpans = b.text.contains("{colspan") || b.text.contains("{vmerge");
		return b;
	}

	/** Concatenate w:t text under any node: runs, hyperlinks, inline sdt, cells. */
	static String text(Object node) {
		StringBuilder sb = new StringBuilder();
		collect(node, sb);
		return sb.toString().trim();
	}

	static void collect(Object o, StringBuilder sb) {
		o = XmlUtils.unwrap(o);
		if (o instanceof Text t) sb.append(t.getValue());
		else if (o instanceof R.Tab) sb.append('\t');
		else if (o instanceof SdtElement se && se.getSdtContent() != null)
			for (Object c : se.getSdtContent().getContent()) collect(c, sb);
		else if (o instanceof ContentAccessor ca) {
			if (o instanceof Tc && sb.length() > 0) sb.append(' ');
			for (Object c : ca.getContent()) collect(c, sb);
		}
	}
}
