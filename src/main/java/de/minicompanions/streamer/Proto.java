package de.minicompanions.streamer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Minimaler Protobuf-Leser und -Schreiber (nur Varint- und Längen-Felder), gerade genug für die
 * TikTok-LIVE-Nachrichten. Unbekannte Felder werden übersprungen; kaputte Daten führen zu einer
 * IOException statt zu einem Absturz.
 */
public final class Proto {
    private Proto() {
    }

    /** Liest Felder nacheinander: {@code while (r.next()) switch (r.field()) ...}. */
    public static final class Reader {
        private final byte[] buf;
        private int pos;
        private final int end;
        private int field;
        private int wire;

        public Reader(byte[] buf) {
            this(buf, 0, buf.length);
        }

        private Reader(byte[] buf, int off, int len) {
            this.buf = buf;
            this.pos = off;
            this.end = off + len;
        }

        public boolean next() throws IOException {
            if (pos >= end) {
                return false;
            }
            long tag = varint();
            field = (int) (tag >>> 3);
            wire = (int) (tag & 7);
            if (field <= 0) {
                throw new IOException("bad_tag");
            }
            return true;
        }

        public int field() {
            return field;
        }

        public int wire() {
            return wire;
        }

        public long varint() throws IOException {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) {
                    throw new IOException("truncated");
                }
                byte b = buf[pos++];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
            }
            throw new IOException("bad_varint");
        }

        public byte[] bytes() throws IOException {
            if (wire != 2) {
                throw new IOException("not_bytes");
            }
            long len = varint();
            if (len < 0 || len > end - pos) {
                throw new IOException("truncated");
            }
            byte[] out = new byte[(int) len];
            System.arraycopy(buf, pos, out, 0, (int) len);
            pos += (int) len;
            return out;
        }

        public String string() throws IOException {
            return new String(bytes(), StandardCharsets.UTF_8);
        }

        public Reader message() throws IOException {
            return new Reader(bytes());
        }

        public long int64() throws IOException {
            if (wire != 0) {
                throw new IOException("not_varint");
            }
            return varint();
        }

        public boolean bool() throws IOException {
            return int64() != 0;
        }

        public void skip() throws IOException {
            switch (wire) {
                case 0 -> varint();
                case 1 -> advance(8);
                case 2 -> {
                    long len = varint();
                    if (len < 0) {
                        throw new IOException("truncated");
                    }
                    advance(len);
                }
                case 5 -> advance(4);
                default -> throw new IOException("bad_wire_type");
            }
        }

        private void advance(long n) throws IOException {
            if (n > end - pos) {
                throw new IOException("truncated");
            }
            pos += (int) n;
        }
    }

    /** Schreibt einfache Nachrichten (Varint- und Längen-Felder). */
    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private void raw(long v) {
            while ((v & ~0x7FL) != 0) {
                out.write((int) ((v & 0x7F) | 0x80));
                v >>>= 7;
            }
            out.write((int) v);
        }

        public Writer int64(int field, long v) {
            raw(((long) field << 3));
            raw(v);
            return this;
        }

        public Writer bool(int field, boolean v) {
            return int64(field, v ? 1 : 0);
        }

        public Writer bytes(int field, byte[] v) {
            raw(((long) field << 3) | 2);
            raw(v.length);
            out.write(v, 0, v.length);
            return this;
        }

        public Writer string(int field, String v) {
            return bytes(field, v.getBytes(StandardCharsets.UTF_8));
        }

        public byte[] toBytes() {
            return out.toByteArray();
        }
    }
}
