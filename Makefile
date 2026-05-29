SHELL := /bin/bash
.PHONY: front back back-local back-dynamic dev teardown minikube-up minikube-reset minikube-preflight vault-up vault-init vault-reset vault-bootstrap vault-seed vault-ui vault-database-engine eso-sync eso-sync-dynamic eso-sync-staging eso-sync-prod aws-secrets-seed deploy-prod-k8s smoke-test-staging smoke-test-dev aws-rotation-enable setup-podman validate-builds validate-manifests validate-terraform validate-devspace validate-all integration-test render-manifests

MINIKUBE_PROFILE ?= hermes-dev
MINIKUBE_DRIVER ?= podman
MINIKUBE_ROOTLESS ?= true
MINIKUBE_KUBERNETES_VERSION ?= v1.31.6
MINIKUBE_MEMORY ?= 6144
MINIKUBE_CPUS ?= 3
MINIKUBE_WAIT_TIMEOUT ?= 10m
MINIKUBE_CONTAINER_RUNTIME ?= containerd
MINIKUBE_START_EXTRA ?=

DEVSPACE_GUARD = @if pgrep -f "[d]evspace dev" >/dev/null 2>&1; then \
	echo "WARNING: devspace is already running (dev mode). Stop it (Ctrl+C) before running this target."; \
	exit 1; \
fi

DEVSPACE_RUN = unset KUBECONFIG; eval $$(minikube -p "$(MINIKUBE_PROFILE)" podman-env -u) 2>/dev/null || true; \
	export MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)"; devspace dev --kube-context "$(MINIKUBE_PROFILE)" --namespace hermes-dev

minikube-up:
	@unset KUBECONFIG; \
	. infrastructure/scripts/minikube-common.sh; \
	if minikube_profile_running "$(MINIKUBE_PROFILE)"; then \
		echo "Minikube profile $(MINIKUBE_PROFILE) already running."; \
	else \
		echo "Starting minikube profile $(MINIKUBE_PROFILE) ($(MINIKUBE_KUBERNETES_VERSION))..."; \
		ROOTLESS_FLAG=""; \
		if [ "$(MINIKUBE_ROOTLESS)" = "true" ] && [ "$(MINIKUBE_DRIVER)" = "podman" ]; then \
			ROOTLESS_FLAG="--rootless"; \
		fi; \
		minikube start -p "$(MINIKUBE_PROFILE)" --driver="$(MINIKUBE_DRIVER)" \
			$$ROOTLESS_FLAG \
			--kubernetes-version="$(MINIKUBE_KUBERNETES_VERSION)" \
			--memory="$(MINIKUBE_MEMORY)" \
			--cpus="$(MINIKUBE_CPUS)" \
			--wait-timeout="$(MINIKUBE_WAIT_TIMEOUT)" \
			--container-runtime="$(MINIKUBE_CONTAINER_RUNTIME)" \
			$(MINIKUBE_START_EXTRA) --force; \
		wait_for_minikube_profile "$(MINIKUBE_PROFILE)" 60 || (echo "Minikube API not ready." && exit 1); \
	fi

minikube-reset:
	unset KUBECONFIG; \
	minikube delete -p "$(MINIKUBE_PROFILE)" || true; \
	rm -rf "$(HOME)/.minikube/profiles/$(MINIKUBE_PROFILE)"; \
	podman rm -f "$(MINIKUBE_PROFILE)" 2>/dev/null || true; \
	podman volume rm "$(MINIKUBE_PROFILE)" 2>/dev/null || true; \
	ROOTLESS_FLAG=""; \
	if [ "$(MINIKUBE_ROOTLESS)" = "true" ] && [ "$(MINIKUBE_DRIVER)" = "podman" ]; then \
		ROOTLESS_FLAG="--rootless"; \
	fi; \
	minikube start -p "$(MINIKUBE_PROFILE)" --driver="$(MINIKUBE_DRIVER)" \
		$$ROOTLESS_FLAG \
		--kubernetes-version="$(MINIKUBE_KUBERNETES_VERSION)" \
		--memory="$(MINIKUBE_MEMORY)" \
		--cpus="$(MINIKUBE_CPUS)" \
		--wait-timeout="$(MINIKUBE_WAIT_TIMEOUT)" \
		--container-runtime="$(MINIKUBE_CONTAINER_RUNTIME)" \
			$(MINIKUBE_START_EXTRA) --force

minikube-preflight:
	unset KUBECONFIG; bash infrastructure/scripts/minikube-preflight.sh

front:
	cd frontend && bun run start

render-manifests:
	bash infrastructure/scripts/render-devspace-manifests.sh

back: minikube-up vault-init eso-sync render-manifests
	$(DEVSPACE_GUARD)
	@echo "Starting DevSpace (Vault + ESO + apps; secrets synced from Vault by default)."
	$(DEVSPACE_RUN)

back-local: minikube-up
	$(DEVSPACE_GUARD)
	@echo "Starting DevSpace with local secrets.env (non-prod offline fallback; no Vault required)."
	unset KUBECONFIG; export MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)"; devspace dev -p local-secrets --kube-context "$(MINIKUBE_PROFILE)"

back-dynamic: minikube-up vault-init vault-database-engine eso-sync-dynamic render-manifests
	$(DEVSPACE_GUARD)
	@echo "Starting DevSpace with Vault database static roles (Phase D dynamic-secrets profile)."
	unset KUBECONFIG; export MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)"; devspace dev -p dynamic-secrets --kube-context "$(MINIKUBE_PROFILE)"

vault-up:
	bash infrastructure/vault/scripts/deploy-dev.sh

