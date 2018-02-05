#!/bin/sh

# find the container id
CONTAINER_ID=$(cat /proc/self/cgroup | egrep "cpu.*\:/" | head -1 |cut -d '-' -f 2 | cut -d '.' -f 1)

PORT=`/agent-client.py ${CONTAINER_ID}`

echo "Staring DS on ${LOCALADDRESS}:${PORT}"

cd /opt/pnfs
/usr/bin/java ${JAVA_OPT} -DPNFS_DS_ADDRESS="${LOCALADDRESS}:${PORT}" -server \
	-jar /opt/pnfs/pnfs.jar svc.xml
