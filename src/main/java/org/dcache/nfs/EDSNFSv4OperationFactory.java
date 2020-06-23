package org.dcache.nfs;

import com.hazelcast.map.IMap;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import org.dcache.nfs.status.BadHandleException;
import org.dcache.nfs.status.BadStateidException;
import org.dcache.nfs.status.NfsIoException;
import org.dcache.nfs.v4.AbstractNFSv4Operation;
import org.dcache.nfs.v4.AbstractOperationExecutor;
import org.dcache.nfs.v4.CompoundContext;
import org.dcache.nfs.v4.OperationBIND_CONN_TO_SESSION;
import org.dcache.nfs.v4.OperationCREATE_SESSION;
import org.dcache.nfs.v4.OperationDESTROY_CLIENTID;
import org.dcache.nfs.v4.OperationDESTROY_SESSION;
import org.dcache.nfs.v4.OperationEXCHANGE_ID;
import org.dcache.nfs.v4.OperationGETATTR;
import org.dcache.nfs.v4.OperationILLEGAL;
import org.dcache.nfs.v4.OperationPUTFH;
import org.dcache.nfs.v4.OperationPUTROOTFH;
import org.dcache.nfs.v4.OperationRECLAIM_COMPLETE;
import org.dcache.nfs.v4.OperationSEQUENCE;
import org.dcache.nfs.v4.xdr.COMMIT4res;
import org.dcache.nfs.v4.xdr.COMMIT4resok;
import org.dcache.nfs.v4.xdr.READ4res;
import org.dcache.nfs.v4.xdr.READ4resok;
import org.dcache.nfs.v4.xdr.WRITE4res;
import org.dcache.nfs.v4.xdr.WRITE4resok;
import org.dcache.nfs.v4.xdr.count4;
import org.dcache.nfs.v4.xdr.nfs_argop4;
import org.dcache.nfs.v4.xdr.nfs_opnum4;
import org.dcache.nfs.v4.xdr.nfs_resop4;
import org.dcache.nfs.v4.xdr.stable_how4;
import org.dcache.nfs.v4.xdr.stateid4;
import org.dcache.nfs.vfs.Inode;
import org.dcache.oncrpc4j.rpc.OncRpcException;

/**
 *
 */
public class EDSNFSv4OperationFactory extends AbstractOperationExecutor {

    // we use 'other' part of stateid as sequence number can change
    private final IMap<byte[], byte[]> mdsStateIdCache;
    private final IoChannelCache fsc;

    public EDSNFSv4OperationFactory(IMap<byte[], byte[]> openStateIdCache, IoChannelCache fc) {
        mdsStateIdCache = openStateIdCache;
        fsc = fc;
    }

    @Override
    protected AbstractNFSv4Operation getOperation(nfs_argop4 op) {
        switch (op.argop) {
            case nfs_opnum4.OP_COMMIT:
                return new DSOperationCOMMIT(op);
            case nfs_opnum4.OP_GETATTR:
                return new OperationGETATTR(op);
            case nfs_opnum4.OP_PUTFH:
                return new OperationPUTFH(op);
            case nfs_opnum4.OP_PUTROOTFH:
                return new OperationPUTROOTFH(op);
            case nfs_opnum4.OP_READ:
                return new DSOperationREAD(op);
            case nfs_opnum4.OP_WRITE:
                return new DSOperationWRITE(op);
            case nfs_opnum4.OP_EXCHANGE_ID:
                return new OperationEXCHANGE_ID(op);
            case nfs_opnum4.OP_CREATE_SESSION:
                return new OperationCREATE_SESSION(op);
            case nfs_opnum4.OP_DESTROY_SESSION:
                return new OperationDESTROY_SESSION(op);
            case nfs_opnum4.OP_SEQUENCE:
                return new OperationSEQUENCE(op);
            case nfs_opnum4.OP_RECLAIM_COMPLETE:
                return new OperationRECLAIM_COMPLETE(op);
            case nfs_opnum4.OP_BIND_CONN_TO_SESSION:
                return new OperationBIND_CONN_TO_SESSION(op);
            case nfs_opnum4.OP_DESTROY_CLIENTID:
                return new OperationDESTROY_CLIENTID(op);
            case nfs_opnum4.OP_ILLEGAL:
        }
        return new OperationILLEGAL(op);
    }

    private class DSOperationWRITE extends AbstractNFSv4Operation {

        public DSOperationWRITE(nfs_argop4 args) {
            super(args, nfs_opnum4.OP_WRITE);
        }

        @Override
        public void process(CompoundContext context, nfs_resop4 result) throws IOException {
            final WRITE4res res = result.opwrite;
            long offset = _args.opwrite.offset.value;
            _args.opwrite.offset.checkOverflow(_args.opwrite.data.remaining(), "offset + length overflow");
            Inode inode = context.currentInode();
            stateid4 stateid = _args.opwrite.stateid;
            byte[] fh = mdsStateIdCache.get(stateid.other);
            if (fh == null) {
                throw new BadStateidException();
            }
            if (!Arrays.equals(fh, inode.toNfsHandle())) {
                throw new BadHandleException();
            }
            FileChannel out = fsc.get(inode).getChannel();
            _args.opwrite.data.rewind();
            int bytesWritten = out.write(_args.opwrite.data, offset);
            if (bytesWritten < 0) {
                throw new NfsIoException("IO not allowd");
            }
            res.status = nfsstat.NFS_OK;
            res.resok4 = new WRITE4resok();
            res.resok4.count = new count4(bytesWritten);
            res.resok4.committed = stable_how4.DATA_SYNC4;
            res.resok4.writeverf = context.getRebootVerifier();
        }
    }

    private class DSOperationREAD extends AbstractNFSv4Operation {

        public DSOperationREAD(nfs_argop4 args) {
            super(args, nfs_opnum4.OP_READ);
        }

        @Override
        public void process(CompoundContext context, nfs_resop4 result) throws ChimeraNFSException, IOException {
            final READ4res res = result.opread;
            Inode inode = context.currentInode();
            stateid4 stateid = _args.opread.stateid;
            byte[] fh = mdsStateIdCache.get(stateid.other);
            if (fh == null) {
                throw new BadStateidException();
            }
            if (!Arrays.equals(fh, inode.toNfsHandle())) {
                throw new BadHandleException();
            }
            boolean eof = false;
            long offset = _args.opread.offset.value;
            int count = _args.opread.count.value;
            ByteBuffer bb = ByteBuffer.allocateDirect(count);
            FileChannel in = fsc.get(inode).getChannel();
            int bytesReaded = in.read(bb, offset);
            bb.flip();
            if (bytesReaded < 0) {
                eof = true;
            }
            res.status = nfsstat.NFS_OK;
            res.resok4 = new READ4resok();
            res.resok4.data = bb;
            res.resok4.eof = eof;
        }
    }

    private class DSOperationCOMMIT extends AbstractNFSv4Operation {

        public DSOperationCOMMIT(nfs_argop4 args) {
            super(args, nfs_opnum4.OP_COMMIT);
        }

        @Override
        public void process(CompoundContext context, nfs_resop4 result) throws ChimeraNFSException, IOException, OncRpcException {
            final COMMIT4res res = result.opcommit;
            Inode inode = context.currentInode();
            RandomAccessFile out = fsc.get(inode);
            out.getFD().sync();
            res.status = nfsstat.NFS_OK;
            res.resok4 = new COMMIT4resok();
            res.resok4.writeverf = context.getRebootVerifier();
        }
    }

}
