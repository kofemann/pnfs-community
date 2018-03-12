#!/bin/sh

cd /opt/pnfs
/usr/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
	-server ${JAVA_OPT} \
	-jar /opt/pnfs/pnfs.jar svc.xml
