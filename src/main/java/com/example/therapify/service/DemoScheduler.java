package com.example.therapify.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatically resets the demo sandbox every 12 hours so it stays usable even if no
 * evaluator ever presses the manual reset button.
 */
@Component
public class DemoScheduler {

    private final DemoService demoService;

    public DemoScheduler(DemoService demoService) {
        this.demoService = demoService;
    }

    @Scheduled(cron = "0 0 0,12 * * *")
    public void resetDemoDataScheduled() {
        try {
            demoService.resetDemoData();
        } catch (Exception e) {
            System.err.println("⚠ No se pudo restablecer los datos demo automáticamente: " + e.getMessage());
        }
    }
}
