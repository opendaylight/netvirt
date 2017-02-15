#!/usr/bin/env bash

set -e

sudo apt-get update -y
sudo apt-get install -y git openvswitch-switch
sudo ovs-vsctl add-br br-physnet1

sudo rm -rf /opt/stack;
sudo mkdir -p /opt/stack
sudo chown vagrant /opt/stack

git clone https://github.com/openstack-dev/devstack
cd devstack
git checkout stable/newton
cp /vagrant/compute.conf local.conf
./stack.sh

sudo ovs-vsctl add-port br-physnet1 vxlan -- set Interface vxlan type=vxlan options:local_ip=192.168.0.20 options:remote_ip=192.168.0.10 options:dst_port=8888

echo "vagrant ssh control -c '/vagrant/run_tempest.sh'"
