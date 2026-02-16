# syntax=docker/dockerfile:1.10
FROM couchbase:enterprise-7.6.7

COPY mflix-sample.zip /opt/couchbase/samples/

COPY --chmod=744 init-cbserver.sh /opt/couchbase/init/
RUN mkdir -p /opt/couchbase/init.d/

ENTRYPOINT [ "/opt/couchbase/init/init-cbserver.sh" ]

