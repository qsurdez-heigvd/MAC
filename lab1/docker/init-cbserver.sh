#!/bin/bash
# inspired by https://github.com/couchbaselabs/couchbase-docker-compose

# used to start couchbase server - can't get around this as docker compose only allows you to start one command - so we have to start couchbase like the standard couchbase Dockerfile would 
# https://github.com/couchbase/docker/blob/master/enterprise/couchbase-server/7.0.3/Dockerfile#L82
/entrypoint.sh couchbase-server & 

# track if setup is complete so we don't try to setup again
FILE=/opt/couchbase/init/setupComplete.txt

if ! [ -f "$FILE" ]; then
  INIT_CLUSTER="${INIT_CLUSTER:-false}"  # Default to false if unset

  if [[ "$INIT_CLUSTER" == "true" ]]; then

    # used to automatically create the cluster based on environment variables
    # https://docs.couchbase.com/server/current/cli/cbcli/couchbase-cli-cluster-init.html
    echo Waiting 10s for cluster to be started...
    sleep 10s 
    echo Initializing cluster with username = $COUCHBASE_ADMINISTRATOR_USERNAME and password = $COUCHBASE_ADMINISTRATOR_PASSWORD
    /opt/couchbase/bin/couchbase-cli cluster-init -c 127.0.0.1 \
    --cluster-name $COUCHBASE_CLUSTER_NAME \
    --cluster-username $COUCHBASE_ADMINISTRATOR_USERNAME \
    --cluster-password $COUCHBASE_ADMINISTRATOR_PASSWORD \
    --services data,index,query \
    --cluster-ramsize ${COUCHBASE_RAM_SIZE:-512} \
    --cluster-index-ramsize ${COUCHBASE_INDEX_RAM_SIZE:-512} \
    --index-storage-setting default

    sleep 2s
  fi

  # create file so we know that the cluster is setup and don't run the setup again 
  touch $FILE
fi 

# docker compose will stop the container from running unless we do this
# known issue and workaround
tail -f /dev/null
