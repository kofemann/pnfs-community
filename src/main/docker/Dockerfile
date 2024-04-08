FROM almalinux:9-minimal

# Add JRE
RUN microdnf -y install java-17-openjdk-headless shadow-utils

# Run community as user 'pnfs'
RUN groupadd pnfs && useradd -r -g pnfs pnfs

# add external files into container at the build time
COPY run.sh /run.sh
RUN chmod +x /run.sh

# where we store the data
RUN mkdir -p /var/lib/pnfs/data /var/lib/pnfs/db /var/lib/pnfs/state /usr/share/pnfs /etc/pnfs

# adjust permissions
RUN chown -R pnfs:pnfs /var/lib/pnfs /etc/pnfs

# Add JARS
COPY maven /usr/share/pnfs/jars

# expose TCP ports for network services
EXPOSE 2049 2052

VOLUME /var/lib/pnfs

# run as user pnfs
USER pnfs

ENTRYPOINT ["/run.sh"]

# by default we start DS
CMD ["ds"]
