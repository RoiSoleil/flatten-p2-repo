#!/bin/bash
set -x
export
cd $GITHUB_ACTION_PATH
wget https://repo1.maven.org/maven2/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar
wget https://repo1.maven.org/maven2/org/tukaani/xz/1.9/xz-1.9.jar
export CLASSPATH=commons-io-2.15.1.jar\;xz-1.9.jar
java FlattenP2Repo.java
