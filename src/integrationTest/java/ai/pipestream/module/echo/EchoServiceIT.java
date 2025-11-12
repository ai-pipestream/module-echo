package ai.pipestream.module.echo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.data.module.MutinyPipeStepProcessorGrpc;
import ai.pipestream.data.util.proto.PipeDocTestDataFactory;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.TimeUnit;

/**
 * Integration test that runs against the packaged application (JAR, native, or container).
 *
 * We will allocate a real client and test it against our running service
 *
 */
@QuarkusIntegrationTest
class EchoServiceIT extends EchoServiceTestBase {

    private static ManagedChannel channel;
    private static MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub echoService;

    @BeforeAll
    public static void setup() {
        int port = Integer.parseInt(System.getProperty("quarkus.http.test-port", "8081"));
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        echoService = MutinyPipeStepProcessorGrpc.newMutinyStub(channel);
    }

    @AfterAll
    public static void teardown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Override
    protected MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub getEchoService() {
        return echoService;
    }

    @Override
    protected PipeDocTestDataFactory getTestDataFactory() {
        return new PipeDocTestDataFactory();
    }

    @Override
    protected String getApplicationName() {
        return "echo";
    }

    //NOTE: earlier we would test this by using REST endpoints - no more.  We need to run the real client and allocate it's managed channel
    //use a mutiny stub too!  we would run this test directory with all the test

}
