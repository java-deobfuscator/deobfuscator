package com.javadeobfuscator.deobfuscator.progress;

import com.javadeobfuscator.deobfuscator.progress.impl.NopProgressChangeListener;
import com.javadeobfuscator.deobfuscator.progress.task.BasicProgressTask;
import com.javadeobfuscator.deobfuscator.progress.task.ProgressTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProgressMonitor {
    private final List<ProgressTask> taskList = new ArrayList<>();

    private ProgressChangeListener changeListener;

    public ProgressMonitor() {
        this.changeListener = new NopProgressChangeListener();
    }

    public void setChangeListener(ProgressChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    public <T extends ProgressTask> TaskWrapper<T> createObjective(T objective) {
        taskList.add(objective);
        changeListener.onObjectiveChange(objective);
        return new TaskWrapper<>(objective);
    }

    public List<ProgressTask> getTaskList() {
        return Collections.unmodifiableList(taskList);
    }

    public class TaskWrapper<T extends ProgressTask> {
        private final T task;

        public TaskWrapper(T task) {
            this.task = task;
        }

        public void close() {
            taskList.remove(task);
        }

        public T getTask() {
            return task;
        }

        public void setProgress(int progress) {
            ((BasicProgressTask) task).setProgress(progress);
            changeListener.onProgressUpdate(task, progress);
        }

        public void increment(int amount) {
            setProgress(task.getCurrentProgress() + amount);
        }

        public void increment() {
            increment(1);
        }
    }
}
