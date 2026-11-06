package io.oz.sdoc.imports;

/**
 * Facts of a run: text and its direct formatting, as the docx has it, neutral of the
 * editor's schema. The client maps them to Tiptap marks (semantic-doc's doctree/facts.ts),
 * so a palette change needs no re-import.
 *
 * <p>Stored per node as {@link #json(java.util.List)}:</p>
 * <pre>{"v":1,"runs":[{"t":"Revenue grew "},{"t":"12%","b":1,"color":"1E8449"}]}</pre>
 *
 * @author ody
 */
public class Run {
	/** version of the facts json */
	public static final int version = 1;

	public final String t;

	public boolean b, i, u, s;
	/** "sup" | "sub" */
	public String va;
	/** font colour, hex RRGGBB */
	public String color;
	/** highlight name, e.g. "yellow" */
	public String hl;
	/** shading fill, hex RRGGBB */
	public String shd;
	/** font size, half-points */
	public Integer sz;
	/** hyperlink target */
	public String href;

	public Run(String t) {
		this.t = t;
	}

	/**
	 * @param runs
	 * @return facts json of a block: {"v":1,"runs":[...]}
	 */
	public static String json(java.util.List<Run> runs) {
		StringBuilder sb = new StringBuilder("{\"v\":").append(version).append(",\"runs\":[");
		for (int x = 0; x < runs.size(); x++) {
			if (x > 0) sb.append(',');
			runs.get(x).json(sb);
		}
		return sb.append("]}").toString();
	}

	void json(StringBuilder sb) {
		sb.append("{\"t\":").append(str(t));
		if (b) sb.append(",\"b\":1");
		if (i) sb.append(",\"i\":1");
		if (u) sb.append(",\"u\":1");
		if (s) sb.append(",\"s\":1");
		if (va != null)    sb.append(",\"va\":").append(str(va));
		if (color != null) sb.append(",\"color\":").append(str(color));
		if (hl != null)    sb.append(",\"hl\":").append(str(hl));
		if (shd != null)   sb.append(",\"shd\":").append(str(shd));
		if (sz != null)    sb.append(",\"sz\":").append(sz);
		if (href != null)  sb.append(",\"href\":").append(str(href));
		sb.append('}');
	}

	/** a json string */
	static String str(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"':  sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
			}
		}
		return sb.append('"').toString();
	}
}
