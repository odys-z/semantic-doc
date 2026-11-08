package io.oz.sdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;
import io.odysz.semantic.ext.TreeModify;

/**
 * Round-trips the types through Anson, using nodes from src/dist/jsons/doc-a.json.
 */
class DoctreeTypesTest {

	@Test
	void respRoundTrip() throws Exception {
		DocInf doc = new DocInf("doc-a", "Annual Report 2026", "ody");
		doc.dirty = true;
		DoctreeResp resp = new DoctreeResp().doc(doc);
		resp.root("a1").rejected(TreeModify.Rejected.noop).newId("a1.2");

		DoctreeResp back = (DoctreeResp) Anson.fromJson(resp.toBlock());

		assertEquals("Annual Report 2026", back.doc.title);
		assertTrue(back.doc.dirty);
		assertNull(back.doc.committedAt);
		assertEquals("a1.2", back.newId());
		assertEquals("a1", back.root());
		assertEquals(TreeModify.Rejected.noop, back.rejected());
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
	void modifyRoundTrip() throws Exception {
		DoctreeReq req = new DoctreeReq("/sdoc").docId("doc-a").modify(TreeModify.indent("a2.3"));
		req.a(DoctreeReq.A.modify);

		DoctreeReq back = (DoctreeReq) Anson.fromJson(req.toBlock());

		assertEquals("u/tree", back.a());
		assertEquals("doc-a", back.docId);
		assertEquals(TreeModify.Op.indent, back.mod.op);
		assertEquals("a2.3", back.mod.nid);
	}

	@Test
	void insertRoundTrip() throws Exception {
		DoctreeReq req = new DoctreeReq("/sdoc").docId("doc-a")
				.modify(TreeModify.insert("a1", TreeModify.At.first, "kind", "p"));
		req.a(DoctreeReq.A.modify);

		DoctreeReq back = (DoctreeReq) Anson.fromJson(req.toBlock());

		assertEquals(TreeModify.Op.insert, back.mod.op);
		assertEquals(TreeModify.At.first, back.mod.at);
		assertEquals("kind", back.mod.nvs.get(0)[0]);
		assertEquals("p", back.mod.nvs.get(0)[1]);
	}

	@Test
	void updateRoundTrip() throws Exception {
		DoctreeReq req = new DoctreeReq("/sdoc").node(new DocNode("doc-a", "a1.1", DocNode.Kind.p)
				.tipjson("{\"contype\":\"doc\"}").rstext("{\"body\":\"x\"}", 1));
		req.a(DoctreeReq.A.update);

		DoctreeReq back = (DoctreeReq) Anson.fromJson(req.toBlock());

		assertEquals("u/node", back.a());
		assertEquals("doc-a", back.docId);
		assertEquals("a1.1", back.node.nid);
		assertEquals(req.node.tipjson, back.node.tipjson);
		assertEquals(req.node.rstext, back.node.rstext);
		assertEquals(1, back.node.rstver);
	}
}
