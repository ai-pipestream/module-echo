package ai.pipestream.module.echo;

import ai.pipestream.data.module.v1.MutinyPipeStepProcessorServiceGrpc;
import ai.pipestream.quarkus.dynamicgrpc.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@QuarkusTest
class EchoServiceTest extends EchoServiceTestBase {

    @Inject
    DynamicGrpcClientFactory dynamicGrpcClientFactory;

    @Inject
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

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
