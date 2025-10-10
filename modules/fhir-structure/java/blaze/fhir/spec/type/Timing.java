package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("DuplicatedCode")
public final class Timing extends AbstractBackboneElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - modifierExtension reference
     * 4 or 8 byte - event reference
     * 4 or 8 byte - repeat reference
     * 4 or 8 byte - code reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 5 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Timing");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Timing ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk EVENT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Timing t ? t.event : this;
        }
    };

    private static final ILookupThunk REPEAT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Timing t ? t.repeat : this;
        }
    };

    private static final ILookupThunk CODE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Timing t ? t.code : this;
        }
    };

    private static final Keyword EVENT = RT.keyword(null, "event");
    private static final Keyword REPEAT = RT.keyword(null, "repeat");
    private static final Keyword CODE = RT.keyword(null, "code");

    private static final Keyword[] FIELDS = {ID, EXTENSION, MODIFIER_EXTENSION, EVENT, REPEAT, CODE};

    private static final FieldName FIELD_NAME_EVENT = FieldName.of("event");
    private static final FieldName FIELD_NAME_REPEAT = FieldName.of("repeat");
    private static final FieldName FIELD_NAME_CODE = FieldName.of("code");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueTiming");

    private static final byte HASH_MARKER = 57;

    @SuppressWarnings("unchecked")
    private static final Timing EMPTY = new Timing(ExtensionData.EMPTY, PersistentVector.EMPTY, PersistentVector.EMPTY,
            null, null);

    private final List<DateTime> event;
    private final Repeat repeat;
    private final CodeableConcept code;

    private Timing(ExtensionData extensionData, List<Extension> modifierExtension, List<DateTime> event, Repeat repeat,
                   CodeableConcept code) {
        super(extensionData, modifierExtension);
        this.event = requireNonNull(event);
        this.repeat = repeat;
        this.code = code;
    }

    public static Timing create(IPersistentMap m) {
        return new Timing(ExtensionData.fromMap(m), Base.listFrom(m, MODIFIER_EXTENSION), Base.listFrom(m, EVENT),
                (Repeat) m.valAt(REPEAT), (CodeableConcept) m.valAt(CODE));
    }

    @Override
    public boolean isInterned() {
        return false;
    }

    public List<DateTime> event() {
        return event;
    }

    public Repeat repeat() {
        return repeat;
    }

    public CodeableConcept code() {
        return code;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == EVENT) return EVENT_LOOKUP_THUNK;
        if (key == REPEAT) return REPEAT_LOOKUP_THUNK;
        if (key == CODE) return CODE_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }


    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == EVENT) return event;
        if (key == REPEAT) return repeat;
        if (key == CODE) return code;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, CODE, code);
        seq = appendElement(seq, REPEAT, repeat);
        seq = appendElement(seq, EVENT, event);
        seq = appendElement(seq, MODIFIER_EXTENSION, modifierExtension);
        return extensionData.append(seq);
    }

    @Override
    public Timing empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Timing assoc(Object key, Object val) {
        if (key == EVENT) return new Timing(extensionData, modifierExtension, Lists.nullToEmpty(val), repeat, code);
        if (key == REPEAT) return new Timing(extensionData, modifierExtension, event, (Repeat) val, code);
        if (key == CODE) return new Timing(extensionData, modifierExtension, event, repeat, (CodeableConcept) val);
        if (key == MODIFIER_EXTENSION) return new Timing(extensionData, Lists.nullToEmpty(val), event, repeat, code);
        if (key == EXTENSION)
            return new Timing(extensionData.withExtension(val), modifierExtension, event, repeat, code);
        if (key == ID) return new Timing(extensionData.withId(val), modifierExtension, event, repeat, code);
        return this;
    }

    @Override
    public Timing withMeta(IPersistentMap meta) {
        return new Timing(extensionData.withMeta(meta), modifierExtension, event, repeat, code);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (!event.isEmpty()) {
            Primitive.serializeJsonPrimitiveList(event, generator, FIELD_NAME_EVENT);
        }
        if (repeat != null) {
            repeat.serializeJsonField(generator, FIELD_NAME_REPEAT);
        }
        if (code != null) {
            code.serializeJsonField(generator, FIELD_NAME_CODE);
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
        if (!event.isEmpty()) {
            sink.putByte((byte) 3);
            Base.hashIntoList(event, sink);
        }
        if (repeat != null) {
            sink.putByte((byte) 4);
            repeat.hashInto(sink);
        }
        if (code != null) {
            sink.putByte((byte) 5);
            code.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(modifierExtension) + Base.memSize(event) +
                Base.memSize(repeat) + Base.memSize(code);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Timing that &&
                extensionData.equals(that.extensionData) &&
                modifierExtension.equals(that.modifierExtension) &&
                Objects.equals(event, that.event) &&
                Objects.equals(repeat, that.repeat) &&
                Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + modifierExtension.hashCode();
        result = 31 * result + Objects.hashCode(event);
        result = 31 * result + Objects.hashCode(repeat);
        result = 31 * result + Objects.hashCode(code);
        return result;
    }

    @Override
    public String toString() {
        return "Timing{" +
                extensionData +
                ", modifierExtension=" + modifierExtension +
                ", event=" + event +
                ", repeat=" + repeat +
                ", code=" + code +
                '}';
    }

    public static class Repeat extends AbstractElement implements Complex {

        /**
         * Memory size.
         * <p>
         * 8 byte - object header
         * 4 or 8 byte - extension data reference
         * 4 or 8 byte - bounds reference
         * 4 or 8 byte - count reference
         * 4 or 8 byte - countMax reference
         * 4 or 8 byte - duration reference
         * 4 or 8 byte - durationMax reference
         * 4 or 8 byte - durationUnit reference
         * 4 or 8 byte - frequency reference
         * 4 or 8 byte - frequencyMax reference
         * 4 or 8 byte - period reference
         * 4 or 8 byte - periodMax reference
         * 4 or 8 byte - periodUnit reference
         * 4 or 8 byte - dayOfWeek reference
         * 4 or 8 byte - timeOfDay reference
         * 4 or 8 byte - when reference
         * 4 or 8 byte - offset reference
         */
        private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 16 * MEM_SIZE_REFERENCE;

        private static final Keyword FHIR_TYPE = RT.keyword("fhir.Timing", "repeat");

        private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat ? FHIR_TYPE : this;
            }
        };

        private static final ILookupThunk BOUNDS_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.bounds : this;
            }
        };

        private static final ILookupThunk COUNT_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.count : this;
            }
        };

        private static final ILookupThunk COUNT_MAX_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.countMax : this;
            }
        };

        private static final ILookupThunk DURATION_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.duration : this;
            }
        };

        private static final ILookupThunk DURATION_MAX_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.durationMax : this;
            }
        };

        private static final ILookupThunk DURATION_UNIT_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.durationUnit : this;
            }
        };

        private static final ILookupThunk FREQUENCY_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.frequency : this;
            }
        };

        private static final ILookupThunk FREQUENCY_MAX_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.frequencyMax : this;
            }
        };

        private static final ILookupThunk PERIOD_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.period : this;
            }
        };

        private static final ILookupThunk PERIOD_MAX_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.periodMax : this;
            }
        };

        private static final ILookupThunk PERIOD_UNIT_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.periodUnit : this;
            }
        };

        private static final ILookupThunk DAY_OF_WEEK_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.dayOfWeek : this;
            }
        };

        private static final ILookupThunk TIME_OF_DAY_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.timeOfDay : this;
            }
        };

        private static final ILookupThunk WHEN_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.when : this;
            }
        };

        private static final ILookupThunk OFFSET_LOOKUP_THUNK = new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Repeat r ? r.offset : this;
            }
        };

        private static final Keyword BOUNDS = RT.keyword(null, "bounds");
        private static final Keyword COUNT = RT.keyword(null, "count");
        private static final Keyword COUNT_MAX = RT.keyword(null, "countMax");
        private static final Keyword DURATION = RT.keyword(null, "duration");
        private static final Keyword DURATION_MAX = RT.keyword(null, "durationMax");
        private static final Keyword DURATION_UNIT = RT.keyword(null, "durationUnit");
        private static final Keyword FREQUENCY = RT.keyword(null, "frequency");
        private static final Keyword FREQUENCY_MAX = RT.keyword(null, "frequencyMax");
        private static final Keyword PERIOD = RT.keyword(null, "period");
        private static final Keyword PERIOD_MAX = RT.keyword(null, "periodMax");
        private static final Keyword PERIOD_UNIT = RT.keyword(null, "periodUnit");
        private static final Keyword DAY_OF_WEEK = RT.keyword(null, "dayOfWeek");
        private static final Keyword TIME_OF_DAY = RT.keyword(null, "timeOfDay");
        private static final Keyword WHEN = RT.keyword(null, "when");
        private static final Keyword OFFSET = RT.keyword(null, "offset");

        private static final Keyword[] FIELDS = {ID, EXTENSION, BOUNDS, COUNT, COUNT_MAX, DURATION, DURATION_MAX,
                DURATION_UNIT, FREQUENCY, FREQUENCY_MAX, PERIOD, PERIOD_MAX, PERIOD_UNIT, DAY_OF_WEEK, TIME_OF_DAY,
                WHEN, OFFSET};

        private static final FieldName FIELD_NAME_BOUNDS_DURATION = FieldName.of("boundsDuration");
        private static final FieldName FIELD_NAME_BOUNDS_RANGE = FieldName.of("boundsRange");
        private static final FieldName FIELD_NAME_BOUNDS_PERIOD = FieldName.of("boundsPeriod");
        private static final FieldName FIELD_NAME_COUNT = FieldName.of("count");
        private static final FieldName FIELD_NAME_COUNT_MAX = FieldName.of("countMax");
        private static final FieldName FIELD_NAME_DURATION = FieldName.of("duration");
        private static final FieldName FIELD_NAME_DURATION_MAX = FieldName.of("durationMax");
        private static final FieldName FIELD_NAME_DURATION_UNIT = FieldName.of("durationUnit");
        private static final FieldName FIELD_NAME_FREQUENCY = FieldName.of("frequency");
        private static final FieldName FIELD_NAME_FREQUENCY_MAX = FieldName.of("frequencyMax");
        private static final FieldName FIELD_NAME_PERIOD = FieldName.of("period");
        private static final FieldName FIELD_NAME_PERIOD_MAX = FieldName.of("periodMax");
        private static final FieldName FIELD_NAME_PERIOD_UNIT = FieldName.of("periodUnit");
        private static final FieldName FIELD_NAME_DAY_OF_WEEK = FieldName.of("dayOfWeek");
        private static final FieldName FIELD_NAME_TIME_OF_DAY = FieldName.of("timeOfDay");
        private static final FieldName FIELD_NAME_WHEN = FieldName.of("when");
        private static final FieldName FIELD_NAME_OFFSET = FieldName.of("offset");

        private static final byte HASH_MARKER = 58;

        @SuppressWarnings("unchecked")
        private static final Repeat EMPTY = new Repeat(ExtensionData.EMPTY, null, null, null, null, null, null, null,
                null, null, null, null, PersistentVector.EMPTY, PersistentVector.EMPTY, PersistentVector.EMPTY, null);

        private final Element bounds;
        private final PositiveInt count;
        private final PositiveInt countMax;
        private final Decimal duration;
        private final Decimal durationMax;
        private final Code durationUnit;
        private final PositiveInt frequency;
        private final PositiveInt frequencyMax;
        private final Decimal period;
        private final Decimal periodMax;
        private final Code periodUnit;
        private final List<Code> dayOfWeek;
        private final List<Time> timeOfDay;
        private final List<Code> when;
        private final UnsignedInt offset;

        private Repeat(ExtensionData extensionData, Element bounds, PositiveInt count, PositiveInt countMax,
                       Decimal duration, Decimal durationMax, Code durationUnit, PositiveInt frequency,
                       PositiveInt frequencyMax, Decimal period, Decimal periodMax, Code periodUnit,
                       List<Code> dayOfWeek, List<Time> timeOfDay, List<Code> when, UnsignedInt offset) {
            super(extensionData);
            this.bounds = bounds;
            this.count = count;
            this.countMax = countMax;
            this.duration = duration;
            this.durationMax = durationMax;
            this.durationUnit = durationUnit;
            this.frequency = frequency;
            this.frequencyMax = frequencyMax;
            this.period = period;
            this.periodMax = periodMax;
            this.periodUnit = periodUnit;
            this.dayOfWeek = requireNonNull(dayOfWeek);
            this.timeOfDay = requireNonNull(timeOfDay);
            this.when = requireNonNull(when);
            this.offset = offset;
        }

        public static Repeat create(IPersistentMap m) {
            Object bounds = m.valAt(BOUNDS);
            if (bounds != null && !(bounds instanceof Duration || bounds instanceof Range || bounds instanceof Period)) {
                throw new IllegalArgumentException("Expecting bounds to be either a Duration, Range or Period but was a `" +
                        bounds.getClass().getSimpleName() + "`.");
            }
            return new Repeat(ExtensionData.fromMap(m), (Element) bounds, (PositiveInt) m.valAt(COUNT),
                    (PositiveInt) m.valAt(COUNT_MAX), (Decimal) m.valAt(DURATION), (Decimal) m.valAt(DURATION_MAX),
                    (Code) m.valAt(DURATION_UNIT), (PositiveInt) m.valAt(FREQUENCY), (PositiveInt) m.valAt(FREQUENCY_MAX),
                    (Decimal) m.valAt(PERIOD), (Decimal) m.valAt(PERIOD_MAX), (Code) m.valAt(PERIOD_UNIT),
                    Base.listFrom(m, DAY_OF_WEEK), Base.listFrom(m, TIME_OF_DAY), Base.listFrom(m, WHEN),
                    (UnsignedInt) m.valAt(OFFSET));
        }

        @Override
        public ILookupThunk getLookupThunk(Keyword key) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
            if (key == BOUNDS) return BOUNDS_LOOKUP_THUNK;
            if (key == COUNT) return COUNT_LOOKUP_THUNK;
            if (key == COUNT_MAX) return COUNT_MAX_LOOKUP_THUNK;
            if (key == DURATION) return DURATION_LOOKUP_THUNK;
            if (key == DURATION_MAX) return DURATION_MAX_LOOKUP_THUNK;
            if (key == DURATION_UNIT) return DURATION_UNIT_LOOKUP_THUNK;
            if (key == FREQUENCY) return FREQUENCY_LOOKUP_THUNK;
            if (key == FREQUENCY_MAX) return FREQUENCY_MAX_LOOKUP_THUNK;
            if (key == PERIOD) return PERIOD_LOOKUP_THUNK;
            if (key == PERIOD_MAX) return PERIOD_MAX_LOOKUP_THUNK;
            if (key == PERIOD_UNIT) return PERIOD_UNIT_LOOKUP_THUNK;
            if (key == DAY_OF_WEEK) return DAY_OF_WEEK_LOOKUP_THUNK;
            if (key == TIME_OF_DAY) return TIME_OF_DAY_LOOKUP_THUNK;
            if (key == WHEN) return WHEN_LOOKUP_THUNK;
            if (key == OFFSET) return OFFSET_LOOKUP_THUNK;
            return super.getLookupThunk(key);
        }

        @Override
        public Object valAt(Object key, Object notFound) {
            if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
            if (key == BOUNDS) return bounds;
            if (key == COUNT) return count;
            if (key == COUNT_MAX) return countMax;
            if (key == DURATION) return duration;
            if (key == DURATION_MAX) return durationMax;
            if (key == DURATION_UNIT) return durationUnit;
            if (key == FREQUENCY) return frequency;
            if (key == FREQUENCY_MAX) return frequencyMax;
            if (key == PERIOD) return period;
            if (key == PERIOD_MAX) return periodMax;
            if (key == PERIOD_UNIT) return periodUnit;
            if (key == DAY_OF_WEEK) return dayOfWeek;
            if (key == TIME_OF_DAY) return timeOfDay;
            if (key == WHEN) return when;
            if (key == OFFSET) return offset;
            return super.valAt(key, notFound);
        }

        @Override
        public ISeq seq() {
            ISeq seq = PersistentList.EMPTY;
            seq = appendElement(seq, OFFSET, offset);
            if (!when.isEmpty()) {
                seq = appendElement(seq, WHEN, when);
            }
            if (!timeOfDay.isEmpty()) {
                seq = appendElement(seq, TIME_OF_DAY, timeOfDay);
            }
            if (!dayOfWeek.isEmpty()) {
                seq = appendElement(seq, DAY_OF_WEEK, dayOfWeek);
            }
            seq = appendElement(seq, PERIOD_UNIT, periodUnit);
            seq = appendElement(seq, PERIOD_MAX, periodMax);
            seq = appendElement(seq, PERIOD, period);
            seq = appendElement(seq, FREQUENCY_MAX, frequencyMax);
            seq = appendElement(seq, FREQUENCY, frequency);
            seq = appendElement(seq, DURATION_UNIT, durationUnit);
            seq = appendElement(seq, DURATION_MAX, durationMax);
            seq = appendElement(seq, DURATION, duration);
            seq = appendElement(seq, COUNT_MAX, countMax);
            seq = appendElement(seq, COUNT, count);
            seq = appendElement(seq, BOUNDS, bounds);
            return extensionData.append(seq);
        }

        @Override
        public Repeat empty() {
            return EMPTY;
        }

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new BaseIterator(this, FIELDS);
        }

        @Override
        public Repeat assoc(Object key, Object val) {
            if (key == BOUNDS) return new Repeat(extensionData, (Element) val, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay, when,
                    offset);
            if (key == COUNT) return new Repeat(extensionData, bounds, (PositiveInt) val, countMax, duration,
                    durationMax, durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek,
                    timeOfDay, when, offset);
            if (key == COUNT_MAX) return new Repeat(extensionData, bounds, count, (PositiveInt) val, duration,
                    durationMax, durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek,
                    timeOfDay, when, offset);
            if (key == DURATION) return new Repeat(extensionData, bounds, count, countMax, (Decimal) val, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay, when,
                    offset);
            if (key == DURATION_MAX) return new Repeat(extensionData, bounds, count, countMax, duration, (Decimal) val,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay, when,
                    offset);
            if (key == DURATION_UNIT) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    (Code) val, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay, when,
                    offset);
            if (key == FREQUENCY) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, (PositiveInt) val, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay,
                    when, offset);
            if (key == FREQUENCY_MAX) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, (PositiveInt) val, period, periodMax, periodUnit, dayOfWeek, timeOfDay,
                    when, offset);
            if (key == PERIOD) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, (Decimal) val, periodMax, periodUnit, dayOfWeek, timeOfDay,
                    when, offset);
            if (key == PERIOD_MAX) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, (Decimal) val, periodUnit, dayOfWeek, timeOfDay,
                    when, offset);
            if (key == PERIOD_UNIT) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, (Code) val, dayOfWeek, timeOfDay, when,
                    offset);
            if (key == DAY_OF_WEEK) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, Lists.nullToEmpty(val),
                    timeOfDay, when, offset);
            if (key == TIME_OF_DAY) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek,
                    Lists.nullToEmpty(val), when, offset);
            if (key == WHEN) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay,
                    Lists.nullToEmpty(val), offset);
            if (key == OFFSET) return new Repeat(extensionData, bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay, when,
                    (UnsignedInt) val);
            if (key == EXTENSION) return new Repeat(extensionData.withExtension(val), bounds, count, countMax, duration,
                    durationMax, durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek,
                    timeOfDay, when, offset);
            if (key == ID) return new Repeat(extensionData.withId(val), bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay, when,
                    offset);
            return this;
        }

        @Override
        public Repeat withMeta(IPersistentMap meta) {
            return new Repeat(extensionData.withMeta(meta), bounds, count, countMax, duration, durationMax,
                    durationUnit, frequency, frequencyMax, period, periodMax, periodUnit, dayOfWeek, timeOfDay,
                    when, offset);
        }

        @Override
        public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
            generator.writeStartObject();
            serializeJsonBase(generator);
            if (bounds != null) {
                switch (bounds) {
                    case Duration boundsDuration ->
                            boundsDuration.serializeJsonField(generator, FIELD_NAME_BOUNDS_DURATION);
                    case Range boundsRange ->
                            boundsRange.serializeJsonField(generator, FIELD_NAME_BOUNDS_RANGE);
                    case Period boundsPeriod ->
                            boundsPeriod.serializeJsonField(generator, FIELD_NAME_BOUNDS_PERIOD);
                    default -> {
                    }
                }
            }
            if (count != null) {
                count.serializeAsJsonProperty(generator, FIELD_NAME_COUNT);
            }
            if (countMax != null) {
                countMax.serializeAsJsonProperty(generator, FIELD_NAME_COUNT_MAX);
            }
            if (duration != null) {
                duration.serializeAsJsonProperty(generator, FIELD_NAME_DURATION);
            }
            if (durationMax != null) {
                durationMax.serializeAsJsonProperty(generator, FIELD_NAME_DURATION_MAX);
            }
            if (durationUnit != null) {
                durationUnit.serializeAsJsonProperty(generator, FIELD_NAME_DURATION_UNIT);
            }
            if (frequency != null) {
                frequency.serializeAsJsonProperty(generator, FIELD_NAME_FREQUENCY);
            }
            if (frequencyMax != null) {
                frequencyMax.serializeAsJsonProperty(generator, FIELD_NAME_FREQUENCY_MAX);
            }
            if (period != null) {
                period.serializeAsJsonProperty(generator, FIELD_NAME_PERIOD);
            }
            if (periodMax != null) {
                periodMax.serializeAsJsonProperty(generator, FIELD_NAME_PERIOD_MAX);
            }
            if (periodUnit != null) {
                periodUnit.serializeAsJsonProperty(generator, FIELD_NAME_PERIOD_UNIT);
            }
            if (!dayOfWeek.isEmpty()) {
                Primitive.serializeJsonPrimitiveList(dayOfWeek, generator, FIELD_NAME_DAY_OF_WEEK);
            }
            if (!timeOfDay.isEmpty()) {
                Primitive.serializeJsonPrimitiveList(timeOfDay, generator, FIELD_NAME_TIME_OF_DAY);
            }
            if (!when.isEmpty()) {
                Primitive.serializeJsonPrimitiveList(when, generator, FIELD_NAME_WHEN);
            }
            if (offset != null) {
                offset.serializeAsJsonProperty(generator, FIELD_NAME_OFFSET);
            }
            generator.writeEndObject();
        }

        @Override
        @SuppressWarnings("UnstableApiUsage")
        public void hashInto(PrimitiveSink sink) {
            sink.putByte(HASH_MARKER);
            extensionData.hashInto(sink);
            if (bounds != null) {
                sink.putByte((byte) 2);
                bounds.hashInto(sink);
            }
            if (count != null) {
                sink.putByte((byte) 3);
                count.hashInto(sink);
            }
            if (countMax != null) {
                sink.putByte((byte) 4);
                countMax.hashInto(sink);
            }
            if (duration != null) {
                sink.putByte((byte) 5);
                duration.hashInto(sink);
            }
            if (durationMax != null) {
                sink.putByte((byte) 6);
                durationMax.hashInto(sink);
            }
            if (durationUnit != null) {
                sink.putByte((byte) 7);
                durationUnit.hashInto(sink);
            }
            if (frequency != null) {
                sink.putByte((byte) 8);
                frequency.hashInto(sink);
            }
            if (frequencyMax != null) {
                sink.putByte((byte) 9);
                frequencyMax.hashInto(sink);
            }
            if (period != null) {
                sink.putByte((byte) 10);
                period.hashInto(sink);
            }
            if (periodMax != null) {
                sink.putByte((byte) 11);
                periodMax.hashInto(sink);
            }
            if (periodUnit != null) {
                sink.putByte((byte) 12);
                periodUnit.hashInto(sink);
            }
            if (dayOfWeek != null) {
                sink.putByte((byte) 13);
                Base.hashIntoList(dayOfWeek, sink);
            }
            if (timeOfDay != null) {
                sink.putByte((byte) 14);
                Base.hashIntoList(timeOfDay, sink);
            }
            if (when != null) {
                sink.putByte((byte) 15);
                Base.hashIntoList(when, sink);
            }
            if (offset != null) {
                sink.putByte((byte) 16);
                offset.hashInto(sink);
            }
        }

        @Override
        public int memSize() {
            return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(bounds) + Base.memSize(count) +
                    Base.memSize(countMax) + Base.memSize(duration) + Base.memSize(durationMax) +
                    Base.memSize(durationUnit) + Base.memSize(frequency) + Base.memSize(frequencyMax) +
                    Base.memSize(period) + Base.memSize(periodMax) + Base.memSize(periodUnit) +
                    Base.memSize(dayOfWeek) + Base.memSize(timeOfDay) + Base.memSize(when) + Base.memSize(offset);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Repeat that &&
                    extensionData.equals(that.extensionData) &&
                    Objects.equals(bounds, that.bounds) &&
                    Objects.equals(count, that.count) &&
                    Objects.equals(countMax, that.countMax) &&
                    Objects.equals(duration, that.duration) &&
                    Objects.equals(durationMax, that.durationMax) &&
                    Objects.equals(durationUnit, that.durationUnit) &&
                    Objects.equals(frequency, that.frequency) &&
                    Objects.equals(frequencyMax, that.frequencyMax) &&
                    Objects.equals(period, that.period) &&
                    Objects.equals(periodMax, that.periodMax) &&
                    Objects.equals(periodUnit, that.periodUnit) &&
                    dayOfWeek.equals(that.dayOfWeek) &&
                    timeOfDay.equals(that.timeOfDay) &&
                    when.equals(that.when) &&
                    Objects.equals(offset, that.offset);
        }

        @Override
        public int hashCode() {
            int result = extensionData.hashCode();
            result = 31 * result + Objects.hashCode(bounds);
            result = 31 * result + Objects.hashCode(count);
            result = 31 * result + Objects.hashCode(countMax);
            result = 31 * result + Objects.hashCode(duration);
            result = 31 * result + Objects.hashCode(durationMax);
            result = 31 * result + Objects.hashCode(durationUnit);
            result = 31 * result + Objects.hashCode(frequency);
            result = 31 * result + Objects.hashCode(frequencyMax);
            result = 31 * result + Objects.hashCode(period);
            result = 31 * result + Objects.hashCode(periodMax);
            result = 31 * result + Objects.hashCode(periodUnit);
            result = 31 * result + dayOfWeek.hashCode();
            result = 31 * result + timeOfDay.hashCode();
            result = 31 * result + when.hashCode();
            result = 31 * result + Objects.hashCode(offset);
            return result;
        }

        @Override
        public String toString() {
            return "Timing.Repeat{" +
                    extensionData +
                    ", bounds=" + bounds +
                    ", count=" + count +
                    ", countMax=" + countMax +
                    ", duration=" + duration +
                    ", durationMax=" + durationMax +
                    ", durationUnit=" + durationUnit +
                    ", frequency=" + frequency +
                    ", frequencyMax=" + frequencyMax +
                    ", period=" + period +
                    ", periodMax=" + periodMax +
                    ", periodUnit=" + periodUnit +
                    ", dayOfWeek=" + dayOfWeek +
                    ", timeOfDay=" + timeOfDay +
                    ", when=" + when +
                    ", offset=" + offset +
                    '}';
        }
    }
}
