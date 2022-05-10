package blaze.server.jetty;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;

public final class AsyncQueuedWriteListener implements WriteListener {

    private final AsyncContext context;
    private final ServletOutputStream out;
    private final BlockingQueue<Object> queue;

    public AsyncQueuedWriteListener(AsyncContext context, ServletOutputStream out, BlockingQueue<Object> queue) {
        this.context = Objects.requireNonNull(context);
        this.out = Objects.requireNonNull(out);
        this.queue = Objects.requireNonNull(queue);
    }

    @Override
    public void onWritePossible() throws IOException {
        while (out.isReady()) {
            try {
                Object data = queue.take();
                if (QueuedOutputStream.END == data) {
                    out.close();
                    context.complete();
                    return;
                } else {
                    out.write((byte[]) data);
                }
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
    }
}
