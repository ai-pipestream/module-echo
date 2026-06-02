package ai.pipestream.echo;

import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.PipeStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EchoProcessorTest {
    @Test
    void echo_returns_input_unchanged() {
        PipeStream in = PipeStream.newBuilder()
                .setStreamId("s1")
                .setDocument(PipeDoc.newBuilder().setDocId("d1").build())
                .build();
        PipeStream out = new EchoProcessor().process(in);
        assertThat(out)
                .as("echo must return the exact input (identity processing)")
                .isEqualTo(in);
    }
}
