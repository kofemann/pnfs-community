#  Based  on CentOS-7.6
FROM centos:7

# Add JRE
RUN yum -y install java-11-openjdk-headless

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
