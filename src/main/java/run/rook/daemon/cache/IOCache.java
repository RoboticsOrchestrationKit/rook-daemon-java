package run.rook.daemon.cache;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

public class IOCache {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Map<String, IOValue> inputs = new HashMap<>();
	private final Map<String, IOValue> outputs = new HashMap<>();
	private final Set<IOCacheListener> listeners = new LinkedHashSet<>();
	private final Disruptor<IOCacheEvent> disruptor = new Disruptor<>(IOCacheEvent::new, 1024,
			(Runnable r) -> new Thread(r, "IOCache"), ProducerType.MULTI, new BlockingWaitStrategy());
	private final RingBuffer<IOCacheEvent> ringBuffer;

	@SuppressWarnings("unchecked")
	public IOCache() {
		disruptor.handleEventsWith(this::handleEvent);
		ringBuffer = disruptor.getRingBuffer();
	}
	
	public void start() {
		disruptor.start();
	}
	
	public void processInput(String name, String dataType, byte[] value, int valueLength, Object source) {
		dispatchEvent(IOCacheEventType.INPUT, name, dataType, value, valueLength, source);
	}
	
	public void processOutput(String name, String dataType, byte[] value, int valueLength, Object source) {
		dispatchEvent(IOCacheEventType.OUTPUT, name, dataType, value, valueLength, source);
	}
	
	private void dispatchEvent(IOCacheEventType eventType, String name, String dataType, byte[] value, int valueLength, Object source) {
		long seq = ringBuffer.next();
		IOCacheEvent event = ringBuffer.get(seq);
		event.setEventType(eventType);
		event.setName(name);
		event.setDataType(dataType);
		event.setValue(value, valueLength);
		event.setSource(source);
		ringBuffer.publish(seq);
	}
	
	public void processInput(String name, String dataType, DirectBuffer value, int valueLength, Object source) {
		dispatchEvent(IOCacheEventType.INPUT, name, dataType, value, valueLength, source);
	}
	
	public void processOutput(String name, String dataType, DirectBuffer value, int valueLength, Object source) {
		dispatchEvent(IOCacheEventType.OUTPUT, name, dataType, value, valueLength, source);
	}
	
	private void dispatchEvent(IOCacheEventType eventType, String name, String dataType, DirectBuffer value, int valueLength, Object source) {
		long seq = ringBuffer.next();
		IOCacheEvent event = ringBuffer.get(seq);
		event.setEventType(eventType);
		event.setName(name);
		event.setDataType(dataType);
		event.setValue(value, valueLength);
		event.setSource(source);
		ringBuffer.publish(seq);
	}
	
	public void registerListener(IOCacheListener listener) {
		dispatchEvent(IOCacheEventType.REGISTER, listener);
	}

	public void deregisterListener(IOCacheListener listener) {
		dispatchEvent(IOCacheEventType.DEREGISTER, listener);
	}
	
	public void getInputs(IOCacheListener listener) {
		dispatchEvent(IOCacheEventType.GET_INPUTS, listener);
	}

	public void getOutputs(IOCacheListener listener) {
		dispatchEvent(IOCacheEventType.GET_OUTPUTS, listener);
	}
	
	private void dispatchEvent(IOCacheEventType eventType, IOCacheListener listener) {
		long seq = ringBuffer.next();
		IOCacheEvent event = ringBuffer.get(seq);
		event.setEventType(eventType);
		event.setListener(listener);
		ringBuffer.publish(seq);
	}
	
	private void handleEvent(IOCacheEvent event, long sequence, boolean endOfBatch) {
		switch(event.getEventType()) {
		case INPUT:
			handleInputEvent(event.getName(), event.getDataType(), event.getValue(), event.getValueLength(), event.getSource());
			break;
		case OUTPUT:
			handleOutputEvent(event.getName(), event.getDataType(), event.getValue(), event.getValueLength(), event.getSource());
			break;
		case REGISTER:
			handleRegisterEvent(event.getListener());
			break;
		case DEREGISTER:
			handleDeregisterEvent(event.getListener());
			break;
		case GET_INPUTS:
			handleGetInputsEvent(event.getListener());
			break;
		case GET_OUTPUTS:
			handleGetOutputsEvent(event.getListener());
			break;
		}
		event.reset();
	}
	
	private void handleInputEvent(String name, String dataType, DirectBuffer value, int valueLength, Object source) {
		updateInputValue(name, dataType, value, valueLength);
		dispatchInput(name, dataType, value, valueLength, source);
	}

