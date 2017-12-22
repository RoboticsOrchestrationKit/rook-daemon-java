FROM ubuntu
RUN apt-get update
RUN apt-get -y install less default-jre-headless mosquitto
ADD target/rook-daemon/ /rook/daemon
ADD docker_init /
ENTRYPOINT /docker_init