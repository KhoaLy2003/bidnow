package com.bidnow.auction.job;

import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DemoJob {

    @Job(name = "Demo: fire-and-forget", retries = 3)
    public void execute(String message) {
        log.info("DemoJob executed — message: {}", message);
    }

    @Job(name = "Demo: failing job", retries = 3)
    public void executeFailing(String message) {
        log.info("DemoJob failing attempt — message: {}", message);
        throw new RuntimeException("Intentional failure — message: " + message);
    }
}
