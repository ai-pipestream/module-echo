package ai.pipestream.module.echo;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that explicitly disables registration.
 * This profile uses the application-no-registration.properties configuration.
 */
public class NoRegistrationTestProfile implements QuarkusTestProfile {
    
    @Override
    public String getConfigProfile() {
        return "no-registration";
    }
    
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        // Explicitly disable registration - use correct property name from @IfBuildProperty
        config.put("module.registration.enabled", "false");
        // Use random port for tests with unified server
//        config.put("quarkus.http.test-port", "");
        config.put("quarkus.grpc.server.use-separate-server", "false");
        // Don't configure echo client - let it use auto-test-config
        return config;
    }
}