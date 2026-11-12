package io.oz.sdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;

/**
 * Round-trips the types through Anson, using nodes from src/dist/jsons/doc-a.json.
 */
class DoctreeTypesTest {

	@Test
	void respRoundTrip() throws Exception {
		DoctreeResp resp = new DoctreeResp()
			.doc(new DocInf("doc-a", "Annual Report 2026", "ody"))
			.node(new DocNode("doc-a", "a1.1", DocNode.Kind.p)
				.parent("a1", 1)
				.json("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Revenue grew \"}]}]}"))
			.node(new DocNode("doc-a", "a2.2", DocNode.Kind.ref).parent("a2", 2));
		resp.nodes.get(1).refId = "b2";

		DoctreeResp back = (DoctreeResp) Anson.fromJson(resp.toBlock());

		assertEquals("Annual Report 2026", back.doc.title);
		assertEquals(2, back.nodes.size());
		assertEquals(resp.nodes.get(0).json, back.nodes.get(0).json);
		assertEquals("b2", back.nodes.get(1).refId);
		assertNull(back.nodes.get(1).json);
	}

	@Test
	void docsRoundTrip() throws Exception {
		ArrayList<DocInf> docs = new ArrayList<DocInf>();
		docs.add(new DocInf("doc-a", "Annual Report 2026", "ody"));
		docs.add(new DocInf("doc-b", "Glossary", "ody"));

		DoctreeResp back = (DoctreeResp) Anson.fromJson(new DoctreeResp().docs(docs).toBlock());

		assertEquals(2, back.docs.size());
		assertEquals("Glossary", back.docs.get(1).title);
	}

	@Test
	void reqRoundTrip() throws Exception {
		DoctreeReq req = new DoctreeReq("/sdoc").docId("doc-a").move("a2.3", "a1", 2);
		req.a(DoctreeReq.A.move);

		DoctreeReq back = (DoctreeReq) Anson.fromJson(req.toBlock());

		assertEquals(DoctreeReq.A.move, back.a());
		assertEquals("a1", back.parentId);
		assertEquals(2, back.sort);
	}
}
