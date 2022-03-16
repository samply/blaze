package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface SystemString {

    byte HASH_KEY = 1;

    static void hashInto(PrimitiveSink sink, String value) {
        sink.putByte(HASH_KEY);
        sink.putString(value, UTF_8);
    }

    static int memSize(String value) {
        return 40 + (value.length() + 7) / 8 * 8;
    }
}
