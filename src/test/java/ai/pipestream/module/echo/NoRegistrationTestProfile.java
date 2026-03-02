package ai.pipestream.module.echo;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that explicitly disables registration.
 */
public class NoRegistrationTestProfile implements QuarkusTestProfile {
    
    @Override
    public String getConfigProfile() {
        return "no-registration";
    }
    
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        // Explicitly disable registration using the correct platform property
        config.put("pipestream.registration.enabled", "false");
        
        // Define direct address for dynamic-grpc to find the service without Consul
        // Use the default gRPC port 9000 for tests if not overridden
        config.put("quarkus.dynamic-grpc.service.echo.address", "localhost:${quarkus.http.test-port:9000}");
        
        return config;
    }
}
