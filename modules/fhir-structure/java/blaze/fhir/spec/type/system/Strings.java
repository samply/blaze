package blaze.fhir.spec.type.system;

import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import static blaze.fhir.spec.type.Base.MEM_SIZE_OBJECT_HEADER;
import static blaze.fhir.spec.type.Base.MEM_SIZE_REFERENCE;
import static java.nio.charset.StandardCharsets.UTF_8;

public interface Strings {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - byte array reference
     * 4 byte - hash
     * 1 byte - coder
     * 1 byte - hashIsZero
     */
    int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + MEM_SIZE_REFERENCE + 4 + 1 + 1 + 7) & ~7;

    int MEM_SIZE_SERIALIZED_STRING_OBJECT = 32;
    byte HASH_MARKER = 1;

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(String value, PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        sink.putString(value, UTF_8);
    }

    static int memSize(String value) {
        if (value == null) {
            return 0;
        }
        int length = value.length();
        int byteLength = length;
        for (int i = 0; i < length; i++) {
            if (value.charAt(i) > 0xFF) {
                byteLength = length << 1;
                break;
            }
        }
        return MEM_SIZE_OBJECT + (MEM_SIZE_OBJECT_HEADER + 4 + byteLength + 7) & ~7;
    }

    static int memSize(SerializedString value) {
        return value == null ? 0 : MEM_SIZE_SERIALIZED_STRING_OBJECT + memSize(value.getValue());
    }
}
