package blaze.fhir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public final class XmlUtf8Writer extends Writer {

    private final OutputStream out;
    private final byte[] buffer;
    private int pos;

    public XmlUtf8Writer(OutputStream out, int bufferSize) {
        this.out = out;
        this.buffer = new byte[bufferSize];
    }

    private void ensureOpen() throws IOException {
        if (out == null) {
            throw new IOException("Stream closed");
        }
    }

    private void ensureCapacity(int n) throws IOException {
        if (buffer.length - pos < n) {
            flushBuffer();
        }
    }

    private void flushBuffer() throws IOException {
        if (pos > 0) {
            out.write(buffer, 0, pos);
            pos = 0;
        }
    }

    private void writeByte(int b) throws IOException {
        ensureCapacity(1);
        buffer[pos++] = (byte) b;
    }

    private void writeCodePoint(int codePoint) throws IOException {
        if (codePoint < 0x80) {
            writeByte(codePoint);
        } else if (codePoint < 0x800) {
            ensureCapacity(2);
            buffer[pos++] = (byte) (0xC0 | (codePoint >> 6));
            buffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        } else if (codePoint < 0x10000) {
            ensureCapacity(3);
            buffer[pos++] = (byte) (0xE0 | (codePoint >> 12));
            buffer[pos++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
            buffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        } else {
            ensureCapacity(4);
            buffer[pos++] = (byte) (0xF0 | (codePoint >> 18));
            buffer[pos++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
            buffer[pos++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
            buffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        }
    }

    private void writeAscii(String str, int off, int end) throws IOException {
        while (off < end) {
            ensureCapacity(1);
            int n = Math.min(buffer.length - pos, end - off);
            for (int i = 0; i < n; i++) {
                buffer[pos + i] = (byte) str.charAt(off + i);
            }
            pos += n;
            off += n;
        }
    }

    private void writeAscii(char[] cbuf, int off, int end) throws IOException {
        while (off < end) {
            ensureCapacity(1);
            int n = Math.min(buffer.length - pos, end - off);
            for (int i = 0; i < n; i++) {
                buffer[pos + i] = (byte) cbuf[off + i];
            }
            pos += n;
            off += n;
        }
    }

    private static boolean validXmlChar(char c) {
        return c == 0x09 || c == 0x0A || c == 0x0D ||
                (0x20 <= c && c <= 0xD7FF) ||
                (0xE000 <= c && c <= 0xFFFD);
    }

    public void writeEscaped(String str) throws IOException {
        ensureOpen();
        int len = str.length();
        int start = 0;
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '&' -> {
                    if (start < i) writeAscii(str, start, i);
                    writeAscii("&amp;", 0, 5);
                    start = i + 1;
                }
                case '<' -> {
                    if (start < i) writeAscii(str, start, i);
                    writeAscii("&lt;", 0, 4);
                    start = i + 1;
                }
                case '>' -> {
                    if (start < i) writeAscii(str, start, i);
                    writeAscii("&gt;", 0, 4);
                    start = i + 1;
                }
                case '"' -> {
                    if (start < i) writeAscii(str, start, i);
                    writeAscii("&quot;", 0, 6);
                    start = i + 1;
                }
                default -> {
                    if (c >= 0x80) {
                        if (start < i) writeAscii(str, start, i);
                        if (Character.isHighSurrogate(c)) {
                            int j = i + 1;
                            if (j < len && Character.isLowSurrogate(str.charAt(j))) {
                                writeCodePoint(Character.toCodePoint(c, str.charAt(j)));
                                i = j;
                                start = j + 1;
                            } else {
                                writeByte('?');
                                start = j;
                            }
                        } else if (Character.isLowSurrogate(c) || !validXmlChar(c)) {
                            writeByte('?');
                            start = i + 1;
                        } else {
                            writeCodePoint(c);
                            start = i + 1;
                        }
                    } else if (!validXmlChar(c)) {
                        if (start < i) writeAscii(str, start, i);
                        writeByte('?');
                        start = i + 1;
                    }
                }
            }
        }
        if (start < len) writeAscii(str, start, len);
    }

    @Override
    public void write(int c) throws IOException {
        ensureOpen();
        writeCodePoint(Character.isSurrogate((char) c) ? '?' : c);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        ensureOpen();
        int end = off + len;
        for (int i = off; i < end; i++) {
            char c = cbuf[i];
            if (c < 0x80) {
                int start = i++;
                while (i < end && cbuf[i] < 0x80) {
                    i++;
                }
                writeAscii(cbuf, start, i);
                i--;
            } else if (Character.isHighSurrogate(c)) {
                int j = i + 1;
                if (j < end && Character.isLowSurrogate(cbuf[j])) {
                    writeCodePoint(Character.toCodePoint(c, cbuf[j]));
                    i = j;
                } else {
                    writeByte('?');
                }
            } else if (Character.isLowSurrogate(c)) {
                writeByte('?');
            } else {
                writeCodePoint(c);
            }
        }
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        ensureOpen();
        int end = off + len;
        for (int i = off; i < end; i++) {
            char c = str.charAt(i);
            if (c < 0x80) {
                int start = i++;
                while (i < end && str.charAt(i) < 0x80) {
                    i++;
                }
                writeAscii(str, start, i);
                i--;
            } else if (Character.isHighSurrogate(c)) {
                int j = i + 1;
                if (j < end && Character.isLowSurrogate(str.charAt(j))) {
                    writeCodePoint(Character.toCodePoint(c, str.charAt(j)));
                    i = j;
                } else {
                    writeByte('?');
                }
            } else if (Character.isLowSurrogate(c)) {
                writeByte('?');
            } else {
                writeCodePoint(c);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        flushBuffer();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        flush();
        out.close();
    }
}
