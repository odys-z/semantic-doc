package io.oz.sdoc.docx;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.docx4j.XmlUtils;
import org.docx4j.dml.CTBlip;
import org.docx4j.dml.Graphic;
import org.docx4j.dml.wordprocessingDrawing.Anchor;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.model.PropertyResolver;
import org.docx4j.model.listnumbering.Emulator;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.CTObject;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.STVerticalAlignRun;
import org.docx4j.wml.SdtRun;
import org.docx4j.wml.SdtBlock;
import org.docx4j.wml.SdtElement;
import org.docx4j.wml.Style;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.TcPr;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.docx4j.wml.UnderlineEnumeration;

import io.oz.sdoc.imports.Block;
import io.oz.sdoc.imports.Run;

/**
 * The "facts first" walker of hello/docx4j (FactsDemo), producing doctree.jserv's {@link Block}s:
 * per block, the source path, style, effective outline level and numbering label;
 * per paragraph, its runs: text with direct formatting ({@link Run}).
 *
 * <p>Pictures (w:drawing, inline or anchored, embedded in the docx) of a paragraph are
 * {@link Block#image} blocks following the paragraph's block. An embedded Excel object is
 * the text {@link #excelIgnored} in its paragraph. Pictures in table cells, VML pictures (w:pict)
 * and linked (external) pictures are not collected.</p>
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
	/** the text an embedded Excel object (w:object, ProgID Excel.*) is imported as */
	public static final String excelIgnored = "[excel object is ignored in semantic-doc 0.1.1]";

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
			if (o instanceof P p) {
				blocks.add(paragraph(p, here));
				pictures(p, here);
			}
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
		runs(p.getContent(), null, b.runs);

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

	/**
	 * Add an {@link Block#image} block for each embedded picture (w:drawing) of the paragraph,
	 * src: the paragraph's + "/pic/" + index, text: the picture's file name.
	 */
	void pictures(P p, String src) {
		List<String> parts = new ArrayList<>();
		embeds(p, parts);
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i);
			blocks.add(new Block(Block.image, src + "/pic/" + i)
					.res(part)
					.text(part.substring(part.lastIndexOf('/') + 1)));
		}
	}

	/** Collect the parts of the pictures under a node, in order: runs, hyperlinks, inline sdt. */
	void embeds(Object o, List<String> parts) {
		o = XmlUtils.unwrap(o);
		if (o instanceof Drawing d) {
			for (Object g : d.getAnchorOrInline()) {
				Graphic graphic = g instanceof Inline in ? in.getGraphic()
								: g instanceof Anchor an ? an.getGraphic() : null;
				String part = part(graphic);
				if (part != null) parts.add(part);
			}
		}
		else if (o instanceof SdtElement se && se.getSdtContent() != null)
			for (Object c : se.getSdtContent().getContent()) embeds(c, parts);
		else if (o instanceof ContentAccessor ca)
			for (Object c : ca.getContent()) embeds(c, parts);
	}

	/** the part (zip entry) of a picture embedded in the docx, e.g. "word/media/image1.png"; else null */
	String part(Graphic graphic) {
		try {
			CTBlip blip = graphic.getGraphicData().getPic().getBlipFill().getBlip();
			RelationshipsPart rels = pkg.getMainDocumentPart().getRelationshipsPart();
			Relationship rel = rels.getRelationshipByID(blip.getEmbed());
			Part part = rels.getPart(rel);
			String name = part.getPartName().getName();
			return name.startsWith("/") ? name.substring(1) : name;
		} catch (Exception e) {
			return null; // a chart, a shape, a linked picture ...
		}
	}

	/**
	 * Collect the runs of a paragraph's content, in order: runs, runs in hyperlinks
	 * and inline content controls. Bookmarks, proofing marks etc. are skipped.
	 *
	 * @param content
	 * @param href the target of the hyperlink the content is in, or null
	 * @param runs output
	 */
	void runs(List<Object> content, String href, List<Run> runs) {
		for (Object c : content) {
			Object o = XmlUtils.unwrap(c);
			if (o instanceof R r) {
				Run f = facts(r);
				if (f != null) {
					f.href = href;
					runs.add(f);
				}
			}
			else if (o instanceof P.Hyperlink h)
				runs(h.getContent(), target(h), runs);
			else if (o instanceof SdtRun sdt && sdt.getSdtContent() != null)
				runs(sdt.getSdtContent().getContent(), href, runs);
		}
	}

	/** a run's text and direct formatting (rPr only: style formatting isn't the author's emphasis) */
	static Run facts(R r) {
		StringBuilder sb = new StringBuilder();
		collect(r, sb); // not trimmed: the spaces between runs are text
		String t = sb.toString();
		if (t.isEmpty()) return null;

		Run f = new Run(t);
		RPr pr = r.getRPr();
		if (pr == null) return f;

		f.b = on(pr.getB());
		f.i = on(pr.getI());
		f.u = pr.getU() != null && pr.getU().getVal() != null && pr.getU().getVal() != UnderlineEnumeration.NONE;
		f.s = on(pr.getStrike()) || on(pr.getDstrike());
		if (pr.getVertAlign() != null && pr.getVertAlign().getVal() != null)
			f.va = pr.getVertAlign().getVal() == STVerticalAlignRun.SUPERSCRIPT ? "sup"
				 : pr.getVertAlign().getVal() == STVerticalAlignRun.SUBSCRIPT ? "sub" : null;
		if (pr.getColor() != null && pr.getColor().getVal() != null && !"auto".equalsIgnoreCase(pr.getColor().getVal()))
			f.color = pr.getColor().getVal();
		if (pr.getHighlight() != null && !"none".equalsIgnoreCase(pr.getHighlight().getVal()))
			f.hl = pr.getHighlight().getVal();
		if (pr.getShd() != null && pr.getShd().getFill() != null && !"auto".equalsIgnoreCase(pr.getShd().getFill()))
			f.shd = pr.getShd().getFill();
		if (pr.getSz() != null && pr.getSz().getVal() != null)
			f.sz = pr.getSz().getVal().intValue();
		return f;
	}

	static boolean on(BooleanDefaultTrue v) {
		return v != null && v.isVal();
	}

	/** a hyperlink's target: the relationship's url, or "#anchor" inside the docx */
	String target(P.Hyperlink h) {
		try {
			if (h.getId() != null) {
				RelationshipsPart rels = pkg.getMainDocumentPart().getRelationshipsPart();
				Relationship rel = rels == null ? null : rels.getRelationshipByID(h.getId());
				if (rel != null) return rel.getTarget();
			}
		} catch (Exception e) { }
		return h.getAnchor() != null ? "#" + h.getAnchor() : null;
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

	/**
	 * Concatenate w:t text under any node: runs, hyperlinks, inline sdt, cells;
	 * an embedded Excel object is {@link #excelIgnored}.
	 */
	static String text(Object node) {
		StringBuilder sb = new StringBuilder();
		collect(node, sb);
		return sb.toString().trim();
	}

	/** w:object with an o:OLEObject of ProgID Excel.*, e.g. "Excel.Sheet.12" */
	static boolean isExcel(CTObject obj) {
		for (Object any : obj.getAnyAndAny())
			if (XmlUtils.unwrap(any) instanceof org.docx4j.vml.officedrawing.CTOLEObject ole
					&& ole.getProgID() != null && ole.getProgID().startsWith("Excel."))
				return true;
		return false;
	}

	static void collect(Object o, StringBuilder sb) {
		o = XmlUtils.unwrap(o);
		if (o instanceof Text t) sb.append(t.getValue());
		else if (o instanceof R.Tab) sb.append('\t');
		else if (o instanceof CTObject obj) {
			if (isExcel(obj)) sb.append(excelIgnored);
		}
		else if (o instanceof SdtElement se && se.getSdtContent() != null)
			for (Object c : se.getSdtContent().getContent()) collect(c, sb);
		else if (o instanceof ContentAccessor ca) {
			if (o instanceof Tc && sb.length() > 0) sb.append(' ');
			for (Object c : ca.getContent()) collect(c, sb);
		}
	}
}
