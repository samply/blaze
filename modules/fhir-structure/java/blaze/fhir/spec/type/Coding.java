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

import static blaze.fhir.spec.type.Extension.extensionHashInto;
import static blaze.fhir.spec.type.Extension.serializeVector;

@SuppressWarnings("UnstableApiUsage")
public final class Coding implements ComplexType {

    public static final StdSerializer<Coding> SERIALIZER = new Serializer();

    private static final Keyword TYPE = RT.keyword("fhir", "Coding");
    private static final Symbol SYM_ID = Symbol.intern(null, "id");
    private static final Symbol SYM_EXTENSION = Symbol.intern(null, "extension");
    private static final Symbol SYM_SYSTEM = Symbol.intern(null, "system");
    private static final Symbol SYM_VERSION = Symbol.intern(null, "version");
    private static final Symbol SYM_CODE = Symbol.intern(null, "code");
    private static final Symbol SYM_DISPLAY = Symbol.intern(null, "display");
    private static final Keyword KW_ID = Keyword.intern(SYM_ID);
    private static final Keyword KW_EXTENSION = Keyword.intern(SYM_EXTENSION);
    private static final Keyword KW_SYSTEM = Keyword.intern(SYM_SYSTEM);
    private static final Keyword KW_VERSION = Keyword.intern(SYM_VERSION);
    private static final Keyword KW_CODE = Keyword.intern(SYM_CODE);
    private static final Keyword KW_DISPLAY = Keyword.intern(SYM_DISPLAY);
    private static final byte HASH_KEY = 38;
    private static final byte HASH_KEY_ID = 0;
    private static final byte HASH_KEY_EXTENSION = 1;
    private static final byte HASH_KEY_SYSTEM = 2;
    private static final byte HASH_KEY_VERSION = 3;
    private static final byte HASH_KEY_CODE = 4;
    private static final byte HASH_KEY_DISPLAY = 5;
    private static final PersistentVector FIELDS = (PersistentVector) RT.vector(KW_ID, KW_EXTENSION, KW_SYSTEM, KW_VERSION, KW_CODE, KW_DISPLAY);
    private static final IPersistentVector BASIS = RT.vector(SYM_ID, SYM_EXTENSION, SYM_SYSTEM, SYM_VERSION, SYM_CODE, SYM_DISPLAY);

    public final String id;
    public final IPersistentVector extension;
    public final Uri system;
    public final Object version;
    public final Code code;
    public final Object display;

    public Coding(String id, IPersistentVector extension, Uri system, Object version, Code code, Object display) {
        this.id = id;
        this.extension = extension;
        this.system = system;
        this.version = version;
        this.code = code;
        this.display = display;
    }

    public static IPersistentVector getBasis() {
        return BASIS;
    }

    public static Coding create(IPersistentMap m) {
        return new Coding(
                (String) m.valAt(KW_ID),
                (IPersistentVector) m.valAt(KW_EXTENSION),
                (Uri) m.valAt(KW_SYSTEM),
                m.valAt(KW_VERSION),
                (Code) m.valAt(KW_CODE),
                m.valAt(KW_DISPLAY));
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

    public Uri system() {
        return system;
    }

    public Object version() {
        return version;
    }
    
    public Code code() {
        return code;
    }
    
    public Object display() {
        return display;
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
            extensionHashInto(extension, sink);
        }
        if (system != null) {
            sink.putByte(HASH_KEY_SYSTEM);
            system.hashInto(sink);
        }
        if (version != null) {
            sink.putByte(HASH_KEY_VERSION);
            FhirString.hashInto(sink, version);
        }
        if (code != null) {
            sink.putByte(HASH_KEY_CODE);
            code.hashInto(sink);
        }
        if (display != null) {
            sink.putByte(HASH_KEY_DISPLAY);
            FhirString.hashInto(sink, display);
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
        return 0;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public Object valAt(Object key, Object notFound) {
        if (KW_CODE == key) {
            return code;
        }
        if (KW_SYSTEM == key) {
            return system;
        }
        if (KW_VERSION == key) {
            return version;
        }
        if (KW_DISPLAY == key) {
            return display;
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
        if (system != null) c++;
        if (version != null) c++;
        if (code != null) c++;
        if (display != null) c++;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coding that = (Coding) o;
        return Objects.equals(id, that.id) && Objects.equals(extension, that.extension) && Objects.equals(system, that.system) &&
                Objects.equals(version, that.version) && Objects.equals(code, that.code) && Objects.equals(display, that.display);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, system, version, code, display);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Coding.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("extension=" + extension)
                .add("system='" + system + "'")
                .add("version=" + version)
                .add("code=" + code)
                .add("display=" + display)
                .toString();
    }

    private static final class Serializer extends StdSerializer<Coding> {

        private Serializer() {
            super(Coding.class);
        }

        @Override
        public void serialize(Coding coding, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject(coding);
            if (coding.id != null) {
                gen.writeStringField("id", coding.id);
            }
            if (coding.extension != null) {
                serializeVector(coding.extension, gen, provider);
            }
            if (coding.system != null) {
                gen.writeFieldName("system");
                Uri.SERIALIZER.serialize(coding.system, gen, provider);
            }
            if (coding.version != null) {
                FhirString.serialize("version", coding.version, gen, provider);
            }
            if (coding.code != null) {
                gen.writeFieldName("code");
                Code.SERIALIZER.serialize(coding.code, gen, provider);
            }
            if (coding.display != null) {
                FhirString.serialize("display", coding.display, gen, provider);
            }
            gen.writeEndObject();
        }
    }
}
