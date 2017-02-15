#!/usr/bin/env bash

set -e

sudo apt-get update -y
sudo apt-get install -y git openvswitch-switch
sudo ovs-vsctl add-br br-physnet1

sudo rm -rf /opt/stack
sudo mkdir -p /opt/stack
sudo chown vagrant /opt/stack

git clone https://github.com/openstack-dev/devstack
cd devstack
git checkout stable/newton
cp /vagrant/control.conf local.conf
./stack.sh

sudo ovs-vsctl add-port br-physnet1 vxlan -- set Interface vxlan type=vxlan options:local_ip=192.168.0.10 options:remote_ip=192.168.0.20 options:dst_port=8888
sudo ifconfig br-physnet1 up 10.10.10.1/24

source ~/devstack/openrc admin admin

neutron net-create external-net --router:external --provider:network_type=flat --provider:physical_network=physnet1
neutron subnet-create external-net 10.10.10.0/24  --name external-subnet --gateway 10.10.10.1

tempest init ~/tempest
cp /opt/stack/tempest/etc/tempest.conf ~/tempest/etc
netid=`neutron net-list | grep external-net | awk '{print $2}'`
sed -i "s/public_network_id.*/public_network_id = $netid/" ~/tempest/etc/tempest.conf
