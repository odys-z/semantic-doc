package io.oz.semandoc;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.Utils.warn;

import javax.servlet.annotation.WebServlet;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.oz.semandoc.protocol.Samport;
import io.oz.semandoc.servs.JsampleCtrl;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.ServPort.PrintstreamProvider;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jserv.echo.Echo;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;

/**
 * The semantic-doc demo server (demo only, copied from semantic-jserv/jserv-sample's JSampleApp), an embedded Jetty server.
 * Built and packaged by semantic-doc/jserv/tasks.py.
 *
 * <p>Runs in a package folder the same way as a synode (jserv-album), with the provided db files:</p>
 * <pre>
 * bin/semantic-doc.jserv-[version].jar
 * WEB-INF/config.xml, connects.xml, settings.json, ...
 * volume/system.sqlite, ...
 * </pre>
 * Started with {@code java -jar bin/semantic-doc.jserv-[version].jar [settings.json]}, and stopped by a post
 * to {@link JsampleCtrl} from localhost.
 *
 * <p>TODO refactor: registerPorts(), registerServlets(), instanserver(), start(), allowCors() and stop()
 * are copies of jserv-album's io.oz.syntier.serv.SynotierJettyApp, to be shared in a common Jetty app class.</p>
 *
 * Copied from jserv-sample 1.5.9 (io.odysz.jsample.JSampleApp).
 */
public class SmandocServ {
	private static final String servpath = "smandoc";

	/**
	 * Configuration folder, relative to the working folder, the package folder.
	 * No -DWEB-INF override: to run in Eclipse, set the working folder to src/test/res.
	 */
	public static final String webinf = "WEB-INF";
	public static final String config_xml = "config.xml";
	public static final String settings_json = "settings.json";

	public static final String sample_name = "Semantic-doc Demo Serv";

	public static SmandocServ app;

	static public Sampleton sampleton() {
		return app.syngleton;
	}

	final Sampleton syngleton;

	Server server;

	ServletContextHandler schandler;
	public Sampleton syngleton() { return syngleton; }

