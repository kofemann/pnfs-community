package org.dcache.nfs;

import java.io.IOException;

import java.util.logging.LogManager;
import org.slf4j.bridge.SLF4JBridgeHandler;

import org.springframework.beans.BeansException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {

    public static void main(String[] args) throws IOException {

        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        if (args.length < 1) {
            System.err.println("Usage: Main <mode>");
            System.exit(1);
        }

        String mode = args[0];

        String config = "org/dcache/nfs/" + mode + ".xml";
        try (ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(config)) {
            context.getBean("oncrpcsvc");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // shutdown
        } catch (BeansException e) {
            Throwable t = e.getMostSpecificCause();
            System.err.println("Faled to initialize beans: " + t.getMessage());
            System.exit(1);
        }

        System.exit(0);
    }
}
