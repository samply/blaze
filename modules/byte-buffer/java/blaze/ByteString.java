package blaze;

import com.google.common.io.BaseEncoding;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    /**
     * Builds a ByteString or a byte[] of a known fixed size by writing primitive
     * values and ByteStrings into a single backing array.
     * <p>
     * Internally uses VarHandle-based array writes (big-endian) so there is no
     * ByteBuffer envelope and no intermediate copy on either terminal.
     * <p>
     * Single-shot: after {@link #build()} or {@link #toBytes()} has been called,
     * the caller must drop the Builder reference. The handed-out array is
     * shared with this Builder; further writes via the Builder would mutate
     * the previously returned result.
     */
    public static final class Builder {

        private static final VarHandle SHORT_VH =
                MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
        private static final VarHandle INT_VH =
                MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
        private static final VarHandle LONG_VH =
                MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

        private final byte[] array;
        private int position;

        public Builder(int size) {
            this.array = new byte[size];
        }

        public Builder putByte(byte b) {
            array[position++] = b;
            return this;
        }

        public Builder putShort(short x) {
            SHORT_VH.set(array, position, x);
            position += 2;
            return this;
        }

        public Builder putInt(int x) {
            INT_VH.set(array, position, x);
            position += 4;
            return this;
        }

        public Builder putLong(long x) {
            LONG_VH.set(array, position, x);
            position += 8;
            return this;
        }

        public Builder putByteArray(byte[] src) {
            System.arraycopy(src, 0, array, position, src.length);
            position += src.length;
            return this;
        }

        public Builder putByteString(ByteString bs) {
            System.arraycopy(bs.bytes, 0, array, position, bs.bytes.length);
            position += bs.bytes.length;
            return this;
        }

        public Builder putNullTerminatedByteString(ByteString bs) {
            System.arraycopy(bs.bytes, 0, array, position, bs.bytes.length);
            position += bs.bytes.length;
            array[position++] = 0;
            return this;
        }

        public int position() {
            return position;
        }

        public int capacity() {
            return array.length;
        }

        /** Zero-copy: wraps the underlying array in a new ByteString. */
        public ByteString build() {
            check();
            return new ByteString(array);
        }

        /** Zero-copy: returns the underlying array. */
        public byte[] toBytes() {
            check();
            return array;
        }

        private void check() {
            if (position != array.length) {
                throw new IllegalStateException(
                        "position " + position + " != capacity " + array.length);
            }
        }
    }
}
