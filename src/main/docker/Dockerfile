#  Based on Alpine-3.10
FROM alpine:3.10

# Add JRE
RUN apk --no-cache add openjdk11 --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community

# Run community as user 'pnfs'
RUN addgroup pnfs && adduser -S -G pnfs pnfs

# add external files into container at the build time
COPY run.sh /run.sh
RUN chmod +x /run.sh

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
