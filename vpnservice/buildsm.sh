mvn -f statemanager/pom.xml clean install -Pq
mvn -f features/pom.xml clean install -Pq
mvn -f distribution/karaf/pom.xml clean install -Pq
