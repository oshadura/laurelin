package edu.vanderbilt.accre.laurelin.array;

import java.nio.ByteBuffer;

public class RawArray extends PrimitiveArray {
    RawArray(int length) {
        super(null, length);
        this.disk_buffer = ByteBuffer.allocate(length);
    }

    RawArray(RawArray rawarray) {
        super(null, rawarray.length());
        this.disk_buffer = rawarray.raw();
    }

    public RawArray(ByteBuffer buffer) {
        super(null, buffer.limit());
        this.disk_buffer = buffer;
    }

    @Override
    public int disk_itemsize() {
        return 1;
    }

    @Override
    public int multiplicity() {
        return 1;
    }

    public RawArray slice(int start, int stop) {
        ByteBuffer tmp = disk_buffer.duplicate();
        tmp.position(start);
        tmp.limit(stop);
        return new RawArray(tmp.slice());
    }

    @Override
    public Array clip(int start, int stop) {
        ByteBuffer out = this.disk_buffer.duplicate();
        out.position(start);
        out.limit(stop);
        return this.make(out);
    }

    public RawArray compact(PrimitiveArray.Int4 byteoffsets, int skipbytes, int local_entrystart, int local_entrystop) {
        if (skipbytes == 0) {
            ByteBuffer out = this.disk_buffer.duplicate();
            out.position(byteoffsets.get(local_entrystart));
            out.limit(byteoffsets.get(local_entrystop));
            return new RawArray(out);
        } else {
            ByteBuffer out = ByteBuffer.allocate(byteoffsets.get(local_entrystop) - byteoffsets.get(local_entrystart) - skipbytes * (local_entrystop - local_entrystart));
            this.disk_buffer.position(0);
            for (int i = local_entrystart;  i < local_entrystop;  i++) {
                int start = byteoffsets.get(i) + skipbytes;
                int count = byteoffsets.get(i + 1) - start;
                byte[] copy = new byte[count];
                this.disk_buffer.get(copy);
                out.put(copy);
            }
            this.disk_buffer.position(0);
            out.position(0);
            return new RawArray(out);
        }
    }

    @Override
    public Object toArray(boolean bigEndian) {
        byte[] out = new byte[this.disk_buffer.limit() - this.disk_buffer.position()];
        this.disk_buffer.get(out);
        return out;
    }

    @Override
    protected Array make(ByteBuffer out) {
        return new RawArray(out);
    }

    @Override
    public Array subarray() {
        throw new UnsupportedOperationException("RawArray is not subarrayable");
    }
}
