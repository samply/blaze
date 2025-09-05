package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.math.BigDecimal;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface Decimals {

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(BigDecimal value, PrimitiveSink sink) {
        sink.putByte((byte) 4);
        sink.putString(value.toString(), UTF_8);
    }
}
