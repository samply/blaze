package blaze;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface ByteBufferUtil {

    /**
     * Reads the next {@code size} bytes of {@code buffer} as ISO-8859-1
     * encoded string and increments its position by {@code size}. The
     * {@code buffer} has to have an accessible backing array.
     */
    static String getIso88591String(ByteBuffer buffer, int size) {
        if (buffer.remaining() < size) {
            throw new BufferUnderflowException();
        }
        int position = buffer.position();
        var id = new String(buffer.array(), buffer.arrayOffset() + position, size, StandardCharsets.ISO_8859_1);
        buffer.position(position + size);
        return id;
    }

    /**
     * Puts the ISO-8859-1 encoded bytes of {@code s} into {@code array}
     * starting at {@code offset} without creating an intermediate byte array.
     *
     * @throws IllegalArgumentException if {@code s} contains chars outside
     *                                  of ISO-8859-1, in which case nothing
     *                                  is written
     */
    @SuppressWarnings("deprecation")
    static void putIso88591String(byte[] array, int offset, String s) {
        int length = s.length();
        for (int i = 0; i < length; i++) {
            if (s.charAt(i) > 0xFF) {
                throw new IllegalArgumentException("The string `%s` contains chars outside of ISO-8859-1.".formatted(s));
            }
        }
        s.getBytes(0, length, array, offset);
    }

    /**
     * Puts the ISO-8859-1 encoded bytes of {@code s} into {@code target}
     * without creating an intermediate byte array. The {@code target} has to
     * have an accessible backing array.
     *
     * @throws IllegalArgumentException if {@code s} contains chars outside
     *                                  of ISO-8859-1, in which case nothing
     *                                  is written and the position isn't
     *                                  changed
     * @throws BufferOverflowException  if {@code s} doesn't fit into the
     *                                  remaining bytes of {@code target}, in
     *                                  which case nothing is written and the
     *                                  position isn't changed
     */
    static ByteBuffer putIso88591String(ByteBuffer target, String s) {
        int length = s.length();
        int position = target.position();
        if (target.remaining() < length) {
            throw new BufferOverflowException();
        }
        putIso88591String(target.array(), target.arrayOffset() + position, s);
        target.position(position + length);
        return target;
    }
}
