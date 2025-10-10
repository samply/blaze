package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.chrono.IsoChronology;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;

import static blaze.fhir.spec.type.Base.MEM_SIZE_OBJECT_HEADER;
import static java.time.temporal.ChronoField.*;

@SuppressWarnings("UnstableApiUsage")
public final class DateTimeDate implements DateTime, Comparable<DateTimeDate> {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - year
     * 2 byte - month
     * 2 byte - day
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 8;

    private final int year;
    private final short month;
    private final short day;

    DateTimeDate(int year, int month, int dayOfMonth) {
        this.year = year;
        this.month = (short) month;
        this.day = (short) dayOfMonth;
    }

    private static DateTimeDate create(int year, int month, int dayOfMonth) {
        if (dayOfMonth > 28) {
            int dom = 31;
            switch (month) {
                case 2:
                    dom = (IsoChronology.INSTANCE.isLeapYear(year) ? 29 : 28);
                    break;
                case 4:
                case 6:
                case 9:
                case 11:
                    dom = 30;
                    break;
            }
            if (dayOfMonth > dom) {
                if (dayOfMonth == 29) {
                    throw new DateTimeException("Invalid date 'February 29' as '" + year + "' is not a leap year");
                } else {
                    throw new DateTimeException("Invalid date '" + Month.of(month).name() + " " + dayOfMonth + "'");
                }
            }
        }
        return new DateTimeDate(year, month, dayOfMonth);
    }

    public static DateTimeDate of(int year, int month, int dayOfMonth) {
        YEAR_RANGE.checkValidValue(year, YEAR);
        MONTH_OF_YEAR.checkValidValue(month);
        DAY_OF_MONTH.checkValidValue(dayOfMonth);
        return create(year, month, dayOfMonth);
    }

    static DateTimeDate parse(String text) {
        try {
            return of(Integer.parseInt(text.substring(0, 4)),
                    Integer.parseInt(text.substring(5, 7)),
                    Integer.parseInt(text.substring(8, 10)));
        } catch (NumberFormatException e) {
            throw new DateTimeException("Invalid date component: " + e.getMessage());
        }
    }

    public static DateTimeDate fromLocalDate(LocalDate date) {
        return of(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    public int day() {
        return day;
    }

    @Override
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        sink.putInt(year);
        sink.putInt(month);
        sink.putInt(day);
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT;
    }

    public DateDate toDate() {
        return DateDate.of(year, month, day);
    }

    public LocalDateTime atStartOfDay() {
        return LocalDate.of(year, month, day).atStartOfDay();
    }

    public LocalDateTime atTime(int hour, int minute, int second) {
        return LocalDate.of(year, month, day).atTime(hour, minute, second);
    }

    @Override
    public boolean isSupported(TemporalUnit unit) {
        return LocalDate.of(year, month, day).isSupported(unit);
    }

    @Override
    public Temporal with(TemporalField field, long newValue) {
        return fromLocalDate(LocalDate.of(year, month, day).with(field, newValue));
    }

    @Override
    public Temporal plus(long amountToAdd, TemporalUnit unit) {
        return fromLocalDate(LocalDate.of(year, month, day).plus(amountToAdd, unit));
    }

    /**
     * Returns a copy of this {@code DateTimeDate} with the specified number of days added.
     * <p>
     * This method adds the specified amount to the days field incrementing the
     * month and year fields as necessary to ensure the result remains valid.
     * The result is only invalid if the maximum/minimum year is exceeded.
     * <p>
     * For example, 2008-12-31 plus one day would result in 2009-01-01.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param daysToAdd the days to add, may be negative
     * @return a {@code DateTimeDate} based on this date with the days added, not null
     * @throws DateTimeException if the result exceeds the supported date range
     */
    public DateTimeDate plusDays(int daysToAdd) {
        return toDate().plusDays(daysToAdd).toDateTime();
    }

    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        return LocalDate.of(year, month, day).until(endExclusive, unit);
    }

    @Override
    public boolean isSupported(TemporalField field) {
        return LocalDate.of(year, month, day).isSupported(field);
    }

    @Override
    public long getLong(TemporalField field) {
        return LocalDate.of(year, month, day).getLong(field);
    }

    @Override
    public int compareTo(DateTimeDate other) {
        int cmp = (year - other.year);
        if (cmp == 0) {
            cmp = (month - other.month);
            if (cmp == 0) {
                cmp = (day - other.day);
            }
        }
        return cmp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DateTimeDate that && year == that.year && month == that.month && day == that.day;
    }

    @Override
    public int hashCode() {
        int yearValue = year;
        return (yearValue & 0xFFFFF800) ^ ((yearValue << 11) + ((int) month << 6) + ((int) day));
    }

    @Override
    public String toString() {
        int yearValue = year;
        int monthValue = month;
        int dayValue = day;
        int absYear = Math.abs(yearValue);
        StringBuilder buf = new StringBuilder(10);
        if (absYear < 1000) {
            buf.append(yearValue + 10000).deleteCharAt(0);
        } else {
            buf.append(yearValue);
        }
        return buf.append(monthValue < 10 ? "-0" : "-")
                .append(monthValue)
                .append(dayValue < 10 ? "-0" : "-")
                .append(dayValue)
                .toString();
    }
}
