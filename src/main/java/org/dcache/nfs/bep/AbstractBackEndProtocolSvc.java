package org.dcache.nfs.bep;

import java.io.IOException;
import org.dcache.xdr.OncRpcException;
import org.dcache.xdr.RpcCall;
import org.dcache.xdr.RpcDispatchable;
import org.dcache.xdr.XdrInt;

/**
 *
 */
public abstract class AbstractBackEndProtocolSvc implements RpcDispatchable {

    public static final int SET_SIZE = 1;

    @Override
    public void dispatchOncRpcCall(RpcCall call) throws OncRpcException, IOException {

        int procedure = call.getProcedure();
        switch (procedure) {
            case SET_SIZE: {
                SetSize setSize = new SetSize();
                call.retrieveCall(setSize);
                XdrInt res = setInodeSize(setSize);
                call.reply(res);
                break;
            }
            default:
                call.failProcedureUnavailable();
        }
    }

    public abstract XdrInt setInodeSize(SetSize arg);
}
