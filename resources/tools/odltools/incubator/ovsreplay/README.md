# ovs-replay.py - launch a running OVS configured from dumps

This script creates a docker container running a OVS configured
with the exact ports, flows, and groups captured from a running
netvirt environment. Once the containerized OVS is running you 
can debug via 
* ovs-appctl ofproto/trace (you need to manually specify the vswitchd socket file...need to fix this)
* send packets from the port namespaces created (see them by typeing `ip netns`)
* run tcpdump on a port (don't forget -n) 
* run tcpdump on a flow using: https://github.com/jhershberg/WIP/blob/master/ovs-flow-snoop


## Usage

Just once do: 
`sudo docker pull jhershbe/fedora-ovs-replay`

```
$ ./ovs-replay.py --help
usage: ovs-replay.py [-h] [--name NAME] flows groups ports show

positional arguments:
  flows        path to the flow dump
  groups       path to the groups dump
  ports        path to the ports dump
  show         path to the show dump

optional arguments:
  -h, --help   show this help message and exit
  --name NAME  name the container, or "ovs-replay" if option omitted
```
The dumps must be generated such:
* flows: ovs-ofctl -OOpenFlow13 dump-flows br-int
* groups: ovs-ofctl -OOpenFlow13 dump-group br-int
* ports: restconf response of /neutron:neutron/ports
* show: ovs-ofctl show br-int
