package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface Longs {

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(Long l, PrimitiveSink sink) {
        sink.putByte((byte) 3);
        sink.putLong(l);
    }
}
