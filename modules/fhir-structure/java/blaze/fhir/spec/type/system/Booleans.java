package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

public interface Booleans {

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(boolean b, PrimitiveSink sink) {
        sink.putByte((byte) 0);
        sink.putBoolean(b);
    }
}
