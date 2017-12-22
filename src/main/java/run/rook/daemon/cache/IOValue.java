package run.rook.daemon.cache;

import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class IOValue {
	private String type;
	private MutableDirectBuffer value = new UnsafeBuffer(new byte[8]);
	private int length;
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public void setValue(DirectBuffer src, int len) {
		if(len > value.capacity()) {
			value = new UnsafeBuffer(new byte[len]);
		}
		value.putBytes(0, src, 0, len);
		length = len;
	}
	
	public MutableDirectBuffer getValue() {
		return value;
	}
	
	public int getLength() {
		return length;
	}
	
	@Override
	public String toString() {
		return "InputValue [type=" + type + ", value=" + Arrays.toString(Arrays.copyOf(value.byteArray(), length)) + "]";
	}
	
}
