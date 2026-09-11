package com.example.therapify.config;

import com.example.therapify.service.DemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the fixed demo doctor/patient accounts exist on every startup, so recruiters and
 * evaluators always have a working login even on a fresh database.
 */
@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final DemoService demoService;

    public DemoDataInitializer(DemoService demoService) {
        this.demoService = demoService;
    }

    @Override
    public void run(String... args) {
        demoService.ensureDemoAccountsSeeded();
    }
}
