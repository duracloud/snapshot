#!/bin/bash
# Installs Oracle JDK, Tomcat, and deploys the Duracloud Snapshot WAR file in Tomcat.
# @author Erik Paulsson

# define some variables
JAVA_PKG=oracle-java7-installer
JAVA_DIR=java-7-oracle
TOMCAT_NAME=tomcat6
MVN_REPO_URL=https://m2.duraspace.org/content/repositories
MVN_REPO_FILE=releases/org/duracloud/snapshot/snapshot/1.0.0/snapshot-1.0.0.war
CONTENT_DIR=/export/duraspace

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

# Install Tomcat
apt-get install $TOMCAT_NAME -y

# Download and deploy the Duracloud Bridge application
wget -O /var/lib/$TOMCAT_NAME/webapps/snapshot.war $MVN_REPO_URL/$MVN_REPO_FILE

groupadd duraspace
usermod -a -G duraspace $TOMCAT_NAME
chown -R root:duraspace $CONTENT_DIR
chmod -R g+w $CONTENT_DIR

# Post setup options:
# Setup apache mod_jk to use apache as proxy to tomcat:
# http://thetechnocratnotebook.blogspot.com/2012/05/installing-tomcat-7-and-apache2-with.html
