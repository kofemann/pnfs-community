#
# Build (packaging) image
#
FROM centos:7 as build

# Add JRE (work around untill offcial rpm is available)
ADD https://download.java.net/java/GA/jdk11/13/GPL/openjdk-11.0.1_linux-x64_bin.tar.gz /tmp
# unpack the  JVM and create compack jre with jlink
RUN tar -C /tmp -xzf /tmp/openjdk-11.0.1_linux-x64_bin.tar.gz && \
    /tmp/jdk-11.0.1/bin/jlink --compress=2 --module-path /tmp/jdk-11.0.1/jmods \
    --add-modules java.se,jdk.unsupported \
    --output /java-11


#
# Final image to deploy. Uses artifacts (jlinked JRE) from the build image
#
FROM centos:7

COPY --from=build /java-11 /usr/java-11
RUN ln -s  /usr/java-11/bin/java /usr/bin/java

# Run community as user 'pnfs'
RUN groupadd pnfs && useradd -r -g pnfs pnfs

# add external files into container at the build time
COPY run.sh /run.sh
COPY agent-client.py /agent-client.py
RUN chmod +x /run.sh /agent-client.py

# where we store the data
RUN mkdir -p /pnfs/data /pnfs/db /pnfs/etc  /pnfs/state

# adjust permissions
RUN chown -R pnfs:pnfs /pnfs

# Add JARS
COPY maven /pnfs/jars

# expose TCP ports for network services
EXPOSE 2049 2052

VOLUME /pnfs

# run as user pnfs
USER pnfs

ENTRYPOINT ["/run.sh"]

# by default we start DS
CMD ["ds"]
