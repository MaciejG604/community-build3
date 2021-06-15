#!/usr/bin/env bash
set -e

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

shopt -s expand_aliases
source $scriptDir/env.sh

kubectl apply -f https://download.elastic.co/downloads/eck/1.6.0/all-in-one.yaml

scbk apply -f k8s/elastic.yaml