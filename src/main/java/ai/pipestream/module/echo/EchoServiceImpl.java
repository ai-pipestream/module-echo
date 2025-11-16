package ai.pipestream.module.echo;

import ai.pipestream.api.annotation.GrpcServiceRegistration;
import ai.pipestream.api.annotation.ProcessingBuffered;
import ai.pipestream.data.module.*;
import ai.pipestream.data.util.proto.PipeDocTestDataFactory;
import ai.pipestream.data.v1.PipeDoc;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Echo Service Implementation for the Pipestream Pipeline.
 *
 * <p>This service implements the {@code PipeStepProcessor} gRPC interface to provide a simple
 * echo functionality for documents flowing through the Pipestream data processing pipeline.
 * It receives documents, enriches them with processing metadata, and returns them unchanged.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><b>Document Echo</b> - Returns documents with added metadata for traceability</li>
 *   <li><b>Metadata Enrichment</b> - Adds processing timestamp, processor ID, and version information</li>
 *   <li><b>Service Registration</b> - Supports dynamic service discovery via Consul</li>
 *   <li><b>Health Checks</b> - Provides built-in health check capabilities for orchestration</li>
 *   <li><b>Processing Buffer</b> - Optional document buffering for analysis and debugging</li>
 * </ul>
 *
 * <h2>Use Cases:</h2>
 * <ul>
 *   <li>Pipeline validation and testing</li>
 *   <li>Document flow debugging</li>
 *   <li>Integration testing of pipeline components</li>
 *   <li>Performance benchmarking with minimal overhead</li>
 *   <li>Health monitoring and connectivity validation</li>
 * </ul>
 *
 * <h2>Metadata Tags Added:</h2>
 * <p>The service adds the following tags to processed documents:</p>
 * <ul>
 *   <li>{@code processed_by_echo} - Application name that processed the document</li>
 *   <li>{@code echo_timestamp} - ISO-8601 timestamp when processing occurred</li>
 *   <li>{@code echo_module_version} - Version of the echo module (currently 1.0.0)</li>
 *   <li>{@code echo_stream_id} - Stream ID from request metadata (if available)</li>
 *   <li>{@code echo_step_name} - Pipeline step name from request metadata (if available)</li>
 * </ul>
 *
 * <h2>Configuration:</h2>
 * <p>The service is configured through application.properties:</p>
 * <pre>
 * quarkus.application.name=echo
 * quarkus.http.port=39000
 * module.registration.enabled=true
 * processing.buffer.enabled=false
 * </pre>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * // Create a gRPC client
 * PipeStepProcessorGrpc.PipeStepProcessorBlockingStub stub =
 *     PipeStepProcessorGrpc.newBlockingStub(channel);
 *
 * // Create a request with a document
 * ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
 *     .setDocument(myPipeDoc)
 *     .setMetadata(ServiceMetadata.newBuilder()
 *         .setStreamId("stream-123")
 *         .setPipeStepName("validation-step")
 *         .build())
 *     .build();
 *
 * // Process the document
 * ModuleProcessResponse response = stub.processData(request);
 *
 * // Check result
 * if (response.getSuccess()) {
 *     PipeDoc enrichedDoc = response.getOutputDoc();
 *     // Document now contains echo metadata tags
 * }
 * }</pre>
 *
 * @author Rokkon Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MutinyPipeStepProcessorGrpc.PipeStepProcessorImplBase
 * @see ModuleProcessRequest
 * @see ModuleProcessResponse
 * @see ServiceRegistrationMetadata
 */
@GrpcService
@Singleton
@GrpcServiceRegistration(
    metadata = {"category=testing", "complexity=simple"}
)
public class EchoServiceImpl extends MutinyPipeStepProcessorGrpc.PipeStepProcessorImplBase {

    private static final Logger LOG = Logger.getLogger(EchoServiceImpl.class);

    /**
     * The application name as configured in application.properties.
     * <p>This is injected from the {@code quarkus.application.name} property and is used
     * to identify the processor in metadata tags added to documents.</p>
     */
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    /**
     * Factory for creating test documents.
     * <p>Used by the {@link #testProcessData(ModuleProcessRequest)} method to create
     * sample documents when no document is provided in the test request.</p>
     */
    @Inject
    PipeDocTestDataFactory pipeDocTestDataFactory;

