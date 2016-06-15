package org.dcache.nfs;

import javax.security.auth.Subject;
import org.dcache.auth.GidPrincipal;
import org.dcache.auth.UidPrincipal;
import org.dcache.xdr.RpcLoginService;
import org.dcache.xdr.XdrTransport;
import org.ietf.jgss.GSSContext;

public class RpcGssLogin implements RpcLoginService {

    @Override
    public Subject login(XdrTransport transport, GSSContext gssContext) {
        Subject s = new Subject();
        s.getPrincipals().add(new UidPrincipal(10006));
        s.getPrincipals().add(new GidPrincipal(1000, true));
        return s;
    }
}
