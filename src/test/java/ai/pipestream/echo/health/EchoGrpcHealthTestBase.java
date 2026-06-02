package ai.pipestream.echo.health;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared assertions for gRPC health against a running Quarkus server (unified HTTP/gRPC port).
 */
public abstract class EchoGrpcHealthTestBase {

    protected abstract HealthGrpc.HealthBlockingStub healthService();

    @Test
    void defaultHealthCheck_isServing() {
        HealthCheckResponse response = healthService().check(HealthCheckRequest.getDefaultInstance());
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }

    @Test
    void unknownServiceHealthCheck_isUnknown() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
                .setService("com.example.NonExistentService")
                .build();
        HealthCheckResponse response = healthService().check(request);
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.UNKNOWN);
    }
}
