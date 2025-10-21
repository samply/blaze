package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.math.BigDecimal;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface Decimals {

    int MEM_SIZE_OBJECT = 32;

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(BigDecimal value, PrimitiveSink sink) {
        sink.putByte((byte) 4);
        sink.putString(value.toString(), UTF_8);
    }

    static int memSize(BigDecimal value) {
        // TODO: works up to long representation
        return value == null ? 0 : MEM_SIZE_OBJECT;
    }
}
