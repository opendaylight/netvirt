===============
Flow Based SNAT
===============

https://git.opendaylight.org/gerrit/#/q/topic:snat_conntrack

The ovs flow based SNAT implements Source Network Address Translation using openflow rules by
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
The SNAT enables the VM in a tenant network to the external world without using a floating ip. It
uses NAPT for sharing the external ip address across the vm.

Proposed change
===============

The proposed implementation uses linux netfilter framework to do the NAPT (Network Address Port
Translation) and for tracking the connection. The first packet of  a traffic will be committed to
the netfilter for translation along with the external ip for translation.  The subsequent packets
will use the entry in the netfilter for inbound and outbound translation. The router id will be
used as the zone id in the netfilter. Each zone tracks the connection in its own table. The rest
of the implementation for selecting the designated NAPT switch and non designated switches will
remain the same. The pipeline changes will happen in the designated switch.

The openflow plugin needs to support new set of action for flow based NAPT. This shall be added in
the nicira plugin extension of OpenFlow plugin.

Pipeline changes
----------------
The ovs based NAPT flows will replace the controller based NAPT flows. It reuses the existing
tables, the changes are limited to the designated switch for the router.

Outbound NAPT
-------------
Table 26  => submits the packet to netfilter to check whether it is an existing connection.
Resubmits the packet back to 46.

Table 46 => if it is an established connection which indicates the translation is done and the
packet is forwarded to table 47.
If it is a new connection the connection will be committed to netfilter and this entry will be
used for napt. The translated packet will be resubmitted to table 47.

Sample Flows

table=26,priority=5,ip,metadata=0x222e0/0xfffffffe action=ct'('table=46,zone=1,nat')'
table=46,priority=5,ct_state=+trk+est,metadata=0x222e0/0xfffffffe,ip,action=write_metadata:0x222e0/0xfffffffe,goto_table:47
table=46,priority=5,ip,ct_state=+trk+new,metadata=0x222e0/0xfffffffe,actions=write_metadata:0x222e0/0xfffffffe,ct'('commit,zone=1,nat'('src=192.168.56.9-192.168.56.9'))',ct'('table=47,zone=1,nat')’

Inbound NAPT
------------
Table 21 => submits the packet to netfilter to check for an existing connection. The packet
will be submitted back to table 44.
Table 44 =>writes the appropriate metadata to the translated packet and submits the submit the
packet to table 47.

Sample Flows

table=21,priority=5,ip,metadata=0x222e0/0xfffffffe,nw_dst=192.168.56.9,actions=ct'('table=44,zone=1,nat')'
table=44,priority=5,ip,metadata=0x222e0/0xfffffffe,nw_dst=10.100.5.3,actions=write_metadata:0x222e2/0xfffffffe,goto_table:47

Yang changes
------------
The nicira-action.yang and the openflowplugin-extension-nicira-action.yang needs to be updated
with nat action. The action structure shall be

container nx-action-nat {
            leaf flags {
                type uint16;
            }
            leaf ip-address-min {
                type inet:ip-address;
            }
            leaf ip-address-max {
                type inet:ip-address;
            }
        }

Configuration impact
--------------------
The proposed change requires the NAT service to provide a configuration knob to switch between the
controller based/flow based implementation. A new configuration file shall be added for this.

Clustering considerations
-------------------------
To be Updated

Other Infra considerations
--------------------------
The implementation requires ovs2.6 with the kernel module installed.

Security considerations
-----------------------
To be Updated

Scale and Performance Impact
----------------------------
The new SNAT implementation is expected to improve the performance when compared to the existing
one and will reduce the flows in ovs pipeline.

Targeted Release
----------------
Carbon

Alternatives
------------
The idea of decentralized SNAT was considered where a ip address will be assigned per compute for
an external router interface per tenant. But was put on hold considering the scarcity of external
IP address.

An alternative implementation of X NAPT switches was discussed, which will not be a part of this
document but will be considered as a further enhancement.

Usage
=====
To be Updated

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
To be Updated

CLI
---
To be Updated

Implementation
==============

Assignee(s)
-----------
To be Updated

Work Items
----------
To be Updated

Dependencies
============
To be Updated

Testing
=======
To be Updated

Unit Tests
----------

Integration Tests
-----------------

CSIT
----

Documentation Impact
====================
To be Updated

References
==========
[1] https://etherpad.openstack.org/p/decentralized-snat
