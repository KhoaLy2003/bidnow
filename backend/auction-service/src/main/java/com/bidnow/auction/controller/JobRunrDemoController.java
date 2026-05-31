package com.bidnow.auction.controller;

import com.bidnow.auction.job.DemoJob;
import com.bidnow.common.dto.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.jobrunr.scheduling.BackgroundJob;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/demo/jobs")
@RequiredArgsConstructor
public class JobRunrDemoController {

    private final DemoJob demoJob;

    @PostMapping("/enqueue")
    public BaseResponse<String> enqueue(
            @RequestParam(defaultValue = "Hello from JobRunr!") String message) {
        var jobId = BackgroundJob.<DemoJob>enqueue(job -> job.execute(message));
        return BaseResponse.success("Job enqueued: " + jobId);
    }

    @PostMapping("/schedule")
    public BaseResponse<String> schedule(
            @RequestParam(defaultValue = "Hello from scheduled JobRunr!") String message,
            @RequestParam(defaultValue = "10") int seconds) {
        var jobId = BackgroundJob.<DemoJob>schedule(
                Instant.now().plusSeconds(seconds),
                job -> job.execute(message));
        return BaseResponse.success("Job scheduled in " + seconds + "s: " + jobId);
    }

    @PostMapping("/failing")
    public BaseResponse<String> failing(
            @RequestParam(defaultValue = "This will fail!") String message) {
        var jobId = BackgroundJob.<DemoJob>enqueue(job -> job.executeFailing(message));
        return BaseResponse.success("Failing job enqueued: " + jobId);
    }
}
