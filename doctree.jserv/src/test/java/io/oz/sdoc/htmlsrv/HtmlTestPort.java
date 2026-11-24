package io.oz.sdoc.htmlsrv;

import java.io.IOException;
import java.io.OutputStream;

import io.odysz.anson.AnsonException;
import io.odysz.anson.IJsonable;
import io.odysz.anson.JSONAnsonListener;
import io.odysz.anson.JsonOpt;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantics.x.SemanticException;

/**
 * Ports of the tests, as html-service's (io.oz.srv.respaths.HtmlPort), which this module can't depend on:
 * {@link ResPathsTier}'s, and the local synode's it queries, io.oz.sdoc.tier.DoctreeTier.
 */
public enum HtmlTestPort implements IPort {
	respaths(ResPathsTier.url),
	doctree(io.oz.sdoc.tier.DoctreeTier.url),
	/** a port of a wrong url, rejected by {@link ResPathsTier}, as its or the synode's */
	wrong("paths.serv");

	static {
		JSONAnsonListener.registFactory(HtmlTestPort.class, (s) -> HtmlTestPort.valueOf(s));
	}

	private String url;
	HtmlTestPort(String v) { url = v; };
	public String url() { return url; }

	@Override
	public IPort valof(String pname) throws SemanticException {
		try { return Port.valueOf(pname); }
		catch (Exception e) {
			try { return valueOf(pname); }
			catch (IllegalArgumentException ex) { throw new SemanticException(ex.getMessage()); }
		}
	}

	@Override
	public IJsonable toBlock(OutputStream stream, JsonOpt... opts) throws AnsonException, IOException {
		stream.write('\"');
		stream.write(name().getBytes());
		stream.write('\"');
		return this;
	}

	@Override
	public IJsonable toJson(StringBuffer buf) throws IOException, AnsonException {
		buf.append('\"').append(name()).append('\"');
		return this;
	}
}
