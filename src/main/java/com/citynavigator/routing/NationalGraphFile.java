package com.citynavigator.routing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Stores a {@link NationalGraph} in one binary file of plain arrays, so a server loads a country in seconds instead
 * of rebuilding it. Big-endian; the layout follows {@link NationalGraph.Parts} field by field.
 */
public final class NationalGraphFile {

    static final byte[] MAGIC = "CNGRAPH1".getBytes(StandardCharsets.US_ASCII);
    private static final int BUFFER = 1 << 22;

    private NationalGraphFile() {
    }

    public static void write(NationalGraph graph, Path file) throws IOException {
        NationalGraph.Parts parts = graph.parts();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            Writer out = new Writer(channel);
            out.bytes(MAGIC);
            out.longValue(parts.dataTimestamp == null ? Long.MIN_VALUE : parts.dataTimestamp.getEpochSecond());
            out.intValue(parts.towerLat.length);
            out.intValue(parts.edgeFrom.length);
            out.intValue(parts.pillarLat.length);
            out.intValue(parts.wayOsmId.length);
            out.intValue(parts.names.length);
            out.ints(parts.towerLat);
            out.ints(parts.towerLon);
            out.ints(parts.edgeFrom);
            out.ints(parts.edgeTo);
            out.ints(parts.edgeWay);
            out.floats(parts.edgeLength);
            out.ints(parts.edgePillarStart);
            out.ints(parts.pillarLat);
            out.ints(parts.pillarLon);
            out.longs(parts.wayOsmId);
            out.bytes(parts.wayClass);
            out.bytes(parts.wayFlags);
            out.shorts(parts.wayMaxSpeed);
            out.ints(parts.wayName);
            for (String name : parts.names) {
                byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
                out.intValue(utf8.length);
                out.bytes(utf8);
            }
            out.flush();
        }
    }

    public static NationalGraph read(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            Reader in = new Reader(channel);
            byte[] magic = in.bytes(MAGIC.length);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException(file + " is not a national graph file");
            }
            NationalGraph.Parts parts = new NationalGraph.Parts();
            long timestamp = in.longValue();
            parts.dataTimestamp = timestamp == Long.MIN_VALUE ? null : Instant.ofEpochSecond(timestamp);
            long size = channel.size();
            int towers = count(in.intValue(), 8, size);
            int edges = count(in.intValue(), 20, size);
            int pillars = count(in.intValue(), 8, size);
            int ways = count(in.intValue(), 16, size);
            int names = count(in.intValue(), 4, size);
            parts.towerLat = in.ints(towers);
            parts.towerLon = in.ints(towers);
            parts.edgeFrom = in.ints(edges);
            parts.edgeTo = in.ints(edges);
            parts.edgeWay = in.ints(edges);
            parts.edgeLength = in.floats(edges);
            parts.edgePillarStart = in.ints(edges + 1);
            parts.pillarLat = in.ints(pillars);
            parts.pillarLon = in.ints(pillars);
            parts.wayOsmId = in.longs(ways);
            parts.wayClass = in.bytes(ways);
            parts.wayFlags = in.bytes(ways);
            parts.wayMaxSpeed = in.shorts(ways);
            parts.wayName = in.ints(ways);
            parts.names = new String[names];
            for (int i = 0; i < names; i++) {
                parts.names[i] = new String(in.bytes(in.intValue()), StandardCharsets.UTF_8);
            }
            return new NationalGraph(parts);
        }
    }

    /** A count from the header, checked against the file size so a damaged file cannot ask for huge arrays. */
    private static int count(int value, int bytesEach, long fileSize) throws IOException {
        if (value < 0 || (long) value * bytesEach > fileSize) {
            throw new IOException("the graph file is damaged: impossible element count " + value);
        }
        return value;
    }

    public static boolean exists(Path file) {
        return Files.isRegularFile(file);
    }

    /** Copies arrays through a 4 MB buffer; element-wise stream calls would take minutes for a country. */
    private static final class Writer {

        private final FileChannel channel;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER);

        Writer(FileChannel channel) {
            this.channel = channel;
        }

        void ints(int[] values) throws IOException {
            int done = 0;
            while (done < values.length) {
                room(4);
                int count = Math.min(buffer.remaining() / 4, values.length - done);
                buffer.asIntBuffer().put(values, done, count);
                buffer.position(buffer.position() + count * 4);
                done += count;
            }
        }

        void floats(float[] values) throws IOException {
            int done = 0;
            while (done < values.length) {
                room(4);
                int count = Math.min(buffer.remaining() / 4, values.length - done);
                buffer.asFloatBuffer().put(values, done, count);
                buffer.position(buffer.position() + count * 4);
                done += count;
            }
        }

        void longs(long[] values) throws IOException {
            int done = 0;
            while (done < values.length) {
                room(8);
                int count = Math.min(buffer.remaining() / 8, values.length - done);
                buffer.asLongBuffer().put(values, done, count);
                buffer.position(buffer.position() + count * 8);
                done += count;
            }
        }

        void shorts(short[] values) throws IOException {
            int done = 0;
            while (done < values.length) {
                room(2);
                int count = Math.min(buffer.remaining() / 2, values.length - done);
                buffer.asShortBuffer().put(values, done, count);
                buffer.position(buffer.position() + count * 2);
                done += count;
            }
        }

        void bytes(byte[] values) throws IOException {
            int done = 0;
            while (done < values.length) {
                room(1);
                int count = Math.min(buffer.remaining(), values.length - done);
                buffer.put(values, done, count);
                done += count;
            }
        }

        void intValue(int value) throws IOException {
            room(4);
            buffer.putInt(value);
        }

        void longValue(long value) throws IOException {
            room(8);
            buffer.putLong(value);
        }

        void flush() throws IOException {
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.clear();
        }

        private void room(int bytes) throws IOException {
            if (buffer.remaining() < bytes) {
                flush();
            }
        }
    }

    private static final class Reader {

        private final FileChannel channel;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER);

        Reader(FileChannel channel) {
            this.channel = channel;
            buffer.flip();
        }

        int[] ints(int count) throws IOException {
            int[] values = new int[count];
            int done = 0;
            while (done < count) {
                need(4);
                int n = Math.min(buffer.remaining() / 4, count - done);
                buffer.asIntBuffer().get(values, done, n);
                buffer.position(buffer.position() + n * 4);
                done += n;
            }
            return values;
        }

        float[] floats(int count) throws IOException {
            float[] values = new float[count];
            int done = 0;
            while (done < count) {
                need(4);
                int n = Math.min(buffer.remaining() / 4, count - done);
                buffer.asFloatBuffer().get(values, done, n);
                buffer.position(buffer.position() + n * 4);
                done += n;
            }
            return values;
        }

        long[] longs(int count) throws IOException {
            long[] values = new long[count];
            int done = 0;
            while (done < count) {
                need(8);
                int n = Math.min(buffer.remaining() / 8, count - done);
                buffer.asLongBuffer().get(values, done, n);
                buffer.position(buffer.position() + n * 8);
                done += n;
            }
            return values;
        }

        short[] shorts(int count) throws IOException {
            short[] values = new short[count];
            int done = 0;
            while (done < count) {
                need(2);
                int n = Math.min(buffer.remaining() / 2, count - done);
                buffer.asShortBuffer().get(values, done, n);
                buffer.position(buffer.position() + n * 2);
                done += n;
            }
            return values;
        }

        byte[] bytes(int count) throws IOException {
            byte[] values = new byte[count];
            int done = 0;
            while (done < count) {
                need(1);
                int n = Math.min(buffer.remaining(), count - done);
                buffer.get(values, done, n);
                done += n;
            }
            return values;
        }

        int intValue() throws IOException {
            need(4);
            return buffer.getInt();
        }

        long longValue() throws IOException {
            need(8);
            return buffer.getLong();
        }

        /** Makes sure at least the given number of bytes is buffered. */
        private void need(int bytes) throws IOException {
            if (buffer.remaining() >= bytes) {
                return;
            }
            buffer.compact();
            while (buffer.position() < bytes) {
                if (channel.read(buffer) < 0) {
                    throw new IOException("the graph file ends too early");
                }
            }
            buffer.flip();
        }
    }
}
