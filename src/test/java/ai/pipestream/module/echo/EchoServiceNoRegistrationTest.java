package ai.pipestream.module.echo;
import ai.pipestream.data.util.proto.PipeDocTestDataFactory;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.data.module.MutinyPipeStepProcessorGrpc;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * This test verifies that the Echo service can start and function correctly
 * without registration enabled. It uses the application-no-registration.properties
 * configuration which explicitly disables registration.
 */
@QuarkusTest
@TestProfile(NoRegistrationTestProfile.class)
class EchoServiceNoRegistrationTest extends EchoServiceTestBase {

    private static final Logger LOG = Logger.getLogger(EchoServiceNoRegistrationTest.class);

    private ManagedChannel directChannel;
    private MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub directClient;

    @BeforeEach
    void setupDirectClient() {
        // Create direct gRPC client without CDI to test raw connection
        directChannel = ManagedChannelBuilder.forAddress("localhost", 49000)
            .usePlaintext()
            .build();
        directClient = MutinyPipeStepProcessorGrpc.newMutinyStub(directChannel);
        LOG.infof("Created direct gRPC client for localhost:49000");
    }

    @AfterEach
    void teardownDirectClient() {
        if (directChannel != null) {
            directChannel.shutdown();
        }
    }

    @GrpcClient
    MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub pipeStepProcessor;

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
        // Use direct client instead of CDI-injected one for testing
        return directClient;
    }

    @Override
    protected PipeDocTestDataFactory getTestDataFactory() {
        return pipeDocTestDataFactory;
    }

}