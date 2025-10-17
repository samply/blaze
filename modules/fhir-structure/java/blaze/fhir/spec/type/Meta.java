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

public final class Meta extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Meta");

    private static final Keyword VERSION_ID = Keyword.intern("versionId");
    private static final Keyword LAST_UPDATED = Keyword.intern("lastUpdated");
    private static final Keyword SOURCE = Keyword.intern("source");
    private static final Keyword PROFILE = Keyword.intern("profile");
    private static final Keyword SECURITY = Keyword.intern("security");
    private static final Keyword TAG = Keyword.intern("tag");

    private static final Keyword[] FIELDS = {ID, EXTENSION, VERSION_ID, LAST_UPDATED, SOURCE, PROFILE, SECURITY, TAG};

    private static final FieldName FIELD_NAME_VERSION_ID = FieldName.of("versionId");
    private static final FieldName FIELD_NAME_LAST_UPDATED = FieldName.of("lastUpdated");
    private static final FieldName FIELD_NAME_SOURCE = FieldName.of("source");
    private static final FieldName FIELD_NAME_PROFILE = FieldName.of("profile");
    private static final SerializedString FIELD_NAME_SECURITY = new SerializedString("security");
    private static final SerializedString FIELD_NAME_TAG = new SerializedString("tag");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueMeta");

    private static final byte HASH_MARKER = 44;

    private final Id versionId;
    private final Instant lastUpdated;
    private final Uri source;
    private final List<Canonical> profile;
    private final List<Coding> security;
    private final List<Coding> tag;

    @SuppressWarnings("unchecked")
    public Meta(java.lang.String id, List<Extension> extension, Id versionId, Instant lastUpdated, Uri source,
                List<Canonical> profile, List<Coding> security, List<Coding> tag) {
        super(id, extension);
        this.versionId = versionId;
        this.lastUpdated = lastUpdated;
        this.source = source;
        this.profile = profile == null ? PersistentVector.EMPTY : profile;
        this.security = security == null ? PersistentVector.EMPTY : security;
        this.tag = tag == null ? PersistentVector.EMPTY : tag;
    }

    public static Meta create(IPersistentMap m) {
        return new Meta((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), (Id) m.valAt(VERSION_ID),
                (Instant) m.valAt(LAST_UPDATED), (Uri) m.valAt(SOURCE), Base.listFrom(m, PROFILE),
                Base.listFrom(m, SECURITY), Base.listFrom(m, TAG));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned() && Base.isInterned(versionId) && Base.isInterned(lastUpdated) &&
                Base.isInterned(source) && Base.areAllInterned(profile) &&
                Base.areAllInterned(security) && Base.areAllInterned(tag);
    }

    public Id versionId() {
        return versionId;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    public Uri source() {
        return source;
    }

    public List<Canonical> profile() {
        return profile;
    }

    public List<Coding> security() {
        return security;
    }

    public List<Coding> tag() {
        return tag;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VERSION_ID) return versionId;
        if (key == LAST_UPDATED) return lastUpdated;
        if (key == SOURCE) return source;
        if (key == PROFILE) return profile;
        if (key == SECURITY) return security;
        if (key == TAG) return tag;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Meta empty() {
        return new Meta(null, PersistentVector.EMPTY, null, null, null, PersistentVector.EMPTY, PersistentVector.EMPTY,
                PersistentVector.EMPTY);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Meta assoc(Object key, Object val) {
        if (key == ID)
            return new Meta((java.lang.String) val, extension, versionId, lastUpdated, source, profile, security, tag);
        if (key == EXTENSION)
            return new Meta(id, (List<Extension>) val, versionId, lastUpdated, source, profile, security, tag);
        if (key == VERSION_ID)
            return new Meta(id, extension, (Id) val, lastUpdated, source, profile, security, tag);
        if (key == LAST_UPDATED)
            return new Meta(id, extension, versionId, (Instant) val, source, profile, security, tag);
        if (key == SOURCE)
            return new Meta(id, extension, versionId, lastUpdated, (Uri) val, profile, security, tag);
        if (key == PROFILE)
            return new Meta(id, extension, versionId, lastUpdated, source, (List<Canonical>) val, security, tag);
        if (key == SECURITY)
            return new Meta(id, extension, versionId, lastUpdated, source, profile, (List<Coding>) val, tag);
        if (key == TAG)
            return new Meta(id, extension, versionId, lastUpdated, source, profile, security, (List<Coding>) val);
        throw new UnsupportedOperationException("The key `''' + key + '''` isn't supported on FHIR.Meta.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        if (!tag.isEmpty()) {
            seq = appendElement(seq, TAG, tag);
        }
        if (!security.isEmpty()) {
            seq = appendElement(seq, SECURITY, security);
        }
        if (!profile.isEmpty()) {
            seq = appendElement(seq, PROFILE, profile);
        }
        seq = appendElement(seq, SOURCE, source);
        seq = appendElement(seq, LAST_UPDATED, lastUpdated);
        seq = appendElement(seq, VERSION_ID, versionId);
        return appendBase(seq);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (versionId != null) {
            versionId.serializeAsJsonProperty(generator, FIELD_NAME_VERSION_ID);
        }
        if (lastUpdated != null) {
            lastUpdated.serializeAsJsonProperty(generator, FIELD_NAME_LAST_UPDATED);
        }
        if (source != null) {
            source.serializeAsJsonProperty(generator, FIELD_NAME_SOURCE);
        }
        if (!profile.isEmpty()) {
            Primitives.serializeJsonPrimitiveList(profile, generator, FIELD_NAME_PROFILE);
        }
        if (!security.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_SECURITY);
            generator.writeStartArray();
            for (Coding c : security) {
                c.serializeAsJsonValue(generator);
            }
            generator.writeEndArray();
        }
        if (!tag.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_TAG);
            generator.writeStartArray();
            for (Coding c : tag) {
                c.serializeAsJsonValue(generator);
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
        if (versionId != null) {
            sink.putByte((byte) 2);
            versionId.hashInto(sink);
        }
        if (lastUpdated != null) {
            sink.putByte((byte) 3);
            lastUpdated.hashInto(sink);
        }
        if (source != null) {
            sink.putByte((byte) 4);
            source.hashInto(sink);
        }
        if (!profile.isEmpty()) {
            sink.putByte((byte) 5);
            sink.putByte((byte) 36);
            for (Canonical c : profile) {
                c.hashInto(sink);
            }
        }
        if (!security.isEmpty()) {
            sink.putByte((byte) 6);
            sink.putByte((byte) 36);
            for (Coding c : security) {
                c.hashInto(sink);
            }
        }
        if (!tag.isEmpty()) {
            sink.putByte((byte) 7);
            sink.putByte((byte) 36);
            for (Coding c : tag) {
                c.hashInto(sink);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Meta that = (Meta) o;
        return Objects.equals(id, that.id) &&
                extension.equals(that.extension) &&
                Objects.equals(versionId, that.versionId) &&
                Objects.equals(lastUpdated, that.lastUpdated) &&
                Objects.equals(source, that.source) &&
                Objects.equals(profile, that.profile) &&
                Objects.equals(security, that.security) &&
                Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, versionId, lastUpdated, source, profile, security, tag);
    }

    @Override
    public java.lang.String toString() {
        return "Meta{" +
                "id=" + (id == null ? null : "'''" + id + "'''") +
                ", extension=" + extension +
                ", versionId=" + versionId +
                ", lastUpdated=" + lastUpdated +
                ", source=" + source +
                ", profile=" + profile +
                ", security=" + security +
                ", tag=" + tag +
                '}';
    }
}
