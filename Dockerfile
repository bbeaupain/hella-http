FROM openjdk:18
EXPOSE 8080
COPY target/hella-http-1.0-SNAPSHOT-jar-with-dependencies.jar .
CMD java -jar hella-http-1.0-SNAPSHOT-jar-with-dependencies.jar
