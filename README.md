snapshot
========

Application responsible for managing a snapshot of content originating from a DuraCloud space


## Running with integration tests
In order to run the build with integration tests successfully a few conditions need to be in place: 

1. You need to specify appropriate -D properties on the maven commandline:
   * you'll definitely need to specify -DskipTestITs=false -Daws.accessKey=<yourkey> -Daws.secretKey=<yourpassword>
   * additionally you'll likely need override the properties set in ./snapshot-webapp/pom.xml. Properties you'll likely need to override: snapshot.duracloud.spaceId, snapshot.duracloud.storeId etc.
2. The duracloud instance referenced in those properties will need to be up and running.
