package ai.pipestream.echo.health;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthGrpc;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.TimeUnit;

/**
 * Real gRPC client against the in-process Quarkus test server (unified HTTP/gRPC port).
 */
@QuarkusTest
class EchoGrpcHealthTest extends EchoGrpcHealthTestBase {

    @ConfigProperty(name = "quarkus.http.test-port")
    int httpPort;

    private ManagedChannel channel;

    @BeforeEach
    void connect() {
        channel = ManagedChannelBuilder.forAddress("localhost", httpPort)
                .usePlaintext()
                .build();
    }

    @AfterEach
    void disconnect() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Override
    protected HealthGrpc.HealthBlockingStub healthService() {
        return HealthGrpc.newBlockingStub(channel);
    }
}
