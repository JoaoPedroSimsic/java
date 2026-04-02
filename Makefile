.PHONY: front back dev minikube-up minikube-reset

MINIKUBE_PROFILE ?= hermes-dev
MINIKUBE_DRIVER ?= docker
MINIKUBE_KUBERNETES_VERSION ?= v1.31.6
MINIKUBE_MEMORY ?= 6144
MINIKUBE_CPUS ?= 3
MINIKUBE_WAIT_TIMEOUT ?= 10m
MINIKUBE_START_EXTRA ?=
SKAFFOLD_TRIGGER ?= polling
SKAFFOLD_DEV_FLAGS ?= --tail=true --verbosity=warn

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

back: minikube-up
	MINIKUBE_PROFILE="$(MINIKUBE_PROFILE)" skaffold dev --trigger="$(SKAFFOLD_TRIGGER)" $(SKAFFOLD_DEV_FLAGS)

dev:
	bash -euo pipefail -c '\
	trap "kill $$(jobs -p) 2>/dev/null" INT TERM; \
	$(MAKE) back & \
	$(MAKE) front & \
	wait'
