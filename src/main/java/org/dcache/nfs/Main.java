package org.dcache.nfs;

import java.io.IOException;
import java.util.Arrays;

import java.util.logging.LogManager;
import org.slf4j.bridge.SLF4JBridgeHandler;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {


    private static final String OPTION_PREFIX = "--with-";

    public static void main(String[] args) throws IOException {

        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        if (args.length < 1) {
            System.err.println("Usage: Main <mode> [--with-<profile1>]...[--with-<profile>N]");
            System.exit(1);
        }

        String mode = args[0];

        String[] profiles = Arrays.asList(args)
                .stream()
                .filter(s -> s.startsWith(OPTION_PREFIX))
                .map(s -> s.substring(OPTION_PREFIX.length()))
                .toArray(String[]::new);

        String config = "org/dcache/nfs/" + mode + ".xml";
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {

            context.getEnvironment().setActiveProfiles(profiles);
            XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(context);
            xmlReader.loadBeanDefinitions(config);
            context.refresh();

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
