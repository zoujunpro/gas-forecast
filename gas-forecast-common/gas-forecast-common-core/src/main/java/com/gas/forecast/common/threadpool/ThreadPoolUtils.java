package com.gas.forecast.common.threadpool;

/** 线程池创建入口。 */
public final class ThreadPoolUtils {
    private ThreadPoolUtils() {
    }

    /**
     * 创建具名、容量受限且具备任务追踪日志的线程池。
     *
     * <p>调用方应在应用关闭时主动调用 {@link TraceableThreadPoolExecutor#shutdown()}。</p>
     */
    public static TraceableThreadPoolExecutor create(ThreadPoolConfig config) {
        return new TraceableThreadPoolExecutor(config);
    }
}
