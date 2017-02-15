#!/usr/bin/env bash

set -e

sudo ifconfig br-physnet1 up 10.10.10.1/24

source ~/devstack/openrc admin admin
neutron net-create external-net --router:external --provider:network_type=flat --provider:physical_network=physnet1
neutron subnet-create external-net 10.10.10.0/24  --name external-subnet --gateway 10.10.10.1

tempest init ~/tempest
cp /opt/stack/tempest/etc/tempest.conf ~/tempest/etc
netid=`neutron net-list | grep external-net | awk '{print $2}'`
sed -i "s/public_network_id.*/public_network_id = $netid/" ~/tempest/etc/tempest.conf
cd ~/tempest; tempest run --regex tempest.scenario.test_network_basic_ops.TestNetworkBasicOps.test_mtu_sized_frames
