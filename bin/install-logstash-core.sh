# install logstash-core.jar to local repository
mvn install:install-file -Dfile=./logstash-core.jar -DgroupId=org.logstash -DartifactId=logstash-core -Dversion=7.10.0 -Dpackaging=jar