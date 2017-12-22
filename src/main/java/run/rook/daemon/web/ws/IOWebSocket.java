package run.rook.daemon.web.ws;

import static run.rook.daemon.web.ws.IOConst.*;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import run.rook.daemon.cache.IOCache;

@WebSocket
public class IOWebSocket {

	public static final String PROTOCOL = "rook_io";

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Gson gson = new Gson();
	private final Map<Session, IOSessionContext> sessionContexts = Collections.synchronizedMap(new HashMap<>());
	private final IOCache cache;

	public IOWebSocket(IOCache cache) {
		this.cache = cache;
	}

	@OnWebSocketConnect
	public void onWebSocketConnect(Session session) {
		logger.info("WebSocket Connect: " + session.getRemote().getInetSocketAddress());
		sessionContexts.put(session, new IOSessionContext(session, cache));
	}

	@OnWebSocketClose
	public void onWebSocketClose(Session session, int code, String reason) {
		logger.info("WebSocket Close: " + session.getRemote().getInetSocketAddress());
		IOSessionContext context = sessionContexts.remove(session);
		if (context != null) {
			context.close();
		}
	}

	@OnWebSocketMessage
	public void onText(Session session, String message) throws IOException {
		if (logger.isDebugEnabled()) {
			logger.debug(
					"Handling Request: remote=" + session.getRemote().getInetSocketAddress() + " message=" + message);
		}
		IOInboundMessage req = gson.fromJson(message, IOInboundMessage.class);
		if (req.type.equals(TYPE_INBOUND_INPUT_SUBSCRIBE)) {
			subscribeInputs(session, req.name);
		} else if (req.type.equals(TYPE_INBOUND_OUTPUT_SUBSCRIBE)) {
			subscribeOutputs(session, req.name);
		} else if (req.type.equals(TYPE_INBOUND_INPUT_UNSUBSCRIBE)) {
			unsubscribeInputs(session, req.name);
		} else if (req.type.equals(TYPE_INBOUND_OUTPUT_UNSUBSCRIBE)) {
			unsubscribeOutputs(session, req.name);
		} else if (req.type.equals(TYPE_INBOUND_INPUT_PUBLISH)) {
			publishInput(session, req.name, req.dataType, req.value);
		} else if (req.type.equals(TYPE_INBOUND_OUTPUT_PUBLISH)) {
			publishOutput(session, req.name, req.dataType, req.value);
		}
	}

	private void subscribeInputs(Session session, String name) {
		IOSessionContext context = sessionContexts.get(session);
		if (context == null) {
			logger.warn("Received message from unknown session: " + session.getRemote().getInetSocketAddress());
			return;
		}
		context.inputSubscribe(name);
	}

	private void unsubscribeInputs(Session session, String name) {
		IOSessionContext context = sessionContexts.get(session);
		if (context == null) {
			logger.warn("Received message from unknown session: " + session.getRemote().getInetSocketAddress());
			return;
		}
		context.inputUnsubscribe(name);
	}

	private void subscribeOutputs(Session session, String name) {
		IOSessionContext context = sessionContexts.get(session);
		if (context == null) {
			logger.warn("Received message from unknown session: " + session.getRemote().getInetSocketAddress());
			return;
		}
		context.outputSubscribe(name);
	}

	private void unsubscribeOutputs(Session session, String name) {
		IOSessionContext context = sessionContexts.get(session);
		if (context == null) {
			logger.warn("Received message from unknown session: " + session.getRemote().getInetSocketAddress());
			return;
		}
		context.outputUnsubscribe(name);
	}

	private void publishInput(Session session, String name, String dataType, String b64Value) {
		if (logger.isDebugEnabled()) {
			logger.debug("Publishing Input: session=" + session.getRemote().getInetSocketAddress() + " name=" + name
					+ " dataType=" + dataType + " value=" + b64Value);
		}
		byte[] value = Base64.getDecoder().decode(b64Value);
		cache.processInput(name, dataType, value, value.length, this);
	}

	private void publishOutput(Session session, String name, String dataType, String b64Value) {
		if (logger.isDebugEnabled()) {
			logger.debug("Publishing Output: session=" + session.getRemote().getInetSocketAddress() + " name=" + name
					+ " dataType=" + dataType + " value=" + b64Value);
		}
		byte[] value = Base64.getDecoder().decode(b64Value);
		cache.processOutput(name, dataType, value, value.length, this);
	}
}