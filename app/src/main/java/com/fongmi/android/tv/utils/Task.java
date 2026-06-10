package com.fongmi.android.tv.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.crawler.SpiderDebug;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Task {

    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final int QUEUE_CAPACITY = 128;
    private static final RejectedExecutionHandler REJECT_HANDLER = (r, e) -> SpiderDebug.log("task", "rejected task from pool=%s", e.toString());
    private static final ListeningExecutorService executor = MoreExecutors.listeningDecorator(new ThreadPoolExecutor(Math.max(2, CORES), Math.max(2, CORES), 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY), REJECT_HANDLER));
    private static final ListeningExecutorService largeExecutor = MoreExecutors.listeningDecorator(new ThreadPoolExecutor(Math.max(4, CORES * 2), Math.max(4, CORES * 2), 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY), REJECT_HANDLER));
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1, REJECT_HANDLER);

    public static ListeningExecutorService executor() {
        return executor;
    }

    public static ListeningExecutorService largeExecutor() {
        return largeExecutor;
    }

    public static ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public static Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    public static Future<?> submitLarge(Runnable task) {
        return largeExecutor.submit(task);
    }

    public static void execute(Runnable task) {
        executor.execute(task);
    }

    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(task, delay, unit);
    }

    public static <T> FutureCallback<T> callback(Consumer<T> onSuccess) {
        return callback(onSuccess, null);
    }

    public static <T> FutureCallback<T> callback(Consumer<T> onSuccess, @Nullable Consumer<Throwable> onFailure) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(@NonNull Throwable error) {
                if (onFailure != null) onFailure.accept(error);
            }
        };
    }
}
