#!/bin/bash

export IMAGO_MEDIA_STORAGE=imago.storage.file
export IMAGO_MEDIA_PATH=./imago-dev

export IMAGO_AWS_ID=$AWS_ACCESS_KEY_ID
export IMAGO_AWS_SECRET=$AWS_SECRET_KEY
export IMAGO_S3_BUCKET=com.example
export IMAGO_S3_PREFIX=imago

export IMAGO_GRAPH_IMPL=imago.graph.memory
export IMAGO_GRAPH_PATH=./imago-dev/graph.db

echo "launching imago..."

lein $@

echo "done."