    /**
     * Processes a document by echoing it back with enriched metadata.
     *
     * <p>This is the primary processing method of the Echo Service. It receives a
     * {@link ModuleProcessRequest} containing a document, adds processing metadata
     * to the document's tags, and returns it wrapped in a {@link ModuleProcessResponse}.</p>
     *
     * <h3>Processing Flow:</h3>
     * <ol>
     *   <li>Log the incoming request with document ID (if present)</li>
     *   <li>Create a success response builder</li>
     *   <li>If a document exists:
     *     <ul>
     *       <li>Extract or create search metadata</li>
     *       <li>Add/update tags with processing information</li>
     *       <li>Include stream ID and step name from request metadata (if available)</li>
     *       <li>Build the enriched document</li>
     *     </ul>
     *   </li>
     *   <li>Return the response with success status and processor logs</li>
     * </ol>
     *
     * <h3>Metadata Tags Added:</h3>
     * <table border="1">
     *   <tr><th>Tag Key</th><th>Description</th><th>Example Value</th></tr>
     *   <tr><td>processed_by_echo</td><td>Application name</td><td>echo</td></tr>
     *   <tr><td>echo_timestamp</td><td>ISO-8601 timestamp</td><td>2024-01-15T10:30:00Z</td></tr>
     *   <tr><td>echo_module_version</td><td>Module version</td><td>1.0.0</td></tr>
     *   <tr><td>echo_stream_id</td><td>Stream identifier</td><td>stream-abc-123</td></tr>
     *   <tr><td>echo_step_name</td><td>Pipeline step name</td><td>validation-step</td></tr>
     * </table>
     *
     * <h3>Processing Buffer Support:</h3>
     * <p>This method is annotated with {@link ProcessingBuffered} to optionally capture
     * processed documents for analysis. Enable this by setting:</p>
     * <pre>processing.buffer.enabled=true</pre>
     *
     * @param request the module process request containing the document to echo and optional metadata
     * @return a {@link Uni} emitting a {@link ModuleProcessResponse} with:
     *         <ul>
     *           <li>{@code success} - always {@code true} for successful processing</li>
     *           <li>{@code outputDoc} - the original document with added metadata tags</li>
     *           <li>{@code processorLogs} - log messages describing the processing</li>
     *         </ul>
     *
     * @see ProcessingBuffered
     * @see ServiceMetadata
     * @see PipeDoc
     * @see ai.pipestream.data.v1.Tags
     */
    @Override
    @ProcessingBuffered(type = PipeDoc.class, enabled = "${processing.buffer.enabled:false}")
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        LOG.debugf("Echo service received document: %s",
                 request.hasDocument() ? request.getDocument().getDocId() : "no document");

        // Build response with success status
        ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Echo service successfully processed document");

        // If there's a document, add metadata and echo it back
        if (request.hasDocument()) {
            PipeDoc originalDoc = request.getDocument();
            PipeDoc.Builder docBuilder = originalDoc.toBuilder();

            // Get existing search metadata or create new one
            ai.pipestream.data.v1.SearchMetadata.Builder searchMetadataBuilder =
                originalDoc.hasSearchMetadata()
                    ? originalDoc.getSearchMetadata().toBuilder()
                    : ai.pipestream.data.v1.SearchMetadata.newBuilder();

            // Add or update tags with processing metadata
            ai.pipestream.data.v1.Tags.Builder tagsBuilder = searchMetadataBuilder.hasTags()
                    ? searchMetadataBuilder.getTags().toBuilder()
                    : ai.pipestream.data.v1.Tags.newBuilder();

            // Add echo module metadata
            tagsBuilder.putTagData("processed_by_echo", applicationName);
            tagsBuilder.putTagData("echo_timestamp", Instant.now().toString());
            tagsBuilder.putTagData("echo_module_version", "1.0.0");

            // Add request metadata if available
            if (request.hasMetadata()) {
                ServiceMetadata metadata = request.getMetadata();
                if (!metadata.getStreamId().isEmpty()) {
                    tagsBuilder.putTagData("echo_stream_id", metadata.getStreamId());
                }
                if (!metadata.getPipeStepName().isEmpty()) {
                    tagsBuilder.putTagData("echo_step_name", metadata.getPipeStepName());
                }
            }

            // Set the updated tags in search metadata
            searchMetadataBuilder.setTags(tagsBuilder.build());

            // Update the document with modified search metadata
            docBuilder.setSearchMetadata(searchMetadataBuilder.build());

            // Set the updated document in the response
            responseBuilder.setOutputDoc(docBuilder.build());
            responseBuilder.addProcessorLogs("Echo service added metadata to document");
        }

