package ai.pipestream.echo;

import ai.pipestream.data.v1.PipeStream;
import ai.pipestream.module.runtime.work.ModuleWorkEngineClient;
import ai.pipestream.module.runtime.work.ModuleWorkerLoop;
import ai.pipestream.module.runtime.work.WorkerLoopConfig;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@ApplicationScoped
public class EchoWorkerConfig {

    @Inject
    ModuleWorkEngineClient engineClient;

    @Produces
    @Singleton
    ModuleWorkerLoop<PipeStream> echoWorkerLoop(WorkerLoopConfig config) {
        return new ModuleWorkerLoop<>(
                PipeStream.class,
                new EchoProcessor(),
                engineClient,
                config);
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
