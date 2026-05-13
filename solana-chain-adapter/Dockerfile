FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/solana-chain-adapter-*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
