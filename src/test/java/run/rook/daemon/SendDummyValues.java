package run.rook.daemon;

import java.util.Random;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

public class SendDummyValues {

	public static void main(String[] args) throws Exception {
		MqttClient client = new MqttClient("tcp://localhost:1883", "test1");
		client.connect();
		
		while(true) {
			send("Distance", client);
			send("Value", client);
			send("Some Other Sensor", client);
			Thread.sleep(1000);
		}
	}

	private static final Random RAND = new Random();
	private static void send(String name, MqttClient client) throws MqttPersistenceException, MqttException {
		int v = RAND.nextInt(1024);
		MqttMessage message = new MqttMessage();
		message.setPayload(new byte[] { (byte)(v & 0xFF), (byte)((v >>> 8) & 0xFF) });
		message.setQos(0);
		client.publish("rook/io/i/" + name + "/I16", message);
	}
}
