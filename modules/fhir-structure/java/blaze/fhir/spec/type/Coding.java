package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public final class Coding extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - system reference
     * 4 byte - version reference
     * 4 byte - code reference
     * 4 byte - display reference
     * 4 byte - userSelected reference
     * 1 byte - interned boolean
     * 7 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 32;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Coding");
    private static final Keyword SYSTEM = Keyword.intern("system");
    private static final Keyword VERSION = Keyword.intern("version");
    private static final Keyword CODE = Keyword.intern("code");
    private static final Keyword DISPLAY = Keyword.intern("display");
    private static final Keyword USER_SELECTED = Keyword.intern("userSelected");

    private static final Keyword[] FIELDS = {ID, EXTENSION, SYSTEM, VERSION, CODE, DISPLAY, USER_SELECTED};

    private static final FieldName FIELD_NAME_SYSTEM = FieldName.of("system");
    private static final FieldName FIELD_NAME_VERSION = FieldName.of("version");
    private static final FieldName FIELD_NAME_CODE = FieldName.of("code");
    private static final FieldName FIELD_NAME_DISPLAY = FieldName.of("display");
    private static final FieldName FIELD_NAME_USER_SELECTED = FieldName.of("userSelected");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCoding");

    private static final byte HASH_MARKER = 38;

    private static final Interner<InternerKey, Coding> INTERNER = Interners.weakInterner(
            k -> new Coding(k.extensionData, k.system, k.version, k.code, k.display, k.userSelected, true)
    );
    public static final Coding EMPTY = new Coding(ExtensionData.EMPTY, null, null, null, null, null, true);

    private final Uri system;
    private final String version;
    private final Code code;
    private final String display;
    private final Boolean userSelected;
    private final boolean interned;

    private Coding(ExtensionData extensionData, Uri system, String version, Code code, String display,
                   Boolean userSelected, boolean interned) {
        super(extensionData);
        this.system = system;
        this.version = version;
        this.code = code;
        this.display = display;
        this.userSelected = userSelected;
        this.interned = interned;
    }

    private static Coding maybeIntern(ExtensionData extensionData, Uri system, String version, Code code, String display,
                                      Boolean userSelected) {
        return extensionData.isInterned() && Base.isInterned(system) && Base.isInterned(version) &&
                Base.isInterned(code) && Base.isInterned(display) && Base.isInterned(userSelected)
                ? INTERNER.intern(new InternerKey(extensionData, system, version, code, display, userSelected))
                : new Coding(extensionData, system, version, code, display, userSelected, false);
    }

    public static Coding create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Uri) m.valAt(SYSTEM), (String) m.valAt(VERSION),
                (Code) m.valAt(CODE), (String) m.valAt(DISPLAY), (Boolean) m.valAt(USER_SELECTED));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return interned;
    }

    public Uri system() {
        return system;
    }

    public String version() {
        return version;
    }

    public Code code() {
        return code;
    }

    public String display() {
        return display;
    }

    public Boolean userSelected() {
        return userSelected;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == CODE) return code;
        if (key == SYSTEM) return system;
        if (key == VERSION) return version;
        if (key == DISPLAY) return display;
        if (key == USER_SELECTED) return userSelected;
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, USER_SELECTED, userSelected);
        seq = appendElement(seq, DISPLAY, display);
        seq = appendElement(seq, CODE, code);
        seq = appendElement(seq, VERSION, version);
        seq = appendElement(seq, SYSTEM, system);
        return extensionData.append(seq);
    }

    @Override
    public Coding empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Coding assoc(Object key, Object val) {
        if (key == CODE) return maybeIntern(extensionData, system, version, (Code) val, display, userSelected);
        if (key == SYSTEM) return maybeIntern(extensionData, (Uri) val, version, code, display, userSelected);
        if (key == VERSION) return maybeIntern(extensionData, system, (String) val, code, display, userSelected);
        if (key == DISPLAY) return maybeIntern(extensionData, system, version, code, (String) val, userSelected);
        if (key == USER_SELECTED) return maybeIntern(extensionData, system, version, code, display, (Boolean) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), system, version, code, display, userSelected);
        if (key == ID)
            return maybeIntern(extensionData.withId((java.lang.String) val), system, version, code, display, userSelected);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Coding.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (system != null) {
            system.serializeAsJsonProperty(generator, FIELD_NAME_SYSTEM);
        }
        if (version != null) {
            version.serializeAsJsonProperty(generator, FIELD_NAME_VERSION);
        }
        if (code != null) {
            code.serializeAsJsonProperty(generator, FIELD_NAME_CODE);
        }
        if (display != null) {
            display.serializeAsJsonProperty(generator, FIELD_NAME_DISPLAY);
        }
        if (userSelected != null) {
            userSelected.serializeAsJsonProperty(generator, FIELD_NAME_USER_SELECTED);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (system != null) {
            sink.putByte((byte) 2);
            system.hashInto(sink);
        }
        if (version != null) {
            sink.putByte((byte) 3);
            version.hashInto(sink);
        }
        if (code != null) {
            sink.putByte((byte) 4);
            code.hashInto(sink);
        }
        if (display != null) {
            sink.putByte((byte) 5);
            display.hashInto(sink);
        }
        if (userSelected != null) {
            sink.putByte((byte) 6);
            userSelected.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(system) +
                Base.memSize(version) + Base.memSize(code) + Base.memSize(display) + Base.memSize(userSelected);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Coding that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(system, that.system) &&
                Objects.equals(version, that.version) &&
                Objects.equals(code, that.code) &&
                Objects.equals(display, that.display) &&
                Objects.equals(userSelected, that.userSelected);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(system);
        result = 31 * result + Objects.hashCode(version);
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(display);
        result = 31 * result + Objects.hashCode(userSelected);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Coding{" +
                extensionData +
                ", system=" + system +
                ", version=" + version +
                ", code=" + code +
                ", display=" + display +
                ", userSelected=" + userSelected +
                '}';
    }

    private record InternerKey(ExtensionData extensionData, Uri system, String version, Code code, String display,
                               Boolean userSelected) {
        private InternerKey {
            requireNonNull(extensionData);
        }
    }
}
