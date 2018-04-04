.. contents:: Table of Contents
   :depth: 3

====================
Conntrack Based SNAT
====================

https://git.opendaylight.org/gerrit/#/q/topic:snat_conntrack

The ovs conntrack based SNAT implements Source Network Address Translation using openflow rules by
leveraging ovs-netfilter integration.

Problem description
===================

Today SNAT is done in Opendaylight netvirt using controller punting and thus controller installing
the rules for inbound and outbound NAPT. This causes significant delay as the first packet of all
the new connections needs to go through the controller.The number of flows grows linearly with the
increase in the vms. Also the current implementation does not support ICMP.

The current algorithm for selecting the NAPT switch does not work well with conntrack based SNAT.
For a NAPT switch to remain as designated NAPT switch, it requires at least one port from any of
the subnets present in the router. When such a port cease to exist a new NAPT switch will be
elected. With the controller based implementation the failover is faster as the NAT flows are
reinstalled to the new NAPT switch and should not lead to termination of existing connection.
With the conntrack based approach, the translation will be lost and the newly elected switch will
have to redo the translation. This will lead to connection timeout for TCP like connections. So
the re-election needs to be prevented unless switch is down. Also the current implementation
tends to select the node running the DHCP agent as the designated NAPT switch as the DHCP port is
the first port created for a subnet.

Use Cases
---------
The following use case will be realized by the implementation

External Network Access
The SNAT enables the VM in a tenant network access the external network without using a floating ip. It
uses NAPT for sharing the external ip address across multiple VMs that share the same router
gateway.

Proposed change
===============

The proposed implementation uses linux netfilter framework to do the NAPT (Network Address Port
Translation) and for tracking the connection. The first packet of  a traffic will be committed to
the netfilter for translation along with the external ip. The subsequent packets will use the entry
in the netfilter for inbound and outbound translation. The router id will be used as the zone id in
the netfilter. Each zone tracks the connection in its own table. The rest of the implementation for
selecting the designated NAPT switch and non designated switches will remain the same. The pipeline
changes will happen in the designated switch. With this implementation we will be able to do
translation for icmp as well.

The openflow plugin needs to support new set of actions for conntrack based NAPT. This shall be
added in the nicira plugin extension of OpenFlow plugin.

The new implementation will not re-install the existing NAT entries to the new NAPT switch during
fail-over.  Also spec does not cover the use case of having multiple external subnets in the same
router.

The HA framework will have a new algorithm  to elect the designated NAPT switch. The
new logic will be applicable only if the conntrack mode is selected. The switch selection logic
will also be modified to use round robin logic with weights associated with each switch. It will
not take into account whether a port belonging to a subnet in the router is present in the switch.
The initial weight of all the switches shall be 0 and will be incremented by 1 when the switch is
selected as the designated NAPT. The weights shall be decremented by 1 when the router is deleted.
At any point of time the switch with the lowest weight will be selected as the designated NAPT
switch for a new router. If there are multiple the first one with the lowest weight will be
selected. A pseudo port will be added in the switch which is selected as the designated NAPT
switch. This port will be deleted only when the switch cease to be a designated NAPT switch. This
helps the switch to maintain the remote flows even when there are no ports in the router subnet in
the switch. Only if the switch hosting the designated NAPT switch is down a new NAPT switch will be
elected.

Pipeline changes
----------------
The ovs based NAPT flows will replace the controller based NAPT flows. The changes are limited
to the designated switch for the router. Below is the illustration for flat external network.

Outbound NAPT

Table 26 (PSNAT Table)  => submits the packet to netfilter to check whether it is an existing
connection. Resubmits the packet back to 46.

Table 46 (NAPT OUTBOUND TABLE) => if it is an established connection, it indicates the
translation is done and the packet is forwarded to table 47 after writing the external network
metadata.

If it is a new connection the connection will be committed to netfilter and this entry will be
used for NAPT. The translated packet will be resubmitted to table 47. The external network
metadata will be written before sending the packet to netfilter.

Table 47 (NAPT FIB TABLE) => The translated packet will be sent to the egress group.

