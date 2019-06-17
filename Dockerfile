FROM openjdk:10-jre-slim
RUN mkdir /Ramsey
COPY target/ramsey-mw.jar /Ramsey/
COPY target/version.txt /Ramsey/
CMD ["java", "-jar", "/Ramsey/ramsey-mw.jar"]
