package ai.pipestream.echo;

import ai.pipestream.data.v1.PipeStream;
import ai.pipestream.server.work.ModuleProcessor;

/** Identity processor: the engine-scope contract's echo step. */
public final class EchoProcessor implements ModuleProcessor<PipeStream> {
    @Override
    public PipeStream process(PipeStream input) {
        return input;
    }
}
