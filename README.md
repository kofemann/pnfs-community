# NFSv4.1/pNFS community

A simple NFSv4.1/pNFS community for testing and demonstration.

## Building

### Requirements

- Java 17
- Apache maven 3.3
- docker + docker-compose or podman + podman-compose

## Running


### As docker container

For a quick start, use provided **docker-compose.yml** file. As
pNFS DSes have to publish their IP addresses, add .env file
with the IP address which has to be offered to the clients:

```property
# example .env file
LOCAL_ADDRESS=1.2.3.4
```

To start:
```sh
docker-compose up -d
```

or

```sh
podman compose up -d
```

As it's not possible for code inside the container to discover which IP address to use,
you have to adjust **LOCALADDRESS** environment variable.

The provided **docker-compose.yml** file comes with pre-configured zookeeper, kafka for
layout stats processing and hazelcast cache for MDS<->DS state validation.

#### Layout statistics

To publish flexfile layout statistics into apache-kafka topic the `mds` must know kafka broker
endpoint and topics to which to publish layouts IO statistics and error reports:

```yaml
  mds:
    image: dcache/pnfs-community
    depends_on:
    - hz
    - zk
    environment:
    - KAFKA_BOOTSTRAP_SERVER=kafka:9092
    - KAFKA_IOSTAT_TOPIC=iostat
    - KAFKA_IOERR_TOPIC=ioerr
    - NFS_PORT=2049
    command: mds --with-layoutstats
```

#### RPC-over-TLS

[RPC-over-TLS allows](https://datatracker.ietf.org/doc/draft-ietf-nfsv4-rpc-tls/) in transit packet protection by enabling TLS for client-server communication. This experimental feature can be enabled by adding `--with-tls` option to the command section:

```
command: mds --with-tls
```

>NOTE: when tls is used, it should be enabled on MDS and all DSes.

In addition to `hostcert.pem`, `hostkey.pem` and `ca-chain.pem` files required for host certificate, key and trusted CA chains.

## Configuration

The `pnfs-community` uses various environment variables the as configuration

| Name                   | description                             | component  | required |
| :---                   | ------------:                           | ---------: | --------: |
| NFS_PORT               | TCP port exposed to the clients         | MDS and DS | yes|
| BEP_PORT               | [IP:]TCP port used by back-end protocol | DS         | yes|
| HAZELCAST_HOST         | host where hazelcast service is running | MDS and DS | yes |
| ZOOKEEPER_CONNECT      | Zookeeper endpoint                      | MDS and DS | yes |
| KAFKA_BOOTSTRAP_SERVER | Kafka broker endpoint                   | MDS        | only if kafka enabled |
| KAFKA_IOSTAT_TOPIC     | Kafka topic to report io statistics     | MDS        | only if kafka enabled |
| KAFKA_IOERR_TOPIC      | Kafka topic to report io errors         | MDS        | only if kafka enabled |

## License

LGPL v2 or newer
