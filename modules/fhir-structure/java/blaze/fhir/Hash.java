package blaze.fhir;

import blaze.ByteString;

import java.nio.ByteBuffer;
import java.util.HexFormat;

public final class Hash {

    public static final int SIZE = 32;
    public static final int PREFIX_SIZE = Integer.BYTES;
    public static final Hash DELETED = new Hash(0, 0, 0, 0);

    private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private Hash(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    public static Hash fromHex(String s) {
        return new Hash(
                HexFormat.fromHexDigitsToLong(s, 0, 16),
                HexFormat.fromHexDigitsToLong(s, 16, 32),
                HexFormat.fromHexDigitsToLong(s, 32, 48),
                HexFormat.fromHexDigitsToLong(s, 48, 64));
    }

    public static Hash fromByteBuffer(ByteBuffer buffer) {
        return new Hash(buffer.getLong(), buffer.getLong(), buffer.getLong(), buffer.getLong());
    }

    public int prefix() {
        return (int) (l0 >>> 32);
    }

    public void copyTo(ByteBuffer target) {
        target.putLong(l0);
        target.putLong(l1);
        target.putLong(l2);
        target.putLong(l3);
    }

    public void copyTo(ByteString.Builder target) {
        target.putLong(l0);
        target.putLong(l1);
        target.putLong(l2);
        target.putLong(l3);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Hash that && l0 == that.l0 && l1 == that.l1 && l2 == that.l2 && l3 == that.l3;
    }

    @Override
    public int hashCode() {
        return (int) l0;
    }

    @Override
    public String toString() {
        return HEX_FORMAT.toHexDigits(l0) + HEX_FORMAT.toHexDigits(l1) +
                HEX_FORMAT.toHexDigits(l2) + HEX_FORMAT.toHexDigits(l3);
    }
}
