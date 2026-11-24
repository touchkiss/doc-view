package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One sequence of replaceable read tasks owned by a preview popup. */
final class PreviewRequests {
    private final AtomicLong generation = new AtomicLong();
    private final ConcurrentMap<Long, ProgressIndicator> active = new ConcurrentHashMap<>();

    long invalidate() {
        long next = generation.incrementAndGet();
        active.values().forEach(ProgressIndicator::cancel);
        return next;
    }

    boolean isCurrent(long id) {
        return id == generation.get();
    }

    <T> void submit(Project project, String title, long id, Supplier<T> read,
                    Consumer<T> publish, Runnable canceled, Consumer<Throwable> failed) {
        new Task.Backgroundable(project, title, true) {
            private T result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (ApplicationManager.getApplication().isDispatchThread()) {
                    throw new IllegalStateException("Preview generation requires a background thread");
                }
                indicator.setIndeterminate(true);
                active.put(id, indicator);
                try {
                    if (!isCurrent(id) || project.isDisposed()) {
                        indicator.cancel();
                    }
                    result = ReadAction.computeBlocking(() -> {
                        indicator.checkCanceled();
                        T value = read.get();
                        indicator.checkCanceled();
                        return value;
                    });
                } finally {
                    active.remove(id, indicator);
                }
            }

            @Override
            public void onSuccess() {
                if (isCurrent(id) && !project.isDisposed()) {
                    try {
                        publish.accept(result);
                    } catch (RuntimeException exception) {
                        failed.accept(exception);
                    }
                }
            }

            @Override
            public void onCancel() {
                if (isCurrent(id) && !project.isDisposed()) {
                    canceled.run();
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                if (isCurrent(id) && !project.isDisposed()) {
                    failed.accept(error);
                }
            }
        }.queue();
    }
}
