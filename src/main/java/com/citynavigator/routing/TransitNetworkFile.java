package com.citynavigator.routing;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The compact, gzip-compressed file of a {@link TransitNetwork}. A trip's times are written as the time it leaves
 * and how long each stretch to the next stop takes, and a line's shape as the steps from point to point, which keeps
 * Istanbul's timetables to a few megabytes.
 */
public final class TransitNetworkFile {

    static final int MAGIC = 0x4f5a5452;
    static final int VERSION = 2;
    private static final int BUFFER = 1 << 16;

    private TransitNetworkFile() {
    }

    /** Writes the network to the stream and closes it. */
    public static void write(TransitNetwork network, OutputStream stream) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(stream),
                BUFFER))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(network.source());
            out.writeInt(network.stopCount());
            for (int stop = 0; stop < network.stopCount(); stop++) {
                GeoPoint point = network.stop(stop);
                out.writeInt((int) Math.round(point.lat() * 1e6));
                out.writeInt((int) Math.round(point.lon() * 1e6));
                out.writeUTF(network.stopName(stop));
            }
            out.writeInt(network.lineCount());
            for (int line = 0; line < network.lineCount(); line++) {
                out.writeUTF(network.lineName(line));
                out.writeUTF(network.lineLongName(line));
                out.writeUTF(network.lineMode(line).name());
            }
            out.writeInt(network.patternCount());
            for (int pattern = 0; pattern < network.patternCount(); pattern++) {
                int length = network.patternLength(pattern);
                out.writeInt(network.patternLine(pattern));
                out.writeUTF(network.patternHeadsign(pattern));
                writeNumber(out, length);
                for (int index = 0; index < length; index++) {
                    writeNumber(out, network.patternStop(pattern, index));
                }
                writeShape(out, network.shape(pattern));
                writeNumber(out, network.tripCount(pattern));
                for (int trip = 0; trip < network.tripCount(pattern); trip++) {
                    out.writeByte(network.days(pattern, trip));
                    writeNumber(out, network.time(pattern, trip, 0));
                    for (int index = 1; index < length; index++) {
                        writeNumber(out, network.time(pattern, trip, index) - network.time(pattern, trip, index - 1));
                    }
                }
            }
        }
    }

    /** Reads a network the {@link #write} method wrote, and closes the stream. */
    public static TransitNetwork read(InputStream stream) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream), BUFFER))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IOException("not a transit network file of this version");
            }
            TransitNetwork.Builder builder = TransitNetwork.builder(in.readUTF());
            int stops = in.readInt();
            for (int stop = 0; stop < stops; stop++) {
                builder.addStop(in.readInt() / 1e6, in.readInt() / 1e6, in.readUTF());
            }
            int lines = in.readInt();
            for (int line = 0; line < lines; line++) {
                builder.addLine(in.readUTF(), in.readUTF(), TransitMode.valueOf(in.readUTF()));
            }
            int patterns = in.readInt();
            for (int pattern = 0; pattern < patterns; pattern++) {
                int line = in.readInt();
                String headsign = in.readUTF();
                int length = readNumber(in);
                int[] calls = new int[length];
                for (int index = 0; index < length; index++) {
                    calls[index] = readNumber(in);
                }
                TransitNetwork.Shape shape = readShape(in, length);
                int trips = readNumber(in);
                int[] times = new int[trips * length];
                byte[] days = new byte[trips];
                for (int trip = 0; trip < trips; trip++) {
                    days[trip] = in.readByte();
                    int at = trip * length;
                    times[at] = readNumber(in);
                    for (int index = 1; index < length; index++) {
                        times[at + index] = times[at + index - 1] + readNumber(in);
                    }
                }
                builder.addPattern(line, headsign, calls, times, days, shape);
            }
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new IOException("the transit network file is damaged", e);
        }
    }

    /** No shape, or its points as steps in millionths of a degree and the point of each stop. */
    private static void writeShape(DataOutputStream out, TransitNetwork.Shape shape) throws IOException {
        if (shape == null) {
            writeNumber(out, 0);
            return;
        }
        writeNumber(out, shape.lat().length);
        int lastLat = 0;
        int lastLon = 0;
        for (int point = 0; point < shape.lat().length; point++) {
            int lat = (int) Math.round(shape.lat()[point] * 1e6);
            int lon = (int) Math.round(shape.lon()[point] * 1e6);
            writeNumber(out, zigzag(lat - lastLat));
            writeNumber(out, zigzag(lon - lastLon));
            lastLat = lat;
            lastLon = lon;
        }
        int lastPoint = 0;
        for (int point : shape.stopPoints()) {
            writeNumber(out, point - lastPoint);
            lastPoint = point;
        }
    }

    private static TransitNetwork.Shape readShape(DataInputStream in, int stops) throws IOException {
        int points = readNumber(in);
        if (points == 0) {
            return null;
        }
        double[] lat = new double[points];
        double[] lon = new double[points];
        int lastLat = 0;
        int lastLon = 0;
        for (int point = 0; point < points; point++) {
            lastLat += unzigzag(readNumber(in));
            lastLon += unzigzag(readNumber(in));
            lat[point] = lastLat / 1e6;
            lon[point] = lastLon / 1e6;
        }
        int[] stopPoints = new int[stops];
        int lastPoint = 0;
        for (int stop = 0; stop < stops; stop++) {
            lastPoint += readNumber(in);
            stopPoints[stop] = lastPoint;
        }
        return new TransitNetwork.Shape(lat, lon, stopPoints);
    }

    /** A signed number as a non-negative one: 0, -1, 1, -2 become 0, 1, 2, 3. */
    private static int zigzag(int value) {
        return value << 1 ^ value >> 31;
    }

    private static int unzigzag(int value) {
        return value >>> 1 ^ -(value & 1);
    }

    /** A non-negative number in as few bytes as it needs, seven bits a byte. */
    private static void writeNumber(DataOutputStream out, int value) throws IOException {
        if (value < 0) {
            throw new IllegalArgumentException("negative: " + value);
        }
        int rest = value;
        while (rest >= 0x80) {
            out.writeByte(rest & 0x7f | 0x80);
            rest >>>= 7;
        }
        out.writeByte(rest);
    }

    private static int readNumber(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        int part;
        do {
            if (shift > 28) {
                throw new IOException("a number is too long");
            }
            part = in.readUnsignedByte();
            value |= (part & 0x7f) << shift;
            shift += 7;
        } while ((part & 0x80) != 0);
        return value;
    }
}
