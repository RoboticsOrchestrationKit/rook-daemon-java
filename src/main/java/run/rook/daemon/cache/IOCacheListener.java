package run.rook.daemon.cache;

import org.agrona.DirectBuffer;

public interface IOCacheListener {
	void onInput(String name, String dataType, DirectBuffer value, int valueLength, Object source);
	void onOutput(String name, String dataType, DirectBuffer value, int valueLength, Object source);
}
