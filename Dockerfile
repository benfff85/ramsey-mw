FROM adoptopenjdk:13-jre-hotspot
RUN mkdir /Ramsey
COPY target/ramsey-mw.jar /Ramsey/
COPY target/version.txt /Ramsey/
CMD java -jar -XX:+UnlockExperimentalVMOptions -XX:MaxRAMPercentage=90 -XX:+CrashOnOutOfMemoryError -XshowSettings -Dspring.profiles.active=${SPRING_PROFILE} /Ramsey/ramsey-mw.jar
