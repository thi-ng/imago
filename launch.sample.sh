#!/bin/bash

export IMAGO_AWS_ID=$AWS_ACCESS_KEY_ID
export IMAGO_AWS_SECRET=$AWS_SECRET_KEY
export IMAGO_S3_BUCKET=com.example
export IMAGO_S3_PREFIX=imago

echo "launching imago..."

lein $@

echo "done."
