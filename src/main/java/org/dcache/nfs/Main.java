package org.dcache.nfs;

import java.io.IOException;
import java.util.Arrays;
import org.dcache.xdr.OncRpcSvc;
import org.springframework.beans.BeansException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: Main <config> [profile1.....profileN]");
            System.exit(1);
        }

        try (ConfigurableApplicationContext context = new FileSystemXmlApplicationContext(args[0])) {
            if (args.length > 1) {
                context.getEnvironment().setActiveProfiles(Arrays.copyOfRange(args, 1, args.length));
            }
            OncRpcSvc service = (OncRpcSvc) context.getBean("oncrpcsvc");
            service.start();

            System.in.read();
        } catch (BeansException e) {
            System.err.println("Spring: " + e.getMessage());
            System.exit(1);
        }

        System.exit(0);
    }
}
