package com.rpacloud.execution.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FlowScheduleServiceTest {

    @ParameterizedTest
    @CsvSource({
            "'0 8 * * *',    '0 0 8 * * ?'",
            "'30 6 * * 1-5', '0 30 6 * * 1-5'",
            "'*/5 * * * *',  '0 */5 * * * ?'",
            "'0 0 1 * *',    '0 0 0 1 * ?'",
            "'15 14 1 * *',  '0 15 14 1 * ?'",
    })
    void toQuartzCron_converts5FieldTo6Field(String input, String expected) {
        assertThat(FlowScheduleService.toQuartzCron(input)).isEqualTo(expected);
    }

    @Test
    void toQuartzCron_passthrough6Field() {
        String sixField = "0 0 8 * * ?";
        assertThat(FlowScheduleService.toQuartzCron(sixField)).isEqualTo(sixField);
    }

    @Test
    void toQuartzCron_passthrough7Field() {
        String sevenField = "0 0 8 ? * MON 2025";
        assertThat(FlowScheduleService.toQuartzCron(sevenField)).isEqualTo(sevenField);
    }

    @Test
    void toQuartzCron_rejectsInvalidFieldCount() {
        assertThatThrownBy(() -> FlowScheduleService.toQuartzCron("0 8"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toQuartzCron_handlesDayOfWeekConflict() {
        // day=15, weekday=1 → Quartz requires ? for one of them
        String result = FlowScheduleService.toQuartzCron("0 8 15 * 1");
        assertThat(result).isEqualTo("0 0 8 15 * ?");
    }
}
