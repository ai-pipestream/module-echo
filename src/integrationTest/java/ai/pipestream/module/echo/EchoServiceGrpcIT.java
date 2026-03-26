package ai.pipestream.module.echo;

import ai.pipestream.data.module.v1.*;
import ai.pipestream.data.module.v1.ProcessingOutcome;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.SearchMetadata;
import ai.pipestream.data.v1.ProcessConfiguration;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import org.jboss.logging.Logger;

/**
 * Integration test for EchoService using real gRPC client and Mutiny.
 * This test runs against the packaged JAR as a black-box test.
 */
@QuarkusIntegrationTest
public class EchoServiceGrpcIT {

    private static final Logger LOG = Logger.getLogger(EchoServiceGrpcIT.class);

    private ManagedChannel channel;
    private PipeStepProcessorService echoService;

    @BeforeEach
    void setUp() {
        // Get the test port from Quarkus configuration
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);

        LOG.infof("Connecting gRPC client to localhost:%d", port);

        // Create a real gRPC channel
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext() // No TLS for local tests
                .build();

        // Create the Mutiny-based client stub using the service interface
        echoService = MutinyPipeStepProcessorServiceGrpc.newMutinyStub(channel);
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
        GetServiceRegistrationRequest request = GetServiceRegistrationRequest.newBuilder().build();

        // Call the service and block for result using Mutiny await
        GetServiceRegistrationResponse response = echoService.getServiceRegistration(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Module name should be present", response.getModuleName(), not(emptyString()));
        assertThat("Health check should pass", response.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate service is healthy",
                  response.getHealthCheckMessage(), containsString("healthy"));

        LOG.infof("Received registration: %s", response.getModuleName());
    }

    @Test
    void testProcessData() {
        // Build test document
        String docId = UUID.randomUUID().toString();
        PipeDoc document = PipeDoc.newBuilder()
                .setDocId(docId)
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setBody("Test content from integration test")
                        .setTitle("IT Title")
                        .build())
                .build();

        // Build request
        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setDocument(document)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("integration-test")
                        .setPipeStepName("echo")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        // Call the service and block for result using Mutiny await
        ProcessDataResponse response = echoService.processData(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Response should be successful", response.getOutcome(), is(ProcessingOutcome.PROCESSING_OUTCOME_SUCCESS));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        PipeDoc returnedDoc = response.getOutputDoc();
        assertThat("Returned document ID should match input ID", returnedDoc.getDocId(), equalTo(docId));
        assertThat("Returned document body should match input body",
                  returnedDoc.getSearchMetadata().getBody(), equalTo("Test content from integration test"));
        
        // Echo service adds tags
        assertThat("Metadata tags should contain processed_by_echo", 
                  returnedDoc.getSearchMetadata().getTags().getTagDataMap(), hasKey("processed_by_echo"));

        LOG.infof("Document processed successfully: %s", returnedDoc.getDocId());
    }

    @Test
    void testProcessDataWithEmptyDocument() {
        // Build minimal document
        String docId = "min-doc-123";
        PipeDoc document = PipeDoc.newBuilder()
                .setDocId(docId)
                .build();

        // Build request
        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setDocument(document)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("integration-test")
                        .setPipeStepName("echo")
                        .setStreamId("test-stream-2")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        // Call the service and block for result using Mutiny await
        ProcessDataResponse response = echoService.processData(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Response should be successful even with minimal document", response.getOutcome(), is(ProcessingOutcome.PROCESSING_OUTCOME_SUCCESS));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        PipeDoc returnedDoc = response.getOutputDoc();
        assertThat("Returned document ID should match input ID", returnedDoc.getDocId(), equalTo(docId));
        assertThat("Metadata should contain processed_by_echo tag", 
                  returnedDoc.getSearchMetadata().getTags().getTagDataMap(), hasKey("processed_by_echo"));

        LOG.info("Minimal document processed successfully");
    }
}
