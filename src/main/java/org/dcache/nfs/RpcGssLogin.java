package org.dcache.nfs;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import org.dcache.auth.GidPrincipal;
import org.dcache.auth.UidPrincipal;
import org.dcache.oncrpc4j.rpc.RpcLoginService;
import org.dcache.oncrpc4j.rpc.RpcTransport;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcGssLogin implements RpcLoginService {

    private final static Logger LOGGER = LoggerFactory.getLogger(RpcGssLogin.class);

    @Override
    public Subject login(RpcTransport transport, GSSContext gssContext) {
        Subject s = new Subject();
        s.getPrincipals().add(new UidPrincipal(10006));
        s.getPrincipals().add(new GidPrincipal(1000, true));

        // preserve original Kerberos principal, if possible ;)
        try {
            s.getPrincipals().add(new KerberosPrincipal(gssContext.getSrcName().toString()));
        } catch (GSSException e) {
            LOGGER.warn("Failed to get source pringipal: ", e.getMessage());
        }
        return s;
    }
}
