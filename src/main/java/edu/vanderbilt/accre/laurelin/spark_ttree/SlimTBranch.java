package edu.vanderbilt.accre.laurelin.spark_ttree;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.vanderbilt.accre.laurelin.Cache;
import edu.vanderbilt.accre.laurelin.array.ArrayBuilder;
import edu.vanderbilt.accre.laurelin.array.RawArray;
import edu.vanderbilt.accre.laurelin.root_proxy.Cursor;
import edu.vanderbilt.accre.laurelin.root_proxy.ROOTFile;
import edu.vanderbilt.accre.laurelin.root_proxy.ROOTFileCache;
import edu.vanderbilt.accre.laurelin.root_proxy.TBasket;
import edu.vanderbilt.accre.laurelin.root_proxy.TBranch;

/**
 * Contains all the info needed to read a TBranch and its constituent TBaskets
 * without needing to deserialize the ROOT metadata -- i.e. this contains paths
 * and byte offsets to each basket
 */
public class SlimTBranch implements Serializable, SlimTBranchInterface {
    private static final long serialVersionUID = 1L;
    private String path;
    private long []basketEntryOffsets;
    private List<SlimTBasket> baskets;
    private TBranch.ArrayDescriptor arrayDesc;

    public SlimTBranch(String path, long []basketEntryOffsets, TBranch.ArrayDescriptor desc) {
        this.path = path;
        this.basketEntryOffsets = basketEntryOffsets;
        this.baskets = new LinkedList<SlimTBasket>();
        this.arrayDesc = desc;
    }

    public static SlimTBranch getFromTBranch(TBranch fatBranch) {
        SlimTBranch slimBranch = new SlimTBranch(fatBranch.getTree().getBackingFile().getFileName(), fatBranch.getBasketEntryOffsets(), fatBranch.getArrayDescriptor());
        for (TBasket basket: fatBranch.getBaskets()) {
            SlimTBasket slimBasket = new SlimTBasket(slimBranch,
                                                        basket.getAbsoluteOffset(),
                                                        basket.getBasketBytes() - basket.getKeyLen(),
                                                        basket.getObjLen(),
                                                        basket.getKeyLen(),
                                                        basket.getLast()
                                                        );
            slimBranch.addBasket(slimBasket);
        }
        return slimBranch;
    }

    @Override
    public long [] getBasketEntryOffsets() {
        return basketEntryOffsets;
    }

    @Override
    public SlimTBasket getBasket(int basketid) {
        return baskets.get(basketid);
    }

    @Override
    public void addBasket(SlimTBasket basket) {
        baskets.add(basket);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public TBranch.ArrayDescriptor getArrayDesc() {
        return arrayDesc;
    }

    /**
     * Glue callback to integrate with edu.vanderbilt.accre.laurelin.array
     * @param basketCache the cache we should be using
     * @param fileCache
     * @return GetBasket object used by array
     */
    @Override
    public ArrayBuilder.GetBasket getArrayBranchCallback(Cache basketCache, ROOTFileCache fileCache) {
        return new BranchCallback(basketCache, this, fileCache);
    }

    class BranchCallback implements ArrayBuilder.GetBasket {
        Cache basketCache;
        SlimTBranchInterface branch;
        ROOTFileCache fileCache;

        public BranchCallback(Cache basketCache, SlimTBranchInterface branch, ROOTFileCache fileCache) {
            this.basketCache = basketCache;
            this.branch = branch;
            this.fileCache = fileCache;
        }

        @Override
        public ArrayBuilder.BasketKey basketkey(int basketid) {
            SlimTBasket basket = branch.getBasket(basketid);
            return new ArrayBuilder.BasketKey(basket.getKeyLen(), basket.getLast(), basket.getObjLen());
        }

        @Override
        public RawArray dataWithoutKey(int basketid) {
            SlimTBasket basket = branch.getBasket(basketid);
            ROOTFile tmpFile;
            try {
                if (fileCache == null) {
                    tmpFile = ROOTFile.getInputFile(path);
                } else {
                    tmpFile = fileCache.getROOTFile(path);
                }
                // the offset of each basket is guaranteed to be unique and
                // stable
                RawArray data = null;
                data = basketCache.get(tmpFile, basket.getOffset());
                if (data == null) {
                    data = new RawArray(basket.getPayload());
                    basketCache.put(tmpFile, basket.getOffset(), data);
                }
                return data;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class SlimTBasket implements Serializable {
        private static final Logger logger = LogManager.getLogger();

        private static final long serialVersionUID = 1L;
        private SlimTBranchInterface branch;
        private long offset;
        private int compressedLen;
        private int uncompressedLen;
        private int keyLen;
        private int last;
        private Cursor payload;

        public SlimTBasket(SlimTBranchInterface branch, long offset, int compressedLen, int uncompressedLen, int keyLen, int last) {
            this.branch = branch;
            this.offset = offset;
            this.compressedLen = compressedLen;
            this.uncompressedLen = uncompressedLen;
            this.keyLen = keyLen;
            this.last = last;
        }

        public int getKeyLen() {
            return keyLen;
        }

        public int getObjLen() {
            return uncompressedLen;
        }

        public int getLast() {
            return last;
        }

        public long getOffset() {
            return offset;
        }

        private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

        public static String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for ( int j = 0; j < bytes.length; j++ ) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }
        private void initializePayload() throws IOException {
            ROOTFile tmpFile = ROOTFile.getInputFile(branch.getPath());
            Cursor fileCursor = tmpFile.getCursor(offset);
            this.payload = fileCursor.getPossiblyCompressedSubcursor(0,
                    compressedLen,
                    uncompressedLen,
                    0);
        }

        public ByteBuffer getPayload(long offset, int len) throws IOException {
            if (this.payload == null) {
                initializePayload();
            }
            return this.payload.readBuffer(offset, len);
        }

        public ByteBuffer getPayload() throws IOException {
            if (this.payload == null) {
                initializePayload();
            }
            long len = payload.getLimit();
            return this.payload.readBuffer(0, uncompressedLen);
        }

    }

}
