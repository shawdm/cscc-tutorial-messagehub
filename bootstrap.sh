#!/usr/bin/env bash

# update
apt-get update -y

apt-get autoremove -y

# install java
apt-get install -y openjdk-7-jdk

# install maven
apt-get install -y maven

# install gradle
apt-get install -y gradle
