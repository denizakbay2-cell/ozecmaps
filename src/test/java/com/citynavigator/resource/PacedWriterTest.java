package com.citynavigator.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PacedWriterTest {

    @Test
    void handsALargeBodyOnInPiecesAndKeepsEveryByte() throws IOException {
        Recording server = new Recording();
        byte[] body = new byte[150_000];
        Arrays.fill(body, (byte) 'x');
        body[149_999] = 'y';

        try (PacedWriter.Paced paced = new PacedWriter.Paced(server, 64 * 1024)) {
            paced.write(body, 0, body.length);
            paced.write('z');
        }

        assertThat(server.flushedAt).startsWith(65_536, 131_072);
        assertThat(server.toByteArray()).hasSize(150_001).endsWith((byte) 'y', (byte) 'z');
        assertThat(server.closed).isFalse();
    }

    @Test
    void countsSingleBytesToo() throws IOException {
        Recording server = new Recording();
        PacedWriter.Paced paced = new PacedWriter.Paced(server, 4);
        for (int i = 0; i < 9; i++) {
            paced.write('a' + i);
        }

        assertThat(server.flushedAt).containsExactly(4, 8);
        assertThat(server).hasToString("abcdefghi");
    }

    /** A stream that remembers after how many bytes it was flushed. */
    private static final class Recording extends ByteArrayOutputStream {

        private final List<Integer> flushedAt = new ArrayList<>();
        private boolean closed;

        @Override
        public void flush() {
            flushedAt.add(size());
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