        ModuleProcessResponse response = responseBuilder.build();
        LOG.debugf("Echo service returning success: %s", response.getSuccess());

        return Uni.createFrom().item(response);
    }

    /**
     * Retrieves comprehensive service registration metadata.
     *
     * <p>This method provides detailed information about the Echo Service for service
     * discovery and monitoring purposes. It returns metadata including the module name,
     * version, description, owner, tags, and optional health check results.</p>
     *
     * <h3>Registration Information Provided:</h3>
     * <ul>
     *   <li><b>Module Name</b> - Application name from configuration</li>
     *   <li><b>Version</b> - Service version (1.0.0)</li>
     *   <li><b>Display Name</b> - Human-readable name ("Echo Service")</li>
     *   <li><b>Description</b> - Service description</li>
     *   <li><b>Owner</b> - Rokkon Team</li>
     *   <li><b>Tags</b> - pipeline-module, echo, processor</li>
     *   <li><b>Server Info</b> - Operating system information</li>
     *   <li><b>SDK Version</b> - SDK version (1.0.0)</li>
     *   <li><b>Metadata</b> - Implementation language and JVM version</li>
     *   <li><b>Health Check</b> - Optional health check results</li>
     * </ul>
     *
     * <h3>Health Check Behavior:</h3>
     * <p>If a {@code testRequest} is provided in the {@link RegistrationRequest}, the service
     * performs a health check by:</p>
     * <ol>
     *   <li>Processing the test request through {@link #processData(ModuleProcessRequest)}</li>
     *   <li>Setting {@code healthCheckPassed} to {@code true} if processing succeeds</li>
     *   <li>Setting {@code healthCheckMessage} with the result or error details</li>
     *   <li>Recovering gracefully from exceptions with appropriate error messages</li>
     * </ol>
     *
     * <p>If no test request is provided, the service assumes healthy status.</p>
     *
     * <h3>Example Response:</h3>
     * <pre>{@code
     * ServiceRegistrationMetadata {
     *   moduleName: "echo"
     *   version: "1.0.0"
     *   displayName: "Echo Service"
     *   description: "A simple echo module that returns documents with added metadata"
     *   owner: "Rokkon Team"
     *   tags: ["pipeline-module", "echo", "processor"]
     *   registrationTimestamp: 2024-01-15T10:30:00Z
     *   serverInfo: "Linux 5.15.0"
     *   sdkVersion: "1.0.0"
     *   metadata: {
     *     "implementation_language": "Java",
     *     "jvm_version": "21.0.1"
     *   }
     *   healthCheckPassed: true
     *   healthCheckMessage: "Echo module is healthy and functioning correctly"
     * }
     * }</pre>
     *
     * @param request the registration request, optionally containing a test request for health check
     * @return a {@link Uni} emitting {@link ServiceRegistrationMetadata} with complete service information
     *
     * @see RegistrationRequest
     * @see ServiceRegistrationMetadata
     * @see GrpcServiceRegistration
     */
    @Override
    public Uni<ServiceRegistrationMetadata> getServiceRegistration(RegistrationRequest request) {
        LOG.debug("Echo service registration requested");

        // Build a more comprehensive registration response with metadata
        ServiceRegistrationMetadata.Builder responseBuilder = ServiceRegistrationMetadata.newBuilder()
                .setModuleName(applicationName)
                .setVersion("1.0.0")
                .setDisplayName("Echo Service")
                .setDescription("A simple echo module that returns documents with added metadata")
                .setOwner("Rokkon Team")
                .addTags("pipeline-module")
                .addTags("echo")
                .addTags("processor")
                .setRegistrationTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .setNanos(Instant.now().getNano())
                        .build());

        // Add server info and SDK version
        responseBuilder
                .setServerInfo(System.getProperty("os.name") + " " + System.getProperty("os.version"))
                .setSdkVersion("1.0.0");

        // Add metadata
        responseBuilder
                .putMetadata("implementation_language", "Java")
                .putMetadata("jvm_version", System.getProperty("java.version"));

        // If test request is provided, perform health check
        if (request.hasTestRequest()) {
            LOG.debug("Performing health check with test request");
            return processData(request.getTestRequest())
                .map(processResponse -> {
                    if (processResponse.getSuccess()) {
                        responseBuilder
                            .setHealthCheckPassed(true)
                            .setHealthCheckMessage("Echo module is healthy and functioning correctly");
                    } else {
                        responseBuilder
                            .setHealthCheckPassed(false)
                            .setHealthCheckMessage("Echo module health check failed: " +
                                (processResponse.hasErrorDetails() ?
                                    processResponse.getErrorDetails().toString() :
                                    "Unknown error"));
                    }
                    return responseBuilder.build();
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.error("Health check failed with exception", error);
                    return responseBuilder
                        .setHealthCheckPassed(false)
                        .setHealthCheckMessage("Health check failed with exception: " + error.getMessage())
                        .build();
                });
        } else {
            // No test request provided, assume healthy
            responseBuilder
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("Service is healthy");
            return Uni.createFrom().item(responseBuilder.build());
        }
    }

    /**
     * Test endpoint for validating the echo service functionality.
     *
     * <p>This method provides a test-specific version of document processing, intended for
     * validation, debugging, and health check purposes. It can operate with or without
     * an input document and marks all processing logs with a {@code [TEST]} prefix for
     * easy identification.</p>
     *
     * <h3>Behavior:</h3>
     * <ul>
     *   <li><b>With Document</b> - Processes the provided document normally</li>
     *   <li><b>Without Document</b> - Creates a sample complex document automatically</li>
     *   <li><b>Log Marking</b> - All processor logs are prefixed with "[TEST]"</li>
     *   <li><b>Validation</b> - Adds a final log message indicating test completion</li>
     * </ul>
     *
     * <h3>Auto-Generated Test Data:</h3>
     * <p>When no document is provided, the method creates a complex test document using
     * {@link PipeDocTestDataFactory#createComplexDocument(int)} with ID 10101. The test
     * request also includes predefined metadata:</p>
     * <ul>
     *   <li>Stream ID: "test-stream"</li>
     *   <li>Pipe Step Name: "test-step"</li>
     *   <li>Pipeline Name: "test-pipeline"</li>
     * </ul>
     *
     * <h3>Use Cases:</h3>
     * <ul>
     *   <li>Health check validation during service registration</li>
     *   <li>Manual testing of service functionality</li>
     *   <li>Integration testing with predefined scenarios</li>
     *   <li>Debugging document processing flow</li>
     * </ul>
     *
     * <h3>Example Response Logs:</h3>
     * <pre>
     * [TEST] Echo service successfully processed document
     * [TEST] Echo service added metadata to document
     * [TEST] Echo module test validation completed successfully
     * </pre>
     *
     * @param request the test request, may be {@code null} or without a document
     *                (a test document will be created automatically)
     * @return a {@link Uni} emitting a {@link ModuleProcessResponse} with:
     *         <ul>
     *           <li>Success status from normal processing</li>
     *           <li>Processed document with metadata tags</li>
     *           <li>All processor logs prefixed with "[TEST]"</li>
     *           <li>Additional validation completion message</li>
     *         </ul>
     *
     * @see #processData(ModuleProcessRequest)
     * @see PipeDocTestDataFactory#createComplexDocument(int)
     * @see ServiceMetadata
     */
    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        LOG.debug("TestProcessData called - executing test version of processing");

        // For test processing, create a test document if none provided
        if (request == null || !request.hasDocument()) {
            PipeDoc testDoc = pipeDocTestDataFactory.createComplexDocument(10101);

            ServiceMetadata testMetadata = ServiceMetadata.newBuilder()
                    .setStreamId("test-stream")
                    .setPipeStepName("test-step")
                    .setPipelineName("test-pipeline")
                    .build();

            request = ModuleProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(testMetadata)
                    .build();
        }

        // Process normally but with test flag in logs
        return processData(request)
                .onItem().transform(response -> {
                    // Add test marker to logs
                    ModuleProcessResponse.Builder builder = response.toBuilder();
                    for (int i = 0; i < builder.getProcessorLogsCount(); i++) {
                        builder.setProcessorLogs(i, "[TEST] " + builder.getProcessorLogs(i));
                    }
                    builder.addProcessorLogs("[TEST] Echo module test validation completed successfully");
                    return builder.build();
                });
    }
}
