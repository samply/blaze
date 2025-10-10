package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

public interface Longs {

    byte HASH_MARKER = 3;

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(long value, PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        sink.putLong(value);
    }
}
