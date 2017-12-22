package run.rook.daemon.web;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import run.rook.daemon.cache.IOCache;

public class DaemonWebServer {

	private final int port;
	private final IOCache cache;
	private Server server;

	public DaemonWebServer(int port, IOCache cache) {
		this.port = port;
		this.cache = cache;
	}

	public void start() throws Exception {
		WebSocketCreator wsCreator = new DaemonWebSocketCreator(cache);
		WebSocketHandler wsHandler = new WebSocketHandler() {
			@Override
			public void configure(WebSocketServletFactory factory) {
				factory.setCreator(wsCreator);
			}
		};
		
		ResourceHandler htmlHandler = new ResourceHandler();
		htmlHandler.setDirectoriesListed(false);
		htmlHandler.setWelcomeFiles(new String[] { "index.html" });
		htmlHandler.setResourceBase(getClass().getResource("/html").toExternalForm());
		ContextHandler htmlContext = new ContextHandler();
		htmlContext.setContextPath("/");
		htmlContext.setHandler(htmlHandler);
		
		HandlerList handlerList = new HandlerList();
	    handlerList.setHandlers(new Handler[] { wsHandler, htmlHandler, new DefaultHandler() });
	    
	    server = new Server(port);
	    server.setHandler(handlerList);
	    server.start();
	}

	public void stop() throws Exception {
		server.stop();
	}
}
