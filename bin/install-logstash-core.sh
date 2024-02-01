# install logstash-core.jar to local repository
mvn install:install-file -Dfile=./logstash-core.jar -DgroupId=org.logstash -DartifactId=logstash-core -Dversion=8.12.0 -Dpackaging=jar