package com.example.therapify.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one way this feature can fail completely and silently.
 *
 * The application runs with spring.main.lazy-initialization=true. A lazy bean is never
 * instantiated, so ScheduledAnnotationBeanPostProcessor never inspects it and its @Scheduled
 * method is never registered: the job would simply never run, with nothing in the logs to say
 * so. AppointmentCompletionJob carries @Lazy(false) for exactly that reason, and this test
 * boots the context with lazy initialisation ON to prove the cron really is armed.
 */
@SpringBootTest(properties = "spring.main.lazy-initialization=true")
@ActiveProfiles("test")
class AppointmentCompletionSchedulingTest {

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @Test
    void theHourlyCompletionCronIsRegisteredEvenUnderLazyInitialization() {

        Set<ScheduledTask> tasks = scheduledTaskHolder.getScheduledTasks();

        CronTask completionTask = tasks.stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filter(task -> describe(task).contains("completeFinishedAppointmentsScheduled"))
                .findFirst()
                .orElse(null);

        assertTrue(completionTask != null,
                "el cron de completado no quedó registrado: " + tasks.stream().map(ScheduledTask::getTask).map(this::describe).toList());

        assertEquals("0 0 * * * *", completionTask.getExpression());
    }

    private String describe(Task task) {
        return task.toString();
    }
}
