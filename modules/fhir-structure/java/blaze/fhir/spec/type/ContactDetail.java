package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public final class ContactDetail extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - name reference
     * 4 byte - telecom reference
     * 4 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 16;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "ContactDetail");

    private static final Keyword NAME = Keyword.intern("name");
    private static final Keyword TELECOM = Keyword.intern("telecom");

    private static final Keyword[] FIELDS = {ID, EXTENSION, NAME, TELECOM};

    private static final FieldName FIELD_NAME_NAME = FieldName.of("name");
    private static final SerializedString FIELD_NAME_TELECOM = new SerializedString("telecom");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueContactDetail");

    private static final byte HASH_MARKER = 52;

    @SuppressWarnings("unchecked")
    private static final ContactDetail EMPTY = new ContactDetail(ExtensionData.EMPTY, null, PersistentVector.EMPTY);

    private final String name;
    private final List<ContactPoint> telecom;

    private ContactDetail(ExtensionData extensionData, String name, List<ContactPoint> telecom) {
        super(extensionData);
        this.name = name;
        this.telecom = requireNonNull(telecom);
    }

    public static ContactDetail create(IPersistentMap m) {
        return new ContactDetail(ExtensionData.fromMap(m), (String) m.valAt(NAME), Base.listFrom(m, TELECOM));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public String name() {
        return name;
    }

    public List<ContactPoint> telecom() {
        return telecom;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == NAME) return name;
        if (key == TELECOM) return telecom;
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, TELECOM, telecom);
        seq = appendElement(seq, NAME, name);
        return extensionData.append(seq);
    }

    @Override
    public ContactDetail empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContactDetail assoc(Object key, Object val) {
        if (key == NAME) return new ContactDetail(extensionData, (String) val, telecom);
        if (key == TELECOM)
            return new ContactDetail(extensionData, name, (List<ContactPoint>) (val == null ? PersistentVector.EMPTY : val));
        if (key == EXTENSION)
            return new ContactDetail(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), name, telecom);
        if (key == ID) return new ContactDetail(extensionData.withId((java.lang.String) val), name, telecom);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.ContactDetail.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (name != null) {
            name.serializeAsJsonProperty(generator, FIELD_NAME_NAME);
        }
        if (!telecom.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_TELECOM);
            generator.writeStartArray();
            for (ContactPoint contactPoint : telecom) {
                contactPoint.serializeAsJsonValue(generator);
            }
            generator.writeEndArray();
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (name != null) {
            sink.putByte((byte) 2);
            name.hashInto(sink);
        }
        if (!telecom.isEmpty()) {
            sink.putByte((byte) 3);
            Base.hashIntoList(telecom, sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(name) + Base.memSize(telecom);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ContactDetail that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(name, that.name) &&
                Objects.equals(telecom, that.telecom);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(telecom);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "ContactDetail{" +
                extensionData +
                ", name=" + name +
                ", telecom=" + telecom +
                '}';
    }
}