vault-init: vault-up
	@if [ -f .env ]; then \
		echo "Bootstrapping Vault Kubernetes auth and seeding KV from .env..."; \
		bash infrastructure/vault/scripts/bootstrap-dev-k8s-auth.sh; \
		bash infrastructure/vault/scripts/seed-dev-secrets.sh; \
	else \
		echo "WARNING: .env not found — copy .env.example to .env, run setup-env.sh dev, then make vault-seed."; \
	fi

vault-reset:
	kubectl delete namespace vault --ignore-not-found --wait=true
	bash infrastructure/vault/scripts/deploy-dev.sh

teardown:
	bash infrastructure/k8s/shared/external-secrets/scripts/delete-eso-resources.sh
	unset KUBECONFIG; devspace purge --kube-context "$(MINIKUBE_PROFILE)"
	kubectl delete namespace vault --ignore-not-found --wait=false

vault-bootstrap:
	bash infrastructure/vault/scripts/bootstrap-dev-k8s-auth.sh

vault-seed:
	bash infrastructure/vault/scripts/seed-dev-secrets.sh

vault-database-engine:
	bash infrastructure/vault/scripts/bootstrap-dev-database-engine.sh

eso-sync:
	bash infrastructure/k8s/shared/external-secrets/scripts/deploy-dev.sh
	bash infrastructure/k8s/shared/external-secrets/scripts/wait-for-synced-secrets.sh

eso-sync-dynamic:
	bash infrastructure/k8s/shared/external-secrets/scripts/deploy-dev-dynamic.sh
	bash infrastructure/k8s/shared/external-secrets/scripts/wait-for-synced-secrets.sh

aws-secrets-seed:
	bash infrastructure/scripts/seed-secrets.sh

eso-sync-staging:
	@test -n "$$ESO_IRSA_ROLE_ARN" || (echo "Set ESO_IRSA_ROLE_ARN (terraform output eso_irsa_role_arn)" && exit 1)
	HERMES_ENV=staging bash infrastructure/k8s/shared/external-secrets/scripts/deploy-aws.sh
	HERMES_ENV=staging bash infrastructure/k8s/shared/external-secrets/scripts/wait-for-synced-secrets-aws.sh

eso-sync-prod:
	@read -r -p "Sync production secrets via ESO? [y/N] " confirm; \
	[ "$$confirm" = "y" ] || [ "$$confirm" = "Y" ] || (echo "Aborted." && exit 1)
	@test -n "$$ESO_IRSA_ROLE_ARN" || (echo "Set ESO_IRSA_ROLE_ARN (terraform output eso_irsa_role_arn)" && exit 1)
	HERMES_ENV=prod bash infrastructure/k8s/shared/external-secrets/scripts/deploy-aws.sh
	HERMES_ENV=prod bash infrastructure/k8s/shared/external-secrets/scripts/wait-for-synced-secrets-aws.sh

deploy-prod-k8s: eso-sync-prod
	@read -r -p "Apply production Kubernetes manifests? [y/N] " confirm; \
	[ "$$confirm" = "y" ] || [ "$$confirm" = "Y" ] || (echo "Aborted." && exit 1)
	@test -n "$$USER_SERVICE_IRSA_ROLE_ARN" || (echo "Set USER_SERVICE_IRSA_ROLE_ARN (terraform output user_service_irsa_role_arn)" && exit 1)
	kubectl kustomize infrastructure/k8s/clusters/prod --load-restrictor=LoadRestrictionsNone --enable-helm \
	  | sed "s|PLACEHOLDER_USER_SERVICE_IRSA_ROLE_ARN|$$USER_SERVICE_IRSA_ROLE_ARN|g" \
	  | kubectl apply --server-side --force-conflicts -f -

aws-rotation-enable:
	@echo "Set enable_automatic_rotation = true in terraform.tfvars, then:"
	@echo "  cd infrastructure/terraform/secrets-manager && ./secrets-manager-tf.sh apply"
	@echo "Post-rotation: force ESO sync + rolling restart (see ROTATION.md / PHASE-D.md)."

vault-ui:
	@echo "Vault UI: kubectl port-forward -n vault svc/vault 8200:8200  (then http://127.0.0.1:8200/ui )"

smoke-test-staging:
	HERMES_ENV=staging bash infrastructure/k8s/scripts/smoke-test-secrets.sh

smoke-test-dev:
	HERMES_ENV=dev bash infrastructure/k8s/scripts/smoke-test-secrets.sh

dev:
	bash -euo pipefail -c '\
	trap "kill $$(jobs -p) 2>/dev/null" INT TERM; \
	$(MAKE) back & \
	$(MAKE) front & \
	wait'

setup-podman:
	bash scripts/setup-podman.sh

validate-builds: setup-podman
	bash infrastructure/scripts/validate-builds.sh

validate-manifests:
	bash infrastructure/scripts/validate-manifests.sh

validate-terraform:
	bash infrastructure/scripts/validate-terraform.sh

validate-devspace:
	@KUBECONFIG=/dev/null devspace print > /dev/null
	@for profile in local-secrets integration dynamic-secrets staging prod; do \
		echo "Validating DevSpace profile: $$profile"; \
		KUBECONFIG=/dev/null devspace print -p "$$profile" > /dev/null; \
	done
	@echo "DevSpace config OK for all profiles"

validate-all: validate-builds validate-manifests validate-terraform validate-devspace

integration-test:
	unset KUBECONFIG; MINIKUBE_PROFILE=hermes-test bash infrastructure/scripts/integration-test-local.sh

# Re-run without recreating minikube (~5–10 min if images cached):
#   INTEGRATION_REUSE_CLUSTER=1 INTEGRATION_KEEP_CLUSTER=1 make integration-test
