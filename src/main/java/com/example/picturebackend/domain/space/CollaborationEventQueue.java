package com.example.picturebackend.domain.space;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;

@Component
public class CollaborationEventQueue {
    private final Disruptor<Event> disruptor = new Disruptor<>(
            (EventFactory<Event>) Event::new, 1024, Executors.defaultThreadFactory());
    private RingBuffer<Event> ringBuffer;

    @PostConstruct
    public void start() {
        disruptor.handleEventsWith((EventHandler<Event>) (event, sequence, endOfBatch) -> {
            Runnable action = event.action;
            event.action = null;
            if (action != null) action.run();
        });
        ringBuffer = disruptor.start();
    }

    public void publish(Runnable action) {
        long sequence = ringBuffer.next();
        try {
            ringBuffer.get(sequence).action = action;
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @PreDestroy
    public void stop() {
        disruptor.shutdown();
    }

    private static final class Event {
        private Runnable action;
    }
}
