package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

public interface Booleans {

    byte HASH_MARKER = 0;

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(boolean b, PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        sink.putBoolean(b);
    }
}
