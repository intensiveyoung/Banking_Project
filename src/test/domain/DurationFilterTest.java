package domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationFilterTest {

    @Test
    @DisplayName("Should expose the configured day counts for duration presets")
    void testPresetDayCounts() {
        assertEquals(7, DurationFilter.ONE_WEEK.getDays());
        assertEquals(14, DurationFilter.TWO_WEEKS.getDays());
        assertEquals(30, DurationFilter.ONE_MONTH.getDays());
        assertEquals(90, DurationFilter.THREE_MONTHS.getDays());
        assertEquals(365, DurationFilter.ONE_YEAR.getDays());
        assertEquals(1825, DurationFilter.FIVE_YEARS.getDays());
        assertEquals(0, DurationFilter.ALL_TIME.getDays());
    }

    @Test
    @DisplayName("Should map duration menu selections to preset filters")
    void testMenuSelectionMapping() {
        assertEquals(DurationFilter.ONE_WEEK, DurationFilter.fromSelection(1));
        assertEquals(DurationFilter.ALL_TIME, DurationFilter.fromSelection(7));
        assertThrows(IllegalArgumentException.class, () -> DurationFilter.fromSelection(8));
    }
}
