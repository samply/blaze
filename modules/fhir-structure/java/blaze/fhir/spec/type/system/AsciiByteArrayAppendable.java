package blaze.fhir.spec.type.system;

import java.io.IOException;

/**
 * An appendable that only allows 7-bit ASCII chars to append.
 * <p>
 * Useful in situations like date/time formatting where only 7-bit ASCII chars
 * are created.
 * <p>
 * The class is not thread safe and mutable. Please use with care.
 */
final class AsciiByteArrayAppendable implements Appendable {

    private final byte[] buffer;
    private int i = 0;

    AsciiByteArrayAppendable(int size) {
        buffer = new byte[size];
    }

    @Override
    public Appendable append(CharSequence csq) throws IOException {
        return append(csq, 0, csq.length());
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end) throws IOException {
        for (int i = start; i < end; i++) {
            append(csq.charAt(i));
        }
        return this;
    }

    @Override
    public Appendable append(char c) throws IOException {
        if (c <= 127) {
            buffer[i++] = (byte) c;
        } else {
            throw new IOException("Illegal char: " + c);
        }
        return this;
    }

    byte[] toByteArray() {
        return buffer;
    }

    int length() {
        return i;
    }
}
