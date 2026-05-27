.PHONY: front back dev teardown minikube-up minikube-reset vault-up vault-reset vault-bootstrap vault-seed vault-ui

MINIKUBE_PROFILE ?= hermes-dev
MINIKUBE_DRIVER ?= docker
MINIKUBE_KUBERNETES_VERSION ?= v1.31.6
MINIKUBE_MEMORY ?= 6144
MINIKUBE_CPUS ?= 3
MINIKUBE_WAIT_TIMEOUT ?= 10m
MINIKUBE_START_EXTRA ?=
SKAFFOLD_TRIGGER ?= polling
SKAFFOLD_DEV_FLAGS ?= --tail=true --verbosity=warn --keep-running-on-failure

minikube-up:
	@if ! minikube status -p "$(MINIKUBE_PROFILE)" --format='{{.Host}}' 2>/dev/null | grep -q Running; then \
		echo "Starting minikube profile $(MINIKUBE_PROFILE) ($(MINIKUBE_KUBERNETES_VERSION))..."; \
		minikube start -p "$(MINIKUBE_PROFILE)" --driver="$(MINIKUBE_DRIVER)" \
			--kubernetes-version="$(MINIKUBE_KUBERNETES_VERSION)" \
			--memory="$(MINIKUBE_MEMORY)" \
			--cpus="$(MINIKUBE_CPUS)" \
			--wait-timeout="$(MINIKUBE_WAIT_TIMEOUT)" \
			$(MINIKUBE_START_EXTRA); \
	else \
		echo "Minikube profile $(MINIKUBE_PROFILE) already running."; \
	fi

minikube-reset:
	minikube delete -p "$(MINIKUBE_PROFILE)" || true
	rm -rf "$(HOME)/.minikube/profiles/$(MINIKUBE_PROFILE)"
	minikube start -p "$(MINIKUBE_PROFILE)" --driver="$(MINIKUBE_DRIVER)" \
		--kubernetes-version="$(MINIKUBE_KUBERNETES_VERSION)" \
		--memory="$(MINIKUBE_MEMORY)" \
		--cpus="$(MINIKUBE_CPUS)" \
		--wait-timeout="$(MINIKUBE_WAIT_TIMEOUT)" \
		$(MINIKUBE_START_EXTRA)

front:
	cd frontend && bun run start

back: minikube-up vault-up
	@if pgrep -x skaffold >/dev/null 2>&1; then \
		echo "WARNING: another 'skaffold dev' is already running. Stop it (Ctrl+C) before make back."; \
		exit 1; \
	fi
	@echo "Starting Skaffold (15 Deployments; Vault is deployed separately via vault-up)."
	MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)" skaffold dev --trigger="$(SKAFFOLD_TRIGGER)" $(SKAFFOLD_DEV_FLAGS)

vault-up:
	bash infrastructure/vault/scripts/deploy-dev.sh

vault-reset:
	kubectl delete namespace vault --ignore-not-found --wait=true
	bash infrastructure/vault/scripts/deploy-dev.sh

teardown:
	bash infrastructure/k8s/shared/external-secrets/scripts/delete-eso-resources.sh
	skaffold delete
	kubectl delete namespace vault --ignore-not-found --wait=false

vault-bootstrap:
	bash infrastructure/vault/scripts/bootstrap-dev-k8s-auth.sh

vault-seed:
	bash infrastructure/vault/scripts/seed-dev-secrets.sh

vault-ui:
	@echo "Vault UI: kubectl port-forward -n vault svc/vault 8200:8200  (then http://127.0.0.1:8200/ui )"

dev:
	bash -euo pipefail -c '\
	trap "kill $$(jobs -p) 2>/dev/null" INT TERM; \
	$(MAKE) back & \
	$(MAKE) front & \
	wait'
