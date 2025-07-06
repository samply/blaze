package blaze;

import com.google.common.io.BaseEncoding;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import static java.util.Objects.checkFromIndexSize;

public final class ByteString implements Comparable<ByteString> {

    public static final ByteString EMPTY = new ByteString(new byte[0]);

    private final byte[] bytes;

    private ByteString(byte[] bytes) {
        this.bytes = bytes;
    }

    public static ByteString copyFrom(ByteBuffer buffer) {
        return copyFrom(buffer, buffer.remaining());
    }

    public static ByteString copyFrom(ByteBuffer buffer, int size) {
        checkFromIndexSize(0, size, buffer.remaining());
        byte[] copy = new byte[size];
        buffer.get(copy);
        return new ByteString(copy);
    }

    public static ByteString copyFrom(byte[] bytes) {
        return new ByteString(bytes.clone());
    }

    public static ByteString copyFrom(String s, Charset charset) {
        return new ByteString(s.getBytes(charset));
    }

    public byte byteAt(int index) {
        return bytes[index];
    }

    public int size() {
        return bytes.length;
    }

    public ByteString substring(int start) {
        return substring(start, bytes.length);
    }

    public ByteString substring(int start, int end) {
        return new ByteString(Arrays.copyOfRange(bytes, start, end));
    }

    public ByteString concat(ByteString other) {
        byte[] copy = new byte[bytes.length + other.bytes.length];
        System.arraycopy(bytes, 0, copy, 0, bytes.length);
        System.arraycopy(other.bytes, 0, copy, bytes.length, other.bytes.length);
        return new ByteString(copy);
    }

    public byte[] toByteArray() {
        return bytes.clone();
    }

    public ByteBuffer asReadOnlyByteBuffer() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    public void copyTo(ByteBuffer target) {
        target.put(bytes);
    }

    @Override
    public int compareTo(ByteString other) {
        return Arrays.compareUnsigned(bytes, other.bytes);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ByteString &&
                Arrays.equals(bytes, ((ByteString) obj).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    public String toString(Charset charset) {
        return new String(bytes, charset);
    }

    @Override
    public String toString() {
        return "0x" + BaseEncoding.base16().encode(bytes);
    }
}
