package ai.pipestream.echo;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/** Explicitly disables Consul registration for startup tests. */
public class EchoNoRegistrationTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "no-registration";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        config.put("pipestream.registration.enabled", "false");
        config.put("pipestream.module.worker-loop.enabled", "false");
        config.put("quarkus.http.port", "0");
        config.put("quarkus.http.test-port", "0");
        return config;
    }
}
