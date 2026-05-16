package ai.pipestream.echo;

import ai.pipestream.data.v1.PipeStream;
import ai.pipestream.module.work.v1.ModuleWorkServiceGrpc;
import ai.pipestream.server.work.ModuleWorkerLoop;
import ai.pipestream.server.work.WorkerLoopConfig;
import io.grpc.Channel;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
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
}
