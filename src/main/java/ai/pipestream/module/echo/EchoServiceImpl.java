package ai.pipestream.module.echo;

import ai.pipestream.api.annotation.GrpcServiceRegistration;
import ai.pipestream.api.annotation.ProcessingBuffered;
import ai.pipestream.data.module.v1.*;
import ai.pipestream.data.v1.PipeDoc;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Implementation of the PipeStepProcessorService that simply echos the input
 * document back while adding some processing metadata.
 * 
 * This service serves as a baseline for document processing steps and
 * provides a simple example of how to implement a module.
 */
@GrpcService
@Singleton
public class EchoServiceImpl implements PipeStepProcessorService {

    private static final Logger LOG = Logger.getLogger(EchoServiceImpl.class);

    /**
     * The name of this application, used in metadata tagging.
     */
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "echo-service")
    String applicationName;

    /**
     * Processes the incoming document by adding echo-specific metadata tags.
     * 
     * @param request The processing request containing the document
     * @return A Uni containing the response with the modified document
     */
    @Override
    public Uni<ProcessDataResponse> processData(ProcessDataRequest request) {
        LOG.debugf("Echo service received document: %s", 
                 request.hasDocument() ? request.getDocument().getDocId() : "no document");

        // Build response with success status
        ProcessDataResponse.Builder responseBuilder = ProcessDataResponse.newBuilder()
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

        ProcessDataResponse response = responseBuilder.build();
        LOG.debugf("Echo service returning success: %s", response.getSuccess());

        return Uni.createFrom().item(response);
    }

    /**
     * Returns the service registration metadata for the echo module.
     * Includes health status and operational capabilities.
     * 
     * @param request The registration request
     * @return A Uni containing the registration response
     */
    @Override
    public Uni<GetServiceRegistrationResponse> getServiceRegistration(GetServiceRegistrationRequest request) {
        LOG.debug("Echo service registration requested");

        // Build a more comprehensive registration response with metadata
        GetServiceRegistrationResponse.Builder responseBuilder = GetServiceRegistrationResponse.newBuilder()
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
}
