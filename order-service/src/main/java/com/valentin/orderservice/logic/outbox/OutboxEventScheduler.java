package com.valentin.orderservice.logic.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventScheduler {

    private final OutboxEventPoller outboxEventPoller;

    @Scheduled(fixedDelayString = "${app.outbox.polling.fixed-delay-ms}")
    public void pollEvents() {
        try {
            outboxEventPoller.pollAndPublish();
        } catch (Exception exception) {
            log.error("Unexpected error during outbox polling", exception);
        }
    }
}
