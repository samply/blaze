package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface Booleans {

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(boolean b, PrimitiveSink sink) {
        sink.putByte((byte) 0);
        sink.putBoolean(b);
    }
}
