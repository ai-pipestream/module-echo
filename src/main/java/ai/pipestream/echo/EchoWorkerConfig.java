package ai.pipestream.echo;

import ai.pipestream.data.v1.PipeStream;
import ai.pipestream.module.work.v1.ModuleWorkServiceGrpc;
import ai.pipestream.server.work.ModuleWorkerLoop;
import ai.pipestream.server.work.WorkerLoopConfig;
import io.grpc.Channel;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class EchoWorkerConfig {

    @Produces
    @Singleton
    ModuleWorkerLoop<PipeStream> echoWorkerLoop(
            @GrpcClient("engine") Channel engineChannel,
            WorkerLoopConfig config) {
        ModuleWorkServiceGrpc.ModuleWorkServiceStub stub =
                ModuleWorkServiceGrpc.newStub(engineChannel);
        return new ModuleWorkerLoop<>(
                PipeStream.class, new EchoProcessor(), stub, config);
    }

    void onStart(@Observes StartupEvent ev, ModuleWorkerLoop<PipeStream> loop) {
        // Small delay to ensure Stork/Consul and gRPC infra are fully ready
        // in the SynchronizationContext.
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            loop.onStart(ev);
        }).start();
    }

    void onStop(@Observes ShutdownEvent ev, ModuleWorkerLoop<PipeStream> loop) {
        loop.onStop(ev);
    }
}
