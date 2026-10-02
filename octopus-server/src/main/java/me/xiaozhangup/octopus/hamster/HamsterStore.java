package me.xiaozhangup.octopus.hamster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.logging.log4j.status.StatusLogger;

final class HamsterStore implements Runnable {
    private static final Gson GSON = new Gson();
    private static final long FILE_LIMIT = 16L * 1024 * 1024;
    private static final Pattern FILE_NAME = Pattern.compile("hamster-\\d{4}-\\d{2}-\\d{2}-[0-9a-f-]{36}-\\d+\\.jsonl");
    private final HamsterRecorder recorder;
    private final Path directory;
    private final int retentionDays;
    private final long maxDiskBytes;
    private final long startedAt = System.currentTimeMillis();
    private final LinkedHashMap<String, Boolean> groups = new LinkedHashMap<>(1024, 0.75f, true);
    private OutputStream output;
    private Path currentFile;
    private LocalDate date;
    private int part;
    private long size;
    private long lossSequence;

    HamsterStore(HamsterRecorder recorder, Path directory, int retentionDays, long maxDiskBytes) throws IOException {
        this.recorder = recorder;
        this.directory = directory;
        this.retentionDays = retentionDays;
        this.maxDiskBytes = maxDiskBytes;
        Files.createDirectories(directory);
        try {
            this.rotate();
        } catch (IOException e) {
            if (this.output != null) this.output.close();
            throw e;
        }
    }

    @Override
    public void run() {
        long flushed = System.nanoTime();
        long cleaned = flushed;
        try {
            while (this.recorder.accepting || !this.recorder.queue.isEmpty()) {
                HamsterRecorder.ErrorRecord record = this.recorder.queue.poll(100, TimeUnit.MILLISECONDS);
                if (record != null) this.writeError(record);
                this.writeLoss();
                long now = System.nanoTime();
                if (now - flushed >= TimeUnit.SECONDS.toNanos(1)) {
                    this.output.flush();
                    flushed = now;
                }
                if (now - cleaned >= TimeUnit.MINUTES.toNanos(1)) {
                    this.output.flush();
                    this.cleanup();
                    cleaned = now;
                }
            }
            this.writeLoss();
        } catch (IOException e) {
            this.recorder.accepting = false;
            this.recorder.queue.clear();
            StatusLogger.getLogger().error("Hamster stopped recording after a file error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                this.output.close();
                this.cleanup();
            } catch (IOException e) {
                StatusLogger.getLogger().error("Cannot close Hamster output", e);
            }
        }
    }

    private void writeError(HamsterRecorder.ErrorRecord record) throws IOException {
        boolean first = !this.groups.containsKey(record.fingerprint());
        byte[] bytes = encode(this.errorJson(record, first));
        if (this.size + bytes.length > FILE_LIMIT || !LocalDate.now().equals(this.date)) {
            this.rotate();
            bytes = encode(this.errorJson(record, true));
        }
        this.write(bytes);
        this.groups.put(record.fingerprint(), Boolean.TRUE);
        if (this.groups.size() > 1024) this.groups.remove(this.groups.keySet().iterator().next());
    }

    private JsonObject errorJson(HamsterRecorder.ErrorRecord record, boolean first) {
        JsonObject json = this.base(first ? "issue" : "occurrence");
        json.addProperty("id", this.recorder.sessionId + ':' + record.sequence());
        json.addProperty("issue_id", record.fingerprint());
        json.addProperty("fingerprint", record.fingerprint());
        json.addProperty("time", record.time());
        json.addProperty("level", record.level());
        json.addProperty("logger", record.logger());
        json.addProperty("thread", record.thread());
        json.addProperty("message", record.message());
        json.addProperty("exception", record.exception());
        json.add("context", GSON.toJsonTree(record.context()));
        if (first) {
            json.addProperty("stack", record.stack());
            json.addProperty("truncated", record.truncated());
            json.add("breadcrumbs", GSON.toJsonTree(record.breadcrumbs()));
        }
        return json;
    }

    private void writeLoss() throws IOException {
        long count = this.recorder.dropped.getAndSet(0);
        if (count == 0) return;
        JsonObject json = this.base("loss");
        json.addProperty("id", this.recorder.sessionId + ":loss:" + ++this.lossSequence);
        json.addProperty("time", System.currentTimeMillis());
        json.addProperty("count", count);
        json.addProperty("reason", "queue_full");
        byte[] bytes = encode(json);
        if (this.size + bytes.length > FILE_LIMIT || !LocalDate.now().equals(this.date)) this.rotate();
        this.write(bytes);
    }

    private void rotate() throws IOException {
        if (this.output != null) this.output.close();
        this.date = LocalDate.now();
        this.currentFile = this.directory.resolve("hamster-" + this.date + '-' + this.recorder.sessionId + '-' + String.format("%04d", ++this.part) + ".jsonl");
        this.output = new BufferedOutputStream(Files.newOutputStream(this.currentFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
        this.size = 0;
        this.groups.clear();
        JsonObject session = this.base("session");
        session.addProperty("started_at", this.startedAt);
        session.addProperty("server", this.recorder.serverVersion);
        session.addProperty("java", System.getProperty("java.version"));
        session.addProperty("part", this.part);
        this.write(encode(session));
        this.output.flush();
        this.cleanup();
    }

    private JsonObject base(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", HamsterRecorder.SCHEMA_VERSION);
        json.addProperty("type", type);
        json.addProperty("session_id", this.recorder.sessionId);
        return json;
    }

    private static byte[] encode(JsonObject json) {
        return (GSON.toJson(json) + '\n').getBytes(StandardCharsets.UTF_8);
    }

    private void write(byte[] bytes) throws IOException {
        this.output.write(bytes);
        this.size += bytes.length;
    }

    private void cleanup() throws IOException {
        record FileInfo(Path path, long modified, long bytes) {}
        ArrayList<FileInfo> files = new ArrayList<>();
        long total = this.size;
        try (var paths = Files.list(this.directory)) {
            for (Path path : paths.toList()) {
                if (path.equals(this.currentFile) || !FILE_NAME.matcher(path.getFileName().toString()).matches()
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                long bytes = Files.size(path);
                files.add(new FileInfo(path, Files.getLastModifiedTime(path).toMillis(), bytes));
                total += bytes;
            }
        }
        files.sort(Comparator.comparingLong(FileInfo::modified));
        long expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(this.retentionDays);
        for (FileInfo file : files) {
            if (file.modified() < expiry || total > this.maxDiskBytes) {
                Files.delete(file.path());
                total -= file.bytes();
            }
        }
    }
}
