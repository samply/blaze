package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

public interface Integers {

    byte HASH_MARKER = 2;

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(int value, PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        sink.putInt(value);
    }
}
