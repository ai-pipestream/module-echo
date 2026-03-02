package ai.pipestream.module.echo;

import ai.pipestream.data.module.v1.MutinyPipeStepProcessorServiceGrpc;
import ai.pipestream.quarkus.dynamicgrpc.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

@QuarkusTest
class EchoServiceTest extends EchoServiceTestBase {

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
        // Use DynamicGrpcClientFactory to get the client, mimicking production usage
        return dynamicGrpcClientFactory.getClient("echo", MutinyPipeStepProcessorServiceGrpc::newMutinyStub)
                .await().indefinitely();
    }
}
