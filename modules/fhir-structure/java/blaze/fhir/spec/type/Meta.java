package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("DuplicatedCode")
public final class Meta extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - versionId reference
     * 4 or 8 byte - lastUpdated reference
     * 4 or 8 byte - source reference
     * 4 or 8 byte - profile reference
     * 4 or 8 byte - security reference
     * 4 or 8 byte - tag reference
     * 1 byte - interned boolean
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 7 * MEM_SIZE_REFERENCE + 1 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Meta");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Meta ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk VERSION_ID_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Meta m ? m.versionId : this;
        }
    };

    private static final ILookupThunk LAST_UPDATED_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Meta m ? m.lastUpdated : this;
        }
    };

    private static final ILookupThunk SOURCE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Meta m ? m.source : this;
        }
    };

    private static final ILookupThunk PROFILE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Meta m ? m.profile : this;
        }
    };

    private static final ILookupThunk SECURITY_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Meta m ? m.security : this;
        }
    };

    private static final ILookupThunk TAG_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Meta m ? m.tag : this;
        }
    };

    private static final Keyword VERSION_ID = RT.keyword(null, "versionId");
    private static final Keyword LAST_UPDATED = RT.keyword(null, "lastUpdated");
    private static final Keyword SOURCE = RT.keyword(null, "source");
    private static final Keyword PROFILE = RT.keyword(null, "profile");
    private static final Keyword SECURITY = RT.keyword(null, "security");
    private static final Keyword TAG = RT.keyword(null, "tag");

    private static final Keyword[] FIELDS = {ID, EXTENSION, VERSION_ID, LAST_UPDATED, SOURCE, PROFILE, SECURITY, TAG};

    private static final FieldName FIELD_NAME_VERSION_ID = FieldName.of("versionId");
    private static final FieldName FIELD_NAME_LAST_UPDATED = FieldName.of("lastUpdated");
    private static final FieldName FIELD_NAME_SOURCE = FieldName.of("source");
    private static final FieldName FIELD_NAME_PROFILE = FieldName.of("profile");
    private static final SerializedString FIELD_NAME_SECURITY = new SerializedString("security");
    private static final SerializedString FIELD_NAME_TAG = new SerializedString("tag");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueMeta");

    private static final byte HASH_MARKER = 44;

    private static final Interner<InternerKey, Meta> INTERNER = Interners.weakInterner(
            k -> new Meta(k.extensionData, null, null, k.source, k.profile, k.security, k.tag, true)
    );
    @SuppressWarnings("unchecked")
    private static final Meta EMPTY = new Meta(ExtensionData.EMPTY, null, null, null, PersistentVector.EMPTY,
            PersistentVector.EMPTY, PersistentVector.EMPTY, true);

    private final Id versionId;
    private final Instant lastUpdated;
    private final Uri source;
    private final List<Canonical> profile;
    private final List<Coding> security;
    private final List<Coding> tag;
    private final boolean interned;

    private Meta(ExtensionData extensionData, Id versionId, Instant lastUpdated, Uri source, List<Canonical> profile,
                 List<Coding> security, List<Coding> tag, boolean interned) {
        super(extensionData);
        this.versionId = versionId;
        this.lastUpdated = lastUpdated;
        this.source = source;
        this.profile = requireNonNull(profile);
        this.security = requireNonNull(security);
        this.tag = requireNonNull(tag);
        this.interned = interned;
    }

    private static Meta maybeIntern(ExtensionData extensionData, Id versionId, Instant lastUpdated, Uri source,
                                    List<Canonical> profile, List<Coding> security, List<Coding> tag) {
        return extensionData.isInterned() && versionId == null && lastUpdated == null && Base.isInterned(source) &&
                Base.areAllInterned(profile) && Base.areAllInterned(security) && Base.areAllInterned(tag)
                ? INTERNER.intern(new InternerKey(extensionData, source, profile, security, tag))
                : new Meta(extensionData, versionId, lastUpdated, source, profile, security, tag, false);
    }

    public static Meta create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Id) m.valAt(VERSION_ID), (Instant) m.valAt(LAST_UPDATED),
                (Uri) m.valAt(SOURCE), Base.listFrom(m, PROFILE), Base.listFrom(m, SECURITY), Base.listFrom(m, TAG));
    }

    @Override
    public boolean isInterned() {
        return interned;
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
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == VERSION_ID) return VERSION_ID_LOOKUP_THUNK;
        if (key == LAST_UPDATED) return LAST_UPDATED_LOOKUP_THUNK;
        if (key == SOURCE) return SOURCE_LOOKUP_THUNK;
        if (key == PROFILE) return PROFILE_LOOKUP_THUNK;
        if (key == SECURITY) return SECURITY_LOOKUP_THUNK;
        if (key == TAG) return TAG_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == VERSION_ID) return versionId;
        if (key == LAST_UPDATED) return lastUpdated;
        if (key == SOURCE) return source;
        if (key == PROFILE) return profile;
        if (key == SECURITY) return security;
        if (key == TAG) return tag;
        return super.valAt(key, notFound);
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
        return extensionData.append(seq);
    }

    @Override
    public Meta empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Meta assoc(Object key, Object val) {
        if (key == VERSION_ID)
            return maybeIntern(extensionData, (Id) val, lastUpdated, source, profile, security, tag);
        if (key == LAST_UPDATED)
            return maybeIntern(extensionData, versionId, (Instant) val, source, profile, security, tag);
        if (key == SOURCE)
            return maybeIntern(extensionData, versionId, lastUpdated, (Uri) val, profile, security, tag);
        if (key == PROFILE)
            return maybeIntern(extensionData, versionId, lastUpdated, source, Lists.nullToEmpty(val), security, tag);
        if (key == SECURITY)
            return maybeIntern(extensionData, versionId, lastUpdated, source, profile, Lists.nullToEmpty(val), tag);
        if (key == TAG)
            return maybeIntern(extensionData, versionId, lastUpdated, source, profile, security, Lists.nullToEmpty(val));
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension(val), versionId, lastUpdated, source, profile, security, tag);
        if (key == ID)
            return maybeIntern(extensionData.withId(val), versionId, lastUpdated, source, profile, security, tag);
        return this;
    }

    @Override
    public Meta withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), versionId, lastUpdated, source, profile, security, tag);
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
            Primitive.serializeJsonPrimitiveList(profile, generator, FIELD_NAME_PROFILE);
        }
        if (!security.isEmpty()) {
            serializeJsonComplexList(security, generator, FIELD_NAME_SECURITY);
        }
        if (!tag.isEmpty()) {
            serializeJsonComplexList(tag, generator, FIELD_NAME_TAG);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
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
            Base.hashIntoList(profile, sink);
        }
        if (!security.isEmpty()) {
            sink.putByte((byte) 6);
            Base.hashIntoList(security, sink);
        }
        if (!tag.isEmpty()) {
            sink.putByte((byte) 7);
            Base.hashIntoList(tag, sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(versionId) +
                Base.memSize(lastUpdated) + Base.memSize(source) + Base.memSize(profile) + Base.memSize(security) +
                Base.memSize(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Meta that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(versionId, that.versionId) &&
                Objects.equals(lastUpdated, that.lastUpdated) &&
                Objects.equals(source, that.source) &&
                profile.equals(that.profile) &&
                security.equals(that.security) &&
                tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(versionId);
        result = 31 * result + Objects.hashCode(lastUpdated);
        result = 31 * result + Objects.hashCode(source);
        result = 31 * result + profile.hashCode();
        result = 31 * result + security.hashCode();
        result = 31 * result + tag.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Meta{" +
                extensionData +
                ", versionId=" + versionId +
                ", lastUpdated=" + lastUpdated +
                ", source=" + source +
                ", profile=" + profile +
                ", security=" + security +
                ", tag=" + tag +
                '}';
    }

    private record InternerKey(ExtensionData extensionData, Uri source, List<Canonical> profile, List<Coding> security,
                               List<Coding> tag) {
        private InternerKey {
            requireNonNull(extensionData);
            requireNonNull(profile);
            requireNonNull(security);
            requireNonNull(tag);
        }
    }
}
