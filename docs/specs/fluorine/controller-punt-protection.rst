.. contents:: Table of Contents
   :depth: 3

=============================
Controller Punt Protection
=============================

https://git.opendaylight.org/gerrit/#/q/topic:controller-punt-protection

This feature aims to avoid potential overloading of the controller due to excessive
punting of packets done via different flow tables. It will use OVS learn actions to
rate limit packets punted to the controller similar to [1]_.

Problem description
===================

The current behavior of modules that punt the packet via their flow tables is to continue
punting the packets to the controller until controller processes the packet and installs
certain flows that prevent further punting of such packets.  During the time it takes the
controller to process the packet which might require data store accesses, same packets need not
be punted again since it puts unnecessary load on the controller as it does the same processing
which it did for the first packet.

Use Cases
---------
1. Subnet Route - Unknown IP packets belonging to particular subnet are punted
2. ARP - ARP requests/responses are punted to controller
3. SNAT - Controller based SNAT requires punting of packets for establishing new nat sessions

Proposed change
===============

Packets punting to the controller can be rate limited by installing a temporary flow with
the use of OVS learn actions. The first packet that gets punted will install this
temporary flow and subsequent packets matching this flow will not be punted.  The hard
timeout for the learnt flows will be configurable and the configuration changes will take
effect on ODL restart.

Packets that get punted to the controller can be classified in the following way depending on
the openflow actions associated with them.

1. punt the packet to controller and stop pipeline processing. This is generally done for
unicast packets eg punts done by subnet route and SNAT.

2. punt a copy of the packet to controller but continue the pipeline processing of the packet
through other flow tables. This is usually done for broadcast/multicast packets eg ARP punts.

For rate limiting punts of type (1), a temporary learnt flow with higher priority than the flow
that punts the packet to controller will be installed. Subsequent packets will hit the learnt
flow and punting will not occur till the learnt flow time outs. This approach will be used for
subnet route and SNAT punt use cases.

For punts of type (2), new tables need to be added which will have the temporary learnt flows
and the result of the match done by the learnt flows will be used by the next tables in the
pipeline to determine whether or not to punt this packet. This approach will be used for ARP
punts.
Two new tables will be introduced for ARP punts which will rate limit arp requests and responses.
GARP packets will not be rate limited. The reason for not rate limiting the GARP packets is because
they are used by VNFs to advertise themselves and are sent unsolicited.
The rate limiting of these can lead to ODL not knowing the presence of the VNF and in cases like
VRRP can lead to traffic drops.


Pipeline changes
----------------

1. Subnet route pipeline change

   Existing flow for subnet route punt will be modified to add learn action which will install
   a higher prirority flow to match on vpn id and destination IP.

   **Example flows after change:**

   .. code-block:: bash

       cookie=0x8000004, duration=2167.659s, table=22, n_packets=0, n_bytes=0, priority=42,ip,metadata=0x30d40/0xfffffe,nw_dst=10.1.1.255 actions=drop
       cookie=0x8000004, duration=1706.113s, table=22, n_packets=0, n_bytes=0, priority=42,ip,metadata=0x30d40/0xfffffe,nw_dst=20.1.1.255 actions=drop
       cookie=0x0, duration=4.651s, table=22, n_packets=4, n_bytes=392, hard_timeout=10, priority=10,ip,metadata=0x30d40/0xfffffe,nw_dst=10.1.1.6 actions=drop

       cookie=0x0, duration=4456.957s, table=22, n_packets=1, n_bytes=98, priority=0,ip actions=CONTROLLER:65535,
                   learn(table=22,hard_timeout=10,priority=10,eth_type=0x800,NXM_OF_IP_DST[],OXM_OF_METADATA[1..23])

