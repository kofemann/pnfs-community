#!/bin/sh

PORT=`/agent-client.py`

echo "Staring DS on ${LOCALADDRESS}:${PORT}"

cd /opt/pnfs
/usr/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
	${JAVA_OPT} -DPNFS_DS_ADDRESS="${LOCALADDRESS}:${PORT}" -server \
	-cp "/opt/pnfs/jars/*" org.dcache.nfs.Main svc.xml
