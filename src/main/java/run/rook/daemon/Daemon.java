package run.rook.daemon;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import run.rook.daemon.cache.IOCache;
import run.rook.daemon.mqtt.IOMqttClient;
import run.rook.daemon.web.DaemonWebServer;

public class Daemon {

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("wp", "webPort", true, "Port to use for http [default: 8080]");
		options.addOption("mh", "mqttHost", true, "MQTT Host [default: localhost]");
		options.addOption("mp", "mqttPort", true, "MQTT Port [default: 1883]");
		options.addOption("mc", "mqttClientId", true, "MQTT Client ID [default: rook_daemon]");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);
		
		String webPortVal = cmd.getOptionValue("webPort");
		String mqttHostVal = cmd.getOptionValue("mqttHost");
		String mqttPortVal = cmd.getOptionValue("mqttPort");
		String mqttClientIdVal = cmd.getOptionValue("mqttClientId");
		
		int webPort = webPortVal == null ? 8080 : Integer.parseInt(webPortVal);
		String mqttHost = mqttHostVal == null ? "localhost" : mqttHostVal;
		int mqttPort = mqttPortVal == null ? 1883 : Integer.parseInt(mqttPortVal);
		String mqttClientId = mqttClientIdVal == null ? "rook_daemon" : mqttClientIdVal;
		
		String mqttUrl = "tcp://" + mqttHost + ":" + mqttPort;
		
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
