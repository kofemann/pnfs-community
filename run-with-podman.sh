#!/bin/sh

if [ $# != 1 ]
then
  echo "Usage `basename $0` <start|stop>"
  exit 1
fi

if [ -f .env ]
then
  . ./.env
fi
POD_NAME=pnfs-community

case $1 in

  start)
      # create pod with exposed ports
      podman pod create --name ${POD_NAME} -p 2049:2049 -p 2053:2053 -p 2052:2052 -p 9092:9092

      podman run --restart=always --pod=pnfs-community -d --name zk \
          -e ZOO_LOG4J_PROP=WARN,CONSOLE \
          zookeeper:3.5

      podman run --restart=always --pod=${POD_NAME} -d --name kafka \
          -e KAFKA_BROKER_ID=1 \
          -e KAFKA_ADVERTISED_HOST_NAME=${LOCAL_ADDRESS} \
          -e KAFKA_CREATE_TOPICS=iostat:1:1,ioerr:1:1 \
          -e KAFKA_ZOOKEEPER_CONNECT=127.0.0.1:2181/kafka \
          -e LOG4J_LOGGER_KAFKA=WARN \
          -e LOG4J_LOGGER_ORG_APACHE_KAFKA=WARN \
          -e LOG4J_LOGGER_ORG_APACHE_ZOOKEEPER=WARN \
          wurstmeister/kafka

      podman run --restart=always --pod=${POD_NAME} -d --name hz \
          -e JAVA_OPTS=-Dhazelcast.config=/hazelcast.xml \
          -v ${PWD}/hazelcast.xml:/hazelcast.xml:z \
          hazelcast/hazelcast:4.1

      podman run --pod=${POD_NAME} -d --name mds \
          -e ZOOKEEPER_CONNECT=127.0.0.1:2181 \
          -e NFS_PORT=2049 \
          -e HAZELCAST_HOST=127.0.0.1 \
          -e KAFKA_IOERR_TOPIC=ioerr -e KAFKA_IOSTAT_TOPIC=iostat \
          -e KAFKA_BOOTSTRAP_SERVER=127.0.0.1:9092 \
          -v ${PWD}/exports:/pnfs/etc/exports:z \
          -v ${PWD}/nfs.properties:/pnfs/etc/nfs.properties:z \
          -v ${PWD}/chimera.properties:/pnfs/etc/chimera.properties:z \
          dcache/pnfs-community mds --with-layoutstats

      podman run --restart=always --pod=${POD_NAME} -d --name ds0 \
          -e ZOOKEEPER_CONNECT=127.0.0.1:2181 \
          -e NFS_PORT=2052 \
          -e BEP_PORT=1711 \
          -e HAZELCAST_HOST=127.0.0.1 \
          -e LOCALADDRESS=${LOCAL_ADDRESS} \
          dcache/pnfs-community ds

      podman run --restart=always --pod=${POD_NAME} -d --name ds1 \
          -e ZOOKEEPER_CONNECT=127.0.0.1:2181 \
          -e NFS_PORT=2053 \
          -e BEP_PORT=1712 \
          -e HAZELCAST_HOST=127.0.0.1 \
          -e LOCALADDRESS=${LOCAL_ADDRESS} \
          dcache/pnfs-community ds
    ;;
  stop)
    podman pod kill ${POD_NAME}
    podman pod rm ${POD_NAME}
    ;;
  *)
    echo "Usage `basename $0` <start|stop>"
    exit 1
    ;;
esac
