# Use an existing docker image as a base. This gets used by Travis integration testing to test core
# This is used to run the travis tests
# Copies code to this container and then runs gradle build
FROM synadia/bridge-gradle:0.10.0-beta6

#Copying files
COPY . ./usr/app

WORKDIR /usr/app/core

ENV JAVA_HOME /root/.sdkman/candidates/java/current
ENV GRADLE_HOME /root/.sdkman/candidates/gradle/current

ENV NATS_BRIDGE_IBM_MQ_HOST=tcp://ibm-mq:1414
ENV NATS_BRIDGE_NATS_SERVERS=nats://nats-server:4222
ENV NATS_BRIDGE_JMS_CONNECTION_FACTORY=tcp://active-mq:61616

#Build Admin
WORKDIR /usr/app/message
RUN bash -c "./gradlew clean build publishToMavenLocal -x test"


WORKDIR /usr/app/core
CMD bash -c "cd /usr/app/message && $GRADLE_HOME/bin/gradle clean build publishToMavenLocal -x test && cd /usr/app/core && $GRADLE_HOME/bin/gradle clean test"
