NFSv4.1/pNFS community
==============

A simple NFSv4.1/pNFS community for testing and demonstration.


Building:
=========

Requirements:
-------------

  - Java 8
  - Apache maven 3.3
  - docker (optional)


Running:
========

As stand-alone:
---------------

```
$ java -jar chimera-nfs-0.0.1-SNAPSHOT.jar oncrpcsvc.xml
```

where **oncrpcsvc.xml** is spring config file to start the server.


As docker container:
--------------------

For a quick start, use provided **docker-compose.yml** file:

```
$ docker-compose up -d
```

or

```
$ docker-compose up --scale ds=<N> -d
```

As it's not possible for code inside the container to discover which IP address to use,
you have to adjust **LOCALADDRESS** environment variable.

The provided **docker-compose.yml** file comes with pre-configured zookeeper, kafka for
layout stats processing and hazelcast cache for MDS<->DS state validation.

to start **N** data servers. NOTICE: if number of data servers more than one (1)
and flexfiles layout is usedm then MDS will offer to write into two (2) DSes (mirroring).

License:
========
LGPL v2 or newer
