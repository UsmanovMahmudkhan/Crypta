.PHONY: test backend-test mobile-test verifier-test compose-smoke

test: backend-test mobile-test verifier-test

backend-test:
	mvn test

mobile-test:
	cd mobile && flutter pub get && flutter test

verifier-test:
	cd crypta-security-verifier && go test ./...

compose-smoke:
	docker compose up --build -d
	docker compose ps
	curl -fsS http://localhost:8080/actuator/health/readiness
