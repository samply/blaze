package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

public interface Longs {

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(long value, PrimitiveSink sink) {
        sink.putByte((byte) 3);
        sink.putLong(value);
    }
}
