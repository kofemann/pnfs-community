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
  sh)
  exec /bin/sh
  ;;
  *)
  echo "Invalid option " $1
  exit 1
  ;;
esac

cd /opt/pnfs
exec /usr/bin/java -server \
	${JAVA_OPT} ${JAVA_ARGS} \
	-cp "/opt/pnfs/jars/*" org.dcache.nfs.Main svc.xml