Sample Flows

::

 table=26, priority=5,ip,metadata=0x222e2/0xfffffffe actions=ct(table=46,zone=5003,nat)
 table=46, priority=6,ct_state=+snat,ip,metadata=0x222e2/0xfffffffe actions=set_field:0x222e0->metadata,resubmit(,47)
 table=46, priority=5,ct_state=+new+trk,ip,metadata=0x222e2/0xfffffffe actions=set_field:0x222e0->metadata,ct(commit,table=47,zone=5003,nat(src=192.168.111.21))
 table=47, n_packets=0, n_bytes=0, priority=6,ct_state=+snat,ip,nw_src=192.168.111.21 actions=group:200000


Inbound NAPT

Table 44 (NAPT INBOUND Table)=> submits the packet to netfilter to check for an existing
connection after changing the metadata to that of the internal network. The packet will be
submitted back to table 47.

Table 47 (NAPT FIB TABLE) => The translated packet will be submitted back to table 21.

Sample Flows

::

 table=21, priority=42,ip,metadata=0x222e0/0xfffffffe,nw_dst=192.168.111.21 actions=resubmit(,44)
 table=44, priority=10,ip,metadata=0x222e0/0xfffffffe,nw_dst=192.168.111.21 actions=set_field:0x222e2->metadata,ct(table=47,zone=5003,nat)
 table=47, priority=5,ct_state=+dnat,ip actions=resubmit(,21)

Yang changes
------------
The nicira-action.yang and the openflowplugin-extension-nicira-action.yang needs to be updated
with nat action. The action structure shall be

::

  typedef nx-action-nat-range-present {
      type enumeration {
          enum NX_NAT_RANGE_IPV4_MIN {
              value 1;
              description "IPV4 minimum value is present";
          }
          enum NX_NAT_RANGE_IPV4_MAX {
              value 2;
              description "IPV4 maximum value is present";
          }
          enum NX_NAT_RANGE_IPV6_MIN {
              value 4;
              description "IPV6 minimum value is present in range";
          }
          enum NX_NAT_RANGE_IPV6_MAX {
              value 8;
              description "IPV6 maximum value is present in range";
          }
          enum NX_NAT_RANGE_PROTO_MIN {
              value 16;
              description "Port minimum value is present in range";
          }
          enum NX_NAT_RANGE_PROTO_MAX {
              value 32;
              description "Port maximum value is present in range";
          }
      }
   }

  typedef nx-action-nat-flags {
      type enumeration {
          enum NX_NAT_F_SRC {
              value 1;
              description "Source nat is selected ,Mutually exclusive with NX_NAT_F_DST";
          }
          enum NX_NAT_F_DST {
              value 2;
              description "Destination nat is selected";
          }
          enum NX_NAT_F_PERSISTENT {
              value 4;
              description "Persistent flag is selected";
          }
          enum NX_NAT_F_PROTO_HASH {
              value 8;
              description "Hash mode is selected for port mapping, Mutually exclusive with
              NX_NAT_F_PROTO_RANDOM ";
          }
          enum NX_NAT_F_PROTO_RANDOM {
              value 16;
              description "Port mapping will be randomized";
          }
      }
   }

  grouping ofj-nx-action-conntrack-grouping {
      container nx-action-conntrack {
          leaf flags {
              type uint16;
          }
          leaf zone-src {
              type uint32;
          }
          leaf conntrack-zone {
              type uint16;
          }
          leaf recirc-table {
              type uint8;
          }
          leaf experimenter-id {
              type oft:experimenter-id;
          }
          list ct-actions{
              uses ofpact-actions;
          }
      }
   }

  grouping ofpact-actions {
      description
         "Actions to be performed with conntrack.";
      choice ofpact-actions {
           case nx-action-nat-case {
              container nx-action-nat {
                  leaf flags {
                      type uint16;
                  }
                  leaf range_present {
                      type uint16;
                  }
                  leaf ip-address-min {
                      type inet:ip-address;
                  }
                  leaf ip-address-max {
                      type inet:ip-address;
                  }
                  leaf port-min {
                      type uint16;
                  }
                  leaf port-max {
                      type uint16;
                  }
              }
          }
      }
  }

