#!/usr/bin/env bash
set -e

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

docker build -t virtuslab/scala-community-build-sample-repos $scriptDir/../sample-repos
