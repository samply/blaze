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
public sealed abstract class Meta extends AbstractElement implements Complex, ExtensionValue
        permits Meta.Normal, Meta.Interned {

    /**
     * Memory size of a not interned Meta.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - versionId reference
     * 4 or 8 byte - lastUpdated reference
     * 4 or 8 byte - source reference
     * 4 or 8 byte - profile reference
     * 4 or 8 byte - security reference
     * 4 or 8 byte - tag reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 7 * MEM_SIZE_REFERENCE + 7) & ~7;

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
            k -> new Interned(k.extensionData, k.profile, k.security, k.tag)
    );

    private final Id versionId;
    private final Instant lastUpdated;
    private final Uri source;
    private final PersistentVector profile;
    private final PersistentVector security;
    private final PersistentVector tag;

    private Meta(ExtensionData extensionData, Id versionId, Instant lastUpdated, Uri source, PersistentVector profile,
                 PersistentVector security, PersistentVector tag) {
        super(extensionData);
        this.versionId = versionId;
        this.lastUpdated = lastUpdated;
        this.source = source;
        this.profile = requireNonNull(profile);
        this.security = requireNonNull(security);
        this.tag = requireNonNull(tag);
    }

    private static Meta maybeIntern(ExtensionData extensionData, Id versionId, Instant lastUpdated, Uri source,
                                    PersistentVector profile, PersistentVector security, PersistentVector tag) {
        if (extensionData.isInterned() && versionId == null && lastUpdated == null && source == null &&
                Base.areAllInterned(profile) && Base.areAllInterned(security) && Base.areAllInterned(tag)) {
            return extensionData == ExtensionData.EMPTY && profile.isEmpty() && security.isEmpty() && tag.isEmpty()
                    ? Interned.EMPTY
                    : INTERNER.intern(new InternerKey(extensionData, profile, security, tag));
        }
        return new Normal(extensionData, versionId, lastUpdated, source, profile, security, tag);
    }

    public static Meta create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Id) m.valAt(VERSION_ID), (Instant) m.valAt(LAST_UPDATED),
                (Uri) m.valAt(SOURCE), Base.listFrom(m, PROFILE), Base.listFrom(m, SECURITY), Base.listFrom(m, TAG));
    }

    /**
     * Creates a Meta from {@code slots} holding the values of the keys of
     * {@link #fields()} at the same index.
     */
    public static Meta fromSlots(Object[] slots) {
        return maybeIntern(ExtensionData.fromSlots(slots), (Id) slots[2], (Instant) slots[3],
                (Uri) slots[4], Lists.nullToEmpty(slots[5]), Lists.nullToEmpty(slots[6]), Lists.nullToEmpty(slots[7]));
    }

    /**
     * Returns the keys of all fields of Meta in slot order.
     */
    public static Keyword[] fields() {
        return FIELDS.clone();
    }

    @Override
    public boolean isInterned() {
        return false;
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

    @SuppressWarnings("unchecked")
    public List<Canonical> profile() {
        return profile;
    }

    @SuppressWarnings("unchecked")
    public List<Coding> security() {
        return security;
    }

    @SuppressWarnings("unchecked")
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
    public Interned empty() {
        return Interned.EMPTY;
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
    public void collectReferences(List<PersistentVector> refs) {
        if (isInterned()) return;
        super.collectReferences(refs);
        Base.collectReferences(versionId, refs);
        Base.collectReferences(lastUpdated, refs);
        Base.collectReferences(source, refs);
        Base.collectReferences(profile, refs);
        Base.collectReferences(security, refs);
        Base.collectReferences(tag, refs);
    }

    final boolean equalComponents(Meta that) {
        return extensionData.equals(that.extensionData) &&
                Objects.equals(versionId, that.versionId) &&
                Objects.equals(lastUpdated, that.lastUpdated) &&
                Objects.equals(source, that.source) &&
                profile.equals(that.profile) &&
                security.equals(that.security) &&
                tag.equals(that.tag);
    }

    final int hashComponents() {
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
    public final String toString() {
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

    public static final class Normal extends Meta {

        private Normal(ExtensionData extensionData, Id versionId, Instant lastUpdated, Uri source,
                       PersistentVector profile, PersistentVector security, PersistentVector tag) {
            super(extensionData, versionId, lastUpdated, source, profile, security, tag);
        }

        public static Normal create(IPersistentMap m) {
            return new Normal(ExtensionData.fromMap(m), (Id) m.valAt(VERSION_ID), (Instant) m.valAt(LAST_UPDATED),
                    (Uri) m.valAt(SOURCE), Base.listFrom(m, PROFILE), Base.listFrom(m, SECURITY),
                    Base.listFrom(m, TAG));
        }

        @Override
        public int memSize() {
            return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(versionId()) +
                    Base.memSize(lastUpdated()) + Base.memSize(source()) + Base.memSize(profile()) +
                    Base.memSize(security()) + Base.memSize(tag());
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Meta that && equalComponents(that);
        }

        @Override
        public int hashCode() {
            return hashComponents();
        }
    }

    /**
     * An interned Meta.
     * <p>
     * Interned Metas never have a versionId, lastUpdated or source, because their values are mostly unique per
     * resource. Interned Metas with equal components are identical, so they are compared by identity. Interned Metas
     * can still be equal to not interned Metas, because components like {@link Coding.Normal} and
     * {@link Coding.Interned} with equal values are equal.
     */
    public static final class Interned extends Meta {

        private static final Interned EMPTY = new Interned(ExtensionData.EMPTY, PersistentVector.EMPTY,
                PersistentVector.EMPTY, PersistentVector.EMPTY);

        private final int hash;

        private Interned(ExtensionData extensionData, PersistentVector profile, PersistentVector security,
                         PersistentVector tag) {
            super(extensionData, null, null, null, profile, security, tag);
            this.hash = hashComponents();
        }

        public static Interned create(IPersistentMap m) {
            if (Meta.create(m) instanceof Interned interned) return interned;
            throw new IllegalArgumentException("Can't create an interned FHIR.Meta using non-interned components.");
        }

        @Override
        public boolean isInterned() {
            return true;
        }

        @Override
        public int memSize() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Normal that && equalComponents(that);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Key of the interner.
     * <p>
     * The extension data is interned, so equal ones are identical and it is compared by identity. Because the key
     * references it strongly, it can't be collected and recreated as a different instance while the key exists. The
     * lists are compared structurally, because lists aren't interned.
     */
    private static final class InternerKey {

        private final ExtensionData extensionData;
        private final PersistentVector profile;
        private final PersistentVector security;
        private final PersistentVector tag;
        private final int hash;

        private InternerKey(ExtensionData extensionData, PersistentVector profile, PersistentVector security,
                            PersistentVector tag) {
            this.extensionData = requireNonNull(extensionData);
            this.profile = requireNonNull(profile);
            this.security = requireNonNull(security);
            this.tag = requireNonNull(tag);
            int result = System.identityHashCode(extensionData);
            result = 31 * result + profile.hashCode();
            result = 31 * result + security.hashCode();
            result = 31 * result + tag.hashCode();
            this.hash = result;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof InternerKey that &&
                    hash == that.hash &&
                    extensionData == that.extensionData &&
                    profile.equals(that.profile) &&
                    security.equals(that.security) &&
                    tag.equals(that.tag);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
