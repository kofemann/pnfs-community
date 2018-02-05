#!/bin/sh

cd /opt/pnfs
/usr/bin/java -server ${JAVA_OPT} \
	-jar /opt/pnfs/pnfs.jar svc.xml
