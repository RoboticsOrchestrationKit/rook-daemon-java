package run.rook.daemon.mqtt;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import run.rook.daemon.cache.IOCache;
import run.rook.daemon.cache.IOCacheListener;

public class IOMqttClient implements Runnable {

	private static final long RECONNECT_TIMEOUT = 500;
	private static final String TOPIC_INPUT_PREFIX = "rook/io/i/";
	private static final String TOPIC_OUTPUT_PREFIX = "rook/io/o/";
	private static final String TOPIC_SEPARATOR = "/";
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Pattern topicPatterm = Pattern.compile("rook/io/([io])/(.+)/(.+)");
	private final AtomicReference<MqttClient> clientRef = new AtomicReference<>(null);
	private final String mqttUrl;
	private final String mqttClientId;
	private final IOCache cache;
	
	public IOMqttClient(String mqttUrl, String mqttClientId, IOCache cache) {
		this.mqttUrl = mqttUrl;
		this.mqttClientId = mqttClientId;
		this.cache = cache;
	}
	
	private final IOCacheListener ioCacheListener = new IOCacheListener() {

		@Override
		public void onInput(String name, String dataType, DirectBuffer value, int valueLength, Object src) {
			// check source to avoid infinite send/receive loop over MQTT
			if(src != IOMqttClient.this && src != cache) {
				trySend(TOPIC_INPUT_PREFIX + name + TOPIC_SEPARATOR + dataType, value, valueLength);
			}
		}
	
		@Override
		public void onOutput(String name, String dataType, DirectBuffer value, int valueLength, Object src) {
			// check source to avoid infinite send/receive loop over MQTT
			if(src != IOMqttClient.this && src != cache) {
				trySend(TOPIC_OUTPUT_PREFIX + name + TOPIC_SEPARATOR + dataType, value, valueLength);
			}
		}
	
	};
	
	public void trySend(String topic, DirectBuffer value, int valueLength) {
		MqttClient client = clientRef.get();
		if(client != null) {
			byte[] payload = new byte[valueLength];
			value.getBytes(0, payload);
			if(logger.isDebugEnabled()) {
				logger.debug("Sending: topic=" + topic + " payload=" + Base64.getEncoder().encodeToString(payload));
			}
			MqttMessage message = new MqttMessage();
			message.setQos(0);
			message.setPayload(payload);
			try {
				client.publish(topic, message);
			} catch (Exception e) {
				logger.error("Could not send MQTT Message", e);
			}
		} else if(logger.isDebugEnabled()) {
			logger.debug("Not connected to MQTT Broker. Will not send message.");
		}
	}

	@Override
	public void run() {
		cache.registerListener(ioCacheListener);
		
		final Object reconnectNotifier = new Object();
		MqttClient client = null;
		boolean inErrorState = false;
		try {
			logger.info("Connecting...");
			while (true) {
				try {
					client = new MqttClient(mqttUrl, mqttClientId);
					client.connect();
					client.setCallback(new MqttCallback() {
						@Override
						public void messageArrived(String topic, MqttMessage message) throws Exception {
							Matcher m = topicPatterm.matcher(topic);
							if (m.matches()) {
								switch (m.group(1)) {
								case "i":
									cache.processInput(m.group(2), m.group(3), message.getPayload(), message.getPayload().length, IOMqttClient.this);
									break;
								case "o":
									cache.processOutput(m.group(2), m.group(3), message.getPayload(), message.getPayload().length, IOMqttClient.this);
									break;
								}
							}
						}

						@Override
						public void deliveryComplete(IMqttDeliveryToken token) {
							// QoS=0
						}

						@Override
						public void connectionLost(Throwable t) {
							logger.error("Lost MQTT Connection", t);
							synchronized (reconnectNotifier) {
								reconnectNotifier.notifyAll();
							}
						}
					});
					client.subscribe("rook/io/i/+/+");
					client.subscribe("rook/io/o/+/+");
					clientRef.set(client);
					
					// successfully connected (no error thrown to this point)
					logger.info("Connected");
					inErrorState = false;

					// wait for reconnect notification
					synchronized (reconnectNotifier) {
						while (client.isConnected()) {
							reconnectNotifier.wait();
						}
					}
					
					logger.info("Reconnecting...");
					clientRef.set(null);
				} catch (MqttException e) {
					// only log error once until a proper connection can be
					// established
					if (!inErrorState) {
						logger.error("Could not connect to MQTT URL '" + mqttUrl + "' - Will continuously retry every "
								+ RECONNECT_TIMEOUT + " milliseconds...", e);
						inErrorState = true;
					}
					// throttle reconnecting
					Thread.sleep(RECONNECT_TIMEOUT);
				}
			}
		} catch (InterruptedException e) {
			logger.error("Interrupted. Exiting...");
		}

		// attempt to close client when exiting thread
		if (client != null) {
			try {
				client.close();
			} catch (MqttException e) {

			}
		}
	}
}
