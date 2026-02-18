package com.rpacloud.execution.schedule;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowScheduleService {

    private static final String JOB_GROUP = "flow-triggers";
    private final Scheduler scheduler;

    public void syncSchedule(long flowId, String cronExpression, boolean isActive) {
        JobKey jobKey = jobKey(flowId);
        TriggerKey triggerKey = triggerKey(flowId);

        try {
            if (!isActive || cronExpression == null || cronExpression.isBlank()) {
                removeJob(jobKey, triggerKey);
                return;
            }

            String quartzCron = toQuartzCron(cronExpression);

            JobDetail job = JobBuilder.newJob(FlowTriggerJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("flowId", flowId)
                    .storeDurably(false)
                    .requestRecovery(true)
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(quartzCron)
                            .withMisfireHandlingInstructionDoNothing())
                    .build();

            if (scheduler.checkExists(jobKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
                log.info("Rescheduled flow {} with cron '{}'", flowId, cronExpression);
            } else {
                scheduler.scheduleJob(job, trigger);
                log.info("Scheduled flow {} with cron '{}'", flowId, cronExpression);
            }
        } catch (SchedulerException e) {
            log.error("Failed to sync schedule for flow {}: {}", flowId, e.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to sync schedule: " + e.getMessage());
        }
    }

    public void removeSchedule(long flowId) {
        try {
            removeJob(jobKey(flowId), triggerKey(flowId));
        } catch (SchedulerException e) {
            log.error("Failed to remove schedule for flow {}: {}", flowId, e.getMessage());
            // Removal failure is non-critical; log but don't block flow deletion
        }
    }

    private void removeJob(JobKey jobKey, TriggerKey triggerKey) throws SchedulerException {
        if (scheduler.checkExists(triggerKey)) {
            scheduler.unscheduleJob(triggerKey);
            log.info("Unscheduled trigger {}", triggerKey);
        }
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("Deleted job {}", jobKey);
        }
    }

    /**
     * Convert 5-field cron (min hour day month weekday) to Quartz 6-field (sec min hour day month weekday).
     * Prepends "0" as seconds field.
     */
    static String toQuartzCron(String cron) {
        String trimmed = cron.trim();
        String[] fields = trimmed.split("\\s+");
        if (fields.length == 6 || fields.length == 7) {
            return trimmed; // already Quartz format
        }
        if (fields.length == 5) {
            // Standard unix cron: min hour day month weekday
            // Quartz: sec min hour dayOfMonth month dayOfWeek
            String dayOfMonth = fields[2];
            String dayOfWeek = fields[4];
            // Quartz requires day-of-month or day-of-week to be '?' if the other is specified
            if (!"*".equals(dayOfMonth) && !"?".equals(dayOfWeek)) {
                dayOfWeek = "?";
            } else if ("*".equals(dayOfMonth) && "*".equals(dayOfWeek)) {
                dayOfWeek = "?";
            }
            return String.join(" ", "0", fields[0], fields[1], dayOfMonth, fields[3], dayOfWeek);
        }
        throw new IllegalArgumentException("Invalid cron expression: " + cron);
    }

    private static JobKey jobKey(long flowId) {
        return JobKey.jobKey("flow-" + flowId, JOB_GROUP);
    }

    private static TriggerKey triggerKey(long flowId) {
        return TriggerKey.triggerKey("flow-" + flowId, JOB_GROUP);
    }
}
