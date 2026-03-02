package ai.pipestream.module.echo;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import ai.pipestream.data.module.v1.*;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.SearchMetadata;
import ai.pipestream.data.v1.ProcessConfiguration;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

/**
 * Base test class for Echo module testing.
 * Aligned with v1 protos and explicit Mutiny types.
 */
public abstract class EchoServiceTestBase {

    protected abstract MutinyPipeStepProcessorServiceGrpc.MutinyPipeStepProcessorServiceStub getEchoService();

    protected abstract String getApplicationName();

    protected PipeDoc createTestDoc() {
        return PipeDoc.newBuilder()
                .setDocId(UUID.randomUUID().toString())
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setBody("Test document body")
                        .setTitle("Test Title")
                        .build())
                .build();
    }

    @Test
    void testProcessData() {
        // Create a test document
        PipeDoc testDoc = createTestDoc();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("echo-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setJsonConfig(Struct.newBuilder()
                        .putFields("mode", Value.newBuilder().setStringValue("echo").build())
                        .build())
                .putConfigParams("mode", "echo")
                .build();

        // Create request
        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify using explicit Mutiny stub
        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));
        assertThat("Output document ID should match input document ID", response.getOutputDoc().getDocId(), equalTo(testDoc.getDocId()));
        assertThat("Output document body should match input document body", response.getOutputDoc().getSearchMetadata().getBody(), equalTo(testDoc.getSearchMetadata().getBody()));
        assertThat("Processor logs should not be empty", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Processor logs should contain success message", response.getProcessorLogsList(), hasItem(containsString("successfully processed")));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed (echo service is tolerant)
        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("echo-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful even without document", response.getSuccess(), is(true));
        assertThat("Response should not have output document", response.hasOutputDoc(), is(false));
        assertThat("Processor logs should not be empty", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Processor logs should contain success message", response.getProcessorLogsList(), hasItem(containsString("successfully processed")));
    }

    @Test
    void testGetServiceRegistrationWithoutHealthCheck() {
        // Call without test request
        GetServiceRegistrationRequest request = GetServiceRegistrationRequest.newBuilder().build();

        var registration = getEchoService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Module name should match application name", registration.getModuleName(), equalTo(getApplicationName()));
        // Echo service has no JSON schema - it accepts any input
        assertThat("JSON config schema should not be present", registration.hasJsonConfigSchema(), is(false));
        // Should be healthy without test
        assertThat("Health check should pass", registration.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate service is healthy", registration.getHealthCheckMessage(), containsString("Service is healthy"));
    }

    @Test
    void testGetServiceRegistrationWithHealthCheck() {
        // Create a test document for health check
        PipeDoc testDoc = createTestDoc();

        ProcessDataRequest processRequest = ProcessDataRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("health-check")
                        .setPipeStepName("echo-health")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                .build();

        // Call with test request for health check
        GetServiceRegistrationRequest request = GetServiceRegistrationRequest.newBuilder()
                .setTestRequest(processRequest)
                .build();

        var registration = getEchoService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Module name should match application name", registration.getModuleName(), equalTo(getApplicationName()));
        assertThat("JSON config schema should not be present", registration.hasJsonConfigSchema(), is(false));
        // Health check should pass
        assertThat("Health check should pass with test request", registration.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate service is functioning correctly", 
                  registration.getHealthCheckMessage(), containsString("healthy and functioning correctly"));
    }

    @Test
    void testMetadataPropagation() {
        // Test that metadata is properly propagated
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("metadata-test")
                .setPipeStepName("echo-metadata")
                .setStreamId("stream-123")
                .setCurrentHopNumber(5)
                .putContextParams("tenant", "test-tenant")
                .putContextParams("region", "us-east-1")
                .build();

        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setDocument(createTestDoc())
                .setMetadata(metadata)
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        var tags = response.getOutputDoc().getSearchMetadata().getTags().getTagDataMap();
        assertThat(tags, hasEntry("processed_by_echo", getApplicationName()));
        assertThat(tags, hasEntry("echo_stream_id", "stream-123"));
        assertThat(tags, hasEntry("echo_step_name", "echo-metadata"));
    }

    @Test
    void testLargeDocument() {
        // Test with a large document
        StringBuilder largeBody = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeBody.append("This is line ").append(i).append(" of a large document. ");
        }

        PipeDoc largeDoc = PipeDoc.newBuilder()
                .setDocId("large-doc")
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setBody(largeBody.toString())
                        .setTitle("Large Document Test")
                        .build())
                .build();

        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setDocument(largeDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("large-doc-test")
                        .setPipeStepName("echo")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));
        assertThat("Output document should have large body", 
                  response.getOutputDoc().getSearchMetadata().getBody().length(), 
                  greaterThan(10000));
    }

    @Test
    void testExistingCustomData() {
        // Test that existing custom_data is preserved and extended
        Struct existingCustomData = Struct.newBuilder()
                .putFields("existing_field", Value.newBuilder().setStringValue("existing_value").build())
                .putFields("existing_number", Value.newBuilder().setNumberValue(42.0).build())
                .build();

        PipeDoc docWithCustomData = PipeDoc.newBuilder()
                .setDocId("custom-data-doc")
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setBody("Document with existing custom data")
                        .setCustomFields(existingCustomData)
                        .build())
                .build();

        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setDocument(docWithCustomData)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("custom-data-test")
                        .setPipeStepName("echo-custom")
                        .build())
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        var customData = response.getOutputDoc().getSearchMetadata().getCustomFields().getFieldsMap();
        // Original data should be preserved
        assertThat("Original field should be preserved", customData, hasKey("existing_field"));
        assertThat("Original field value should be preserved", 
                  customData.get("existing_field").getStringValue(), equalTo("existing_value"));
        assertThat("Original number field should be preserved", customData, hasKey("existing_number"));
        assertThat("Original number value should be preserved", 
                  customData.get("existing_number").getNumberValue(), equalTo(42.0));

        // New echo data should be added to tags
        var tags = response.getOutputDoc().getSearchMetadata().getTags().getTagDataMap();
        assertThat("Echo processor marker should be added to tags", tags, hasKey("processed_by_echo"));
        assertThat("Echo timestamp should be added to tags", tags, hasKey("echo_timestamp"));
    }
}
