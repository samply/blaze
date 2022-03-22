package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.SystemDecimal;
import blaze.fhir.spec.type.system.SystemString;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Objects;
import java.util.StringJoiner;

@SuppressWarnings("UnstableApiUsage")
public final class Quantity implements ComplexType {

    public static final StdSerializer<Quantity> SERIALIZER = new Serializer();

    private static final Keyword TYPE = RT.keyword("fhir", "Quantity");
    private static final Symbol SYM_ID = Symbol.intern(null, "id");
    private static final Symbol SYM_EXTENSION = Symbol.intern(null, "extension");
    private static final Symbol SYM_VALUE = Symbol.intern(null, "value");
    private static final Symbol SYM_COMPARATOR = Symbol.intern(null, "comparator");
    private static final Symbol SYM_UNIT = Symbol.intern(null, "unit");
    private static final Symbol SYM_SYSTEM = Symbol.intern(null, "system");
    private static final Symbol SYM_CODE = Symbol.intern(null, "code");
    private static final Keyword KW_ID = Keyword.intern(SYM_ID);
    private static final Keyword KW_EXTENSION = Keyword.intern(SYM_EXTENSION);
    private static final Keyword KW_VALUE = Keyword.intern(SYM_VALUE);
    private static final Keyword KW_COMPARATOR = Keyword.intern(SYM_COMPARATOR);
    private static final Keyword KW_UNIT = Keyword.intern(SYM_UNIT);
    private static final Keyword KW_SYSTEM = Keyword.intern(SYM_SYSTEM);
    private static final Keyword KW_CODE = Keyword.intern(SYM_CODE);
    private static final byte HASH_KEY = 40;
    private static final byte HASH_KEY_ID = 0;
    private static final byte HASH_KEY_EXTENSION = 1;
    private static final byte HASH_KEY_VALUE = 2;
    private static final byte HASH_KEY_COMPARATOR = 3;
    private static final byte HASH_KEY_UNIT = 4;
    private static final byte HASH_KEY_SYSTEM = 5;
    private static final byte HASH_KEY_CODE = 6;
    private static final PersistentVector FIELDS = (PersistentVector) RT.vector(KW_ID, KW_EXTENSION, KW_VALUE, KW_COMPARATOR, KW_UNIT, KW_SYSTEM, KW_CODE);
    private static final IPersistentVector BASIS = RT.vector(SYM_ID, SYM_EXTENSION, SYM_VALUE, SYM_COMPARATOR, SYM_UNIT, SYM_SYSTEM, SYM_CODE);

    public final String id;
    public final IPersistentVector extension;
    public final Object value;
    public final Code comparator;
    public final Object unit;
    public final Uri system;
    public final Code code;

    public Quantity(String id, IPersistentVector extension, Object value, Code comparator, Object unit, Uri system, Code code) {
        this.id = id;
        this.extension = extension;
        this.value = value;
        this.comparator = comparator;
        this.unit = unit;
        this.system = system;
        this.code = code;
    }

    public static IPersistentVector getBasis() {
        return BASIS;
    }

    public static Quantity create(IPersistentMap m) {
        return new Quantity(
                (String) m.valAt(KW_ID),
                (IPersistentVector) m.valAt(KW_EXTENSION),
                m.valAt(KW_VALUE),
                (Code) m.valAt(KW_COMPARATOR),
                m.valAt(KW_UNIT),
                (Uri) m.valAt(KW_SYSTEM),
                (Code) m.valAt(KW_CODE));
    }

    @Override
    public Keyword fhirType() {
        return TYPE;
    }

    public String id() {
        return id;
    }

    public IPersistentVector extension() {
        return extension;
    }

    public Object value() {
        return value;
    }

    public Code comparator() {
        return comparator;
    }
    
    public Object unit() {
        return unit;
    }
    
    public Uri system() {
        return system;
    }

    public Code code() {
        return code;
    }
    
