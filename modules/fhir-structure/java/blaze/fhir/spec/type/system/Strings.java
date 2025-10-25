package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import static blaze.fhir.spec.type.Base.MEM_SIZE_OBJECT_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;

public interface Strings {

    int MEM_SIZE_OBJECT = 40;

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(String value, PrimitiveSink sink) {
        sink.putByte((byte) 1);
        sink.putString(value, UTF_8);
    }

    static int memSize(String value) {
        // TODO: only works for latin strings
        return value == null ? 0 : MEM_SIZE_OBJECT + ((value.length() + 3) & ~7);
    }
}
