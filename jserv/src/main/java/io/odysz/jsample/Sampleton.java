package io.odysz.jsample;

import java.io.IOException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;

/**
 * @since 1.0.0
 */
@WebListener
public class Sampleton extends JSingleton implements ServletContextListener {

	/**
	 * @since 1.5.3
	 */
	public final SampleSettings settings;

	public Sampleton(SampleSettings settings) {
		this.settings = settings;
	}

	/**
	 * @deprecated since 1.5.4, JSample is not maintained for working in Servlet containers. 
	 */
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			super.onInitialized(sce);

			JProtocol.setup("jserv-sample", Samport.menu);

		} catch (IOException e) {
			e.printStackTrace();
			Utils.warn("%s: %s", e.getClass().getName(), e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
