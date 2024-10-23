package blaze.server;

import jakarta.servlet.AsyncContext;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ContextOutputStream extends FilterOutputStream {

    private final AsyncContext context;

    public ContextOutputStream(OutputStream out, AsyncContext context) {
        super(out);
        this.context = context;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        context.complete();
    }
}
