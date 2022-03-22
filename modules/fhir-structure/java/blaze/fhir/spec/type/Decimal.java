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

@SuppressWarnings("UnstableApiUsage")
public class Decimal implements PrimitiveType<BigDecimal> {

    public static final StdSerializer<Decimal> SERIALIZER = new StdSerializer<>(Decimal.class) {

        @Override
        public void serialize(Decimal decimal, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject(decimal);
            if (decimal.id != null) {
                gen.writeStringField("id", decimal.id);
            }
            Extension.serializeVector(decimal.extension, gen, provider);
            if (decimal.value != null) {
                gen.writeNumberField("value", decimal.value);
            }
            gen.writeEndObject();
        }
    };

    private static final Symbol SYM_ID = Symbol.intern(null, "id");
    private static final Symbol SYM_EXTENSION = Symbol.intern(null, "extension");
    private static final Symbol SYM_VALUE = Symbol.intern(null, "value");
    private static final Keyword KW_VALUE = Keyword.intern(SYM_VALUE);
    private static final byte HASH_KEY = 4;
    private static final byte HASH_KEY_ID = 0;
    private static final byte HASH_KEY_EXTENSION = 1;
    private static final byte HASH_KEY_VALUE = 2;
    private static final Var VAR_HASH_INTO = RT.var("blaze.fhir.spec.type.protocols", "-hash-into");
    private static final Var VAR_ELEMENT = RT.var("clojure.data.xml.node", "element");
    private static final IPersistentVector BASIS = RT.vector(SYM_ID, SYM_EXTENSION, SYM_VALUE);

    public final String id;
    public final IPersistentVector extension;
    public final BigDecimal value;

    public Decimal(String id, IPersistentVector extension, BigDecimal value) {
        this.id = id;
        this.extension = extension;
        this.value = value;
    }

    public static IPersistentVector getBasis() {
        return BASIS;
    }

    @Override
    public Keyword fhirType() {
        return null;
    }

    static BigDecimal value(Object decimal) {
        return decimal instanceof BigDecimal ? ((BigDecimal) decimal) : ((Decimal) decimal).value;
    }

    @Override
    public BigDecimal value() {
        return value;
    }

    @Override
    public Object toXml() {
        return ((IFn) VAR_ELEMENT.getRawRoot()).invoke(null, RT.mapUniqueKeys(KW_VALUE, value));
    }

    static void hashInto(PrimitiveSink sink, Object value) {
        if (value instanceof BigDecimal) {
            sink.putByte(HASH_KEY);
            sink.putByte(HASH_KEY_VALUE);
            SystemDecimal.hashInto(sink, (BigDecimal) value);
        } else {
            ((Decimal) value).hashInto(sink);
        }
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
            ((IFn) VAR_HASH_INTO.getRawRoot()).invoke(extension, sink);
        }
        if (value != null) {
            sink.putByte(HASH_KEY_VALUE);
            SystemDecimal.hashInto(sink, value);
        }
    }

    @Override
    public PersistentVector references() {
        return extension == null
                ? PersistentVector.EMPTY
                : FhirType.appendExtensionReferences((PersistentVector) extension, PersistentVector.EMPTY);
    }

    static int memSize(Object o) {
        return o instanceof BigDecimal ? SystemDecimal.memSize((BigDecimal) o) : ((Decimal) o).memSize();
    }

    @Override
    public int memSize() {
        return 56 +
                (id == null ? 0 : SystemString.memSize(id)) +
                (extension == null ? 0 : Extension.vectorMemSize(extension)) +
                (value == null ? 0 : SystemDecimal.memSize(value));
    }

    static void serialize(String fieldName, Object o, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (o instanceof BigDecimal) {
            gen.writeNumberField(fieldName, (BigDecimal) o);
        } else {
            BigDecimal value = ((Decimal) o).value;
            if (value != null) {
                gen.writeStringField(fieldName, value.toString());
            }
            gen.writeFieldName("_" + fieldName);
            Decimal.SERIALIZER.serialize((Decimal) o, gen, provider);
        }
    }
}
