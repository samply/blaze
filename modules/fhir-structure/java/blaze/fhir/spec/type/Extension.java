package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.SystemString;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.StringJoiner;

@SuppressWarnings("UnstableApiUsage")
public final class Extension implements ComplexType {

    public static final StdSerializer<Extension> SERIALIZER = new Serializer();

    private static final Keyword TYPE = RT.keyword("fhir", "Extension");
    private static final Symbol SYM_ID = Symbol.intern(null, "id");
    private static final Symbol SYM_EXTENSION = Symbol.intern(null, "extension");
    private static final Symbol SYM_URL = Symbol.intern(null, "url");
    private static final Symbol SYM_VALUE = Symbol.intern(null, "value");
    private static final Keyword KW_ID = Keyword.intern(SYM_ID);
    private static final Keyword KW_EXTENSION = Keyword.intern(SYM_EXTENSION);
    private static final Keyword KW_URL = Keyword.intern(SYM_URL);
    private static final Keyword KW_VALUE = Keyword.intern(SYM_VALUE);
    private static final Keyword KW_FHIR_TYPE = RT.keyword("fhir", "type");
    private static final byte HASH_KEY = 39;
    private static final byte LIST_HASH_KEY = 36;
    private static final byte HASH_KEY_ID = 0;
    private static final byte HASH_KEY_EXTENSION = 1;
    private static final byte HASH_KEY_URL = 2;
    private static final byte HASH_KEY_VALUE = 3;
    private static final Var VAR_TYPE = RT.var("blaze.fhir.spec.type.protocols", "-type");
    private static final Var VAR_HASH_INTO = RT.var("blaze.fhir.spec.type.protocols", "-hash-into");
    private static final Var VAR_REFERENCES = RT.var("blaze.fhir.spec.type.protocols", "-references");
    private static final PersistentVector FIELDS = (PersistentVector) RT.vector(KW_ID, KW_EXTENSION, KW_URL, KW_VALUE);
    private static final IPersistentVector BASIS = RT.vector(SYM_ID, SYM_EXTENSION, SYM_URL, SYM_VALUE);

    public final String id;
    public final IPersistentVector extension;
    public final String url;
    public final Object value;

    public Extension(String id, IPersistentVector extension, String url, Object value) {
        this.id = id;
        this.extension = extension;
        this.url = Objects.requireNonNull(url, "Extension.url can't be null.");
        this.value = value;
    }

    public static IPersistentVector getBasis() {
        return BASIS;
    }

    public static Extension create(IPersistentMap m) {
        return new Extension(
                (String) m.valAt(KW_ID),
                (IPersistentVector) m.valAt(KW_EXTENSION),
                (String) m.valAt(KW_URL),
                m.valAt(KW_VALUE));
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

    public String url() {
        return url;
    }

    public Object value() {
        return value;
    }

    @Override
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_KEY);
        if (id != null) {
            sink.putByte(HASH_KEY_ID);
            SystemString.hashInto(sink, id);
        }
        if (extension != null) {
            sink.putByte(HASH_KEY_EXTENSION);
            extensionHashInto(extension, sink);
        }
        sink.putByte(HASH_KEY_URL);
        FhirString.hashInto(sink, url);
        if (value != null) {
            sink.putByte(HASH_KEY_VALUE);
            ((IFn) VAR_HASH_INTO.getRawRoot()).invoke(value, sink);
        }
    }

    public static void extensionHashInto(Indexed extension, PrimitiveSink sink) {
        sink.putByte(LIST_HASH_KEY);
        for (int i = 0; i < extension.count(); i++) {
            ((Extension) extension.nth(i)).hashInto(sink);
        }
    }

    @Override
    public PersistentVector references() {
        return extension == null
                ? valueReferences() :
                FhirType.appendExtensionReferences((PersistentVector) extension, valueReferences());
    }

    private PersistentVector valueReferences() {
        return value == null ? PersistentVector.EMPTY : (PersistentVector) ((IFn) VAR_REFERENCES.getRawRoot()).invoke(value);
    }


    @Override
    public int memSize() {
        int s = 40;
        if (id != null) s += SystemString.memSize(id);
        if (extension != null) s += vectorMemSize(extension);
        if (value != null) s += FhirType.memSize(value);
        return s;
    }

    public static int vectorMemSize(Indexed extension) {
        int res = 0;
        for (int i = 0; i < extension.count(); i++) {
            res += ((Extension) extension.nth(i)).memSize();
        }
        return res;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (KW_VALUE == key) {
            return value;
        }
        if (KW_URL == key) {
            return url;
        }
        if (KW_EXTENSION == key) {
            return extension;
        }
        if (KW_ID == key) {
            return id;
        }
        return notFound;
    }

    @Override
    public Iterator<MapEntry> iterator() {
        return new TypeIterator<>(FIELDS, this);
    }

    @Override
    public Object kvreduce(IFn f, Object init) {
        return FIELDS.reduce(new KvReduceFn(f, this), init);
    }

    @Override
    public int count() {
        int c = 1;
        if (id != null) c++;
        if (extension != null) c++;
        if (value != null) c++;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Extension that = (Extension) o;
        return Objects.equals(id, that.id) && Objects.equals(extension, that.extension) && url.equals(that.url) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, url, value);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Extension.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("extension=" + extension)
                .add("url='" + url + "'")
                .add("value=" + value)
                .toString();
    }

    private static Keyword type(Object value) {
        return (Keyword) ((IFn) VAR_TYPE.getRawRoot()).invoke(value);
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static final class Serializer extends StdSerializer<Extension> {

        private Serializer() {
            super(Extension.class);
        }

        @Override
        public void serialize(Extension extension, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject(extension);
            if (extension.id != null) {
                gen.writeStringField("id", extension.id);
            }
            if (extension.extension != null) {
                serializeVector(extension.extension, gen, provider);
            }
            gen.writeStringField("url", extension.url);
            if (extension.value != null) {
                gen.writeFieldName("value" + capitalize(type(extension.value).getName()));
                provider.defaultSerializeValue(removeType(extension.value), gen);
            }
            gen.writeEndObject();
        }

        private static Object removeType(Object value) {
            if (value instanceof IPersistentMap) {
                return ((IPersistentMap) value).without(KW_FHIR_TYPE);
            } else {
                return value;
            }
        }
    }

    static void serializeVector(IPersistentVector extension, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeArrayFieldStart("extension");
        for (int i = 0; i < extension.count(); i++) {
            SERIALIZER.serialize((Extension) extension.nth(i), gen, provider);
        }
        gen.writeEndArray();
    }

}
