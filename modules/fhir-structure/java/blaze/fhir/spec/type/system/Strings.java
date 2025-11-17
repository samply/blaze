package blaze.fhir.spec.type.system;

import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface Strings {

    int MEM_SIZE_OBJECT = 40;
    int MEM_SIZE_SERIALIZED_STRING_OBJECT = 32;

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(String value, PrimitiveSink sink) {
        sink.putByte((byte) 1);
        sink.putString(value, UTF_8);
    }

    static int memSize(String value) {
        // TODO: only works for latin strings
        return value == null ? 0 : MEM_SIZE_OBJECT + ((value.length() + 3) & ~7);
    }

    static int memSize(SerializedString value) {
        return value == null ? 0 : MEM_SIZE_SERIALIZED_STRING_OBJECT + memSize(value.getValue());
    }
}
