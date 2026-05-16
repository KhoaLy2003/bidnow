package com.bidnow.media.controller;

import com.bidnow.media.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/test")
@RequiredArgsConstructor
@Tag(name = "Test Endpoints", description = "Endpoints for internal testing and verification")
public class TestController {

    private final EmailService emailService;

    @Operation(summary = "Test email sending", description = "Sends a simple test email to verify SMTP configuration.")
    @GetMapping("/email")
    public String testEmail(
            @Parameter(description = "Recipient email address", example = "test@example.com")
            @RequestParam String to) {
        emailService.sendSimpleEmail(to, "Test Email from BidNow", "Hello! This is a test email to verify MailTrap configuration.");
        return "Test email sent to " + to;
    }
}
