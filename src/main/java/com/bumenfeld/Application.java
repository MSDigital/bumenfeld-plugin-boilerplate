package com.bumenfeld;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Application {

    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private Application() {
    }

    public static void main(String[] args) {
        if (!RUNNING.compareAndSet(false, true)) {
            System.err.println("Application is already running");
            return;
        }

        System.out.println("Bumenfeld boilerplate is ready.");
        // Application entrypoint can be extended by future projects.
        // Add task scheduling, configuration loading, or other framework integrations here.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Shutting down gracefully.")));

        if (args.length > 0) {
            System.out.println("Received arguments:");
            for (String arg : args) {
                System.out.printf(" - %s%n", arg);
            }
        }
    }
}