	public static void main(String[] args) {
		SmandocServ app = _main(args);
		if (app.server != null)
			try {
				// returns after stop(), e.g. requested by JsampleCtrl
				app.server.join();
				Utils.logi("%s is stopped.", sample_name);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	}

	/**
	 * @param args [0] settings.json, default {@link #settings_json}
	 */
	public static SmandocServ _main(String[] args) {
		try {
			// Samport.valof() parses both AnsonMsg.Port and Samport names, e.g. "ctrl" for JsampleCtrl
			JProtocol.setup(servpath, Samport.ctrl);

			SampleSettings settings = SampleSettings.check(webinf, _0(args, settings_json));

			SmandocServ app = boot(webinf, settings)
					.afterboot(settings)
					.print("\n. . . . . . . . SmandocServ (Jetty) is running . . . . . . . ");

			return app;
		} catch (Exception e) {
			e.printStackTrace();

			warn("Fatal errors there. The process is stopped.");
			System.exit(-1);
			return null;
		}
	}

	SmandocServ afterboot(SampleSettings settings) {
		return this;
	}

	/**
	 * Boot with the provided db files, no installation.
	 * @param webinf
	 * @param settings
	 * @param oe
	 * @return the app
	 * @throws Exception
	 */
	public static SmandocServ boot(String webinf, SampleSettings settings,
			PrintstreamProvider ... oe) throws Exception {
		Configs.init(webinf);
		Connects.init(webinf);
		Sampleton.appName = ifnull(Configs.getCfg("app-name"), "Jserv Sample 1.5");

		return createSyndoctierApp(settings, webinf)
				.start(isNull(oe) ? () -> System.out : oe[0],
					  !isNull(oe) && oe.length > 1 ? oe[1] : () -> System.err)
				;
	}

	public SmandocServ(SampleSettings settings) throws Exception {
		syngleton = new Sampleton(settings);
	}

	/**
	 * Create an application instance working as the web service.
	 * @throws Exception
	 */
	public static SmandocServ createSyndoctierApp(SampleSettings settings,
			String webinf) throws Exception {

		app = SmandocServ.instanserver(settings);

		Utils.logi("------------ Starting %s ... --------------", sample_name);

		return registerPorts(app, settings.conn,
				AnSession.init(Connects.defltConn()), new AnQuery(), new AnUpdate(),
				new Echo(true),
				new HeartLink(),
				new JsampleCtrl())
			.allowCors(app.schandler)
			;
	}

    PrintstreamProvider printout;
	PrintstreamProvider printerr;

	public SmandocServ start(PrintstreamProvider out, PrintstreamProvider err) throws Exception {
		printout = out;
		printerr = err;

		ServPort.outstream(printout);
		ServPort.errstream(printout);

		server.start();

		return this;
	}

	/**
	 * Stop gracefully, the same as jserv-album's SynotierJettyApp.jvmStop(): stop Jetty,
	 * AnSession's scheduler (a non-daemon thread keeping the JVM alive) and the db connections.
	 * {@link #main(String[])} returns after this.
	 */
	public void stop() {
		if (server == null) {
			warn("Can't stop %s. Not started correctly.", sample_name);
			return;
		}
		try {
			Utils.logi("Stopping %s ...", sample_name);
			server.stop();

			AnSession.stopScheduled(5000);
			Connects.close();

			Thread.getAllStackTraces().keySet().forEach(thread -> {
				if (!thread.isDaemon() && thread != Thread.currentThread()
						&& !"DestroyJavaVM".equals(thread.getName())
						&& !"main".equals(thread.getName()))
					warn("Non-daemon thread still alive, the JVM may not end: %s", thread.getName());
			});
			Utils.logi("%s stopped.", sample_name);
		} catch (Exception e) {
			e.printStackTrace();
			warn("Failed to stop %s: %s", sample_name, e.getMessage());
		}
	}

	/**
	 * Start jserv with Jetty, register jserv-ports to Jetty.
	 *
	 * @param <T> subclass of {@link ServPort}
	 * @param synapp
	 * @param sysconn
	 * @param servports
	 * @return Jetty server, the {@link SmandocServ}
	 * @throws Exception
	 */
	@SafeVarargs
	static public <T extends ServPort<? extends AnsonBody>> SmandocServ registerPorts(
			SmandocServ synapp, String sysconn, T ... servports) throws Exception {

        synapp.schandler = new ServletContextHandler(synapp.server, f("/%s", servpath));
        for (T t : servports) {
        	synapp.registerServlets(synapp.schandler, t.trb(new DATranscxt(sysconn)));
        }

        return synapp;
	}

	<T extends ServPort<? extends AnsonBody>> SmandocServ registerServlets(
			ServletContextHandler context, T t) {
		WebServlet info = t.getClass().getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			context.addServlet(new ServletHolder(t), pattern);
		}

		return this;
	}

	static SmandocServ instanserver(SampleSettings settings) throws Exception {
	    SmandocServ synapp = new SmandocServ(settings);
		Sampleton.defltScxt = new DATranscxt(settings.conn);
		synapp.server = new Server();

		ServerConnector jconn = new ServerConnector(synapp.server);
		jconn.setHost("0.0.0.0");
		jconn.setPort(settings.port);
		synapp.server.addConnector(jconn);

	    return synapp;
	}

	/**
	 * See <a href='https://github.com/odys-z/semantic-jserv/blob/master/docsync.jserv/src/test/java/io/oz/jserv/docs/syn/singleton/SynotierJettyApp.java'>
	 * SynotierJettyApp</a>#allowCors() for an example of the implementation.
	 * @param context
	 * @return
	 */
	private SmandocServ allowCors(ServletContextHandler context) {
		return this;
	}

	public SmandocServ print(String... msg) {
		String qr = f("%s\n%s", sample_name, jserv());
		Utils.logi("%s\nSynode %s", _0(msg, ""), qr);
		return this;
	}

	public static String jserv() {
		return f("http://localhost:%s/%s", sampleton().settings.port, servpath);
	}
}
