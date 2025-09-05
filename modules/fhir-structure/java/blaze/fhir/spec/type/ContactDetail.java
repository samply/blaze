package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class ContactDetail extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "ContactDetail");

    private static final Keyword NAME = Keyword.intern("name");
    private static final Keyword TELECOM = Keyword.intern("telecom");

    private static final Keyword[] FIELDS = {ID, EXTENSION, NAME, TELECOM};

    private static final FieldName FIELD_NAME_NAME = FieldName.of("name");
    private static final SerializedString FIELD_NAME_TELECOM = new SerializedString("telecom");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueContactDetail");

    private static final byte HASH_MARKER = 52;

    private final String name;
    private final PersistentVector telecom;

    public ContactDetail(java.lang.String id, PersistentVector extension, String name, PersistentVector telecom) {
        super(id, extension);
        this.name = name;
        this.telecom = telecom;
    }

    public static ContactDetail create(IPersistentMap m) {
        return new ContactDetail((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION),
                (String) m.valAt(NAME), (PersistentVector) m.valAt(TELECOM));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "name"),
                Symbol.intern(null, "telecom"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public String name() {
        return name;
    }

    public PersistentVector telecom() {
        return telecom;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == NAME) return name;
        if (key == TELECOM) return telecom;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, TELECOM, telecom);
        seq = appendElement(seq, NAME, name);
        return appendBase(seq);
    }

    @Override
    public IPersistentCollection empty() {
        return new ContactDetail(null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public ContactDetail assoc(Object key, Object val) {
        if (key == ID) return new ContactDetail((java.lang.String) val, extension, name, telecom);
        if (key == EXTENSION) return new ContactDetail(id, (PersistentVector) val, name, telecom);
        if (key == NAME) return new ContactDetail(id, extension, (String) val, telecom);
        if (key == TELECOM) return new ContactDetail(id, extension, name, (PersistentVector) val);
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
        if (telecom != null && !telecom.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_TELECOM);
            generator.writeStartArray();
            for (Object contactPoint : telecom) {
                ((ContactPoint) contactPoint).serializeAsJsonValue(generator);
            }
            generator.writeEndArray();
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (name != null) {
            sink.putByte((byte) 2);
            name.hashInto(sink);
        }
        if (telecom != null && !telecom.isEmpty()) {
            sink.putByte((byte) 3);
            sink.putByte((byte) 36);
            for (Object contactPoint : telecom) {
                ((ContactPoint) contactPoint).hashInto(sink);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactDetail that = (ContactDetail) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(name, that.name) &&
                Objects.equals(telecom, that.telecom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, name, telecom);
    }

    @Override
    public java.lang.String toString() {
        return "ContactDetail{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", name=" + name +
                ", telecom=" + telecom +
                '}';
    }
}
