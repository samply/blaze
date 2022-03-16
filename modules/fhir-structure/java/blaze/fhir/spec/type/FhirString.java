package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.SystemString;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;

@SuppressWarnings("UnstableApiUsage")
public class FhirString implements PrimitiveType<String> {

    public static final StdSerializer<FhirString> SERIALIZER = new StdSerializer<>(FhirString.class) {

        @Override
        public void serialize(FhirString s, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject(s);
            if (s.id != null) {
                gen.writeStringField("id", s.id);
            }
            Extension.serializeVector(s.extension, gen, provider);
            if (s.value != null) {
                gen.writeStringField("value", s.value);
            }
            gen.writeEndObject();
        }
    };

    private static final Symbol SYM_ID = Symbol.intern(null, "id");
    private static final Symbol SYM_EXTENSION = Symbol.intern(null, "extension");
    private static final Symbol SYM_VALUE = Symbol.intern(null, "value");
    private static final byte HASH_KEY = 3;
    private static final byte HASH_KEY_ID = 0;
    private static final byte HASH_KEY_EXTENSION = 1;
    private static final byte HASH_KEY_VALUE = 2;
    private static final Var VAR_HASH_INTO = RT.var("blaze.fhir.spec.type.protocols", "-hash-into");
    private static final IPersistentVector BASIS = RT.vector(SYM_ID, SYM_EXTENSION, SYM_VALUE);

    public final String id;
    public final IPersistentVector extension;
    public final String value;

    public FhirString(String id, IPersistentVector extension, String value) {
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

    static String value(Object fhirString) {
        return fhirString instanceof String ? ((String) fhirString) : ((FhirString) fhirString).value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public Object toXml() {
        return null;
    }

    static void hashInto(PrimitiveSink sink, Object value) {
        if (value instanceof String) {
            sink.putByte(HASH_KEY);
            sink.putByte(HASH_KEY_VALUE);
            SystemString.hashInto(sink, (String) value);
        } else {
            ((FhirString) value).hashInto(sink);
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
            SystemString.hashInto(sink, value);
        }
    }

    @Override
    public PersistentVector references() {
        return extension == null
                ? PersistentVector.EMPTY
                : FhirType.appendExtensionReferences((PersistentVector) extension, PersistentVector.EMPTY);
    }

    static int memSize(Object fhirString) {
        return fhirString instanceof String ? SystemString.memSize((String) fhirString) : ((FhirString) fhirString).memSize();
    }

    @Override
    public int memSize() {
        return 56 +
                (id == null ? 0 : SystemString.memSize(id)) +
                (extension == null ? 0 : Extension.vectorMemSize(extension)) +
                (value == null ? 0 : SystemString.memSize(value));
    }

    static void serialize(String fieldName, Object o, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (o instanceof String) {
            gen.writeStringField(fieldName, (String) o);
        } else {
            String value = ((FhirString) o).value;
            if (value != null) {
                gen.writeStringField(fieldName, value);
            }
            gen.writeFieldName("_" + fieldName);
            FhirString.SERIALIZER.serialize((FhirString) o, gen, provider);
        }
    }
}
