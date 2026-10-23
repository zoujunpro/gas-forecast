package com.gas.forecast.common.threadpool;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 带任务全生命周期日志的线程池。
 *
 * <p>请使用 {@link #executeTracked(String, String, Runnable)} 或
 * {@link #submitTracked(String, String, Callable)} 提交任务，以保证提交、执行、成功、失败和拒绝日志完整。</p>
 */
public final class TraceableThreadPoolExecutor extends ThreadPoolExecutor {
    private static final Logger log = LoggerFactory.getLogger(TraceableThreadPoolExecutor.class);
    private static final String TRACE_ID_KEY = "traceId";

    private final String poolName;

    TraceableThreadPoolExecutor(ThreadPoolConfig config) {
        super(config.corePoolSize(), config.maximumPoolSize(), config.keepAliveTime(), config.keepAliveUnit(),
                new LinkedBlockingQueue<>(config.queueCapacity()), new NamedThreadFactory(config.poolName()),
                new LoggingAbortPolicy(config.poolName()));
        this.poolName = config.poolName();
    }

    public void executeTracked(String taskName, String traceId, Runnable task) {
        TraceableRunnable tracedTask = new TraceableRunnable(poolName, taskName, traceId, task);
        submitted(tracedTask);
        execute(tracedTask);
    }

    public Future<?> submitTracked(String taskName, String traceId, Runnable task) {
        return submitTracked(taskName, traceId, () -> {
            task.run();
            return null;
        });
    }

    public <T> Future<T> submitTracked(String taskName, String traceId, Callable<T> task) {
        TraceableCallable<T> tracedTask = new TraceableCallable<>(poolName, taskName, traceId, task);
        submitted(tracedTask);
        return super.submit(tracedTask);
    }

    private void submitted(TraceableTask task) {
        log.info("[thread-pool] task submitted: pool={}, taskId={}, taskName={}, traceId={}, activeCount={}, poolSize={}, queueSize={}",
                poolName, task.taskId, task.taskName, task.traceId, getActiveCount(), getPoolSize(), getQueue().size());
    }

    private static String requiredTaskName(String taskName) {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("线程池任务名称不能为空");
        }
        return taskName;
    }

    private abstract static class TraceableTask {
        final String poolName;
        final String taskId = UUID.randomUUID().toString();
        final String taskName;
        final String traceId;

        TraceableTask(String poolName, String taskName, String traceId) {
            this.poolName = poolName;
            this.taskName = requiredTaskName(taskName);
            this.traceId = traceId == null || traceId.isBlank() ? taskId : traceId;
        }

        final Map<String, String> putTraceId() {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.put(TRACE_ID_KEY, traceId);
            return previous;
        }

        final void restoreMdc(Map<String, String> previous) {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }

        final void started() {
            log.info("[thread-pool] task started: pool={}, taskId={}, taskName={}, traceId={}, thread={}",
                    poolName, taskId, taskName, traceId, Thread.currentThread().getName());
        }

        final void succeeded(long startedAt) {
            log.info("[thread-pool] task succeeded: pool={}, taskId={}, taskName={}, traceId={}, durationMs={}",
                    poolName, taskId, taskName, traceId, System.currentTimeMillis() - startedAt);
        }

        final void failed(long startedAt, Throwable exception) {
            log.error("[thread-pool] task failed: pool={}, taskId={}, taskName={}, traceId={}, durationMs={}",
                    poolName, taskId, taskName, traceId, System.currentTimeMillis() - startedAt, exception);
        }
    }

    private static final class TraceableRunnable extends TraceableTask implements Runnable {
        private final Runnable delegate;

        private TraceableRunnable(String poolName, String taskName, String traceId, Runnable delegate) {
            super(poolName, taskName, traceId);
            if (delegate == null) {
                throw new IllegalArgumentException("线程池任务不能为空");
            }
            this.delegate = delegate;
        }

        @Override
        public void run() {
            Map<String, String> previous = putTraceId();
            long startedAt = System.currentTimeMillis();
            started();
            try {
                delegate.run();
                succeeded(startedAt);
            } catch (RuntimeException | Error exception) {
                failed(startedAt, exception);
                throw exception;
            } finally {
                restoreMdc(previous);
            }
        }
    }

    private static final class TraceableCallable<T> extends TraceableTask implements Callable<T> {
        private final Callable<T> delegate;

        private TraceableCallable(String poolName, String taskName, String traceId, Callable<T> delegate) {
            super(poolName, taskName, traceId);
            if (delegate == null) {
                throw new IllegalArgumentException("线程池任务不能为空");
            }
            this.delegate = delegate;
        }

        @Override
        public T call() throws Exception {
            Map<String, String> previous = putTraceId();
            long startedAt = System.currentTimeMillis();
            started();
            try {
                T result = delegate.call();
                succeeded(startedAt);
                return result;
            } catch (Exception | Error exception) {
                failed(startedAt, exception);
                throw exception;
            } finally {
                restoreMdc(previous);
            }
        }
    }

    private static final class LoggingAbortPolicy implements RejectedExecutionHandler {
        private final String poolName;

        private LoggingAbortPolicy(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            log.error("[thread-pool] task rejected: pool={}, activeCount={}, poolSize={}, queueSize={}, completedTaskCount={}, shutdown={}",
                    poolName, executor.getActiveCount(), executor.getPoolSize(), executor.getQueue().size(),
                    executor.getCompletedTaskCount(), executor.isShutdown());
            throw new RejectedExecutionException("线程池任务被拒绝：" + poolName);
        }
    }
}
