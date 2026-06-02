package ai.pipestream.echo;

import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.PipeStream;
import ai.pipestream.module.work.v1.AckConfirmed;
import ai.pipestream.module.work.v1.ModuleWorkServiceGrpc;
import ai.pipestream.module.work.v1.NoWorkAvailable;
import ai.pipestream.module.work.v1.ProcessingStatus;
import ai.pipestream.module.work.v1.WorkAck;
import ai.pipestream.module.work.v1.WorkRequest;
import ai.pipestream.module.work.v1.WorkResponse;
import ai.pipestream.module.work.v1.WorkUnit;
import ai.pipestream.module.runtime.work.ModuleWorkEngineClient;
import ai.pipestream.module.runtime.work.ModuleWorkerLoop;
import ai.pipestream.module.runtime.work.WorkerLoopConfig;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration smoke test proving the bidi wiring between {@link EchoProcessor}
 * and {@link ModuleWorkerLoop} works end-to-end against an in-process fake engine.
 *
 * <p>No real engine, Consul, Kafka, or Redis is required. The test runs entirely
 * in-process using {@link InProcessServerBuilder} / {@link InProcessChannelBuilder}.
 *
 * <p>Protocol under test:
 * <ol>
 *   <li>ModuleWorkerLoop opens the bidi stream and sends {@code Hello}.</li>
 *   <li>Fake engine receives Hello, replies with one {@code WorkUnit} carrying a
 *       known {@code PipeStream} packed as {@code Any}.</li>
 *   <li>Framework unpacks, calls {@code EchoProcessor.process} (identity), packs
 *       the result into a {@code WorkAck}.</li>
 *   <li>Fake engine verifies the ack has {@code PROCESSING_STATUS_SUCCESS} and the
 *       updated payload unpacks to the exact {@code PipeStream} that was served.</li>
 *   <li>Fake engine replies {@code AckConfirmed} then {@code NoWorkAvailable} so
 *       the worker can exit cleanly.</li>
 * </ol>
 */
class EchoBidiWiringSmokeTest {

    private static final String STREAM_ID = "s-smoke";
    private static final String DOC_ID    = "d-smoke";
    private static final String WORK_UNIT_ID = "wu-smoke-" + UUID.randomUUID();

    private String serverName;
    private Server server;
    private FakeEngine fakeEngine;
    private ModuleWorkerLoop<PipeStream> loop;

    @BeforeEach
    void startInProcessServer() throws Exception {
        serverName = "echo-bidi-smoke-" + UUID.randomUUID();
        fakeEngine = new FakeEngine();

        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(fakeEngine)
                .build()
                .start();

        AtomicReference<ManagedChannel> channelRef = new AtomicReference<>(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());
        ModuleWorkEngineClient engineClient = new ModuleWorkEngineClient() {
            @Override
            public ModuleWorkServiceGrpc.ModuleWorkServiceStub stub() {
                return ModuleWorkServiceGrpc.newStub(channelRef.get());
            }

            @Override
            public void reconnect() {
                ManagedChannel old = channelRef.getAndSet(
                        InProcessChannelBuilder.forName(serverName).directExecutor().build());
                if (old != null) {
                    old.shutdownNow();
                }
            }
        };

        loop = new ModuleWorkerLoop<>(
                PipeStream.class,
                new EchoProcessor(),
                engineClient,
                testConfig());
    }

