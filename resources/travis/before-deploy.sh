#!/bin/bash
echo 'Starting before-deploy.sh'
if [ "$TRAVIS_BRANCH" = 'master' ] || [ "$TRAVIS_BRANCH" = 'develop' ]; then
    if [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
        echo "Decrypting code signing key"
        openssl aes-256-cbc -K $encrypted_e3f51da3741f_key -iv $encrypted_e3f51da3741f_iv -in resources/travis/codesignkey.asc.enc -out codesignkey.asc -d
        gpg --fast-import codesignkey.asc
    fi
fi

# function that generates a beanstalk zip
generateBeanstalkZip ()
{ echo "Generating beanstalk zip"
   zipFile=$1
   echo "zipFile=${zipFile}"
   echo "stage beanstalk package"
   mvn process-resources --non-recursive
   cd target
   zip -r ${zipFile} ROOT.war bridge.war .ebextensions
   cd ..
}

targetDir=$TRAVIS_BUILD_DIR/target
mkdir -p $targetDir

currentGitCommit=`git rev-parse HEAD`;
projectVersion=`mvn -q -Dexec.executable="echo" -Dexec.args='${project.version}' --non-recursive exec:exec`

if [ "$TRAVIS_BRANCH" = 'develop' ] || [ "$TRAVIS_BRANCH" = 'master' ] || [ ! -z "$TRAVIS_TAG" ]; then
   echo "Generating beanstalk zip for $projectVersion ${currentGitCommit}..."
   beanstalkFile="duracloud-bridge-beanstalk-v$projectVersion-${currentGitCommit:0:7}.zip"
   generateBeanstalkZip ${beanstalkFile}

   #make a copy of the beanstalk file using fixed name:
   cp $targetDir/${beanstalkFile} $targetDir/duracloud-bridge-beanstalk-latest.zip
fi

if [ ! -z "$TRAVIS_TAG" ]; then
    # generate javadocs only for tagged releases
    echo "Generating  javadocs..."
    # the irodsstorageprovider is excluded due to maven complaining about it. This exclusion will likely be temporary.
    # same goes for duradmin and synctoolui due to dependencies on unconventional setup of org.duracloud:jquery* dependencies.
    mvn javadoc:aggregate -Dadditionalparam="-Xdoclint:none" -Pjava8-disable-strict-javadoc  --batch-mode
    cd $targetDir/site/apidocs
    zipFile=duracloud-bridge-${projectVersion}-apidocs.zip
    echo "Zipping javadocs..."
    zip -r ${zipFile} .
    mv ${zipFile} $targetDir/
    cd $targetDir
    rm -rf install site javadoc-bundle-options
fi

cd $TRAVIS_BUILD_DIR
echo 'Completed before-deploy.sh'
