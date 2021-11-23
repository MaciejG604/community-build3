#!/usr/bin/env bash
set -e

if [ -z "$CB_DOCKER_USERNAME" ]; then
  echo >&2 "CB_DOCKER_USERNAME env variable has to be set"
  exit 1
fi

if [ -z "$CB_DOCKER_PASSWORD" ]; then
  echo >&2 "CB_DOCKER_PASSWORD env variable has to be set"
  exit 1
fi

if [ -z "$CB_LICENSE_CLIENT" ]; then
  echo >&2 "CB_LICENSE_CLIENT env variable has to be set"
  exit 1
fi

if [ -z "$CB_LICENSE_KEY" ]; then
  echo >&2 "CB_LICENSE_KEY env variable has to be set"
  exit 1
fi

if [ -z "$CM_K8S_JENKINS_OPERATOR_NAMESPACE" ]; then
  echo >&2 "CM_K8S_JENKINS_OPERATOR_NAMESPACE env variable has to be set"
  exit 1
fi

if [ -z "$CM_K8S_NAMESPACE" ]; then
  echo >&2 "CM_K8S_NAMESPACE env variable has to be set"
  exit 1
fi

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $scriptDir/utils.sh

scbok create secret docker-registry license-token --docker-server=operatorservice.azurecr.io --docker-username="$CB_DOCKER_USERNAME" --docker-password="$CB_DOCKER_PASSWORD" --dry-run=client -o yaml | kubectl apply -f -

cat <<EOF | scbok apply -f - --dry-run=client -o yaml | kubectl apply -f -
apiVersion: v1
kind: Secret
metadata:
  name: license
stringData:
  clientName: "$CB_LICENSE_CLIENT"
  licenseKey: "$CB_LICENSE_KEY"
EOF

HELM_EXPERIMENTAL_OCI=1 helm registry login operatorservice.azurecr.io -u "$CB_DOCKER_USERNAME" -p "$CB_DOCKER_PASSWORD"

HELM_EXPERIMENTAL_OCI=1 helm --namespace="$CM_K8S_JENKINS_OPERATOR_NAMESPACE" \
  install operator oci://operatorservice.azurecr.io/charts/op-svc-jenkins --version 0.1.3 -f k8s/jenkins-operator.yaml \
  --set operator.namespace="$CM_K8S_JENKINS_OPERATOR_NAMESPACE" \
  --set operator.jenkinsNamespace="$CM_K8S_NAMESPACE"