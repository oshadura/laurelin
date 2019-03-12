package edu.vanderbilt.accre.array;

import java.lang.Integer;
import java.nio.ByteBuffer;

import edu.vanderbilt.accre.interpretation.Interpretation;
import edu.vanderbilt.accre.interpretation.AsDtype;
import edu.vanderbilt.accre.array.Array;

public abstract class PrimitiveArray<THIS> extends Array<THIS> {
    ByteBuffer buffer;

    PrimitiveArray(Interpretation interpretation, int length) {
        super(interpretation, length);
        this.buffer = ByteBuffer.allocate(length * (((AsDtype)interpretation).itemsize() * ((AsDtype)interpretation).multiplicity()));
    }

    PrimitiveArray(Interpretation interpretation, RawArray rawarray) {
        super(interpretation, rawarray.length() / (((AsDtype)interpretation).itemsize() * ((AsDtype)interpretation).multiplicity()));
        this.buffer = rawarray.raw();
    }

    protected PrimitiveArray(Interpretation interpretation, ByteBuffer buffer) {
        super(interpretation, buffer.limit() / (((AsDtype)interpretation).itemsize() * ((AsDtype)interpretation).multiplicity()));
        this.buffer = buffer;
    }

    public int itemsize() {
        return ((AsDtype)interpretation).itemsize();
    }

    public int multiplicity() {
        return ((AsDtype)interpretation).multiplicity();
    }

    public int numitems() {
        return this.length * this.multiplicity();
    }

    public void copyitems(PrimitiveArray source, int itemstart, int itemstop) {
        int bytestart = itemstart * this.itemsize();
        int bytestop = itemstart * this.itemsize();
        this.raw().put(source.raw().array(), bytestart, bytestop - bytestart);
    }

    protected ByteBuffer raw() {
        return this.buffer;
    }

    protected ByteBuffer rawclipped(int start, int stop) {
        int mult = this.multiplicity();
        int bytestart = start * mult * this.itemsize();
        int bytestop = stop * mult * this.itemsize();
        ByteBuffer out = this.buffer.duplicate();
        out.position(bytestart);
        out.limit(bytestop);
        return out;
    }
}
