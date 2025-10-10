package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;

@SuppressWarnings("DuplicatedCode")
public final class Count extends AbstractQuantity {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Count");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Count ? FHIR_TYPE : this;
        }
    };
    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCount");

    private static final Count EMPTY = new Count(ExtensionData.EMPTY, null, null, null, null, null);

    private static final Interner<InternerKey, Count> INTERNER = Interners.weakInterner(
            k -> new Count(k.extensionData(), k.value(), k.comparator(), k.unit(), k.system(), k.code())
    );

    private Count(ExtensionData extensionData, Decimal value, Code comparator, String unit, Uri system, Code code) {
        super(extensionData, value, comparator, unit, system, code);
    }

    private static Count maybeIntern(ExtensionData extensionData, Decimal value, Code comparator, String unit,
                                     Uri system, Code code) {
        return extensionData.isInterned() && Base.isInterned(value) && Base.isInterned(comparator) &&
                Base.isInterned(unit) && Base.isInterned(system) && Base.isInterned(code)
                ? INTERNER.intern(new InternerKey(extensionData, value, comparator, unit, system, code))
                : new Count(extensionData, value, comparator, unit, system, code);
    }

    public static Count create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Decimal) m.valAt(VALUE), (Code) m.valAt(COMPARATOR),
                (String) m.valAt(UNIT), (Uri) m.valAt(SYSTEM), (Code) m.valAt(CODE));
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE_LOOKUP_THUNK : super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        return super.valAt(key, notFound);
    }

    @Override
    public Count empty() {
        return EMPTY;
    }

    @Override
    public Count assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (Decimal) val, comparator, unit, system, code);
        if (key == COMPARATOR) return maybeIntern(extensionData, value, (Code) val, unit, system, code);
        if (key == UNIT) return maybeIntern(extensionData, value, comparator, (String) val, system, code);
        if (key == SYSTEM) return maybeIntern(extensionData, value, comparator, unit, (Uri) val, code);
        if (key == CODE) return maybeIntern(extensionData, value, comparator, unit, system, (Code) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension(val), value, comparator, unit, system, code);
        if (key == ID) return maybeIntern(extensionData.withId(val), value, comparator, unit, system, code);
        return this;
    }

    @Override
    public Count withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), value, comparator, unit, system, code);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }
}