2. ARP pipeline changes

   Two new tables(195, 196) will be introduced for ARP processing:

   **Table 43** for resubmitting to tables 195, 196 and 48 (similar to [0])

   **Table 195** for punting the packet as well as installing learnt flows in table 195
   and 196. Table 195 will have flow with higher priority than punt flow and will match
   on packet's actual ARP SPA and TPA and set a register value.

   **Table 196** will do a reverse match on packet's SPA and TPA. This match coupled with match
   from table 195 will be used to identify GARP packets.

   **Table 48** will be modified, with a new flow, which will use the match results from table
   195 and 196. A match in table 195 will indicate that this arp packet is already punted to
   controller, and only rest of the pipeline processing is required. A match both in table 195 and
   196 will identify garp packet and this will be processed as done currently.

   Group for arp request processing will be modified to only contain resubmit to table 81.

   **Example of flows after change:**

   .. code-block:: bash

       group_id=5000,type=all,bucket=actions=resubmit(,81)

       cookie=0x0, duration=780.134s, table=43, n_packets=0, n_bytes=0, priority=100,
                   arp,arp_op=2 actions=resubmit(,195),resubmit(,196),resubmit(,48)
       cookie=0x0, duration=79.665s, table=43, n_packets=20, n_bytes=840, priority=100,
                   arp,arp_op=1 actions=group:5000,resubmit(,195),resubmit(,196),resubmit(,48)
       cookie=0x8220000, duration=5274.013s, table=43, n_packets=8, n_bytes=576, priority=0 actions=goto_table:48

       cookie=0x0, duration=40.951s, table=195, n_packets=9, n_bytes=378, hard_timeout=120, priority=20,
                   arp,arp_spa=30.1.1.4,arp_tpa=30.1.1.4 actions=load:0x1->NXM_NX_REG4[0..7]
       cookie=0x0, duration=24.777s, table=195, n_packets=9, n_bytes=378, hard_timeout=120, priority=20,
                   arp,arp_spa=30.1.1.4,arp_tpa=30.1.1.10 actions=load:0x1->NXM_NX_REG4[0..7]
       cookie=0x0, duration=760.649s, table=195, n_packets=6, n_bytes=252, priority=10, arp
                   actions=learn(table=195,hard_timeout=120,priority=20,eth_type=0x806,NXM_OF_ARP_SPA[], NXM_OF_ARP_TPA[],load:0x1->NXM_NX_REG4[0..7]),
                   learn(table=196,hard_timeout=120,priority=10,eth_type=0x806,NXM_OF_ARP_SPA[]=NXM_OF_ARP_TPA[],NXM_OF_ARP_TPA[]=NXM_OF_ARP_SPA[],
                   load:0x1->NXM_NX_REG4[8..15]),CONTROLLER:65535

       cookie=0x0, duration=43.350s, table=196, n_packets=9, n_bytes=378, hard_timeout=120, priority=10,
                   arp,arp_spa=30.1.1.4,arp_tpa=30.1.1.4 actions=load:0x1->NXM_NX_REG4[8..15]
       cookie=0x0, duration=27.176s, table=196, n_packets=0, n_bytes=0, hard_timeout=120, priority=10,
                   arp,arp_spa=30.1.1.10,arp_tpa=30.1.1.4 actions=load:0x1->NXM_NX_REG4[8..15]

       cookie=0x0, duration=734.523s, table=48, n_packets=9, n_bytes=378, priority=200,
                   arp,reg4=0x101/0xffff actions=load:0->NXM_NX_REG4[],CONTROLLER:65535,resubmit(,49),resubmit(,50)
       cookie=0x0, duration=694.462s, table=48, n_packets=163, n_bytes=6846, priority=100,
                   arp,reg4=0x1/0xffff actions=load:0->NXM_NX_REG4[],resubmit(,49),resubmit(,50)
       cookie=0x8500000, duration=5284.499s, table=48, n_packets=14, n_bytes=828, priority=0 actions=resubmit(,49),resubmit(,50)


3. SNAT pipeline change

   Similar to subnet route punt, existing flow for controller based SNAT will be modified with
   learn action which will put a higher priority flow to match on packet's src ip, dst ip,
   protocol, layer 4 src port and layer 4 dst port along with vpn id.

   **Example flows after change:**

   .. code-block:: bash

       cookie=0x0, duration=95.890s, table=46, n_packets=0, n_bytes=0, priority=5,tcp,metadata=0x30d40/0xfffffe
                   actions=CONTROLLER:65535,learn(table=46,priority=7,eth_type=0x800,nw_proto=6,hard_timeout=5,
                   NXM_OF_IP_SRC[],NXM_OF_IP_DST[], NXM_OF_TCP_DST[],NXM_OF_TCP_SRC[],OXM_OF_METADATA[1..23]),
                   write_metadata:0x30d40/0xfffffe

       cookie=0x0, duration=17.385s, table=46, n_packets=0, n_bytes=0, priority=5,udp,metadata=0x30d40/0xfffffe
                   actions=CONTROLLER:65535,learn(table=46,priority=7,eth_type=0x800,nw_proto=17,hard_timeout=5,
                   NXM_OF_IP_SRC[],NXM_OF_IP_DST[],NXM_OF_UDP_DST[],NXM_OF_UDP_SRC[],OXM_OF_METADATA[1..23]),
                   write_metadata:0x30d40/0xfffffe


