package ai.pipestream.module.echo;

import ai.pipestream.data.module.v1.MutinyPipeStepProcessorServiceGrpc;
import ai.pipestream.quarkus.dynamicgrpc.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

/**
 * This test verifies that the Echo service can start and function correctly
 * without registration enabled.
 */
@QuarkusTest
@TestProfile(NoRegistrationTestProfile.class)
class EchoServiceNoRegistrationTest extends EchoServiceTestBase {

    @Inject
    DynamicGrpcClientFactory dynamicGrpcClientFactory;

    @Inject
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @BeforeEach
    void setupConfig() {
        // Programmatically set the dynamic-grpc address to use the actual random gRPC test port
        int port = ConfigProvider.getConfig().getValue("quarkus.grpc.server.test-port", Integer.class);
        System.setProperty("quarkus.dynamic-grpc.service.echo.address", "localhost:" + port);
    }

    @Override
    protected String getApplicationName() {
        return applicationName;
    }

    @Override
    protected MutinyPipeStepProcessorServiceGrpc.MutinyPipeStepProcessorServiceStub getEchoService() {
        return dynamicGrpcClientFactory.getClient("echo", MutinyPipeStepProcessorServiceGrpc::newMutinyStub)
                .await().indefinitely();
    }
}
