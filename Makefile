.PHONY: dev

dev:
	skaffold dev & cd frontend && bun run start
