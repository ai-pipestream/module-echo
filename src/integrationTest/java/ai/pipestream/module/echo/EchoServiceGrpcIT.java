package ai.pipestream.module.echo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.data.module.*;
import ai.pipestream.data.util.proto.PipeDocTestDataFactory;
import ai.pipestream.data.v1.PipeDoc;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import org.jboss.logging.Logger;

/**
 * Integration test for EchoService using real gRPC client.
 * This test runs against the packaged JAR as a black-box test.
 */
@QuarkusIntegrationTest
public class EchoServiceGrpcIT {

    private static final Logger LOG = Logger.getLogger(EchoServiceGrpcIT.class);

    private ManagedChannel channel;
    private PipeStepProcessor echoService;
    private PipeDocTestDataFactory pipeDocTestDataFactory = new PipeDocTestDataFactory();

    @BeforeEach
    void setUp() {
        // Get the test port from Quarkus configuration
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);

        LOG.infof("Connecting gRPC client to localhost:%d", port);

        // Create a real gRPC channel
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext() // No TLS for local tests
                .build();

        // Create the client using the generated PipeStepProcessorClient
        echoService = new PipeStepProcessorClient("echo", channel, (name, stub) -> stub);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testGetServiceRegistration() {
        // Prepare request
        RegistrationRequest request = RegistrationRequest.newBuilder().build();

        // Call the service and block for result
        ServiceRegistrationMetadata response = echoService.getServiceRegistration(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Module name should be 'echo'", response.getModuleName(), equalTo("echo"));
        assertThat("Health check should pass", response.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate service is healthy",
                  response.getHealthCheckMessage(), equalTo("Service is healthy"));

        LOG.infof("Received registration: %s", response.getModuleName());
    }

    @Test
    void testProcessData() {
        // Build test document
        PipeDoc document = pipeDocTestDataFactory.createComplexDocument(232);

        // Build request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("integration-test")
                        .setPipeStepName("echo")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        // Call the service and block for result
        ModuleProcessResponse response = echoService.processData(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        PipeDoc returnedDoc = response.getOutputDoc();
        assertThat("Returned document ID should match input ID", returnedDoc.getDocId(), equalTo("test-doc-123"));
        assertThat("Returned document body should match input body",
                  returnedDoc.getSearchMetadata().getBody(), equalTo("Test content from integration test"));
        assertThat("Metadata should contain echo_processed flag",returnedDoc.getSearchMetadata().getMetadataMap(), hasKey("echo_processed"));
        assertThat("Echo processed flag should be true",
                  returnedDoc.getSearchMetadata().getMetadataMap().get("echo_processed"), equalTo("true"));

        LOG.infof("Document processed successfully: %s", returnedDoc.getDocId());
    }

    @Test
    void testProcessDataWithEmptyDocument() {
        // Build empty document
        //TODO: may need to fix this by adding a new document type
        PipeDoc document = pipeDocTestDataFactory.createMinimalDocument(222);

        // Build request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("integration-test")
                        .setPipeStepName("echo")
                        .setStreamId("test-stream-2")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        // Call the service and block for result
        ModuleProcessResponse response = echoService.processData(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Response should be successful even with empty document", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        PipeDoc returnedDoc = response.getOutputDoc();
        assertThat("Returned document ID should match input ID", returnedDoc.getDocId(), equalTo(document.getDocId()));
        assertThat("Metadata should contain echo_processed flag", returnedDoc.getSearchMetadata().getTags().getTagDataMap(), hasKey("processed_by_echo"));

        LOG.info("Empty document processed successfully");
    }
}