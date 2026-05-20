FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN if [ -f ./mvnw ]; then chmod +x ./mvnw && ./mvnw -q -B -Dmaven.test.skip=true clean package; else apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/* && mvn -q -B -Dmaven.test.skip=true clean package; fi
RUN set -eux; \
    JAR_PATH=''; \
    # Prefer Spring Boot executable jars first (Boot loader present). \
    for f in $(find /app -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -path '*/archive-tmp/*' ! -path '*/surefire/*' ! -path '*/failsafe/*'); do \
      if unzip -l "$f" 2>/dev/null | grep -Eq 'org/springframework/boot/loader/(launch/)?JarLauncher\.class'; then JAR_PATH="$f"; break; fi; \
    done; \
    # Otherwise prefer jars with explicit Main-Class/Start-Class manifest entries. \
    for f in $(find /app -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -path '*/archive-tmp/*' ! -path '*/surefire/*' ! -path '*/failsafe/*'); do \
      if [ -z "$JAR_PATH" ] && unzip -p "$f" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | grep -Eq '^(Main-Class|Start-Class):'; then JAR_PATH="$f"; break; fi; \
    done; \
    if [ -z "$JAR_PATH" ]; then JAR_PATH=$(find /app -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -path '*/archive-tmp/*' ! -path '*/surefire/*' ! -path '*/failsafe/*' -printf '%s %p\n' | sort -nr | head -n1 | cut -d' ' -f2-); fi; \
    test -n "$JAR_PATH" || { echo 'No jar artifact found after Maven build'; exit 1; }; \
    echo "Selected JAR_PATH=$JAR_PATH"; \
    cp "$JAR_PATH" /app/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar
ENV APP_MAIN_CLASS="com.example.project.ProjectApplication"
ENV RENDER_SERVICE_TYPE="web_service"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "if unzip -p /app/app.jar META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | grep -Eq '^(Main-Class|Start-Class):'; then exec java -jar /app/app.jar; elif [ -n \"$APP_MAIN_CLASS\" ]; then exec java -cp /app/app.jar \"$APP_MAIN_CLASS\"; elif [ \"$RENDER_SERVICE_TYPE\" = \"background_worker\" ]; then echo 'Library/background workload detected; keeping worker alive.'; exec sleep infinity; else echo 'No runnable main class found for web service; failing deploy.'; exit 1; fi"]
