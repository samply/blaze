package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;

@SuppressWarnings("DuplicatedCode")
public final class Contributor extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - type reference
     * 4 or 8 byte - name reference
     * 4 or 8 byte - contact reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 4 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Contributor");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Contributor ? FHIR_TYPE : this;
        }
    };

    private static final Keyword TYPE = RT.keyword(null, "type");
    private static final Keyword NAME = RT.keyword(null, "name");
    private static final Keyword CONTACT = RT.keyword(null, "contact");

    private static final Keyword[] FIELDS = {ID, EXTENSION, TYPE, NAME, CONTACT};

    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_NAME = FieldName.of("name");
    private static final SerializedString FIELD_NAME_CONTACT = new SerializedString("contact");

    private static final byte HASH_MARKER = 60;

    private static final Contributor EMPTY = new Contributor(ExtensionData.EMPTY, null, null, null);

    private final Code type;
    private final String name;
    private final List<ContactDetail> contact;

    private Contributor(ExtensionData extensionData, Code type, String name, List<ContactDetail> contact) {
        super(extensionData);
        this.type = type;
        this.name = name;
        this.contact = contact;
    }

    public static Contributor create(IPersistentMap m) {
        return new Contributor(ExtensionData.fromMap(m), (Code) m.valAt(TYPE), (String) m.valAt(NAME),
                Base.listFrom(m, CONTACT));
    }

    public Code type() {
        return type;
    }

    public String name() {
        return name;
    }

    public List<ContactDetail> contact() {
        return contact;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE_LOOKUP_THUNK : super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == TYPE) return type;
        if (key == NAME) return name;
        if (key == CONTACT) return contact;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, CONTACT, contact);
        seq = appendElement(seq, NAME, name);
        seq = appendElement(seq, TYPE, type);
        return extensionData.append(seq);
    }

    @Override
    public Contributor empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Contributor assoc(Object key, Object val) {
        if (key == TYPE) return new Contributor(extensionData, (Code) val, name, contact);
        if (key == NAME) return new Contributor(extensionData, type, (String) val, contact);
        if (key == CONTACT) return new Contributor(extensionData, type, name, Lists.nullToEmpty(val));
        if (key == EXTENSION)
            return new Contributor(extensionData.withExtension(val), type, name, contact);
        if (key == ID) return new Contributor(extensionData.withId(val), type, name, contact);
        return this;
    }

    @Override
    public Contributor withMeta(IPersistentMap meta) {
        return new Contributor(extensionData.withMeta(meta), type, name, contact);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FieldName.of("valueContributor");
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (type != null) {
            type.serializeAsJsonProperty(generator, FIELD_NAME_TYPE);
        }
        if (name != null) {
            name.serializeAsJsonProperty(generator, FIELD_NAME_NAME);
        }
        if (!contact.isEmpty()) {
            serializeJsonComplexList(contact, generator, FIELD_NAME_CONTACT);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (type != null) {
            sink.putByte((byte) 2);
            type.hashInto(sink);
        }
        if (name != null) {
            sink.putByte((byte) 3);
            name.hashInto(sink);
        }
        if (contact != null) {
            sink.putByte((byte) 4);
            Base.hashIntoList(contact, sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(type) + Base.memSize(name) +
                Base.memSize(contact);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Contributor that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(type, that.type) &&
                Objects.equals(name, that.name) &&
                Objects.equals(contact, that.contact);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(contact);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Contributor{" +
                extensionData +
                ", type=" + type +
                ", name=" + name +
                ", contact=" + contact +
                '}';
    }
}
