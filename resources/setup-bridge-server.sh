#!/bin/bash
# Installs Oracle JDK7, Tomcat6, and deploys the Duracloud Snapshot WAR file in Tomcat.
# @author Erik Paulsson

# define some variables
JAVA_PKG=oracle-java7-installer
JAVA_DIR=java-7-oracle
TOMCAT_NAME=tomcat6
MVN_REPO_URL=https://m2.duraspace.org/content/repositories
MVN_REPO_FILE=releases/org/duracloud/snapshot/snapshot/1.0.0/snapshot-1.0.0.war

# Add the WebUpd8 Java repo
echo 'deb http://ppa.launchpad.net/webupd8team/java/ubuntu precise main' > /etc/apt/sources.list.d/webupd8team-java.list
echo 'deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu precise main' >> /etc/apt/sources.list.d/webupd8team-java.list
apt-key adv --keyserver keyserver.ubuntu.com --recv-keys EEA14886
apt-get update

# Accept the Oracle Java license in an automated fashion
echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections
echo debconf shared/accepted-oracle-license-v1-1 seen true | sudo debconf-set-selections

# Now we can install java without any prompts
apt-get install $JAVA_PKG -y

# Create symlinks for default-java under /usr/lib/jvm
# /usr/lib/jvm/default-java is a standard on debian systems and
# Tomcat looks to see if it exists for setting JAVA_HOME
sudo ln -s /usr/lib/jvm/$JAVA_DIR /usr/lib/jvm/default-java
sudo ln -s /usr/lib/jvm/.$JAVA_DIR.jinfo /usr/lib/jvm/.default-java.jinfo
sudo update-java-alternatives -s default-java

# Install Tomcat6
apt-get install $TOMCAT_NAME -y

# Download and deploy the Duracloud / DPN bridge application
wget -O /var/lib/$TOMCAT_NAME/webapps/snapshot.war $MVN_REPO_URL/$MVN_REPO_FILE
