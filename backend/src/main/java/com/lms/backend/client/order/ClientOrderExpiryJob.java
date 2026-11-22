package com.lms.backend.client.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.lms.backend.shared.database.CustomerOrderReleaseRepository;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "lms.client.orders.expiry.enabled", havingValue = "true", matchIfMissing = true)
class ClientOrderExpiryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientOrderExpiryJob.class);
    private static final int BATCH_SIZE = 100;

    private final CustomerOrderReleaseRepository orders;
    private final ClientOrderReleaseService releases;

    ClientOrderExpiryJob(CustomerOrderReleaseRepository orders, ClientOrderReleaseService releases) {
        this.orders = orders;
        this.releases = releases;
    }

    @Scheduled(fixedDelayString = "${lms.client.orders.expiry.interval:PT1M}")
    public void sweep() {
        long afterId = Long.MIN_VALUE;
        while (true) {
            var candidates = orders.findExpiredOrderIdsAfter(afterId, BATCH_SIZE);
            for (long orderId : candidates) {
                try {
                    releases.expireIfDue(orderId);
                } catch (RuntimeException ex) {
                    // Each order has its own transaction. The next sweep starts from the database again.
                    LOGGER.warn("Could not expire Client order {}. Will retry on the next sweep.", orderId, ex);
                }
            }
            if (candidates.size() < BATCH_SIZE) {
                return;
            }
            afterId = candidates.getLast();
        }
    }
}
