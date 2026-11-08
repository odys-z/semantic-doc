package io.oz.sdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;

/**
 * Round-trips the types through Anson, using nodes from src/dist/jsons/doc-a.json.
 */
class DoctreeTypesTest {

	@Test
	void respRoundTrip() throws Exception {
		DocInf doc = new DocInf("doc-a", "Annual Report 2026", "ody");
		doc.dirty = true;
		DoctreeResp resp = new DoctreeResp().doc(doc).newId("a1.2");
		resp.root("a1").rejected("no-op");

		DoctreeResp back = (DoctreeResp) Anson.fromJson(resp.toBlock());

		assertEquals("Annual Report 2026", back.doc.title);
		assertTrue(back.doc.dirty);
		assertNull(back.doc.committedAt);
		assertEquals("a1.2", back.newId);
		assertEquals("a1", back.root());
		assertEquals("no-op", back.rejected());
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
		assertEquals("u/move", back.a());
		assertEquals("a2.3", back.root());
		assertEquals("a1", back.toParent);
		assertEquals(2, back.toIndex);
	}

	@Test
	void insertRoundTrip() throws Exception {
		DoctreeReq req = new DoctreeReq("/sdoc").docId("doc-a").insert("a1", 0, "kind", "p", "docId", "doc-a");
		req.a(DoctreeReq.A.insert);

		DoctreeReq back = (DoctreeReq) Anson.fromJson(req.toBlock());

		assertEquals("i", back.a());
		assertEquals("a1", back.toParent);
		assertEquals(2, back.nvs.size());
		assertEquals("kind", back.nvs.get(0)[0]);
		assertEquals("p", back.nvs.get(0)[1]);
	}

	@Test
	void updateRoundTrip() throws Exception {
		DoctreeReq req = new DoctreeReq("/sdoc").node(new DocNode("doc-a", "a1.1", DocNode.Kind.p)
				.json("{\"contype\":\"doc\"}").rst("{\"body\":\"x\"}", 1));
		req.a(DoctreeReq.A.update);

		DoctreeReq back = (DoctreeReq) Anson.fromJson(req.toBlock());

		assertEquals("u/node", back.a());
		assertEquals("doc-a", back.docId);
		assertEquals("a1.1", back.node.nid);
		assertEquals(req.node.tipjson, back.node.tipjson);
		assertEquals(1, back.node.rstver);
	}
}
