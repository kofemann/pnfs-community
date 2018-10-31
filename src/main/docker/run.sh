#!/bin/sh

JAVA_ARGS=

case $1 in

  mds)
  echo "Staring MDS"
  ;;
  ds)
	PORT=`/agent-client.py`
	JAVA_ARGS=-DPNFS_DS_ADDRESS="${LOCALADDRESS}:${PORT}"
	echo "Staring DS on ${LOCALADDRESS}:${PORT}"
  ;;
  *)
  echo "Invalid option " $1
  exit 1
  ;;
esac

cd /opt/pnfs
/usr/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -server \
	${JAVA_OPT} ${JAVA_ARGS} \
	-cp "/opt/pnfs/jars/*" org.dcache.nfs.Main svc.xml
