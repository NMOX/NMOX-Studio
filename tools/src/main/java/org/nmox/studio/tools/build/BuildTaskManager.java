package org.nmox.studio.tools.build;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

/**
 * Manages build tasks and their execution.
 */
@ServiceProvider(service = BuildTaskManager.class)
public class BuildTaskManager {
    
    private static BuildTaskManager instance;
    private final ExecutorService executor;
    private final Map<String, BuildTask> runningTasks;
    private final Map<String, BuildTaskHistory> taskHistory;
    private final List<BuildTaskListener> listeners;
    private final BuildToolService buildService;
    
    public BuildTaskManager() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("BuildTask-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        this.runningTasks = new ConcurrentHashMap<>();
        this.taskHistory = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.buildService = BuildToolService.getInstance();
    }
    
    public static synchronized BuildTaskManager getInstance() {
        if (instance == null) {
            instance = new BuildTaskManager();
        }
        return instance;
    }
    
    /**
     * Creates and runs a new build task.
     */
    public BuildTask createBuildTask(File projectDir, BuildConfiguration config) {
        String taskId = UUID.randomUUID().toString();
        BuildTask task = new BuildTask(taskId, projectDir, BuildTask.TaskType.BUILD, config);
        
        executeTask(task);
        return task;
    }
    
    /**
     * Creates and runs a dev server task.
     */
    public BuildTask createServeTask(File projectDir, BuildConfiguration config) {
        String taskId = UUID.randomUUID().toString();
        BuildTask task = new BuildTask(taskId, projectDir, BuildTask.TaskType.SERVE, config);
        
        executeTask(task);
        return task;
    }
    
    /**
     * Creates and runs a test task.
     */
    public BuildTask createTestTask(File projectDir, BuildConfiguration config) {
        String taskId = UUID.randomUUID().toString();
        BuildTask task = new BuildTask(taskId, projectDir, BuildTask.TaskType.TEST, config);
        
        executeTask(task);
        return task;
    }
    
    /**
     * Creates and runs a lint task.
     */
    public BuildTask createLintTask(File projectDir, BuildConfiguration config) {
        String taskId = UUID.randomUUID().toString();
        BuildTask task = new BuildTask(taskId, projectDir, BuildTask.TaskType.LINT, config);
        
        executeTask(task);
        return task;
    }
    
    /**
     * Executes a custom npm script.
     */
    public BuildTask createCustomTask(File projectDir, String scriptName) {
        String taskId = UUID.randomUUID().toString();
        BuildTask task = new BuildTask(taskId, projectDir, scriptName);
        
        executeTask(task);
        return task;
    }
    
    private void executeTask(BuildTask task) {
        runningTasks.put(task.getId(), task);
        notifyTaskStarted(task);

        reportStart(task);

        CompletableFuture<BuildResult> future = null;
        
        switch (task.getType()) {
            case BUILD:
                future = buildService.build(task.getProjectDir(), task.getConfiguration());
                break;
            case SERVE:
                future = buildService.serve(task.getProjectDir(), task.getConfiguration());
                break;
            case TEST:
                future = buildService.test(task.getProjectDir(), task.getConfiguration());
                break;
            case LINT:
                future = buildService.lint(task.getProjectDir(), task.getConfiguration());
                break;
            case CUSTOM:
                future = buildService.runScript(task.getProjectDir(), task.getScriptName());
                break;
        }
        
        if (future != null) {
            task.setFuture(future);
            
            future.whenComplete((result, error) -> {
                if (error != null) {
                    task.setStatus(BuildTask.Status.FAILED);
                    task.setError(error.getMessage());
                } else {
                    task.setResult(result);
                    task.setStatus(result.isSuccess() ?
                        BuildTask.Status.COMPLETED : BuildTask.Status.FAILED);
                }

                task.setEndTime(System.currentTimeMillis());
                reportCompletion(task, error);
                runningTasks.remove(task.getId());
                addToHistory(task);
                notifyTaskCompleted(task);
            });
        }
    }

    /** The shared Output window tab all build tasks report into. */
    private static InputOutput buildIO() {
        try {
            return IOProvider.getDefault().getIO("Build", false);
        } catch (RuntimeException | LinkageError ex) {
            // output window unavailable (tests, stripped platform)
            return null;
        }
    }

    private void reportStart(BuildTask task) {
        InputOutput io = buildIO();
        if (io == null) {
            return;
        }
        io.select();
        io.getOut().println("Starting " + task.getName() + ": "
                + task.getProjectDir().getName());
    }

