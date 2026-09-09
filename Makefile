SHELL := /bin/bash

.DEFAULT_GOAL := help

COMPOSE := docker compose
GRADLE := ./gradlew

SERVICE ?=

.PHONY: help config build up up-build down ps logs restart rebuild test reset


help:
	@echo "Available commands:"
	@echo "  make config                     Validate Docker Compose config"
	@echo "  make build                      Build all Docker images"
	@echo "  make up                         Start the platform"
	@echo "  make up-build                   Build and start the platform"
	@echo "  make down                       Stop the platform"
	@echo "  make ps                         Show containers"
	@echo "  make logs                       Follow all logs"
	@echo "  make logs SERVICE=order-service"
	@echo "  make restart SERVICE=order-service"
	@echo "  make rebuild SERVICE=order-service"
	@echo "  make test                       Run Gradle build and tests"
	@echo "  make reset                      Delete volumes and recreate platform"


.env:
	cp .env.example .env
	@echo "Created .env from .env.example"


config: .env
	$(COMPOSE) config


build: .env
	$(COMPOSE) build


up: .env
	$(COMPOSE) up -d


up-build: .env
	$(COMPOSE) up -d --build


down:
	$(COMPOSE) down


ps:
	$(COMPOSE) ps


logs:
	$(COMPOSE) logs -f $(SERVICE)


restart:
	@test -n "$(SERVICE)" || (echo "Usage: make restart SERVICE=order-service"; exit 1)
	$(COMPOSE) restart $(SERVICE)


rebuild:
	@test -n "$(SERVICE)" || (echo "Usage: make rebuild SERVICE=order-service"; exit 1)
	$(COMPOSE) build --no-cache $(SERVICE)
	$(COMPOSE) up -d --force-recreate $(SERVICE)


test:
	$(GRADLE) clean build --no-daemon


reset: .env
	@echo "WARNING: database volumes will be deleted"
	$(COMPOSE) down -v --remove-orphans
	$(COMPOSE) up -d --build