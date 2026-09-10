FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/inspiration-forge-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"

EXPOSE 8123

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
