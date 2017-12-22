package run.rook.daemon.web.ws;

import static run.rook.daemon.web.ws.IOConst.TYPE_OUTBOUND_INPUT;
import static run.rook.daemon.web.ws.IOConst.TYPE_OUTBOUND_OUTPUT;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.agrona.DirectBuffer;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import run.rook.daemon.cache.IOCache;
import run.rook.daemon.cache.IOCacheListener;

class IOSessionContext implements IOCacheListener {
	private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Gson gson = new Gson();
	private final Session session;
	private final IOCache cache;
	private final String address;
	
	private final Set<String> registeredInputs = new HashSet<>();
	private boolean registeredAllInputs = false;

	private final Set<String> registeredOutputs = new HashSet<>();
	private boolean registeredAllOutputs = false;

	private boolean registeredWithCache = false;

	public IOSessionContext(Session session, IOCache cache) {
		this.session = session;
		this.cache = cache;
		this.address = session.getRemote().getInetSocketAddress().toString();
		new Thread(this::pingLoop, address + " Ping Loop").start();
	}
	
	private void pingLoop() {
		try {
			while(session.isOpen()) {
				Thread.sleep(10000);
				session.getRemote().sendPing(EMPTY_BYTE_BUFFER);
			}
		} catch(Exception e) {
			if(logger.isDebugEnabled()) {
				logger.debug("Ping/Pong Exception", e);
			}
			close();
		}
	}
	
	public void close() {
		cache.deregisterListener(this);
		session.close();
	}

	public void inputSubscribe(String name) {
		if (name == null) {
			registeredAllInputs = true;
		} else {
			registeredInputs.add(name);
		}
		// first callback current values
		cache.getInputs(this);
		// second, register with cache
		checkRegisterWithCache();
	}

	public void outputSubscribe(String name) {
		if (name == null) {
			registeredAllOutputs = true;
		} else {
			registeredOutputs.add(name);
		}
		// first callback current values
		cache.getOutputs(this);
		// second, register with cache
		checkRegisterWithCache();
	}

	private void checkRegisterWithCache() {
		if (!registeredWithCache) {
			cache.registerListener(this);
			registeredWithCache = true;
		}
	}

	public void inputUnsubscribe(String name) {
		if (name == null) {
			registeredAllInputs = false;
		} else {
			registeredInputs.remove(name);
		}
		checkDeregisterWithCache();
	}

	public void outputUnsubscribe(String name) {
		if (name == null) {
			registeredAllOutputs = false;
		} else {
			registeredOutputs.remove(name);
		}
		checkDeregisterWithCache();
	}

	private void checkDeregisterWithCache() {
		if (registeredWithCache && !registeredAllInputs && !registeredAllOutputs
				&& registeredInputs.size() == 0 && registeredOutputs.size() == 0) {
			cache.deregisterListener(this);
			registeredWithCache = false;
		}
	}

	@Override
	public void onInput(String name, String dataType, DirectBuffer value, int valueLength, Object src) {
		if (registeredAllInputs || registeredInputs.contains(name)) {
			IOOutboundMessage m = new IOOutboundMessage();
			m.type = TYPE_OUTBOUND_INPUT;
			m.name = name;
			m.dataType = dataType;
			byte[] valueBytes = new byte[valueLength];
			value.getBytes(0, valueBytes);
			m.value = Base64.getEncoder().encodeToString(valueBytes);
			String json = gson.toJson(m);
			try {
				session.getRemote().sendString(json);
			} catch (Throwable t) {
				logger.info(toString() + " send failure. Closing Session.");
				close();
			}
		}
	}

	@Override
	public void onOutput(String name, String dataType, DirectBuffer value, int valueLength, Object src) {
		if (registeredAllOutputs || registeredOutputs.contains(name)) {
			IOOutboundMessage m = new IOOutboundMessage();
			m.type = TYPE_OUTBOUND_OUTPUT;
			m.name = name;
			m.dataType = dataType;
			byte[] valueBytes = new byte[valueLength];
			value.getBytes(0, valueBytes);
			m.value = Base64.getEncoder().encodeToString(valueBytes);
			String json = gson.toJson(m);
			try {
				session.getRemote().sendString(json);
			} catch (Throwable t) {
				logger.info(toString() + " send failure. Closing Session.");
				close();
			}
		}
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + " [address=" + address + "]";
	}
}