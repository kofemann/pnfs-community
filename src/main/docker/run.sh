#!/bin/sh

JAVA_ARGS=-DPNFS_DS_ADDRESS="${LOCALADDRESS}"

exec /usr/bin/java -server \
	${JAVA_OPT} ${JAVA_ARGS} \
	-cp "/pnfs/jars/*" org.dcache.nfs.Main "$@"
