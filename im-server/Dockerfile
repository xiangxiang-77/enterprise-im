FROM eclipse-temurin:8-jre
WORKDIR /app
COPY target/im-server-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080 9000
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
