package run.rook.daemon.web;

import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

import run.rook.daemon.cache.IOCache;
import run.rook.daemon.web.ws.IOWebSocket;

public class DaemonWebSocketCreator implements WebSocketCreator {

	private final IOWebSocket ioWebSocket;
	
	public DaemonWebSocketCreator(IOCache cache) {
		this.ioWebSocket = new IOWebSocket(cache);
	}
	
	@Override
	public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
		for (String protocol : req.getSubProtocols()) {
			switch (protocol) {
			case IOWebSocket.PROTOCOL:
				resp.setAcceptedSubProtocol(protocol);
				return ioWebSocket;
			}
		}
		return null;
	}

}