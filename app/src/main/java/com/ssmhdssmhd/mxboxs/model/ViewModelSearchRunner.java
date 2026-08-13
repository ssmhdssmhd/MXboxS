package com.ssmhdssmhd.mxboxs.model;

import com.ssmhdssmhd.mxboxs.Constant;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.bean.Site;
import com.ssmhdssmhd.mxboxs.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 搜索并发执行器：
 *  - 线程数按 CPU 核数自适应：min(8, cores*2)（原来硬编码 20，低配机/弱网反而互相抢带宽慢）
 *  - 全局快速搜索早停：首批命中 N 条（默认 20）后先回调 UI 让用户看到结果，
 *    剩余未完成站点延迟到后台继续跑，结果到了再 append。
 *  - 每站超时由 Constant.TIMEOUT_SEARCH 控制（已从 30s → 12s）。
 */
final class ViewModelSearchRunner {

    /** 快速搜索早停阈值：首批命中这么多条就先给用户看。 */
    private static final int FAST_STOP_COUNT = 20;
    /** 剩余未完成站点延迟多少毫秒后继续追结果（不抢占 UI 渲染）。 */
    private static final long TAIL_DELAY_MS = 250L;
    /** 共享搜索线程池：CPU 核数自适应。类加载时单例初始化。 */
    private static final ThreadPoolExecutor SHARED_SEARCH_POOL;
    static {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        int size = Math.min(8, cores * 2);
        SHARED_SEARCH_POOL = new ThreadPoolExecutor(size, size, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "search-worker"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        SHARED_SEARCH_POOL.allowCoreThreadTimeOut(true);
    }

    private final List<Future<?>> futures;
    private final AtomicInteger epoch;

    ViewModelSearchRunner() {
        futures = new CopyOnWriteArrayList<>();
        epoch = new AtomicInteger(0);
    }

    void start(List<Site> sites, Function<Site, Callable<Result>> taskFactory, Consumer<Result> onResult) {
        int current = nextEpoch();
        cancelFutures();
        // 快速早停计数：已成功返回的非空/有列表的 result 数。
        AtomicInteger hitCount = new AtomicInteger(0);
        for (Site site : sites) {
            execute(taskFactory.apply(site), current, onResult, hitCount);
        }
    }

    void stop() {
        nextEpoch();
        cancelFutures();
    }

    private int nextEpoch() {
        return epoch.incrementAndGet();
    }

    private void cancelFutures() {
        futures.forEach(future -> future.cancel(true));
        futures.clear();
    }

    private void execute(Callable<Result> callable, int current, Consumer<Result> onResult, AtomicInteger hitCount) {
        FluentFuture<Result> future = FluentFuture
                .from(SHARED_SEARCH_POOL.submit(callable))
                .withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
        futures.add(future);
        future.addCallback(Task.callback(
                result -> {
                    if (epoch.get() != current) return;
                    // 计数：有列表的才算"命中"，驱动快速早停
                    boolean countThis = result != null && result.getList() != null && !result.getList().isEmpty();
                    int now = countThis ? hitCount.incrementAndGet() : hitCount.get();
                    if (now >= FAST_STOP_COUNT && !countThis) {
                        // 已达到早停阈值：这批后续再到的结果延迟到后台追，
                        // 不抢占主线程渲染节奏。
                        Task.schedule(() -> onResult.accept(result), TAIL_DELAY_MS, TimeUnit.MILLISECONDS);
                    } else {
                        onResult.accept(result);
                    }
                }
        ), MoreExecutors.directExecutor());
    }
}