For the new configuration knob a new yang natservice-config shall be added in NAT service, with the
container for holding the NAT mode configured. It will have two options controller and conntrack,
with controller being the default.

::

  container natservice-config {
    config true;
    leaf nat-mode {
        type enumeration {
            enum "controller";
            enum "conntrack";
        }
        default "controller";
    }
  }

Configuration impact
--------------------
The proposed change requires the NAT service to provide a configuration knob to switch between the
controller based/conntrack based implementation. A new configuration file
netvirt-natservice-config.xml shall be added with default value controller.

::

  <natservice-config xmlns="urn:opendaylight:netvirt:natservice-config">
    <nat-mode>controller</nat-mode>
  </natservice-config>

The dynamic update of nat-mode will not be supported. To change the nat-mode the controller cluster
needs to be restarted after changing the nat-mode. On restart the NAT translation lifecycle will be
reset and after the controller comes up in the updated nat-mode, a new set of switches will be
elected as designated NAPT switches and it can be different from the ones that were forwarding
traffic earlier.

Clustering considerations
-------------------------
NA

Other Infra considerations
--------------------------
The implementation requires ovs2.6 with the kernel module installed. OVS currently does not support
SNAT connection tracking for dpdk datapath. It would be supported in some future release.

Security considerations
-----------------------
NA

Scale and Performance Impact
----------------------------
The new SNAT implementation is expected to improve the performance when compared to the existing
one and will reduce the flows in ovs pipeline.

Targeted Release
----------------
Carbon

Alternatives
------------
An alternative implementation of X NAPT switches was discussed, which will not be a part of this
document but will be considered as a further enhancement.

Usage
=====

Create External Network
-----------------------
Create an external flat network and subnet

::

 neutron net-create ext1 --router:external  --provider:physical_network public --provider:network_type flat
 neutron subnet-create --allocation-pool start=<start-ip>,end=<end-ip> --gateway=<gw-ip> --disable-dhcp --name subext1 ext1 <subnet-cidr>

Create Internal Network
-----------------------
Create an internal n/w and subnet

::

 neutron net-create vx-net1 --provider:network_type vxlan
 neutron subnet-create vx-net1 <subnet-cidr> --name vx-subnet1

Create Router
-------------
Create a router and add an interface to internal n/w. Set the external n/w as the router gateway.

::

 neutron router-create router1
 neutron router-interface-add  router1 vx-subnet1
 neutron router-gateway-set router1 ext1
 nova boot --poll --flavor m1.tiny --image $(nova image-list | grep 'uec\s' | awk '{print $2}' | tail -1) --nic net-id=$(neutron net-list | grep -w vx-net1 | awk '{print $2}') vmvx2

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
NA

CLI
---
A new command line, display-napt-switch, will be added to display the current designated NAPT
switch selected for each router. It shall show the below info.

::

 router id | Host Name of designated NAPT switch | Management Ip of the designated NAPT switch

Implementation
==============

Assignee(s)
-----------
Aswin Suryanarayanan <asuryana@redhat.com>

Work Items
----------
https://trello.com/c/DMLsrLfq/9-snat-decentralized-ovs-nat-based

* Write a framework which can support multiple modes of NAT implementation.
* Add support in openflow plugin for conntrack nat actions.
* Add support in genius for conntrack nat actions.
* Add a config parameter to select between controller based and conntrack based.
* Add the flow programming for SNAT in netvirt.
* Add the new HA framework.
* Add the command to display the designated NAPT switch.
* Write Unit tests for conntrack based snat.

Dependencies
============
NA

Testing
=======


Unit Tests
----------
Unit test needs to be added for the new snat mode. It shall use the component tests framework

Integration Tests
-----------------
Integration tests needs to be added for the conntrack snat flows.

CSIT
----
Run the CSIT with conntrack based SNAT configured.

Documentation Impact
====================
Necessary documentation would be added on how to use this feature.

References
==========
