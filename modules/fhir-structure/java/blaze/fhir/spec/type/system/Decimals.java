package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.math.BigDecimal;

import static blaze.fhir.spec.type.Base.MEM_SIZE_OBJECT_HEADER;
import static blaze.fhir.spec.type.Base.MEM_SIZE_REFERENCE;
import static java.nio.charset.StandardCharsets.UTF_8;

public interface Decimals {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - BigInteger reference
     * 4 byte - scale int
     * 4 byte - precision int
     * 4 or 8 byte - String reference
     * 8 byte - intCompact int
     */
    int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 2 * MEM_SIZE_REFERENCE + 4 + 4 + 8 + 7) & ~7;

    byte HASH_MARKER = 4;

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(BigDecimal value, PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        sink.putString(value.toString(), UTF_8);
    }

    static int memSize(BigDecimal value) {
        // TODO: works up to long representation
        return value == null ? 0 : MEM_SIZE_OBJECT;
    }
}
