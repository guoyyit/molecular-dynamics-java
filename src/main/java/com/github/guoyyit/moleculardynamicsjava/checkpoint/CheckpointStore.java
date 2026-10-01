package com.github.guoyyit.moleculardynamicsjava.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

public class CheckpointStore {
    static final int FORMAT_VERSION = 1;
    private static final String PREFIX = "checkpoint-";
    private static final String SUFFIX = ".json";

    private final Path directory;
    private final int retained;
    private final ObjectMapper mapper = new ObjectMapper();

    public CheckpointStore(Path directory, int retained) {
        this.directory = directory;
        this.retained = retained;
    }

    public void commit(SimulationState state, SimulationConfig config) throws IOException {
        Files.createDirectories(directory);
        Envelope envelope = new Envelope();
        envelope.version = FORMAT_VERSION;
        envelope.payload = new Payload(config, state);
        String payloadJson = mapper.writeValueAsString(envelope.payload);
        envelope.sha256 = sha256(payloadJson);
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope);
        Path target = directory.resolve(String.format("%s%012d%s", PREFIX, state.step, SUFFIX));
        Path temporary = directory.resolve(String.format(".%s%012d.%d.tmp", PREFIX, state.step,
                System.nanoTime()));
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        prune();
    }

    public Checkpoint latest() throws IOException {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().startsWith(PREFIX)
                            && path.getFileName().toString().endsWith(SUFFIX))
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::getFileName).reversed());
        IOException lastError = null;
        for (Path file : files) {
            try {
                return read(file);
            } catch (IOException error) {
                lastError = error;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return null;
    }

    private Checkpoint read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Envelope envelope;
        try {
            envelope = mapper.readValue(bytes, Envelope.class);
        } catch (IOException error) {
            throw new IOException("检查点无法解析 (可能已损坏): " + file.getFileName(), error);
        }
        if (envelope.version != FORMAT_VERSION) {
            throw new IOException("检查点版本不受支持: " + envelope.version
                    + " (" + file.getFileName() + ")");
        }
        String payloadJson = mapper.writeValueAsString(envelope.payload);
        String actual = sha256(payloadJson);
        if (!actual.equals(envelope.sha256)) {
            throw new IOException("检查点校验和不匹配 (可能已损坏): " + file.getFileName());
        }
        SimulationConfig config = mapper.convertValue(envelope.payload.config, SimulationConfig.class);
        SimulationState state = mapper.convertValue(envelope.payload.state, SimulationState.class);
        return new Checkpoint(state, config, file);
    }

    private void prune() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().startsWith(PREFIX)
                            && path.getFileName().toString().endsWith(SUFFIX))
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::getFileName));
        int removeCount = files.size() - retained;
        for (int i = 0; i < removeCount; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    private static final class Envelope {
        public int version;
        public String sha256;
        public Payload payload;

        Envelope() {
        }
    }

    private static final class Payload {
        public Object config;
        public Object state;

        Payload() {
        }

        Payload(Object config, Object state) {
            this.config = config;
            this.state = state;
        }
    }

    public static class Checkpoint {
        public final SimulationState state;
        public final SimulationConfig config;
        public final Path file;

        public Checkpoint(SimulationState state, SimulationConfig config, Path file) {
            this.state = state;
            this.config = config;
            this.file = file;
        }
    }
}
