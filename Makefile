.PHONY: up down api web seed test build logs realm reset help

help:
	@echo "VisionOne - Phase 1"
	@echo "  make up     Start Postgres, Keycloak and Kafka"
	@echo "  make api    Run the Spring Boot API on :8080"
	@echo "  make web    Run the Vite dev server on :5173"
	@echo "  make test   Backend and frontend test suites"
	@echo "  make build  Production builds of both"
	@echo "  make down   Stop the stack (keeps data)"
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
