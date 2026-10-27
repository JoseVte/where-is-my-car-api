#!/usr/bin/env bash
# Seeds the development database (test user in Firebase + MySQL, with categories) and exits.
# Starts MySQL through Spring's docker compose support if it is not running.
set -euo pipefail

cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"

./mvnw -q spring-boot:run -Dspring-boot.run.arguments="\
--app.seed.enabled=true \
--app.seed.exit-after-run=true \
--spring.main.web-application-type=none \
--spring.devtools.restart.enabled=false \
--spring.devtools.livereload.enabled=false \
--spring.docker.compose.lifecycle-management=start-only"
