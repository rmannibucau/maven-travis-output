package com.github.rmannibucau.maven.travis;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.maven.eventspy.EventSpy;
import org.codehaus.plexus.component.annotations.Component;

@Component(role = EventSpy.class, hint = "rmannibucau-travis-output")
public class TravisOutput implements EventSpy {

    private final Collection<Object> events = new ArrayList<>();

    private ScheduledExecutorService executor;

    private ScheduledFuture<?> logTask;

    @Override
    public void init(final Context context) {
        System.out.println("Travis output timer active");
        executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, TravisOutput.class.getSimpleName()));
        logTask = executor.scheduleWithFixedDelay(this::log, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void close() {
        if (logTask != null) {
            logTask.cancel(true);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public synchronized void onEvent(final Object event) {
        events.add(event);
    }

    private void log() {
        final Collection<Object> copy = getEventsAndFlush();
        System.out.println("Events executed:\n" + copy.stream().collect(groupingBy(Object::getClass)).entrySet().stream()
                .sorted(comparing(e -> e.getValue().size())).map(e -> "  " + e.getKey().getSimpleName() + ": #" + e.getValue().size())
                .collect(joining("\n")));
    }

    private synchronized Collection<Object> getEventsAndFlush() {
        final Collection<Object> copy = new ArrayList<>(events);
        events.clear();
        return copy;
    }
}
