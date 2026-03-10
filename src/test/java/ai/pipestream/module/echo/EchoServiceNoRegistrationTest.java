package ai.pipestream.module.echo;

import ai.pipestream.data.module.v1.MutinyPipeStepProcessorServiceGrpc;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * This test verifies that the Echo service can start and function correctly
 * without registration enabled.
 */
@QuarkusTest
@TestProfile(NoRegistrationTestProfile.class)
class EchoServiceNoRegistrationTest extends EchoServiceTestBase {

    @GrpcClient("echo")
    MutinyPipeStepProcessorServiceGrpc.MutinyPipeStepProcessorServiceStub echoService;

    @Inject
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @Override
    protected String getApplicationName() {
        return applicationName;
    }

    @Override
    protected MutinyPipeStepProcessorServiceGrpc.MutinyPipeStepProcessorServiceStub getEchoService() {
        return echoService;
    }
}
