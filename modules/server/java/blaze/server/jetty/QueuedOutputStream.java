package blaze.server.jetty;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;

public final class QueuedOutputStream extends OutputStream {

    public static final Object END = new Object();

    private final Queue<Object> queue;

    public QueuedOutputStream(Queue<Object> queue) {
        this.queue = Objects.requireNonNull(queue);
    }

    @Override
    public void write(int b) {
        queue.add(new byte[]{(byte) b});
    }

    @Override
    public void write(byte[] b, int off, int len) {
        queue.add(Arrays.copyOfRange(b, off, len));
    }

    @Override
    public void close() {
        queue.add(END);
    }
}
