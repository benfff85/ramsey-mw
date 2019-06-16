FROM openjdk:10-jre-slim
RUN mkdir /Ramsey
COPY target/ramsey-mw-${VERSION}.jar /Ramsey/
CMD ["java", "-jar", "/Ramsey/ramsey-mw-${VERSION}.jar"]
