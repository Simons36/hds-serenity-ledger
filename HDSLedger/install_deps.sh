#!/usr/bin/env bash

mvn install:install-file -Dfile=dependencies/gson-2.10.1.jar -DgroupId="com.google.code.gson" -DartifactId="gson" -Dversion="2.10.1" -Dpackaging="jar"
mvn install:install-file -Dfile=dependencies/junit-4.13.2.jar -DgroupId="junit" -DartifactId="junit" -Dversion="4.13.2" -Dpackaging="jar"

# Install Kitty terminal; if you are using a apt package manager, uncomment the following line and comment the dnf line
#sudo apt install kitty

#sudo dnf install kitty