#!/bin/sh

cd /opt/pnfs
/usr/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
	-server ${JAVA_OPT} \
	-cp "/opt/pnfs/jars/*" org.dcache.nfs.Main svc.xml
