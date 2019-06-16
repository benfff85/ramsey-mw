FROM openjdk:10-jre-slim
RUN mkdir /Ramsey
COPY target/ramsey-mw-0.1.0.jar /Ramsey/
CMD ["java", "-jar", "/Ramsey/ramsey-mw-0.1.0.jar"]
