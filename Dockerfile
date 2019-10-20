FROM adoptopenjdk:12-jre-hotspot
RUN mkdir /Ramsey
COPY target/ramsey-mw.jar /Ramsey/
COPY target/version.txt /Ramsey/
CMD java -jar /Ramsey/ramsey-mw.jar --spring.profiles.active=${SPRING_PROFILE}
