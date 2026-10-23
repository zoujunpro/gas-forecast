package com.gas.forecast.common.threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 为线程池线程提供可检索的统一名称。 */
final class NamedThreadFactory implements ThreadFactory {
    private final ThreadGroup threadGroup;
    private final String namePrefix;
    private final AtomicInteger sequence = new AtomicInteger(1);

    NamedThreadFactory(String poolName) {
        this.threadGroup = Thread.currentThread().getThreadGroup();
        this.namePrefix = "gas-" + poolName + "-";
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(threadGroup, runnable, namePrefix + sequence.getAndIncrement(), 0);
        if (thread.isDaemon()) {
            thread.setDaemon(false);
        }
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }
        return thread;
    }
}
