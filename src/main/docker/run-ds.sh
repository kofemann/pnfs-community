#!/bin/sh

# find the container id
# THE FORMAT OF /proc/self/cgroup depends on docker version
CONTAINER_ID=$(cat /proc/self/cgroup | egrep "cpu.*\:/" | head -1 |cut -d '/' -f 3)

PORT=`/agent-client.py ${CONTAINER_ID}`

echo "Staring DS on ${LOCALADDRESS}:${PORT}"

cd /opt/pnfs
/usr/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
	${JAVA_OPT} -DPNFS_DS_ADDRESS="${LOCALADDRESS}:${PORT}" -server \
	-cp "/opt/pnfs/jars/*" org.dcache.nfs.Main svc.xml
