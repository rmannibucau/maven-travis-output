package com.github.rmannibucau.maven.travis;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.codehaus.plexus.component.annotations.Component;

@Component(role = EventSpy.class, hint = "rmannibucau-travis-output")
public class TravisOutput implements EventSpy {

    private final Collection<Object> events = new ArrayList<>();

    private ScheduledExecutorService executor;

    private ScheduledFuture<?> logTask;

    private boolean doDump;
    private boolean logEvents;

    @Override
    public void init(final Context context) {
        System.out.println("Travis output timer active");
        executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, TravisOutput.class.getSimpleName()));
        doDump = Boolean.getBoolean("rmannibucau.travis.dumpOnLog");
        logEvents = Boolean.getBoolean("rmannibucau.travis.logExecutionEvents");
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
        if (logEvents && ExecutionEvent.class.isInstance(event)) {
            final ExecutionEvent ee = ExecutionEvent.class.cast(event);
            if (ee.getType() != null) {
                onExecutionEvent(ee);
            }
        }
    }

    private void onExecutionEvent(final ExecutionEvent ee) {
        switch (ee.getType()) {
            case ProjectStarted:
                if (ee.getProject() != null) {
                    System.out.println("--- Project started: " + ee.getProject().getArtifactId());
                }
                break;
            case MojoStarted:
                if (ee.getMojoExecution() != null) {
                    System.out.println("    --- Mojo started: " +
                            ee.getMojoExecution().getGroupId() + ":" + ee.getMojoExecution().getArtifactId() + ":" + ee.getMojoExecution().getVersion() +
                            (ee.getMojoExecution().getExecutionId() != null ? "@" + ee.getMojoExecution().getExecutionId() : ""));
                }
                break;
            case MojoFailed:
                if (ee.getMojoExecution() != null) {
                    System.err.println("    --- Mojo failed:");
                    if (ee.getException() != null) {
                        ee.getException().printStackTrace();
                    }
                }
            default:
        }
    }

    private void log() {
        final Collection<Object> copy = getEventsAndFlush();
        if (copy.isEmpty()) {
            System.out.println("No event");

            if (doDump) { // only dump if nothing happens (hanging)
                Stream.of(ManagementFactory.getThreadMXBean().dumpAllThreads(false, false)).forEach(info -> {
                    System.out.println(info.getThreadName() + ':');
                    Stream.of(info.getStackTrace()).forEach(elt -> System.out.println("\tat " + elt));
                });
            }
        } else {
            System.out.println("Events executed:\n" + copy.stream().collect(groupingBy(Object::getClass)).entrySet().stream()
                    .sorted(comparing(e -> e.getValue().size()))
                    .map(e -> "  " + e.getKey().getSimpleName() + ": #" + e.getValue().size()).collect(joining("\n")));
        }
    }

    private synchronized Collection<Object> getEventsAndFlush() {
        final Collection<Object> copy = new ArrayList<>(events);
        events.clear();
        return copy;
    }
}
