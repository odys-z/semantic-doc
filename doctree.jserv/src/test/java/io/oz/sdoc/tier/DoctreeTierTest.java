package io.oz.sdoc.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;

/**
 * The tier before it's wired to doc data: r/docs is empty, the rest are refused.
 */
class DoctreeTierTest {

	final DoctreeTier tier = new DoctreeTier(Port.echo);

	@Test
	void docsIsEmpty() throws Exception {
		DoctreeReq req = new DoctreeReq("/sdoc");
		req.a(DoctreeReq.A.docs);

		AnsonMsg<? extends AnsonResp> msg = tier.handle(req);

		assertEquals(MsgCode.ok, msg.code());
		assertTrue(((DoctreeResp) msg.body(0)).docs.isEmpty());
	}

	@Test
	void treeWithoutWorkingCopy() throws Exception {
		DoctreeReq req = new DoctreeReq("/sdoc").docId("doc-a");
		req.a(DoctreeReq.A.tree);

		AnsonMsg<? extends AnsonResp> msg = tier.handle(req);

		assertEquals(MsgCode.exSemantic, msg.code());
		assertTrue(msg.body(0).msg().contains("isn't configured"), msg.body(0).msg());
	}
}
