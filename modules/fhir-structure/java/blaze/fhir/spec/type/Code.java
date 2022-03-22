package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.SystemString;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public final class Code implements PrimitiveType<String> {

    public static final StdSerializer<Code> SERIALIZER = new StdSerializer<>(Code.class) {

        @Override
        public void serialize(Code code, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(code.value);
        }
    };

    private static final Keyword TYPE = RT.keyword("fhir", "code");
    private static final Symbol SYM_VALUE = Symbol.intern(null, "value");
    private static final Keyword KW_VALUE = Keyword.intern(SYM_VALUE);
    private static final Var VAR_ELEMENT = RT.var("clojure.data.xml.node", "element");
    private static final byte HASH_KEY = 13;
    private static final byte HASH_KEY_VALUE = 2;
    private static final IPersistentVector BASIS = RT.vector(SYM_VALUE);

    public final String value;

    public Code(String value) {
        this.value = Objects.requireNonNull(value, "Code.value can't be null.");
    }

    public static IPersistentVector getBasis() {
        return BASIS;
    }

    @Override
    public Keyword fhirType() {
        return TYPE;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public Object toXml() {
        return ((IFn) VAR_ELEMENT.getRawRoot()).invoke(null, RT.mapUniqueKeys(KW_VALUE, value));
    }

    @Override
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_KEY);
        sink.putByte(HASH_KEY_VALUE);
        SystemString.hashInto(sink, value);
    }

    @Override
    public PersistentVector references() {
        return null;
    }

    @Override
    public int memSize() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Code id = (Code) o;
        return value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return 31 + value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