    @Override
    @SuppressWarnings("DuplicatedCode")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_KEY);
        if (id != null) {
            sink.putByte(HASH_KEY_ID);
            SystemString.hashInto(sink, id);
        }
        if (extension != null) {
            sink.putByte(HASH_KEY_EXTENSION);
            Extension.extensionHashInto(extension, sink);
        }
        if (value != null) {
            sink.putByte(HASH_KEY_VALUE);
            Decimal.hashInto(sink, value);
        }
        if (comparator != null) {
            sink.putByte(HASH_KEY_COMPARATOR);
            comparator.hashInto(sink);
        }
        if (unit != null) {
            sink.putByte(HASH_KEY_UNIT);
            FhirString.hashInto(sink, unit);
        }
        if (system != null) {
            sink.putByte(HASH_KEY_SYSTEM);
            system.hashInto(sink);
        }
        if (code != null) {
            sink.putByte(HASH_KEY_CODE);
            code.hashInto(sink);
        }
    }

    @Override
    public PersistentVector references() {
        return extension == null
                ? PersistentVector.EMPTY
                : FhirType.appendExtensionReferences((PersistentVector) extension, PersistentVector.EMPTY);
    }


    @Override
    public int memSize() {
        int s = 40;
        if (id != null) s += SystemString.memSize(id);
        if (extension != null) s += Extension.vectorMemSize(extension);
        if (value != null) s += Decimal.memSize(value);
        return s;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public Object valAt(Object key, Object notFound) {
        if (KW_VALUE == key) {
            return value;
        }
        if (KW_CODE == key) {
            return code;
        }
        if (KW_SYSTEM == key) {
            return system;
        }
        if (KW_UNIT == key) {
            return unit;
        }
        if (KW_EXTENSION == key) {
            return extension;
        }
        if (KW_COMPARATOR == key) {
            return comparator;
        }
        if (KW_ID == key) {
            return id;
        }
        return notFound;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Iterator<MapEntry> iterator() {
        return new TypeIterator<>(FIELDS, this);
    }

    @Override
    public Object kvreduce(IFn f, Object init) {
        return FIELDS.reduce(new KvReduceFn(f, this), init);
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public int count() {
        int c = 0;
        if (id != null) c++;
        if (extension != null) c++;
        if (value != null) c++;
        if (comparator != null) c++;
        if (unit != null) c++;
        if (system != null) c++;
        if (code != null) c++;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quantity that = (Quantity) o;
        return Objects.equals(id, that.id) && Objects.equals(extension, that.extension) &&
                Objects.equals(value, that.value) && Objects.equals(comparator, that.comparator) &&
                Objects.equals(unit, that.unit) && Objects.equals(system, that.system) &&
                Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value, comparator, unit, system, code);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Quantity.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("extension=" + extension)
                .add("value='" + value + "'")
                .add("comparator=" + comparator)
                .add("unit=" + unit)
                .add("system=" + system)
                .add("code=" + code)
                .toString();
    }

    private static final class Serializer extends StdSerializer<Quantity> {

        private Serializer() {
            super(Quantity.class);
        }

        @Override
        public void serialize(Quantity quantity, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject(quantity);
            if (quantity.id != null) {
                gen.writeStringField("id", quantity.id);
            }
            if (quantity.extension != null) {
                Extension.serializeVector(quantity.extension, gen, provider);
            }
            if (quantity.value != null) {
                Decimal.serialize("value", quantity.value, gen, provider);
            }
            if (quantity.comparator != null) {
                gen.writeFieldName("comparator");
                Code.SERIALIZER.serialize(quantity.comparator, gen, provider);
            }
            if (quantity.unit != null) {
                FhirString.serialize("unit", quantity.unit, gen, provider);
            }
            if (quantity.system != null) {
                gen.writeFieldName("system");
                Uri.SERIALIZER.serialize(quantity.system, gen, provider);
            }
            if (quantity.code != null) {
                gen.writeFieldName("code");
                Code.SERIALIZER.serialize(quantity.code, gen, provider);
            }
            gen.writeEndObject();
        }
    }
}
