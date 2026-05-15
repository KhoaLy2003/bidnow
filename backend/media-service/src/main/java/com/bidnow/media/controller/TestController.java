package com.bidnow.media.controller;

import com.bidnow.media.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/test")
@RequiredArgsConstructor
public class TestController {

    private final EmailService emailService;

    @GetMapping("/email")
    public String testEmail(@RequestParam String to) {
        emailService.sendSimpleEmail(to, "Test Email from BidNow", "Hello! This is a test email to verify MailTrap configuration.");
        return "Test email sent to " + to;
    }
}
