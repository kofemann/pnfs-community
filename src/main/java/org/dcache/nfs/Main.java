package org.dcache.nfs;

import java.io.IOException;
import java.util.Arrays;

import java.util.logging.LogManager;
import org.slf4j.bridge.SLF4JBridgeHandler;


import org.dcache.xdr.OncRpcSvc;
import org.springframework.beans.BeansException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Main {

    public static void main(String[] args) throws IOException {

        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        if (args.length < 1) {
            System.err.println("Usage: Main <config> [profile1.....profileN]");
            System.exit(1);
        }

        try (ConfigurableApplicationContext context = new FileSystemXmlApplicationContext(args[0])) {
            if (args.length > 1) {
                context.getEnvironment().setActiveProfiles(Arrays.copyOfRange(args, 1, args.length));
            }
            context.getBean("oncrpcsvc");

            System.in.read();
        } catch (BeansException e) {
            System.err.println("Spring: " + e.getMessage());
            System.exit(1);
        }

        System.exit(0);
    }
}