Yang changes
------------
To support the configuration of timeouts specific to each punt, following yang changes will be done

vpn-config yang changes
^^^^^^^^^^^^^^^^^^^^^^^

``vpnmanager-config:vpn-config`` container will be enhanced with two configuration variables to
reflect the hard timeout values in learnt flows for arp and subnet route punts.

.. code-block:: none
   :caption: vpnmanager-config.yang
   :emphasize-lines: 10-15

   container vpn-config {
        config true;
        leaf arp-cache-size {
            description "arp cache size";
            type uint64;
            default 10000;
        }
        ...

        leaf subnet-route-punt-timeout {
            description "hard timeout value for learnt flows for subnet route punts (unit - seconds).
                To turn off the learnt flows, it should be set to 0";
            type uint32;
            default 10;
        }
    }

elanmanger-config yang changes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
``elan-config:elan-config`` container will be modified wtih the configuration variable
for hard timeout values for ARP learnt flows

.. code-block:: none
   :caption: elanmanager-config.yang
   :emphasize-lines: 10-15

   container elan-config {
       config true;
       leaf auto-create-bridge {
           description "If true, auto-create default bridge";
           type boolean;
           default true;
       }
       ...

       leaf arp-punt-timeout {
            description "hard timeout value for learnt flows for arp punts (unit - seconds).
                To turn off the learnt flows, it should be set to 0";
            type uint32;
            default 5;
       }
   }

natservice-config yang changes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

``natservice-config:natservice-config`` container will be enhanced with configuration
variable to reflect the hard timeout values in learnt flows for SNAT punts.

.. code-block:: none
   :caption: natservice-config.yang
   :emphasize-lines: 10-15

   container natservice-config {
        config true;
        leaf nat-mode {
            type enumeration {
                enum "controller";
                enum "conntrack";
            }
            default "controller";
        }
        leaf snat-punt-timeout {
            description "hard timeout value for learnt flows for snat punts in seconds.
                To turn off the learnt flows, it should be set to 0,";
            type uint32;
            default 5;
        }
   }


Configuration impact
---------------------
Following configuration files will be modified to provide the default values to the
configuration parameters.

netvirt-vpnmanager-config.xml
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: xml
   :emphasize-lines: 4

   <vpnmanager-config xmlns="urn:opendaylight:netvirt:vpn:config">
      <arp-cache-size>10000</arp-cache-size>
      <arp-learn-timeout>2000</arp-learn-timeout>
      <subnet-route-punt-timeout>10</subnet-route-punt-timeout>
   </vpnmanager-config>


netvirt-elanmanger-config.xml
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: xml
   :emphasize-lines: 4

   <elanmanager-config xmlns="urn:opendaylight:netvirt:elan:config">
      ...
      <temp-smac-learn-timeout>10</temp-smac-learn-timeout>
      <arp-punt-timeout>5</arp-punt-timeout>
      ...
   </elanmanager-config>


netvirt-natservice-config.xml
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: xml
   :emphasize-lines: 3

   <natservice-config xmlns="urn:opendaylight:netvirt:natservice:config">
       <nat-mode>controller</nat-mode>
       <snat-punt-timeout>5</snat-punt-timeout>
   </natservice-config>

Clustering considerations
-------------------------
N.A.

Other Infra considerations
--------------------------
None.

Security considerations
-----------------------
None.

Scale and Performance Impact
----------------------------
This change should reduce the packet in load on the controller from subnet route, ARP and
SNAT punts. This will result in overall higher performance on the controller side.

Targeted Release
-----------------
Fluorine

Alternatives
------------
None.

Usage
=====
N/A.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
N/A.

CLI
---
N/A.

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Ravindra Nath Thakur (ravindra.nath.thakur@ericsson.com)

Other contributors:
  Vinayak Joshi (vinayak.joshi@ericsson.com)

Work Items
----------
N/A.

Dependencies
============
None

Testing
=======

Unit Tests
----------
Existing ARP/Subnet Route and SNAT functionality will be tested.

Integration Tests
-----------------
N/A.

CSIT
----
CSIT testcases will be added for all the punt scenarios covered in the spec which
will check learnt flows are getting created and packet counter for learnt flows.
Test cases will also be added to check whether learnt flows are getting deleted
after the configured hard timeout value.


Documentation Impact
====================
Pipeline documentation should be updated accordingly to reflect the changes to the
different services.

References
==========

.. [1] http://docs.opendaylight.org/en/stable-nitrogen/submodules/netvirt/docs/specs/temporary-smac-learning.html
