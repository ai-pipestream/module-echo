package ai.pipestream.module.echo;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables processing buffer functionality for echo module testing.
 */
public class ProcessingBufferEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "processing.buffer.enabled", "true",
                "processing.buffer.capacity", "10",
                "processing.buffer.directory", "target/test-data/enabled",
                "processing.buffer.prefix", "enabled_test"
        );
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}