    private void reportCompletion(BuildTask task, Throwable error) {
        InputOutput io = buildIO();
        if (io == null) {
            return;
        }
        OutputWriter out = io.getOut();
        OutputWriter err = io.getErr();
        if (error != null) {
            err.println(task.getName() + " failed: " + error.getMessage());
            return;
        }
        BuildResult result = task.getResult();
        for (BuildResult.BuildMessage msg : result.getMessages()) {
            OutputWriter w = msg.getType() == BuildResult.BuildMessage.Type.ERROR ? err : out;
            StringBuilder line = new StringBuilder();
            switch (msg.getType()) {
                case ERROR -> line.append("[ERROR] ");
                case WARNING -> line.append("[WARN] ");
                case INFO -> line.append("[INFO] ");
                case SUCCESS -> line.append("[OK] ");
            }
            if (msg.getFile() != null) {
                line.append(msg.getFile());
                if (msg.getLine() > 0) {
                    line.append(':').append(msg.getLine());
                    if (msg.getColumn() > 0) {
                        line.append(':').append(msg.getColumn());
                    }
                }
                line.append(' ');
            }
            line.append(msg.getMessage());
            w.println(line.toString());
        }
        BuildResult.BuildStatistics stats = result.getStatistics();
        if (stats.getErrors() > 0 || stats.getWarnings() > 0) {
            out.println(stats.getErrors() + " error(s), " + stats.getWarnings() + " warning(s)");
        }
        String duration = formatDuration(result.getDuration());
        if (result.isSuccess()) {
            out.println(task.getName() + " completed successfully (" + duration + ")");
        } else {
            err.println(task.getName() + " failed (" + duration + ")");
        }
    }

    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%dm %ds", millis / 60000, (millis % 60000) / 1000);
        }
    }
    
    /**
     * Cancels a running task.
     */
    public void cancelTask(String taskId) {
        BuildTask task = runningTasks.get(taskId);
        if (task != null && task.getFuture() != null) {
            task.getFuture().cancel(true);
            task.setStatus(BuildTask.Status.CANCELLED);
            runningTasks.remove(taskId);
            notifyTaskCancelled(task);
        }
    }
    
    /**
     * Cancels all running tasks.
     */
    public void cancelAllTasks() {
        for (BuildTask task : runningTasks.values()) {
            cancelTask(task.getId());
        }
        buildService.stopAll();
    }
    
    /**
     * Gets all running tasks.
     */
    public Collection<BuildTask> getRunningTasks() {
        return new ArrayList<>(runningTasks.values());
    }
    
    /**
     * Gets task history for a project.
     */
    public BuildTaskHistory getHistory(File projectDir) {
        String key = projectDir.getAbsolutePath();
        return taskHistory.computeIfAbsent(key, k -> new BuildTaskHistory());
    }
    
    private void addToHistory(BuildTask task) {
        BuildTaskHistory history = getHistory(task.getProjectDir());
        history.addTask(task);
    }
    
    /**
     * Adds a task listener.
     */
    public void addTaskListener(BuildTaskListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes a task listener.
     */
    public void removeTaskListener(BuildTaskListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyTaskStarted(BuildTask task) {
        for (BuildTaskListener listener : listeners) {
            listener.taskStarted(task);
        }
    }
    
    private void notifyTaskCompleted(BuildTask task) {
        for (BuildTaskListener listener : listeners) {
            listener.taskCompleted(task);
        }
    }
    
    private void notifyTaskCancelled(BuildTask task) {
        for (BuildTaskListener listener : listeners) {
            listener.taskCancelled(task);
        }
    }
    
    /**
     * Shuts down the task manager.
     */
    public void shutdown() {
        cancelAllTasks();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    /**
     * Listener interface for build task events.
     */
    public interface BuildTaskListener {
        void taskStarted(BuildTask task);
        void taskCompleted(BuildTask task);
        void taskCancelled(BuildTask task);
        void taskProgress(BuildTask task, int progress);
    }
    
    /**
     * Represents a build task.
     */
    public static class BuildTask {
        public enum TaskType {
            BUILD, SERVE, TEST, LINT, CUSTOM
        }
        
        public enum Status {
            PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
        }
        
        private final String id;
        private final File projectDir;
        private final TaskType type;
        private final BuildConfiguration configuration;
        private final String scriptName;
        private final long startTime;
        private long endTime;
        private Status status;
        private BuildResult result;
        private String error;
        private CompletableFuture<BuildResult> future;
        
        public BuildTask(String id, File projectDir, TaskType type, BuildConfiguration configuration) {
            this.id = id;
            this.projectDir = projectDir;
            this.type = type;
            this.configuration = configuration;
            this.scriptName = null;
            this.startTime = System.currentTimeMillis();
            this.status = Status.RUNNING;
        }
        
        public BuildTask(String id, File projectDir, String scriptName) {
            this.id = id;
            this.projectDir = projectDir;
            this.type = TaskType.CUSTOM;
            this.configuration = BuildConfiguration.builder().build();
            this.scriptName = scriptName;
            this.startTime = System.currentTimeMillis();
            this.status = Status.RUNNING;
        }
        
        public String getId() { return id; }
        public File getProjectDir() { return projectDir; }
        public TaskType getType() { return type; }
        public BuildConfiguration getConfiguration() { return configuration; }
        public String getScriptName() { return scriptName; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public Status getStatus() { return status; }
        public BuildResult getResult() { return result; }
        public String getError() { return error; }
        public CompletableFuture<BuildResult> getFuture() { return future; }
        
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public void setStatus(Status status) { this.status = status; }
        public void setResult(BuildResult result) { this.result = result; }
        public void setError(String error) { this.error = error; }
        public void setFuture(CompletableFuture<BuildResult> future) { this.future = future; }
        
        public String getName() {
            if (type == TaskType.CUSTOM) {
                return scriptName;
            } else {
                return type.toString().toLowerCase();
            }
        }
        
        public long getDuration() {
            if (endTime > 0) {
                return endTime - startTime;
            } else {
                return System.currentTimeMillis() - startTime;
            }
        }
    }
    
    /**
     * Stores task history for a project.
     */
    public static class BuildTaskHistory {
        private static final int MAX_HISTORY_SIZE = 50;
        private final LinkedList<BuildTask> tasks = new LinkedList<>();
        
        public void addTask(BuildTask task) {
            tasks.addFirst(task);
            while (tasks.size() > MAX_HISTORY_SIZE) {
                tasks.removeLast();
            }
        }
        
        public List<BuildTask> getTasks() {
            return new ArrayList<>(tasks);
        }
        
        public BuildTask getLastTask() {
            return tasks.isEmpty() ? null : tasks.getFirst();
        }
        
        public void clear() {
            tasks.clear();
        }
    }
}