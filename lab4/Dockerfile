# syntax=docker/dockerfile:1
FROM apache/zeppelin:0.12.0@sha256:1a55de9293896cc16a398d401d46760277b44489236c8c3f67b87585db3ed25f AS base

ENV SPARK_VERSION=3.5.8
ENV SCALA_VERSION=2.13
ENV HADOOP_VERSION=3
ENV SPARK_HOME=/opt/spark
ENV PATH=$SPARK_HOME/bin:$PATH


FROM base AS builder-spark
ADD "https://dlcdn.apache.org/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}-scala${SCALA_VERSION}.tgz" /tmp/apache-spark.tgz

USER root
RUN mkdir -p $SPARK_HOME \
    && tar -xf /tmp/apache-spark.tgz -C $SPARK_HOME --strip-components=1


FROM base AS runner

COPY --from=builder-spark $SPARK_HOME $SPARK_HOME
