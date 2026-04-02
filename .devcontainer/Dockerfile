FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Keep a stable non-default username for this project.
RUN groupmod -n hermes vscode \
    && usermod -l hermes -d /home/hermes -m vscode

ARG KUBECTL_VERSION=v1.30.1
ARG SKAFFOLD_VERSION=v2.13.2
ARG MINIKUBE_VERSION=v1.35.0
ARG HELM_VERSION=v3.15.2
ARG KUSTOMIZE_VERSION=v5.4.2

RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        unzip \
        jq \
        bash-completion \
        openjdk-21-jdk \
        maven \
        golang \
    && rm -rf /var/lib/apt/lists/*

# Angular CLI 21+ requires Node.js >=20.19 or >=22.12 (Ubuntu apt ships an older default).
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# kubectl
RUN curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" -o /usr/local/bin/kubectl \
    && chmod +x /usr/local/bin/kubectl

# skaffold
RUN curl -fsSL "https://storage.googleapis.com/skaffold/releases/${SKAFFOLD_VERSION}/skaffold-linux-amd64" -o /usr/local/bin/skaffold \
    && chmod +x /usr/local/bin/skaffold

# minikube (use with Docker driver + mounted host docker.sock; no separate host install required)
RUN curl -fsSL "https://storage.googleapis.com/minikube/releases/${MINIKUBE_VERSION}/minikube-linux-amd64" -o /usr/local/bin/minikube \
    && chmod +x /usr/local/bin/minikube

# helm
RUN curl -fsSL "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz" -o /tmp/helm.tgz \
    && tar -xzf /tmp/helm.tgz -C /tmp \
    && mv /tmp/linux-amd64/helm /usr/local/bin/helm \
    && chmod +x /usr/local/bin/helm \
    && rm -rf /tmp/helm.tgz /tmp/linux-amd64

# kustomize
RUN curl -fsSL "https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2F${KUSTOMIZE_VERSION}/kustomize_${KUSTOMIZE_VERSION}_linux_amd64.tar.gz" -o /tmp/kustomize.tgz \
    && tar -xzf /tmp/kustomize.tgz -C /usr/local/bin \
    && chmod +x /usr/local/bin/kustomize \
    && rm -f /tmp/kustomize.tgz

# bun for frontend workflow
RUN curl -fsSL https://bun.sh/install | bash \
    && mv /root/.bun/bin/bun /usr/local/bin/bun \
    && chmod +x /usr/local/bin/bun \
    && rm -rf /root/.bun

