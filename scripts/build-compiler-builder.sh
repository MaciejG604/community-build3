#!/usr/bin/env bash
set -e

if [ $# -ne 1 ]; then 
  echo "Wrong number of script arguments. Expected $0 <revision>"
  exit 1
fi

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $scriptDir/utils.sh 

VERSION="$1"
# Use base image with Java 8
TAG_NAME=$(buildTag $VERSION 8)

docker build -t virtuslab/scala-community-build-compiler-builder:"$VERSION" \
  --build-arg BASE_IMAGE="virtuslab/scala-community-build-builder-base:$TAG_NAME" $scriptDir/../compiler-builder