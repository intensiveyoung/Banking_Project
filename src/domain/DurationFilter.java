package domain;

public enum DurationFilter {
    ONE_WEEK("1 Week", 7),
    TWO_WEEKS("2 Weeks", 14),
    ONE_MONTH("1 Month", 30),
    THREE_MONTHS("3 Months", 90),
    ONE_YEAR("1 Year", 365),
    FIVE_YEARS("5 Years", 1825),
    ALL_TIME("All Time", 0);

    private final String displayLabel;
    private final int days;

    DurationFilter(String displayLabel, int days) {
        this.displayLabel = displayLabel;
        this.days = days;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public int getDays() {
        return days;
    }

    public static DurationFilter fromSelection(int selection) {
        DurationFilter[] filters = values();
        if (selection < 1 || selection > filters.length) {
            throw new IllegalArgumentException("Duration selection must be between 1 and 7.");
        }
        return filters[selection - 1];
    }
}
