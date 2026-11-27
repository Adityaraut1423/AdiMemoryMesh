FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .

RUN cat <<'EOF' > /tmp/settings.xml
<settings>
  <mirrors>
    <mirror>
      <id>maven-central-mirror</id>
      <name>Maven Central Mirror</name>
      <url>https://repo.maven.apache.org/maven2</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF

COPY src ./src
RUN for i in 1 2 3; do mvn clean package -DskipTests -B -s /tmp/settings.xml && break || sleep 15; done

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]