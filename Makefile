.PHONY: up down api web seed test build logs realm reset help deploy deploy-logs deploy-down prune

help:
	@echo "VisionOne - Phase 1"
	@echo "  make up     Start Postgres and Keycloak"
	@echo "  make api    Run the Spring Boot API on :8080"
	@echo "  make web    Run the Vite dev server on :5173"
	@echo "  make test   Backend and frontend test suites"
	@echo "  make build  Production builds of both"
	@echo "  make down   Stop the stack (keeps data)"
	@echo ""
	@echo "  On the demo server:"
	@echo "  make deploy       Render the realm and bring the stack up"
	@echo "  make deploy-logs  Follow logs"
	@echo "  make deploy-down  Stop the demo stack"
	@echo "  make prune        Reclaim disk from old images and build cache"
	@echo "  make reset  Stop the stack and delete all data"

up:
	docker compose -f infra/docker-compose.yml up -d
	@echo "Waiting for Postgres and Keycloak to become healthy..."
	@docker compose -f infra/docker-compose.yml ps

down:
	docker compose -f infra/docker-compose.yml down

reset:
	docker compose -f infra/docker-compose.yml down -v

logs:
	docker compose -f infra/docker-compose.yml logs -f

api:
	cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'

web:
	cd frontend && npm run dev

test:
	cd backend && ./gradlew test
	cd frontend && npm test

build:
	cd backend && ./gradlew build
	cd frontend && npm run build

# --- demo deployment (run these ON the server, from the repo root) ---

deploy:
	@test -f infra/.env.prod || (echo "infra/.env.prod is missing - copy infra/.env.prod.example and fill it in"; exit 1)
	set -a && . ./infra/.env.prod && set +a && python3 scripts/render-realm.py
	docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml up -d --build
	docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml ps
	@$(MAKE) --no-print-directory prune

# Reclaim what a build leaves behind. Docker's build cache is unbounded by default and reached
# 6.5 GB on a development machine after a handful of builds - on a 40 GB VPS that is the thing that
# fills the disk, and a full disk takes Postgres down with it. 2 GB is kept so the next deploy still
# reuses the dependency layers rather than re-downloading Gradle's whole graph.
# Images are pruned dangling-only, so a rollback to the previous tagged image stays possible.
prune:
	-docker image prune -f
	-docker builder prune -f --keep-storage=2GB
	@docker system df

deploy-logs:
	docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml logs -f

deploy-down:
	docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml down
