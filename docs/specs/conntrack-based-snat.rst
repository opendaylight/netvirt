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
increase in the vm. Also the current implementation does not support ICMP.

Use Cases
---------
The following use case will be realized by the implementation

External Network Access
The SNAT enables the VM in a tenant network to the external network without using a floating ip. It
uses NAPT for sharing the external ip address across multiple VMs that share the same router
gateway.

Proposed change
===============

The proposed implementation uses linux netfilter framework to do the NAPT (Network Address Port
Translation) and for tracking the connection. The first packet of  a traffic will be committed to
the netfilter for translation along with the external ip for translation.  The subsequent packets
will use the entry in the netfilter for inbound and outbound translation. The router id will be
used as the zone id in the netfilter. Each zone tracks the connection in its own table. The rest
of the implementation for selecting the designated NAPT switch and non designated switches will
remain the same. The pipeline changes will happen in the designated switch. With this
implementation we will be able to do translation for icmp as well.

The openflow plugin needs to support new set of action for conntrack based NAPT. This shall be
added in the nicira plugin extension of OpenFlow plugin.

Pipeline changes
----------------
The ovs based NAPT flows will replace the controller based NAPT flows. It reuses the existing
tables, the changes are limited to the designated switch for the router.

Outbound NAPT

Table 26 (PSNAT Table)  => submits the packet to netfilter to check whether it is an existing
connection. Resubmits the packet back to 46.

Table 46 (NAPT OUTBOUND TABLE) => if it is an established connection which indicates the
translation is done and the packet is forwarded to table 47.
If it is a new connection the connection will be committed to netfilter and this entry will be
used for napt. The translated packet will be resubmitted to table 47.

Sample Flows

table=26,priority=5,ip,metadata=0x222e0/0xfffffffe action=ct'('table=46,zone=1,nat')'
table=46,priority=5,ct_state=+trk+est,metadata=0x222e0/0xfffffffe,ip,action=write_metadata:0x222e0/0xfffffffe,goto_table:47
table=46,priority=5,ip,ct_state=+trk+new,metadata=0x222e0/0xfffffffe,actions=write_metadata:0x222e0/0xfffffffe,ct'('commit,zone=1,nat'('src=192.168.56.9-192.168.56.9'))',ct'('table=47,zone=1,nat')’

Inbound NAPT

Table 21 (L3 FIB Table)=> submits the packet to netfilter to check for an existing connection.
The packet will be submitted back to table 44.
Table 44 (NAPT INBOUND Table)=>writes the appropriate metadata to the translated packet and
submits the packet to table 47.

Sample Flows

table=21,priority=5,ip,metadata=0x222e0/0xfffffffe,nw_dst=192.168.56.9,actions=ct'('table=44,zone=1,nat')'
table=44,priority=5,ip,metadata=0x222e0/0xfffffffe,nw_dst=10.100.5.3,actions=write_metadata:0x222e2/0xfffffffe,goto_table:47

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

Configuration impact
--------------------
The proposed change requires the NAT service to provide a configuration knob to switch between the
controller based/conntrack based implementation. A new configuration file shall be added for this.

Clustering considerations
-------------------------
NA

Other Infra considerations
--------------------------
The implementation requires ovs2.6 with the kernel module installed. DPDK support is not yet
available in ovs.

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

* Create an external flat network and subnet

::

 neutron net-create ext1 --router:external  --provider:physical_network public --provider:network_type flat
 neutron subnet-create --allocation-pool start=<start-ip>,end=<end-ip> --gateway=<gw-ip> --disable-dhcp --name subext1 ext1 <subnet-cidr>

* Create an internal n/w and subnet

::

 neutron net-create vx-net1 --provider:network_type vxlan
 neutron subnet-create vx-net1 <subnet-cidr> --name vx-subnet1

* Create a router and add an interface to internal n/w. Set the external n/w as the router gateway.

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
NA

Implementation
==============

Assignee(s)
-----------
Aswin Suryanarayanan <asuryana@redhat.com>

Work Items
----------
https://trello.com/c/DMLsrLfq/9-snat-decentralized-ovs-nat-based

* Write a framework which can support multiple mode of Nat implementation.
* Add support in openflow plugin for conntrack nat actions.
* Add support in genius for conntrack nat actions.
* Add a config parameter to select between controller based and conntrack based.
* Add the flow programming for SNAT in netvirt.
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
