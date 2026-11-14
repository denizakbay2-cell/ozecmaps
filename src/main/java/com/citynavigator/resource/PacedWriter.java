package com.citynavigator.resource;

import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Hands response bodies to the server in pieces of at most {@link #PIECE_BYTES}. Payara's network layer (Grizzly)
 * otherwise receives a whole body in one write when it is flushed, and closes the connection without sending a byte
 * if that write is larger than its per-connection limit (about 512 KB): long routes with an alternative are bigger
 * than that unless the client accepts gzip. With pieces, the server waits for the network instead.
 */
@Provider
public class PacedWriter implements WriterInterceptor {

    static final int PIECE_BYTES = 64 * 1024;

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        context.setOutputStream(new Paced(context.getOutputStream(), PIECE_BYTES));
        context.proceed();
    }

    /** Flushes after every {@code piece} bytes. */
    static final class Paced extends FilterOutputStream {

        private final int piece;
        private int sinceFlush;

        Paced(OutputStream out, int piece) {
            super(out);
            this.piece = piece;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count(1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            int written = 0;
            while (written < length) {
                int part = Math.min(length - written, piece - sinceFlush);
                out.write(bytes, offset + written, part);
                written += part;
                count(part);
            }
        }

        private void count(int bytes) throws IOException {
            sinceFlush += bytes;
            if (sinceFlush >= piece) {
                out.flush();
                sinceFlush = 0;
            }
        }

        @Override
        public void close() throws IOException {
            // the container closes its own stream; closing here would end the response before Jersey is done
            flush();
        }
    }
}
