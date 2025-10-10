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
import static blaze.fhir.spec.type.Primitive.serializeJsonPrimitiveList;

@SuppressWarnings("DuplicatedCode")
public final class DataRequirement extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - type reference
     * 4 or 8 byte - profile reference
     * 4 or 8 byte - subject reference
     * 4 or 8 byte - mustSupport reference
     * 4 or 8 byte - codeFilter reference
     * 4 or 8 byte - dateFilter reference
     * 4 or 8 byte - limit reference
     * 4 or 8 byte - sort reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 9 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "DataRequirement");

    private static final Keyword TYPE = RT.keyword(null, "type");
    private static final Keyword PROFILE = RT.keyword(null, "profile");
    private static final Keyword SUBJECT = RT.keyword(null, "subject");
    private static final Keyword MUST_SUPPORT = RT.keyword(null, "mustSupport");
    private static final Keyword CODE_FILTER = RT.keyword(null, "codeFilter");
    private static final Keyword DATE_FILTER = RT.keyword(null, "dateFilter");
    private static final Keyword LIMIT = RT.keyword(null, "limit");
    private static final Keyword SORT = RT.keyword(null, "sort");

    private static final Keyword[] FIELDS = {ID, EXTENSION, TYPE, PROFILE, SUBJECT, MUST_SUPPORT, CODE_FILTER, DATE_FILTER, LIMIT, SORT};

    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_PROFILE = FieldName.of("profile");
    private static final FieldName FIELD_NAME_SUBJECT_CODEABLE_CONCEPT = FieldName.of("subjectCodeableConcept");
    private static final FieldName FIELD_NAME_SUBJECT_REFERENCE = FieldName.of("subjectReference");
    private static final FieldName FIELD_NAME_MUST_SUPPORT = FieldName.of("mustSupport");
    private static final SerializedString FIELD_NAME_CODE_FILTER = new SerializedString("codeFilter");
    private static final SerializedString FIELD_NAME_DATE_FILTER = new SerializedString("dateFilter");
    private static final FieldName FIELD_NAME_LIMIT = FieldName.of("limit");
    private static final FieldName FIELD_NAME_SORT = FieldName.of("sort");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueDataRequirement");

    private static final byte HASH_MARKER = 65;

    private static final DataRequirement EMPTY = new DataRequirement(ExtensionData.EMPTY, null, null, null, null, null, null, null, null);

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DataRequirement ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DataRequirement d ? d.type : this;
        }
    };

    private static final ILookupThunk PROFILE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DataRequirement d ? d.profile : this;
        }
    };

    private static final ILookupThunk MUST_SUPPORT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DataRequirement d ? d.mustSupport : this;
        }
    };

    private static final ILookupThunk CODE_FILTER_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DataRequirement d ? d.codeFilter : this;
        }
    };

    private static final ILookupThunk DATE_FILTER_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DataRequirement d ? d.dateFilter : this;
        }
    };

    private static final ILookupThunk LIMIT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DataRequirement d ? d.limit : this;
        }
    };

    private static final ILookupThunk SORT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof DataRequirement d ? d.sort : this;
        }
    };

    private final Code type;
    private final List<Canonical> profile;
    private final Element subject;
    private final List<String> mustSupport;
    private final List<CodeFilter> codeFilter;
    private final List<DateFilter> dateFilter;
    private final PositiveInt limit;
    private final List<Sort> sort;

    private DataRequirement(ExtensionData extensionData, Code type, List<Canonical> profile, Element subject,
                            List<String> mustSupport, List<CodeFilter> codeFilter, List<DateFilter> dateFilter,
                            PositiveInt limit, List<Sort> sort) {
        super(extensionData);
        this.type = type;
        this.profile = profile;
        this.subject = subject;
        this.mustSupport = mustSupport;
        this.codeFilter = codeFilter;
        this.dateFilter = dateFilter;
        this.limit = limit;
        this.sort = sort;
    }

    public static DataRequirement create(IPersistentMap m) {
        return new DataRequirement(ExtensionData.fromMap(m), (Code) m.valAt(TYPE), Base.listFrom(m, PROFILE),
                (Element) m.valAt(SUBJECT), Base.listFrom(m, MUST_SUPPORT), Base.listFrom(m, CODE_FILTER),
                Base.listFrom(m, DATE_FILTER), (PositiveInt) m.valAt(LIMIT), Base.listFrom(m, SORT));
    }

    public Code type() {
        return type;
    }

    public List<Canonical> profile() {
        return profile;
    }

    public Element subject() {
        return subject;
    }

    public List<String> mustSupport() {
        return mustSupport;
    }

    public List<CodeFilter> codeFilter() {
        return codeFilter;
    }

    public List<DateFilter> dateFilter() {
        return dateFilter;
    }

    public PositiveInt limit() {
        return limit;
    }

    public List<Sort> sort() {
        return sort;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == TYPE) return TYPE_LOOKUP_THUNK;
        if (key == PROFILE) return PROFILE_LOOKUP_THUNK;
        if (key == MUST_SUPPORT) return MUST_SUPPORT_LOOKUP_THUNK;
        if (key == CODE_FILTER) return CODE_FILTER_LOOKUP_THUNK;
        if (key == DATE_FILTER) return DATE_FILTER_LOOKUP_THUNK;
        if (key == LIMIT) return LIMIT_LOOKUP_THUNK;
        if (key == SORT) return SORT_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == TYPE) return type;
        if (key == PROFILE) return profile;
        if (key == SUBJECT) return subject;
        if (key == MUST_SUPPORT) return mustSupport;
        if (key == CODE_FILTER) return codeFilter;
        if (key == DATE_FILTER) return dateFilter;
        if (key == LIMIT) return limit;
        if (key == SORT) return sort;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, SORT, sort);
        seq = appendElement(seq, LIMIT, limit);
        seq = appendElement(seq, DATE_FILTER, dateFilter);
        seq = appendElement(seq, CODE_FILTER, codeFilter);
        seq = appendElement(seq, MUST_SUPPORT, mustSupport);
        seq = appendElement(seq, SUBJECT, subject);
        seq = appendElement(seq, PROFILE, profile);
        seq = appendElement(seq, TYPE, type);
        return extensionData.append(seq);
    }

    @Override
    public DataRequirement empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public DataRequirement assoc(Object key, Object val) {
        if (key == TYPE)
            return new DataRequirement(extensionData, (Code) val, profile, subject, mustSupport, codeFilter, dateFilter, limit, sort);
        if (key == PROFILE)
            return new DataRequirement(extensionData, type, (List<Canonical>) val, subject, mustSupport, codeFilter, dateFilter, limit, sort);
        if (key == SUBJECT)
            return new DataRequirement(extensionData, type, profile, (Element) val, mustSupport, codeFilter, dateFilter, limit, sort);
        if (key == MUST_SUPPORT)
            return new DataRequirement(extensionData, type, profile, subject, (List<String>) val, codeFilter, dateFilter, limit, sort);
        if (key == CODE_FILTER)
            return new DataRequirement(extensionData, type, profile, subject, mustSupport, (List<CodeFilter>) val, dateFilter, limit, sort);
        if (key == DATE_FILTER)
            return new DataRequirement(extensionData, type, profile, subject, mustSupport, codeFilter, (List<DateFilter>) val, limit, sort);
        if (key == LIMIT)
            return new DataRequirement(extensionData, type, profile, subject, mustSupport, codeFilter, dateFilter, (PositiveInt) val, sort);
        if (key == SORT)
            return new DataRequirement(extensionData, type, profile, subject, mustSupport, codeFilter, dateFilter, limit, (List<Sort>) val);
        if (key == EXTENSION)
            return new DataRequirement(extensionData.withExtension(val), type, profile, subject, mustSupport, codeFilter, dateFilter, limit, sort);
        if (key == ID)
            return new DataRequirement(extensionData.withId(val), type, profile, subject, mustSupport, codeFilter, dateFilter, limit, sort);
        return this;
    }

    @Override
    public DataRequirement withMeta(IPersistentMap meta) {
        return new DataRequirement(extensionData.withMeta(meta), type, profile, subject, mustSupport, codeFilter, dateFilter, limit, sort);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (type != null) {
            type.serializeAsJsonProperty(generator, FIELD_NAME_TYPE);
        }
        if (profile != null && !profile.isEmpty()) {
            serializeJsonPrimitiveList(profile, generator, FIELD_NAME_PROFILE);
        }
        if (subject != null) {
            switch (subject) {
                case CodeableConcept subjectCodeableConcept ->
                        subjectCodeableConcept.serializeJsonField(generator, FIELD_NAME_SUBJECT_CODEABLE_CONCEPT);
                case Reference subjectReference ->
                        subjectReference.serializeJsonField(generator, FIELD_NAME_SUBJECT_REFERENCE);
                default -> {
                }
            }
        }
        if (mustSupport != null && !mustSupport.isEmpty()) {
            serializeJsonPrimitiveList(mustSupport, generator, FIELD_NAME_MUST_SUPPORT);
        }
        if (codeFilter != null && !codeFilter.isEmpty()) {
            serializeJsonComplexList(codeFilter, generator, FIELD_NAME_CODE_FILTER);
        }
        if (dateFilter != null && !dateFilter.isEmpty()) {
            serializeJsonComplexList(dateFilter, generator, FIELD_NAME_DATE_FILTER);
        }
        if (limit != null) {
            limit.serializeAsJsonProperty(generator, FIELD_NAME_LIMIT);
        }
        if (sort != null && !sort.isEmpty()) {
            serializeJsonComplexList(sort, generator, FIELD_NAME_SORT.normal());
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
        if (profile != null) {
            sink.putByte((byte) 3);
            Base.hashIntoList(profile, sink);
        }
        if (subject != null) {
            sink.putByte((byte) 4);
            subject.hashInto(sink);
        }
        if (mustSupport != null) {
            sink.putByte((byte) 5);
            Base.hashIntoList(mustSupport, sink);
        }
        if (codeFilter != null) {
            sink.putByte((byte) 6);
            Base.hashIntoList(codeFilter, sink);
        }
        if (dateFilter != null) {
            sink.putByte((byte) 7);
            Base.hashIntoList(dateFilter, sink);
        }
        if (limit != null) {
            sink.putByte((byte) 8);
            limit.hashInto(sink);
        }
        if (sort != null) {
            sink.putByte((byte) 9);
            Base.hashIntoList(sort, sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(type) + Base.memSize(profile) +
                Base.memSize(subject) + Base.memSize(mustSupport) + Base.memSize(codeFilter) +
                Base.memSize(dateFilter) + Base.memSize(limit) + Base.memSize(sort);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DataRequirement that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(type, that.type) &&
                Objects.equals(profile, that.profile) &&
                Objects.equals(subject, that.subject) &&
                Objects.equals(mustSupport, that.mustSupport) &&
                Objects.equals(codeFilter, that.codeFilter) &&
                Objects.equals(dateFilter, that.dateFilter) &&
                Objects.equals(limit, that.limit) &&
                Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(profile);
        result = 31 * result + Objects.hashCode(subject);
        result = 31 * result + Objects.hashCode(mustSupport);
        result = 31 * result + Objects.hashCode(codeFilter);
        result = 31 * result + Objects.hashCode(dateFilter);
        result = 31 * result + Objects.hashCode(limit);
        result = 31 * result + Objects.hashCode(sort);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "DataRequirement{" +
                extensionData +
                ", type=" + type +
                ", profile=" + profile +
                ", subject=" + subject +
                ", mustSupport=" + mustSupport +
                ", codeFilter=" + codeFilter +
                ", dateFilter=" + dateFilter +
                ", limit=" + limit +
                ", sort=" + sort +
                '}';
    }

    public static final class CodeFilter extends AbstractElement implements Complex {

        /**
         * Memory size.
         * <p>
         * 8 byte - object header
         * 4 or 8 byte - extension data reference
         * 4 or 8 byte - path reference
         * 4 or 8 byte - searchParam reference
         * 4 or 8 byte - valueSet reference
         * 4 or 8 byte - code reference
         */
        private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 5 * MEM_SIZE_REFERENCE + 7) & ~7;

        private static final Keyword FHIR_TYPE = RT.keyword("fhir.DataRequirement", "codeFilter");

        private static final Keyword PATH = RT.keyword(null, "path");
        private static final Keyword SEARCH_PARAM = RT.keyword(null, "searchParam");
        private static final Keyword VALUE_SET = RT.keyword(null, "valueSet");
        private static final Keyword CODE = RT.keyword(null, "code");

        private static final Keyword[] FIELDS = {ID, EXTENSION, PATH, SEARCH_PARAM, VALUE_SET, CODE};

        private static final FieldName FIELD_NAME_PATH = FieldName.of("path");
        private static final FieldName FIELD_NAME_SEARCH_PARAM = FieldName.of("searchParam");
        private static final FieldName FIELD_NAME_VALUE_SET = FieldName.of("valueSet");
        private static final SerializedString FIELD_NAME_CODE = new SerializedString("code");

        private static final byte HASH_MARKER = 62;

        private static final CodeFilter EMPTY = new CodeFilter(ExtensionData.EMPTY, null, null, null, null);

        private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof CodeFilter ? FHIR_TYPE : this;
            }
        };

        private static final ILookupThunk PATH_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof CodeFilter f ? f.path : this;
            }
        };

        private static final ILookupThunk SEARCH_PARAM_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof CodeFilter f ? f.searchParam : this;
            }
        };

        private static final ILookupThunk VALUE_SET_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof CodeFilter f ? f.valueSet : this;
            }
        };

        private static final ILookupThunk CODE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof CodeFilter f ? f.code : this;
            }
        };

        private final String path;
        private final String searchParam;
        private final Canonical valueSet;
        private final List<Coding> code;

        private CodeFilter(ExtensionData extensionData, String path, String searchParam, Canonical valueSet, List<Coding> code) {
            super(extensionData);
            this.path = path;
            this.searchParam = searchParam;
            this.valueSet = valueSet;
            this.code = code;
        }

        public static CodeFilter create(IPersistentMap m) {
            return new CodeFilter(ExtensionData.fromMap(m), (String) m.valAt(PATH), (String) m.valAt(SEARCH_PARAM),
                    (Canonical) m.valAt(VALUE_SET), Base.listFrom(m, CODE));
        }

        public String path() {
            return path;
        }

        public String searchParam() {
            return searchParam;
        }

        public Canonical valueSet() {
            return valueSet;
        }

        public List<Coding> code() {
            return code;
        }

        @Override
        public ILookupThunk getLookupThunk(Keyword key) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
            if (key == PATH) return PATH_LOOKUP_THUNK;
            if (key == SEARCH_PARAM) return SEARCH_PARAM_LOOKUP_THUNK;
            if (key == VALUE_SET) return VALUE_SET_LOOKUP_THUNK;
            if (key == CODE) return CODE_LOOKUP_THUNK;
            return super.getLookupThunk(key);
        }

        @Override
        public Object valAt(Object key, Object notFound) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
            if (key == PATH) return path;
            if (key == SEARCH_PARAM) return searchParam;
            if (key == VALUE_SET) return valueSet;
            if (key == CODE) return code;
            return super.valAt(key, notFound);
        }

        @Override
        public ISeq seq() {
            ISeq seq = PersistentList.EMPTY;
            seq = appendElement(seq, CODE, code);
            seq = appendElement(seq, VALUE_SET, valueSet);
            seq = appendElement(seq, SEARCH_PARAM, searchParam);
            seq = appendElement(seq, PATH, path);
            return extensionData.append(seq);
        }

        @Override
        public CodeFilter empty() {
            return EMPTY;
        }

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new BaseIterator(this, FIELDS);
        }

        @Override
        public CodeFilter assoc(Object key, Object val) {
            if (key == PATH)
                return new CodeFilter(extensionData, (String) val, searchParam, valueSet, code);
            if (key == SEARCH_PARAM)
                return new CodeFilter(extensionData, path, (String) val, valueSet, code);
            if (key == VALUE_SET)
                return new CodeFilter(extensionData, path, searchParam, (Canonical) val, code);
            if (key == CODE)
                return new CodeFilter(extensionData, path, searchParam, valueSet, Lists.nullToEmpty(val));
            if (key == EXTENSION)
                return new CodeFilter(extensionData.withExtension(val), path, searchParam, valueSet, code);
            if (key == ID)
                return new CodeFilter(extensionData.withId(val), path, searchParam, valueSet, code);
            return this;
        }

        @Override
        public CodeFilter withMeta(IPersistentMap meta) {
            return new CodeFilter(extensionData.withMeta(meta), path, searchParam, valueSet, code);
        }

        @Override
        public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
            generator.writeStartObject();
            serializeJsonBase(generator);
            if (path != null) {
                path.serializeAsJsonProperty(generator, FIELD_NAME_PATH);
            }
            if (searchParam != null) {
                searchParam.serializeAsJsonProperty(generator, FIELD_NAME_SEARCH_PARAM);
            }
            if (valueSet != null) {
                valueSet.serializeAsJsonProperty(generator, FIELD_NAME_VALUE_SET);
            }
            if (code != null && !code.isEmpty()) {
                serializeJsonComplexList(code, generator, FIELD_NAME_CODE);
            }
            generator.writeEndObject();
        }

        @Override
        @SuppressWarnings("UnstableApiUsage")
        public void hashInto(PrimitiveSink sink) {
            sink.putByte(HASH_MARKER);
            extensionData.hashInto(sink);
            if (path != null) {
                sink.putByte((byte) 2);
                path.hashInto(sink);
            }
            if (searchParam != null) {
                sink.putByte((byte) 3);
                searchParam.hashInto(sink);
            }
            if (valueSet != null) {
                sink.putByte((byte) 4);
                valueSet.hashInto(sink);
            }
            if (code != null) {
                sink.putByte((byte) 5);
                Base.hashIntoList(code, sink);
            }
        }

        @Override
        public int memSize() {
            return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(path) + Base.memSize(searchParam) +
                    Base.memSize(valueSet) + Base.memSize(code);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof CodeFilter that &&
                    extensionData.equals(that.extensionData) &&
                    Objects.equals(path, that.path) &&
                    Objects.equals(searchParam, that.searchParam) &&
                    Objects.equals(valueSet, that.valueSet) &&
                    Objects.equals(code, that.code);
        }

        @Override
        public int hashCode() {
            int result = extensionData.hashCode();
            result = 31 * result + Objects.hashCode(path);
            result = 31 * result + Objects.hashCode(searchParam);
            result = 31 * result + Objects.hashCode(valueSet);
            result = 31 * result + Objects.hashCode(code);
            return result;
        }

        @Override
        public java.lang.String toString() {
            return "DataRequirement.CodeFilter{" +
                    extensionData +
                    ", path=" + path +
                    ", searchParam=" + searchParam +
                    ", valueSet=" + valueSet +
                    ", code=" + code +
                    '}';
        }
    }

    public static final class DateFilter extends AbstractElement implements Complex {

        /**
         * Memory size.
         * <p>
         * 8 byte - object header
         * 4 or 8 byte - extension data reference
         * 4 or 8 byte - path reference
         * 4 or 8 byte - searchParam reference
         * 4 or 8 byte - value reference
         */
        private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 4 * MEM_SIZE_REFERENCE;

        private static final Keyword FHIR_TYPE = RT.keyword("fhir.DataRequirement", "dateFilter");

        private static final Keyword PATH = RT.keyword(null, "path");
        private static final Keyword SEARCH_PARAM = RT.keyword(null, "searchParam");
        private static final Keyword VALUE = RT.keyword(null, "value");

        private static final Keyword[] FIELDS = {ID, EXTENSION, PATH, SEARCH_PARAM, VALUE};

        private static final FieldName FIELD_NAME_PATH = FieldName.of("path");
        private static final FieldName FIELD_NAME_SEARCH_PARAM = FieldName.of("searchParam");

        private static final byte HASH_MARKER = 63;

        private static final DateFilter EMPTY = new DateFilter(ExtensionData.EMPTY, null, null, null);

        private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof DateFilter ? FHIR_TYPE : this;
            }
        };

        private static final ILookupThunk PATH_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof DateFilter f ? f.path : this;
            }
        };

        private static final ILookupThunk SEARCH_PARAM_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof DateFilter f ? f.searchParam : this;
            }
        };

        private final String path;
        private final String searchParam;
        private final ExtensionValue value;

        private DateFilter(ExtensionData extensionData, String path, String searchParam, ExtensionValue value) {
            super(extensionData);
            this.path = path;
            this.searchParam = searchParam;
            this.value = value;
        }

        public static DateFilter create(IPersistentMap m) {
            return new DateFilter(ExtensionData.fromMap(m), (String) m.valAt(PATH), (String) m.valAt(SEARCH_PARAM),
                    (ExtensionValue) m.valAt(VALUE));
        }

        public String path() {
            return path;
        }

        public String searchParam() {
            return searchParam;
        }

        public ExtensionValue value() {
            return value;
        }

        @Override
        public ILookupThunk getLookupThunk(Keyword key) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
            if (key == PATH) return PATH_LOOKUP_THUNK;
            if (key == SEARCH_PARAM) return SEARCH_PARAM_LOOKUP_THUNK;
            return super.getLookupThunk(key);
        }

        @Override
        public Object valAt(Object key, Object notFound) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
            if (key == PATH) return path;
            if (key == SEARCH_PARAM) return searchParam;
            if (key == VALUE) return value;
            return super.valAt(key, notFound);
        }

        @Override
        public ISeq seq() {
            ISeq seq = PersistentList.EMPTY;
            seq = appendElement(seq, VALUE, value);
            seq = appendElement(seq, SEARCH_PARAM, searchParam);
            seq = appendElement(seq, PATH, path);
            return extensionData.append(seq);
        }

        @Override
        public DateFilter empty() {
            return EMPTY;
        }

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new BaseIterator(this, FIELDS);
        }

        @Override
        public DateFilter assoc(Object key, Object val) {
            if (key == PATH)
                return new DateFilter(extensionData, (String) val, searchParam, value);
            if (key == SEARCH_PARAM)
                return new DateFilter(extensionData, path, (String) val, value);
            if (key == VALUE)
                return new DateFilter(extensionData, path, searchParam, (ExtensionValue) val);
            if (key == EXTENSION)
                return new DateFilter(extensionData.withExtension(val), path, searchParam, value);
            if (key == ID)
                return new DateFilter(extensionData.withId(val), path, searchParam, value);
            return this;
        }

        @Override
        public DateFilter withMeta(IPersistentMap meta) {
            return new DateFilter(extensionData.withMeta(meta), path, searchParam, value);
        }

        @Override
        public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
            generator.writeStartObject();
            serializeJsonBase(generator);
            if (path != null) {
                path.serializeAsJsonProperty(generator, FIELD_NAME_PATH);
            }
            if (searchParam != null) {
                searchParam.serializeAsJsonProperty(generator, FIELD_NAME_SEARCH_PARAM);
            }
            if (value != null) {
                value.serializeJsonField(generator, value.fieldNameExtensionValue());
            }
            generator.writeEndObject();
        }

        @Override
        @SuppressWarnings("UnstableApiUsage")
        public void hashInto(PrimitiveSink sink) {
            sink.putByte(HASH_MARKER);
            extensionData.hashInto(sink);
            if (path != null) {
                sink.putByte((byte) 2);
                path.hashInto(sink);
            }
            if (searchParam != null) {
                sink.putByte((byte) 3);
                searchParam.hashInto(sink);
            }
            if (value != null) {
                sink.putByte((byte) 4);
                value.hashInto(sink);
            }
        }

        @Override
        public int memSize() {
            return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(path) + Base.memSize(searchParam) +
                    Base.memSize(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof DateFilter that &&
                    extensionData.equals(that.extensionData) &&
                    Objects.equals(path, that.path) &&
                    Objects.equals(searchParam, that.searchParam) &&
                    Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            int result = extensionData.hashCode();
            result = 31 * result + Objects.hashCode(path);
            result = 31 * result + Objects.hashCode(searchParam);
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        @Override
        public java.lang.String toString() {
            return "DataRequirement.DateFilter{" +
                    extensionData +
                    ", path=" + path +
                    ", searchParam=" + searchParam +
                    ", value=" + value +
                    '}';
        }
    }

    public static final class Sort extends AbstractElement implements Complex {

        /**
         * Memory size.
         * <p>
         * 8 byte - object header
         * 4 or 8 byte - extension data reference
         * 4 or 8 byte - path reference
         * 4 or 8 byte - direction reference
         */
        private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

        private static final Keyword FHIR_TYPE = RT.keyword("fhir.DataRequirement", "sort");

        private static final Keyword PATH = RT.keyword(null, "path");
        private static final Keyword DIRECTION = RT.keyword(null, "direction");

        private static final Keyword[] FIELDS = {ID, EXTENSION, PATH, DIRECTION};

        private static final FieldName FIELD_NAME_PATH = FieldName.of("path");
        private static final FieldName FIELD_NAME_DIRECTION = FieldName.of("direction");

        private static final byte HASH_MARKER = 64;

        private static final Sort EMPTY = new Sort(ExtensionData.EMPTY, null, null);

        private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Sort ? FHIR_TYPE : this;
            }
        };

        private static final ILookupThunk PATH_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Sort s ? s.path : this;
            }
        };

        private static final ILookupThunk DIRECTION_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Sort s ? s.direction : this;
            }
        };

        private final String path;
        private final Code direction;

        private Sort(ExtensionData extensionData, String path, Code direction) {
            super(extensionData);
            this.path = path;
            this.direction = direction;
        }

        public static Sort create(IPersistentMap m) {
            return new Sort(ExtensionData.fromMap(m), (String) m.valAt(PATH), (Code) m.valAt(DIRECTION));
        }

        public String path() {
            return path;
        }

        public Code direction() {
            return direction;
        }

        @Override
        public ILookupThunk getLookupThunk(Keyword key) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
            if (key == PATH) return PATH_LOOKUP_THUNK;
            if (key == DIRECTION) return DIRECTION_LOOKUP_THUNK;
            return super.getLookupThunk(key);
        }

        @Override
        public Object valAt(Object key, Object notFound) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
            if (key == PATH) return path;
            if (key == DIRECTION) return direction;
            return super.valAt(key, notFound);
        }

        @Override
        public ISeq seq() {
            ISeq seq = PersistentList.EMPTY;
            seq = appendElement(seq, DIRECTION, direction);
            seq = appendElement(seq, PATH, path);
            return extensionData.append(seq);
        }

        @Override
        public Sort empty() {
            return EMPTY;
        }

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new BaseIterator(this, FIELDS);
        }

        @Override
        public Sort assoc(Object key, Object val) {
            if (key == PATH) return new Sort(extensionData, (String) val, direction);
            if (key == DIRECTION) return new Sort(extensionData, path, (Code) val);
            if (key == EXTENSION) return new Sort(extensionData.withExtension(val), path, direction);
            if (key == ID) return new Sort(extensionData.withId(val), path, direction);
            return this;
        }

        @Override
        public Sort withMeta(IPersistentMap meta) {
            return new Sort(extensionData.withMeta(meta), path, direction);
        }

        @Override
        public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
            generator.writeStartObject();
            serializeJsonBase(generator);
            if (path != null) {
                path.serializeAsJsonProperty(generator, FIELD_NAME_PATH);
            }
            if (direction != null) {
                direction.serializeAsJsonProperty(generator, FIELD_NAME_DIRECTION);
            }
            generator.writeEndObject();
        }

        @Override
        @SuppressWarnings("UnstableApiUsage")
        public void hashInto(PrimitiveSink sink) {
            sink.putByte(HASH_MARKER);
            extensionData.hashInto(sink);
            if (path != null) {
                sink.putByte((byte) 2);
                path.hashInto(sink);
            }
            if (direction != null) {
                sink.putByte((byte) 3);
                direction.hashInto(sink);
            }
        }

        @Override
        public int memSize() {
            return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(path) + Base.memSize(direction);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Sort that &&
                    extensionData.equals(that.extensionData) &&
                    Objects.equals(path, that.path) &&
                    Objects.equals(direction, that.direction);
        }

        @Override
        public int hashCode() {
            int result = extensionData.hashCode();
            result = 31 * result + Objects.hashCode(path);
            result = 31 * result + Objects.hashCode(direction);
            return result;
        }

        @Override
        public java.lang.String toString() {
            return "DataRequirement.Sort{" +
                    extensionData +
                    ", path=" + path +
                    ", direction=" + direction +
                    '}';
        }
    }
}
