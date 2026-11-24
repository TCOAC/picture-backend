package com.example.picturebackend;

import com.example.picturebackend.domain.space.CollaborationEventQueue;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborationEventQueueTests {
    @Test
    void publishesEventsToSingleConsumerInOrder() throws Exception {
        CollaborationEventQueue queue = new CollaborationEventQueue();
        CountDownLatch completed = new CountDownLatch(3);
        AtomicInteger sum = new AtomicInteger();
        try {
            queue.start();
            queue.publish(() -> { sum.addAndGet(1); completed.countDown(); });
            queue.publish(() -> { sum.addAndGet(2); completed.countDown(); });
            queue.publish(() -> { sum.addAndGet(3); completed.countDown(); });
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(6, sum.get());
        } finally {
            queue.stop();
        }
    }
}
