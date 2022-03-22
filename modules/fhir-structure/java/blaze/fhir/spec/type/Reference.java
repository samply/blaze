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
import java.util.regex.Pattern;

import static blaze.fhir.spec.type.Extension.extensionHashInto;

@SuppressWarnings("UnstableApiUsage")
public final class Reference implements ComplexType, Associative {

    public static final StdSerializer<Reference> SERIALIZER = new Serializer();

    private static final Keyword TYPE = RT.keyword("fhir", "Reference");
    private static final Symbol SYM_ID = Symbol.intern(null, "id");
    private static final Symbol SYM_EXTENSION = Symbol.intern(null, "extension");
    private static final Symbol SYM_REFERENCE = Symbol.intern(null, "reference");
    private static final Symbol SYM_TYPE = Symbol.intern(null, "type");
    private static final Symbol SYM_IDENTIFIER = Symbol.intern(null, "identifier");
    private static final Symbol SYM_DISPLAY = Symbol.intern(null, "display");
    private static final Keyword KW_ID = Keyword.intern(SYM_ID);
    private static final Keyword KW_EXTENSION = Keyword.intern(SYM_EXTENSION);
    private static final Keyword KW_REFERENCE = Keyword.intern(SYM_REFERENCE);
    private static final Keyword KW_TYPE = Keyword.intern(SYM_TYPE);
    private static final Keyword KW_IDENTIFIER = Keyword.intern(SYM_IDENTIFIER);
    private static final Keyword KW_DISPLAY = Keyword.intern(SYM_DISPLAY);
    private static final byte HASH_KEY = 43;
    private static final byte HASH_KEY_ID = 0;
    private static final byte HASH_KEY_EXTENSION = 1;
    private static final byte HASH_KEY_REFERENCE = 2;
    private static final byte HASH_KEY_TYPE = 3;
    private static final byte HASH_KEY_IDENTIFIER = 4;
    private static final byte HASH_KEY_DISPLAY = 5;
    private static final Var VAR_HASH_INTO = RT.var("blaze.fhir.spec.type.protocols", "-hash-into");
    private static final Pattern P_SLASH = Pattern.compile("/");
    private static final Pattern P_TYPE = Pattern.compile("[A-Z]([A-Za-z0-9_]){0,254}");
    private static final Pattern P_ID = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");
    private static final PersistentVector FIELDS = (PersistentVector) RT.vector(KW_ID, KW_EXTENSION, KW_REFERENCE, KW_TYPE, KW_IDENTIFIER, KW_DISPLAY);
    private static final IPersistentVector BASIS = RT.vector(SYM_ID, SYM_EXTENSION, SYM_REFERENCE, SYM_TYPE, SYM_IDENTIFIER, SYM_DISPLAY);

    public final String id;
    public final IPersistentVector extension;
    public final Object reference;
    public final Uri type;
    public final Object identifier;
    public final Object display;

    public Reference(String id, IPersistentVector extension, Object reference, Uri type, Object identifier, Object display) {
        this.id = id;
        this.extension = extension;
        this.reference = reference;
        this.type = type;
        this.identifier = identifier;
        this.display = display;
    }

    public static IPersistentVector getBasis() {
        return BASIS;
    }

