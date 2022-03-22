package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.math.BigDecimal;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface SystemDecimal {

    byte HASH_KEY = 4;

    static void hashInto(PrimitiveSink sink, BigDecimal value) {
        sink.putByte(HASH_KEY);
        sink.putString(value.toString(), UTF_8);
    }

    static int memSize(BigDecimal value) {
        return 40;
    }
}
