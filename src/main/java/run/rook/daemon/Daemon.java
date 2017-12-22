package run.rook.daemon;

import run.rook.daemon.cache.IOCache;
import run.rook.daemon.mqtt.IOMqttClient;
import run.rook.daemon.web.DaemonWebServer;

public class Daemon {

	public static void main(String[] args) throws Exception {
		int webPort = 8080;
		String mqttHost = "localhost";
		int mqttPort = 1883;
		String mqttUrl = "tcp://" + mqttHost + ":" + mqttPort;
		String mqttClientId = "rook_daemon";
		new Daemon(webPort, mqttUrl, mqttClientId).start();
	}

	private final int webPort;
	private final String mqttUrl;
	private final String mqttClientId;

	public Daemon(int webPort, String mqttUrl, String mqttClientId) {
		this.webPort = webPort;
		this.mqttUrl = mqttUrl;
		this.mqttClientId = mqttClientId;
	}

	public void start() throws Exception {
		IOCache cache = new IOCache();
		cache.start();
		new DaemonWebServer(webPort, cache).start();
		new Thread(new IOMqttClient(mqttUrl, mqttClientId, cache), "IOMqttClient").start();
	}
	
}