	private void updateInputValue(String name, String dataType, DirectBuffer value, int valueLength) {
		IOValue val = inputs.get(name);
		if (val == null) {
			val = new IOValue();
			inputs.put(name, val);
		}
		val.setType(dataType);
		val.setValue(value, valueLength);
		if(logger.isDebugEnabled()) {
			logger.debug("Updated Input '" + name + "': " + val);
		}
	}

	private void dispatchInput(String name, String dataType, DirectBuffer value, int valueLength, Object source) {
		for (IOCacheListener listener : listeners) {
			try {
				listener.onInput(name, dataType, value, valueLength, source);
			} catch (Throwable t) {
				// protect the caller from a listener exception
				logger.error("Could not dispatch input to listener", t);
			}
		}
	}

	public void handleOutputEvent(String name, String dataType, DirectBuffer value, int valueLength, Object source) {
		updateOutputValue(name, dataType, value, valueLength);
		dispatchOutput(name, dataType, value, valueLength, source);
	}

	private void updateOutputValue(String name, String dataType, DirectBuffer value, int valueLength) {
		IOValue val = outputs.get(name);
		if (val == null) {
			val = new IOValue();
			outputs.put(name, val);
		}
		val.setType(dataType);
		val.setValue(value, valueLength);
		if(logger.isDebugEnabled()) {
			logger.debug("Updated Output '" + name + "': " + val);
		}
	}

	private void dispatchOutput(String name, String dataType, DirectBuffer value, int valueLength, Object source) {
		for (IOCacheListener listener : listeners) {
			try {
				listener.onOutput(name, dataType, value, valueLength, source);
			} catch (Throwable t) {
				// protect the caller from a listener exception
				logger.error("Could not dispatch output to listener", t);
			}
		}
	}

	public void handleGetInputsEvent(IOCacheListener listener) {
		for (Map.Entry<String, IOValue> e : inputs.entrySet()) {
			String name = e.getKey();
			IOValue val = e.getValue();
			listener.onInput(name, val.getType(), val.getValue(), val.getLength(), this);
		}
	}

	public void handleGetOutputsEvent(IOCacheListener listener) {
		for (Map.Entry<String, IOValue> e : outputs.entrySet()) {
			String name = e.getKey();
			IOValue val = e.getValue();
			listener.onOutput(name, val.getType(), val.getValue(), val.getLength(), this);
		}
	}

	public void handleRegisterEvent(IOCacheListener listener) {
		listeners.add(listener);
	}

	public void handleDeregisterEvent(IOCacheListener listener) {
		while (listeners.remove(listener)) {
			
		}
	}

	private static class IOCacheEvent {
		private final MutableDirectBuffer defaultValue = new UnsafeBuffer(new byte[128]);
		private IOCacheEventType eventType;
		private String name;
		private String dataType;
		private MutableDirectBuffer value = defaultValue;
		private int valueLength;
		private Object source;
		private IOCacheListener listener;
		
		public void setEventType(IOCacheEventType type) {
			this.eventType = type;
		}
		
		public IOCacheEventType getEventType() {
			return eventType;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
		
		public void setDataType(String dataType) {
			this.dataType = dataType;
		}
		
		public String getDataType() {
			return dataType;
		}
		
		public void setValue(byte[] value, int valueLength) {
			if(value == null || valueLength == 0) {
				this.valueLength = 0;
				return;
			}
			if(valueLength > defaultValue.capacity()) {
				this.value = new UnsafeBuffer(new byte[128]) ;
			}
			this.value.putBytes(0, value);
			this.valueLength = valueLength;
		}
		
		public MutableDirectBuffer getValue() {
			return value;
		}
		
		public int getValueLength() {
			return valueLength;
		}
		
		public void setValue(DirectBuffer value, int valueLength) {
			if(valueLength > defaultValue.capacity()) {
				this.value = new UnsafeBuffer(new byte[128]) ;
			}
			this.value.putBytes(0, value, 0, valueLength);
			this.valueLength = valueLength;
		}
		
		public void setSource(Object source) {
			this.source = source;
		}
		
		public Object getSource() {
			return source;
		}
		
		public IOCacheListener getListener() {
			return listener;
		}
		
		public void setListener(IOCacheListener listener) {
			this.listener = listener;
		}
		
		public void reset() {
			eventType = null;
			name = null;
			dataType = null;
			value = defaultValue;
			source = null;
			listener = null;
		}
	}
	
	private static enum IOCacheEventType {
		OUTPUT, INPUT, REGISTER, DEREGISTER, GET_INPUTS, GET_OUTPUTS
	}
}
