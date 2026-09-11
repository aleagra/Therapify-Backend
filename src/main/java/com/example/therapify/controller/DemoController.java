package com.example.therapify.controller;

import com.example.therapify.service.DemoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    /**
     * Only the demo accounts themselves or an admin can trigger this — never a real user,
     * even one who somehow knows the endpoint exists. Delegates to DemoService.isDemoAccount
     * so the emails are read from configuration (therapify.demo.*) in one place, not hardcoded
     * here too.
     */
    @PreAuthorize("@demoService.isDemoAccount(authentication.name) or hasRole('ADMIN')")
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        return ResponseEntity.ok(demoService.resetDemoData());
    }
}
