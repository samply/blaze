package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

public interface Integers {

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(int value, PrimitiveSink sink) {
        sink.putByte((byte) 2);
        sink.putInt(value);
    }
}
