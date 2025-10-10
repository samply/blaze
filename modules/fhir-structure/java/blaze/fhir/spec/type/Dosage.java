package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("DuplicatedCode")
public final class Dosage extends AbstractBackboneElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - modifierExtension reference
     * 4 or 8 byte - sequence reference
     * 4 or 8 byte - text reference
     * 4 or 8 byte - additionalInstruction reference
     * 4 or 8 byte - patientInstruction reference
     * 4 or 8 byte - timing reference
     * 4 or 8 byte - asNeeded reference
     * 4 or 8 byte - site reference
     * 4 or 8 byte - route reference
     * 4 or 8 byte - method reference
     * 4 or 8 byte - doseAndRate reference
     * 4 or 8 byte - maxDosePerPeriod reference
     * 4 or 8 byte - maxDosePerAdministration reference
     * 4 or 8 byte - maxDosePerLifetime reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 15 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Dosage");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk SEQUENCE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.sequence : this;
        }
    };

    private static final ILookupThunk TEXT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.text : this;
        }
    };

    private static final ILookupThunk ADDITIONAL_INSTRUCTION_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.additionalInstruction : this;
        }
    };

    private static final ILookupThunk PATIENT_INSTRUCTION_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.patientInstruction : this;
        }
    };

    private static final ILookupThunk TIMING_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.timing : this;
        }
    };

    private static final ILookupThunk AS_NEEDED_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.asNeeded : this;
        }
    };

    private static final ILookupThunk SITE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.site : this;
        }
    };

    private static final ILookupThunk ROUTE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.route : this;
        }
    };

    private static final ILookupThunk METHOD_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.method : this;
        }
    };

    private static final ILookupThunk DOSE_AND_RATE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.doseAndRate : this;
        }
    };

    private static final ILookupThunk MAX_DOSE_PER_PERIOD_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.maxDosePerPeriod : this;
        }
    };

    private static final ILookupThunk MAX_DOSE_PER_ADMINISTRATION_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.maxDosePerAdministration : this;
        }
    };

    private static final ILookupThunk MAX_DOSE_PER_LIFETIME_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Dosage d ? d.maxDosePerLifetime : this;
        }
    };

    private static final Keyword SEQUENCE = RT.keyword(null, "sequence");
    private static final Keyword TEXT = RT.keyword(null, "text");
    private static final Keyword ADDITIONAL_INSTRUCTION = RT.keyword(null, "additionalInstruction");
    private static final Keyword PATIENT_INSTRUCTION = RT.keyword(null, "patientInstruction");
    private static final Keyword TIMING = RT.keyword(null, "timing");
    private static final Keyword AS_NEEDED = RT.keyword(null, "asNeeded");
    private static final Keyword SITE = RT.keyword(null, "site");
    private static final Keyword ROUTE = RT.keyword(null, "route");
    private static final Keyword METHOD = RT.keyword(null, "method");
    private static final Keyword DOSE_AND_RATE = RT.keyword(null, "doseAndRate");
    private static final Keyword MAX_DOSE_PER_PERIOD = RT.keyword(null, "maxDosePerPeriod");
    private static final Keyword MAX_DOSE_PER_ADMINISTRATION = RT.keyword(null, "maxDosePerAdministration");
    private static final Keyword MAX_DOSE_PER_LIFETIME = RT.keyword(null, "maxDosePerLifetime");

    private static final Keyword[] FIELDS = {ID, EXTENSION, MODIFIER_EXTENSION, SEQUENCE, TEXT, ADDITIONAL_INSTRUCTION,
            PATIENT_INSTRUCTION, TIMING, AS_NEEDED, SITE, ROUTE, METHOD, DOSE_AND_RATE, MAX_DOSE_PER_PERIOD,
            MAX_DOSE_PER_ADMINISTRATION, MAX_DOSE_PER_LIFETIME};

    private static final FieldName FIELD_NAME_SEQUENCE = FieldName.of("sequence");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");
    private static final FieldName FIELD_NAME_ADDITIONAL_INSTRUCTION = FieldName.of("additionalInstruction");
    private static final FieldName FIELD_NAME_PATIENT_INSTRUCTION = FieldName.of("patientInstruction");
    private static final FieldName FIELD_NAME_TIMING = FieldName.of("timing");
    private static final FieldName FIELD_NAME_AS_NEEDED_BOOLEAN = FieldName.of("asNeededBoolean");
    private static final FieldName FIELD_NAME_AS_NEEDED_CODEABLE_CONCEPT = FieldName.of("asNeededCodeableConcept");
    private static final FieldName FIELD_NAME_SITE = FieldName.of("site");
    private static final FieldName FIELD_NAME_ROUTE = FieldName.of("route");
    private static final FieldName FIELD_NAME_METHOD = FieldName.of("method");
    private static final FieldName FIELD_NAME_DOSE_AND_RATE = FieldName.of("doseAndRate");
    private static final FieldName FIELD_NAME_MAX_DOSE_PER_PERIOD = FieldName.of("maxDosePerPeriod");
    private static final FieldName FIELD_NAME_MAX_DOSE_PER_ADMINISTRATION = FieldName.of("maxDosePerAdministration");
    private static final FieldName FIELD_NAME_MAX_DOSE_PER_LIFETIME = FieldName.of("maxDosePerLifetime");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueDosage");

    private static final byte HASH_MARKER = 72;

    @SuppressWarnings("unchecked")
    private static final Dosage EMPTY = new Dosage(ExtensionData.EMPTY, PersistentVector.EMPTY, null, null,
            PersistentVector.EMPTY, null, null, null, null, null, null, PersistentVector.EMPTY, null, null, null);

    private final Integer sequence;
    private final String text;
    private final List<CodeableConcept> additionalInstruction;
    private final String patientInstruction;
    private final Timing timing;
    private final Element asNeeded;
    private final CodeableConcept site;
    private final CodeableConcept route;
    private final CodeableConcept method;
    private final List<DoseAndRate> doseAndRate;
    private final Ratio maxDosePerPeriod;
    private final Quantity maxDosePerAdministration;
    private final Quantity maxDosePerLifetime;

    private Dosage(ExtensionData extensionData, List<Extension> modifierExtension, Integer sequence, String text,
                   List<CodeableConcept> additionalInstruction, String patientInstruction, Timing timing,
                   Element asNeeded, CodeableConcept site, CodeableConcept route, CodeableConcept method,
                   List<DoseAndRate> doseAndRate, Ratio maxDosePerPeriod, Quantity maxDosePerAdministration,
                   Quantity maxDosePerLifetime) {
        super(extensionData, modifierExtension);
        this.sequence = sequence;
        this.text = text;
        this.additionalInstruction = requireNonNull(additionalInstruction);
        this.patientInstruction = patientInstruction;
        this.timing = timing;
        this.asNeeded = asNeeded;
        this.site = site;
        this.route = route;
        this.method = method;
        this.doseAndRate = requireNonNull(doseAndRate);
        this.maxDosePerPeriod = maxDosePerPeriod;
        this.maxDosePerAdministration = maxDosePerAdministration;
        this.maxDosePerLifetime = maxDosePerLifetime;
    }

    public static Dosage create(IPersistentMap m) {
        return new Dosage(ExtensionData.fromMap(m), Base.listFrom(m, MODIFIER_EXTENSION), (Integer) m.valAt(SEQUENCE),
                (String) m.valAt(TEXT), Base.listFrom(m, ADDITIONAL_INSTRUCTION), (String) m.valAt(PATIENT_INSTRUCTION),
                (Timing) m.valAt(TIMING), (Element) m.valAt(AS_NEEDED), (CodeableConcept) m.valAt(SITE),
                (CodeableConcept) m.valAt(ROUTE), (CodeableConcept) m.valAt(METHOD), Base.listFrom(m, DOSE_AND_RATE),
                (Ratio) m.valAt(MAX_DOSE_PER_PERIOD), (Quantity) m.valAt(MAX_DOSE_PER_ADMINISTRATION),
                (Quantity) m.valAt(MAX_DOSE_PER_LIFETIME));
    }

    public Integer sequence() {
        return sequence;
    }

    public String text() {
        return text;
    }

    public List<CodeableConcept> additionalInstruction() {
        return additionalInstruction;
    }

    public String patientInstruction() {
        return patientInstruction;
    }

    public Timing timing() {
        return timing;
    }

    public Base asNeeded() {
        return asNeeded;
    }

    public CodeableConcept site() {
        return site;
    }

    public CodeableConcept route() {
        return route;
    }

    public CodeableConcept method() {
        return method;
    }

    public List<DoseAndRate> doseAndRate() {
        return doseAndRate;
    }

    public Ratio maxDosePerPeriod() {
        return maxDosePerPeriod;
    }

    public Quantity maxDosePerAdministration() {
        return maxDosePerAdministration;
    }

    public Quantity maxDosePerLifetime() {
        return maxDosePerLifetime;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == SEQUENCE) return SEQUENCE_LOOKUP_THUNK;
        if (key == TEXT) return TEXT_LOOKUP_THUNK;
        if (key == ADDITIONAL_INSTRUCTION) return ADDITIONAL_INSTRUCTION_LOOKUP_THUNK;
        if (key == PATIENT_INSTRUCTION) return PATIENT_INSTRUCTION_LOOKUP_THUNK;
        if (key == TIMING) return TIMING_LOOKUP_THUNK;
        if (key == AS_NEEDED) return AS_NEEDED_LOOKUP_THUNK;
        if (key == SITE) return SITE_LOOKUP_THUNK;
        if (key == ROUTE) return ROUTE_LOOKUP_THUNK;
        if (key == METHOD) return METHOD_LOOKUP_THUNK;
        if (key == DOSE_AND_RATE) return DOSE_AND_RATE_LOOKUP_THUNK;
        if (key == MAX_DOSE_PER_PERIOD) return MAX_DOSE_PER_PERIOD_LOOKUP_THUNK;
        if (key == MAX_DOSE_PER_ADMINISTRATION) return MAX_DOSE_PER_ADMINISTRATION_LOOKUP_THUNK;
        if (key == MAX_DOSE_PER_LIFETIME) return MAX_DOSE_PER_LIFETIME_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == SEQUENCE) return sequence;
        if (key == TEXT) return text;
        if (key == ADDITIONAL_INSTRUCTION) return additionalInstruction;
        if (key == PATIENT_INSTRUCTION) return patientInstruction;
        if (key == TIMING) return timing;
        if (key == AS_NEEDED) return asNeeded;
        if (key == SITE) return site;
        if (key == ROUTE) return route;
        if (key == METHOD) return method;
        if (key == DOSE_AND_RATE) return doseAndRate;
        if (key == MAX_DOSE_PER_PERIOD) return maxDosePerPeriod;
        if (key == MAX_DOSE_PER_ADMINISTRATION) return maxDosePerAdministration;
        if (key == MAX_DOSE_PER_LIFETIME) return maxDosePerLifetime;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, MAX_DOSE_PER_LIFETIME, maxDosePerLifetime);
        seq = appendElement(seq, MAX_DOSE_PER_ADMINISTRATION, maxDosePerAdministration);
        seq = appendElement(seq, MAX_DOSE_PER_PERIOD, maxDosePerPeriod);
        if (!doseAndRate.isEmpty()) {
            seq = appendElement(seq, DOSE_AND_RATE, doseAndRate);
        }
        seq = appendElement(seq, METHOD, method);
        seq = appendElement(seq, ROUTE, route);
        seq = appendElement(seq, SITE, site);
        seq = appendElement(seq, AS_NEEDED, asNeeded);
        seq = appendElement(seq, TIMING, timing);
        seq = appendElement(seq, PATIENT_INSTRUCTION, patientInstruction);
        if (!additionalInstruction.isEmpty()) {
            seq = appendElement(seq, ADDITIONAL_INSTRUCTION, additionalInstruction);
        }
        seq = appendElement(seq, TEXT, text);
        seq = appendElement(seq, SEQUENCE, sequence);
        seq = appendElement(seq, MODIFIER_EXTENSION, modifierExtension);
        return extensionData.append(seq);
    }

    @Override
    public Dosage empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Dosage assoc(Object key, Object val) {
        if (key == SEQUENCE)
            return new Dosage(extensionData, modifierExtension, (Integer) val, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == TEXT)
            return new Dosage(extensionData, modifierExtension, sequence, (String) val, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == ADDITIONAL_INSTRUCTION)
            return new Dosage(extensionData, modifierExtension, sequence, text, Lists.nullToEmpty(val), patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == PATIENT_INSTRUCTION)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, (String) val, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == TIMING)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, (Timing) val, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == AS_NEEDED)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, (Element) val, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == SITE)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, (CodeableConcept) val, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == ROUTE)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, (CodeableConcept) val, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == METHOD)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, (CodeableConcept) val, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == DOSE_AND_RATE)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, Lists.nullToEmpty(val), maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == MAX_DOSE_PER_PERIOD)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, (Ratio) val, maxDosePerAdministration, maxDosePerLifetime);
        if (key == MAX_DOSE_PER_ADMINISTRATION)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, (Quantity) val, maxDosePerLifetime);
        if (key == MAX_DOSE_PER_LIFETIME)
            return new Dosage(extensionData, modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, (Quantity) val);
        if (key == MODIFIER_EXTENSION)
            return new Dosage(extensionData, Lists.nullToEmpty(val), sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == EXTENSION)
            return new Dosage(extensionData.withExtension(val), modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        if (key == ID)
            return new Dosage(extensionData.withId(val), modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
        return this;
    }

    @Override
    public Dosage withMeta(IPersistentMap meta) {
        return new Dosage(extensionData.withMeta(meta), modifierExtension, sequence, text, additionalInstruction, patientInstruction, timing, asNeeded, site, route, method, doseAndRate, maxDosePerPeriod, maxDosePerAdministration, maxDosePerLifetime);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (sequence != null) {
            sequence.serializeAsJsonProperty(generator, FIELD_NAME_SEQUENCE);
        }
        if (text != null) {
            text.serializeAsJsonProperty(generator, FIELD_NAME_TEXT);
        }
        if (!additionalInstruction.isEmpty()) {
            serializeJsonComplexList(additionalInstruction, generator, FIELD_NAME_ADDITIONAL_INSTRUCTION.normal());
        }
        if (patientInstruction != null) {
            patientInstruction.serializeAsJsonProperty(generator, FIELD_NAME_PATIENT_INSTRUCTION);
        }
        if (timing != null) {
            timing.serializeJsonField(generator, FIELD_NAME_TIMING);
        }
        if (asNeeded != null) {
            switch (asNeeded) {
                case Boolean asNeededBoolean ->
                        asNeededBoolean.serializeAsJsonProperty(generator, FIELD_NAME_AS_NEEDED_BOOLEAN);
                case CodeableConcept asNeededCodeableConcept ->
                        asNeededCodeableConcept.serializeJsonField(generator, FIELD_NAME_AS_NEEDED_CODEABLE_CONCEPT);
                default -> {
                }
            }
        }
        if (site != null) {
            site.serializeJsonField(generator, FIELD_NAME_SITE);
        }
        if (route != null) {
            route.serializeJsonField(generator, FIELD_NAME_ROUTE);
        }
        if (method != null) {
            method.serializeJsonField(generator, FIELD_NAME_METHOD);
        }
        if (!doseAndRate.isEmpty()) {
            serializeJsonComplexList(doseAndRate, generator, FIELD_NAME_DOSE_AND_RATE.normal());
        }
        if (maxDosePerPeriod != null) {
            maxDosePerPeriod.serializeJsonField(generator, FIELD_NAME_MAX_DOSE_PER_PERIOD);
        }
        if (maxDosePerAdministration != null) {
            maxDosePerAdministration.serializeJsonField(generator, FIELD_NAME_MAX_DOSE_PER_ADMINISTRATION);
        }
        if (maxDosePerLifetime != null) {
            maxDosePerLifetime.serializeJsonField(generator, FIELD_NAME_MAX_DOSE_PER_LIFETIME);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (!modifierExtension.isEmpty()) {
            sink.putByte((byte) 2);
            Base.hashIntoList(modifierExtension, sink);
        }
        if (sequence != null) {
            sink.putByte((byte) 3);
            sequence.hashInto(sink);
        }
        if (text != null) {
            sink.putByte((byte) 4);
            text.hashInto(sink);
        }
        if (!additionalInstruction.isEmpty()) {
            sink.putByte((byte) 5);
            Base.hashIntoList(additionalInstruction, sink);
        }
        if (patientInstruction != null) {
            sink.putByte((byte) 6);
            patientInstruction.hashInto(sink);
        }
        if (timing != null) {
            sink.putByte((byte) 7);
            timing.hashInto(sink);
        }
        if (asNeeded != null) {
            sink.putByte((byte) 8);
            asNeeded.hashInto(sink);
        }
        if (site != null) {
            sink.putByte((byte) 9);
            site.hashInto(sink);
        }
        if (route != null) {
            sink.putByte((byte) 10);
            route.hashInto(sink);
        }
        if (method != null) {
            sink.putByte((byte) 11);
            method.hashInto(sink);
        }
        if (!doseAndRate.isEmpty()) {
            sink.putByte((byte) 12);
            Base.hashIntoList(doseAndRate, sink);
        }
        if (maxDosePerPeriod != null) {
            sink.putByte((byte) 13);
            maxDosePerPeriod.hashInto(sink);
        }
        if (maxDosePerAdministration != null) {
            sink.putByte((byte) 14);
            maxDosePerAdministration.hashInto(sink);
        }
        if (maxDosePerLifetime != null) {
            sink.putByte((byte) 15);
            maxDosePerLifetime.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(modifierExtension) + Base.memSize(sequence) +
                Base.memSize(text) + Base.memSize(additionalInstruction) + Base.memSize(patientInstruction) +
                Base.memSize(timing) + Base.memSize(asNeeded) + Base.memSize(site) + Base.memSize(route) +
                Base.memSize(method) + Base.memSize(doseAndRate) + Base.memSize(maxDosePerPeriod) +
                Base.memSize(maxDosePerAdministration) + Base.memSize(maxDosePerLifetime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Dosage that &&
                extensionData.equals(that.extensionData) &&
                modifierExtension.equals(that.modifierExtension) &&
                Objects.equals(sequence, that.sequence) &&
                Objects.equals(text, that.text) &&
                additionalInstruction.equals(that.additionalInstruction) &&
                Objects.equals(patientInstruction, that.patientInstruction) &&
                Objects.equals(timing, that.timing) &&
                Objects.equals(asNeeded, that.asNeeded) &&
                Objects.equals(site, that.site) &&
                Objects.equals(route, that.route) &&
                Objects.equals(method, that.method) &&
                doseAndRate.equals(that.doseAndRate) &&
                Objects.equals(maxDosePerPeriod, that.maxDosePerPeriod) &&
                Objects.equals(maxDosePerAdministration, that.maxDosePerAdministration) &&
                Objects.equals(maxDosePerLifetime, that.maxDosePerLifetime);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + modifierExtension.hashCode();
        result = 31 * result + Objects.hashCode(sequence);
        result = 31 * result + Objects.hashCode(text);
        result = 31 * result + additionalInstruction.hashCode();
        result = 31 * result + Objects.hashCode(patientInstruction);
        result = 31 * result + Objects.hashCode(timing);
        result = 31 * result + Objects.hashCode(asNeeded);
        result = 31 * result + Objects.hashCode(site);
        result = 31 * result + Objects.hashCode(route);
        result = 31 * result + Objects.hashCode(method);
        result = 31 * result + doseAndRate.hashCode();
        result = 31 * result + Objects.hashCode(maxDosePerPeriod);
        result = 31 * result + Objects.hashCode(maxDosePerAdministration);
        result = 31 * result + Objects.hashCode(maxDosePerLifetime);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Dosage{" +
                extensionData +
                ", modifierExtension=" + modifierExtension +
                ", sequence=" + sequence +
                ", text=" + text +
                ", additionalInstruction=" + additionalInstruction +
                ", patientInstruction=" + patientInstruction +
                ", timing=" + timing +
                ", asNeeded=" + asNeeded +
                ", site=" + site +
                ", route=" + route +
                ", method=" + method +
                ", doseAndRate=" + doseAndRate +
                ", maxDosePerPeriod=" + maxDosePerPeriod +
                ", maxDosePerAdministration=" + maxDosePerAdministration +
                ", maxDosePerLifetime=" + maxDosePerLifetime +
                '}';
    }

    @SuppressWarnings("DuplicatedCode")
    public static class DoseAndRate extends AbstractElement implements Complex {

        /**
         * Memory size.
         * <p>
         * 8 byte - object header
         * 4 or 8 byte - extension data reference
         * 4 or 8 byte - type reference
         * 4 or 8 byte - dose reference
         * 4 or 8 byte - rate reference
         */
        private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 4 * MEM_SIZE_REFERENCE;

        private static final Keyword FHIR_TYPE = RT.keyword("fhir.Dosage", "doseAndRate");

        private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof DoseAndRate ? FHIR_TYPE : this;
            }
        };

        private static final ILookupThunk TYPE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof DoseAndRate d ? d.type : this;
            }
        };

        private static final ILookupThunk DOSE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof DoseAndRate d ? d.dose : this;
            }
        };

        private static final ILookupThunk RATE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof DoseAndRate d ? d.rate : this;
            }
        };

        private static final Keyword TYPE = RT.keyword(null, "type");
        private static final Keyword DOSE = RT.keyword(null, "dose");
        private static final Keyword RATE = RT.keyword(null, "rate");

        private static final Keyword[] FIELDS = {ID, EXTENSION, TYPE, DOSE, RATE};

        private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
        private static final FieldName FIELD_NAME_DOSE_RANGE = FieldName.of("doseRange");
        private static final FieldName FIELD_NAME_DOSE_QUANTITY = FieldName.of("doseQuantity");
        private static final FieldName FIELD_NAME_RATE_RATIO = FieldName.of("rateRatio");
        private static final FieldName FIELD_NAME_RATE_RANGE = FieldName.of("rateRange");
        private static final FieldName FIELD_NAME_RATE_QUANTITY = FieldName.of("rateQuantity");

        private static final byte HASH_MARKER = 73;

        private static final DoseAndRate EMPTY = new DoseAndRate(ExtensionData.EMPTY, null, null, null);

        private final CodeableConcept type;
        private final Element dose;
        private final Element rate;

        private DoseAndRate(ExtensionData extensionData, CodeableConcept type, Element dose, Element rate) {
            super(extensionData);
            this.type = type;
            this.dose = dose;
            this.rate = rate;
        }

        public static DoseAndRate create(IPersistentMap m) {
            return new DoseAndRate(ExtensionData.fromMap(m), (CodeableConcept) m.valAt(TYPE), (Element) m.valAt(DOSE),
                    (Element) m.valAt(RATE));
        }

        public CodeableConcept type() {
            return type;
        }

        public Base dose() {
            return dose;
        }

        public Base rate() {
            return rate;
        }

        @Override
        public ILookupThunk getLookupThunk(Keyword key) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
            if (key == TYPE) return TYPE_LOOKUP_THUNK;
            if (key == DOSE) return DOSE_LOOKUP_THUNK;
            if (key == RATE) return RATE_LOOKUP_THUNK;
            return super.getLookupThunk(key);
        }

        @Override
        public Object valAt(Object key, Object notFound) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
            if (key == TYPE) return type;
            if (key == DOSE) return dose;
            if (key == RATE) return rate;
            return super.valAt(key, notFound);
        }

        @Override
        public ISeq seq() {
            ISeq seq = PersistentList.EMPTY;
            seq = appendElement(seq, RATE, rate);
            seq = appendElement(seq, DOSE, dose);
            seq = appendElement(seq, TYPE, type);
            return extensionData.append(seq);
        }

        @Override
        public DoseAndRate empty() {
            return EMPTY;
        }

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new BaseIterator(this, FIELDS);
        }

        @Override
        public DoseAndRate assoc(Object key, Object val) {
            if (key == TYPE) return new DoseAndRate(extensionData, (CodeableConcept) val, dose, rate);
            if (key == DOSE) return new DoseAndRate(extensionData, type, (Element) val, rate);
            if (key == RATE) return new DoseAndRate(extensionData, type, dose, (Element) val);
            if (key == EXTENSION) return new DoseAndRate(extensionData.withExtension(val), type, dose, rate);
            if (key == ID) return new DoseAndRate(extensionData.withId(val), type, dose, rate);
            return this;
        }

        @Override
        public DoseAndRate withMeta(IPersistentMap meta) {
            return new DoseAndRate(extensionData.withMeta(meta), type, dose, rate);
        }

        @Override
        public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
            generator.writeStartObject();
            serializeJsonBase(generator);
            if (type != null) {
                type.serializeJsonField(generator, FIELD_NAME_TYPE);
            }
            if (dose != null) {
                switch (dose) {
                    case Range doseRange -> doseRange.serializeJsonField(generator, FIELD_NAME_DOSE_RANGE);
                    case Quantity doseQuantity -> doseQuantity.serializeJsonField(generator, FIELD_NAME_DOSE_QUANTITY);
                    default -> {
                    }
                }
            }
            if (rate != null) {
                switch (rate) {
                    case Ratio rateRatio -> rateRatio.serializeJsonField(generator, FIELD_NAME_RATE_RATIO);
                    case Range rateRange -> rateRange.serializeJsonField(generator, FIELD_NAME_RATE_RANGE);
                    case Quantity rateQuantity -> rateQuantity.serializeJsonField(generator, FIELD_NAME_RATE_QUANTITY);
                    default -> {
                    }
                }
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
            if (dose != null) {
                sink.putByte((byte) 3);
                dose.hashInto(sink);
            }
            if (rate != null) {
                sink.putByte((byte) 4);
                rate.hashInto(sink);
            }
        }

        @Override
        public int memSize() {
            return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(type) + Base.memSize(dose) + Base.memSize(rate);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof DoseAndRate that &&
                    extensionData.equals(that.extensionData) &&
                    Objects.equals(type, that.type) &&
                    Objects.equals(dose, that.dose) &&
                    Objects.equals(rate, that.rate);
        }

        @Override
        public int hashCode() {
            int result = extensionData.hashCode();
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(dose);
            result = 31 * result + Objects.hashCode(rate);
            return result;
        }

        @Override
        public java.lang.String toString() {
            return "Dosage.DoseAndRate{" +
                    extensionData +
                    ", type=" + type +
                    ", dose=" + dose +
                    ", rate=" + rate +
                    '}';
        }
    }
}
