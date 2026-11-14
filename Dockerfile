# Build stage: compile and package the WAR.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
# Tests run in CI (./mvnw verify); the image build only packages.
RUN mvn -B -q -DskipTests package

# Runtime stage: Payara Micro 7 (Jakarta EE 11) on JDK 21.
FROM payara/micro:7.2026.11
COPY docker/start.sh docker/post-boot.asadmin /opt/payara/
USER root
RUN chmod 755 /opt/payara/start.sh
USER payara
# ROOT.war is served at "/": the map opens at http://host:8080/ and the API lives under /api
COPY --from=build --chown=payara:payara /workspace/target/city-navigator.war /opt/payara/deployments/ROOT.war
ENTRYPOINT ["tini", "--", "/opt/payara/start.sh"]