    @AfterEach
    void stopAll() throws Exception {
        if (loop != null) {
            loop.onStop(new ShutdownEvent());
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(20)
    void echoProcessor_receivesWorkUnit_acksSuccessWithSamePayload() throws Exception {
        // Known PipeStream the fake engine will serve as the work unit payload
        PipeStream served = PipeStream.newBuilder()
                .setStreamId(STREAM_ID)
                .setDocument(PipeDoc.newBuilder().setDocId(DOC_ID).build())
                .build();
        fakeEngine.setServedPipeStream(served, WORK_UNIT_ID);

        // Start the loop (this is how Quarkus triggers it via CDI lifecycle)
        loop.onStart(new StartupEvent());

        // Wait for the fake engine to have fully verified the WorkAck (15 s generous timeout)
        boolean ackVerified = fakeEngine.ackVerifiedLatch.await(15, TimeUnit.SECONDS);
        assertThat(ackVerified)
                .as("fake engine must receive and verify WorkAck within 15 s")
                .isTrue();

        // Propagate any assertion error the fake engine captured during the ack check
        Throwable fakeEngineError = fakeEngine.assertionError.get();
        assertThat(fakeEngineError)
                .as("fake engine must not have encountered an assertion failure: "
                        + (fakeEngineError != null ? fakeEngineError.getMessage() : ""))
                .isNull();

        // Verify Hello carries the module-id routing key (cluster/graph_id/node_id
        // were dropped — engine routes by module_id alone and per-work-unit
        // graph/node identifiers ride on the PipeStream payload).
        WorkRequest helloRequest = fakeEngine.capturedHello.get();
        assertThat(helloRequest)
                .as("fake engine must have captured a Hello WorkRequest")
                .isNotNull();
        assertThat(helloRequest.getHello().getModuleId())
                .as("Hello.module_id must match the WorkerLoopConfig")
                .isEqualTo("echo");
    }

    // ----- Test WorkerLoopConfig -----

    private static WorkerLoopConfig testConfig() {
        return new WorkerLoopConfig() {
            @Override public boolean enabled()                  { return true; }
            @Override public String moduleId()                  { return "echo"; }
            @Override public String grpcClientName()            { return "engine"; }
            @Override public int concurrency()                  { return 1; }
            @Override public int minConcurrency()               { return 1; }
            // Long heartbeat interval: suppress heartbeat traffic during the test
            @Override public Duration heartbeatInterval()       { return Duration.ofSeconds(60); }
            @Override public Duration reconnectInitialDelay()   { return Duration.ofMillis(50); }
            @Override public Duration reconnectMaxDelay()       { return Duration.ofSeconds(1); }
            // Short no-work retry so the loop exits quickly after AckConfirmed + NoWork
            @Override public Duration noWorkRetryAfter()        { return Duration.ofMillis(50); }
            @Override public Duration firstResponseTimeout()   { return Duration.ofSeconds(5); }
        };
    }

    // ----- Fake engine -----

    /**
     * In-process fake of the engine's {@code ModuleWorkService}.
     *
     * <p>Protocol:
     * <ol>
     *   <li>On Hello: record the request, send one WorkUnit with the scripted PipeStream.</li>
     *   <li>On WorkAck: assert status == SUCCESS and unpacked payload == served PipeStream;
     *       reply AckConfirmed then complete the stream. Countdown {@link #ackVerifiedLatch}.</li>
     *   <li>Second Hello (loop reopens after SUCCESS): reply NoWorkAvailable immediately so the
     *       loop sleeps via noWorkRetryAfter and the worker exits when running becomes false.</li>
     * </ol>
     */
    private static final class FakeEngine extends ModuleWorkServiceGrpc.ModuleWorkServiceImplBase {

        // Set before the loop starts
        private volatile PipeStream servedPipeStream;
        private volatile String workUnitId;

        // Captures from the bidi exchange
        final AtomicReference<WorkRequest> capturedHello = new AtomicReference<>();
        final AtomicReference<Throwable>   assertionError = new AtomicReference<>();

        /** Counted down once the WorkAck has been successfully verified. */
        final CountDownLatch ackVerifiedLatch = new CountDownLatch(1);

        /** Tracks how many Hello messages this fake has received (first vs. subsequent). */
        private final AtomicInteger helloCount = new AtomicInteger(0);

        void setServedPipeStream(PipeStream pipeStream, String wuId) {
            this.servedPipeStream = pipeStream;
            this.workUnitId       = wuId;
        }

        @Override
        public StreamObserver<WorkRequest> work(StreamObserver<WorkResponse> responseObserver) {
            return new StreamObserver<>() {
                @Override
                public void onNext(WorkRequest req) {
                    if (req.hasHello()) {
                        int count = helloCount.incrementAndGet();
                        if (count == 1) {
                            // First Hello: capture it and serve the work unit
                            capturedHello.set(req);
                            try {
                                responseObserver.onNext(WorkResponse.newBuilder()
                                        .setWorkUnit(WorkUnit.newBuilder()
                                                .setWorkUnitId(workUnitId)
                                                .setPayload(Any.pack(servedPipeStream))
                                                .build())
                                        .build());
                            } catch (Exception e) {
                                assertionError.set(e);
                                ackVerifiedLatch.countDown();
                            }
                        } else {
                            // Subsequent Hello: no work available — loop will sleep then exit
                            responseObserver.onNext(WorkResponse.newBuilder()
                                    .setNoWork(NoWorkAvailable.newBuilder()
                                            .setRetryAfterMs(50)
                                            .build())
                                    .build());
                            responseObserver.onCompleted();
                        }

                    } else if (req.hasAck()) {
                        WorkAck ack = req.getAck();
                        try {
                            assertThat(ack.getStatus())
                                    .as("WorkAck.status must be PROCESSING_STATUS_SUCCESS "
                                            + "for an identity EchoProcessor")
                                    .isEqualTo(ProcessingStatus.PROCESSING_STATUS_SUCCESS);

                            assertThat(ack.getWorkUnitId())
                                    .as("WorkAck.work_unit_id must echo back the id we served")
                                    .isEqualTo(workUnitId);

                            assertThat(ack.hasUpdatedPayload())
                                    .as("WorkAck SUCCESS must carry updated_payload")
                                    .isTrue();

                            PipeStream echoed = ack.getUpdatedPayload().unpack(PipeStream.class);
                            assertThat(echoed)
                                    .as("EchoProcessor must return the exact PipeStream that was served "
                                            + "(identity contract)")
                                    .isEqualTo(servedPipeStream);

                        } catch (InvalidProtocolBufferException | AssertionError e) {
                            assertionError.set(e);
                        } finally {
                            ackVerifiedLatch.countDown();
                        }

                        // Reply AckConfirmed and close this stream
                        responseObserver.onNext(WorkResponse.newBuilder()
                                .setAckConfirmed(AckConfirmed.newBuilder()
                                        .setWorkUnitId(workUnitId)
                                        .setAccepted(true)
                                        .build())
                                .build());
                        responseObserver.onCompleted();
                    }
                    // Heartbeats silently accepted — no response needed
                }

                @Override public void onError(Throwable t)  { /* client closed early; ignore */ }
                @Override public void onCompleted()         { /* client half-closed; nothing to do */ }
            };
        }
    }
}
