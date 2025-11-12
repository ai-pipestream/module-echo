package ai.pipestream.module.echo;

import ai.pipestream.data.module.MutinyPipeStepProcessorGrpc;
import ai.pipestream.data.util.proto.PipeDocTestDataFactory;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@QuarkusTest
class EchoServiceTest extends EchoServiceTestBase {

    @GrpcClient
    MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub echoService;

    @Inject
    PipeDocTestDataFactory pipeDocTestDataFactory;

    @Inject
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @Override
    protected String getApplicationName() {
        return applicationName;
    }

    @Override
    protected MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub getEchoService() {
        // Return the echo service for testing
        return echoService;
    }

    @Override
    protected PipeDocTestDataFactory getTestDataFactory() {
        return pipeDocTestDataFactory;
    }


}