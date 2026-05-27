.PHONY: front back back-local dev teardown minikube-up minikube-reset vault-up vault-init vault-reset vault-bootstrap vault-seed vault-ui eso-sync

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

back: minikube-up vault-init eso-sync
	@if pgrep -x skaffold >/dev/null 2>&1; then \
		echo "WARNING: another 'skaffold dev' is already running. Stop it (Ctrl+C) before make back."; \
		exit 1; \
	fi
	@echo "Starting Skaffold (Vault + ESO + apps; secrets synced from Vault by default)."
	MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)" skaffold dev --trigger="$(SKAFFOLD_TRIGGER)" $(SKAFFOLD_DEV_FLAGS)

back-local: minikube-up
	@if pgrep -x skaffold >/dev/null 2>&1; then \
		echo "WARNING: another 'skaffold dev' is already running. Stop it (Ctrl+C) before make back-local."; \
		exit 1; \
	fi
	@echo "Starting Skaffold with local secrets.env (non-prod offline fallback; no Vault required)."
	MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)" skaffold dev -p local-secrets --trigger="$(SKAFFOLD_TRIGGER)" $(SKAFFOLD_DEV_FLAGS)

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
	skaffold delete
	kubectl delete namespace vault --ignore-not-found --wait=false

vault-bootstrap:
	bash infrastructure/vault/scripts/bootstrap-dev-k8s-auth.sh

vault-seed:
	bash infrastructure/vault/scripts/seed-dev-secrets.sh

eso-sync:
	bash infrastructure/k8s/shared/external-secrets/scripts/deploy-dev.sh
	bash infrastructure/k8s/shared/external-secrets/scripts/wait-for-synced-secrets.sh

vault-ui:
	@echo "Vault UI: kubectl port-forward -n vault svc/vault 8200:8200  (then http://127.0.0.1:8200/ui )"

dev:
	bash -euo pipefail -c '\
	trap "kill $$(jobs -p) 2>/dev/null" INT TERM; \
	$(MAKE) back & \
	$(MAKE) front & \
	wait'
