#!/bin/bash

DIST_URL=https://nexus.opendaylight.org/content/repositories/opendaylight.snapshot/org/opendaylight/integration/distribution-karaf/0.5.3-SNAPSHOT/

function install_packages {
    sudo apt-get update -y
    sudo apt-get install npm maven openjdk-8-jdk -y

    cat << EOF > $HOME/maven.env
export MAVEN_OPTS="-Xms256m -Xmx512m" # Very important to put the "m" on the end
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
EOF
}

function install_netvirt {
    cd $HOME
    curl $DIST_URL/maven-metadata.xml | grep -A2 tar.gz | grep value | cut -f2 -d'>' | cut -f1 -d'<' | \
        xargs -I {} curl $DIST_URL/distribution-karaf-{}.tar.gz | tar xvz-
    mv distribution-karaf* $HOME/netvirt
}

function start_netvirt {
    cd $HOME/netvirt/
    sed -i "/^featuresBoot[ ]*=/ s/$/,odl-netvirt-openstack/" etc/org.apache.karaf.features.cfg;
    echo "log4j.logger.org.opendaylight.netvirt = DEBUG,stdout" >> etc/org.ops4j.pax.logging.cfg;
    rm -rf journal snapshots; bin/start
    #wait for netvirt ready
    retries=3
    while [ $retries -gt 0 ]
    do
        sleep 60
        top=$(curl -u admin:admin http://192.168.0.5:8080/restconf/operational/network-topology:network-topology/topology/netvirt:1)
        if [ "$top" == '{"topology":[{"topology-id":"netvirt:1"}]}' ]; then
            break
        fi
        retries=$(( $retries - 1 ))
    done
    if [ $retries -eq 0 ]; then
        echo "Netvirt not started. Exit immediately"
        exit 1
    fi
}

echo "ODL: Packages installation"
install_packages

echo "ODL: Netvirt installation"
install_netvirt

echo "ODL: Netvirt startup"
start_netvirt
