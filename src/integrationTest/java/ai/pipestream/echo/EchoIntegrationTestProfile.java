package ai.pipestream.echo;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Packaged-JAR integration tests: no Consul registration, no engine worker loop,
 * random HTTP/gRPC port via {@code quarkus.http.port=0}.
 */
public class EchoIntegrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "pipestream.registration.enabled", "false",
                "pipestream.module.worker-loop.enabled", "false",
                "quarkus.grpc.server.use-separate-server", "false",
                "quarkus.http.port", "0",
                "quarkus.http.test-port", "0");
    }
}
