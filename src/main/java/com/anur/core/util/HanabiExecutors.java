package com.anur.core.util;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Created by Anur IjuoKaruKas on 2/1/2019
 */
public class HanabiExecutors {

    private static Logger logger = LoggerFactory.getLogger("HanabiExecutors");

    /**
     * 统一管理的线程池
     */
    private static ExecutorService Pool;

    /**
     * 线程池的 Queue
     */
    private static BlockingDeque<Runnable> MissionQueue;

    static {
        // 任务可无限堆积
        MissionQueue = new LinkedBlockingDeque<>();

        int coreCount = Runtime.getRuntime()
                               .availableProcessors();
        int threadCount = coreCount * 2;
        logger.info("创建 Hanabi 线程池 => 机器核心数为 {}, 故创建线程 {} 个", coreCount, threadCount);
        Pool = new ThreadPoolExecutor(threadCount, threadCount, 5, TimeUnit.MILLISECONDS, MissionQueue, new ThreadFactoryBuilder().setNameFormat("Hana Pool - %s")
                                                                                                                                  .setDaemon(true)
                                                                                                                                  .build());
    }

    public static Future<?> submit(Runnable runnable) {
        return Pool.submit(runnable);
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return Pool.submit(task);
    }
}
