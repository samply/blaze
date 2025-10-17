package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface Strings {

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(String s, PrimitiveSink sink) {
        sink.putByte((byte) 1);
        sink.putString(s, UTF_8);
    }
}
