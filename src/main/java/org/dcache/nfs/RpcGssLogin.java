package org.dcache.nfs;

import com.sun.security.auth.UnixNumericGroupPrincipal;
import com.sun.security.auth.UnixNumericUserPrincipal;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
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
        s.getPrincipals().add(new UnixNumericUserPrincipal(10006));
        s.getPrincipals().add(new UnixNumericGroupPrincipal(1000, true));

        // preserve original Kerberos principal, if possible ;)
        try {
            s.getPrincipals().add(new KerberosPrincipal(gssContext.getSrcName().toString()));
        } catch (GSSException e) {
            LOGGER.warn("Failed to get source principal: {}", e.getMessage());
        }
        return s;
    }
}
