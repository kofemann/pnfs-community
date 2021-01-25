#!/bin/sh

JAVA_ARGS=-DPNFS_DS_ADDRESS="${LOCALADDRESS}"

JMX=""

if [ ! -z $JMX_PORT ]
then
JMX="-Dcom.sun.management.jmxremote.port=${JMX_PORT} \
           -Dcom.sun.management.jmxremote.ssl=false \
		   -Dcom.sun.management.jmxremote.authenticate=false"
fi

exec /usr/bin/java -server \
	${JAVA_OPT} ${JMX} ${JAVA_ARGS} \
	-cp "/usr/share/pnfs/jars/*" org.dcache.nfs.Main "$@"
