package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.SystemString;
import clojure.lang.*;
import com.google.common.hash.PrimitiveSink;

import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public final class Id implements PrimitiveType<String> {

    private static final Keyword TYPE = RT.keyword("fhir", "id");
    private static final Symbol SYM_VALUE = Symbol.intern(null, "value");
    private static final Keyword KW_VALUE = Keyword.intern(SYM_VALUE);
    private static final Var VAR_ELEMENT = RT.var("clojure.data.xml.node", "element");
    private static final byte HASH_KEY = 15;
    private static final byte HASH_KEY_VALUE = 2;
    private static final IPersistentVector BASIS = RT.vector(SYM_VALUE);

    public final String value;

    public Id(String value) {
        this.value = Objects.requireNonNull(value, "Id.url can't be null.");
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
        return 16 + SystemString.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Id id = (Id) o;
        return value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