    public static Reference create(IPersistentMap m) {
        return new Reference(
                (String) m.valAt(KW_ID),
                (IPersistentVector) m.valAt(KW_EXTENSION),
                m.valAt(KW_REFERENCE),
                (Uri) m.valAt(KW_TYPE),
                m.valAt(KW_IDENTIFIER),
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

    public Object reference() {
        return reference;
    }

    public Uri type() {
        return type;
    }

    public Object identifier() {
        return identifier;
    }

    public Object display() {
        return display;
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
        if (reference != null) {
            sink.putByte(HASH_KEY_REFERENCE);
            FhirString.hashInto(sink, reference);
        }
        if (type != null) {
            sink.putByte(HASH_KEY_TYPE);
            type.hashInto(sink);
        }
        if (identifier != null) {
            sink.putByte(HASH_KEY_IDENTIFIER);
            ((IFn) VAR_HASH_INTO.getRawRoot()).invoke(identifier, sink);
        }
        if (display != null) {
            sink.putByte(HASH_KEY_DISPLAY);
            FhirString.hashInto(sink, display);
        }
    }

    @Override
    public PersistentVector references() {
        if (extension != null) {
            return FhirType.appendExtensionReferences((PersistentVector) extension, localReferences());
        } else {
            return localReferences();
        }

    }

    private PersistentVector localReferences() {
        if (reference != null) {
            String[] parts = P_SLASH.split(FhirString.value(reference), 2);
            if (parts.length == 2) {
                if (P_TYPE.matcher(parts[0]).matches() && P_ID.matcher(parts[1]).matches()) {
                    return (PersistentVector) RT.vector(RT.vector(parts[0], parts[1]));
                }
            }
        }
        return PersistentVector.EMPTY;
    }

    @Override
    public int memSize() {
        int s = 40;
        if (id != null) s += SystemString.memSize(id);
        if (extension != null) s += Extension.vectorMemSize(extension);
        if (reference != null) s += FhirString.memSize(reference);
        if (identifier != null) s += FhirType.memSize(identifier);
        if (display != null) s += FhirString.memSize(display);
        return s;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (KW_REFERENCE == key) {
            return reference;
        }
        if (KW_IDENTIFIER == key) {
            return identifier;
        }
        if (KW_DISPLAY == key) {
            return display;
        }
        if (KW_TYPE == key) {
            return type;
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
    @SuppressWarnings("DuplicatedCode")
    public int count() {
        int c = 0;
        if (id != null) c++;
        if (extension != null) c++;
        if (reference != null) c++;
        if (type != null) c++;
        if (identifier != null) c++;
        if (display != null) c++;
        return c;
    }

    @Override
    public IPersistentCollection cons(Object o) {
        throw new UnsupportedOperationException("Reference.cons");
    }

    @Override
    public IPersistentCollection empty() {
        throw new UnsupportedOperationException("Reference.empty");
    }

    @Override
    public boolean equiv(Object o) {
        return equals(o);
    }

    @Override
    public ISeq seq() {
        return IteratorSeq.create(iterator());
    }

    @Override
    public boolean containsKey(Object key) {
        return valAt(key) != null;
    }

    @Override
    public IMapEntry entryAt(Object key) {
        Object val = valAt(key);
        return val == null ? null : MapEntry.create(key, val);
    }

    @Override
    public Associative assoc(Object key, Object val) {
        if (KW_REFERENCE == key) {
            if (Objects.equals(reference, val)) {
                return this;
            } else {
                return new Reference(id, extension, val, type, identifier, display);
            }
        }
        throw new UnsupportedOperationException("Reference.assoc");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reference that = (Reference) o;
        return Objects.equals(id, that.id) && Objects.equals(extension, that.extension) &&
                Objects.equals(reference, that.reference) && Objects.equals(type, that.type) &&
                Objects.equals(identifier, that.identifier) && Objects.equals(display, that.display);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, reference, type, identifier, display);
    }

    private static final class Serializer extends StdSerializer<Reference> {

        private Serializer() {
            super(Reference.class);
        }

        @Override
        public void serialize(Reference reference, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject(reference);
            if (reference.id != null) {
                gen.writeStringField("id", reference.id);
            }
            if (reference.extension != null) {
                Extension.serializeVector(reference.extension, gen, provider);
            }
            if (reference.reference != null) {
                FhirString.serialize("reference", reference.reference, gen, provider);
            }
            if (reference.type != null) {
                gen.writeFieldName("type");
                Uri.SERIALIZER.serialize(reference.type, gen, provider);
            }
            if (reference.identifier != null) {
                gen.writeFieldName("identifier");
                provider.defaultSerializeValue(reference.identifier, gen);
            }
            if (reference.display != null) {
                gen.writeFieldName("display");
                provider.defaultSerializeValue(reference.display, gen);
            }
            gen.writeEndObject();
        }
    }
}
