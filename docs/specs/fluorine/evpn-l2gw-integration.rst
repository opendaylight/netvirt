.. contents:: Table of Contents
      :depth: 5

=========================================================
Evpn and l2gw integration
=========================================================

https://git.opendaylight.org/gerrit/#/q/topic:EVPN_L2GW

Enable support of l2gw integration with evpn.

Problem description
===================

OpenDaylight NetVirt service today supports EVPN over VXLAN tunnels.
L2DCI communication was made possible with this.

It also supports l2gw use cases.

This spec attempts to integrate these two services.

In scope
--------
Only L2 support will be added now.

Out of scope
------------

L3 communication is not supported.

Use Cases
---------

All the use cases supported by RT2 feature will be supported.

In addition the following use cases will be supported.

1) Intra subnet communication between sriov vms across datacenters.
-------------------------------------------------------------------

   To achieve this remote mcast table should have a tep pointing towards the datacenter gateway,
   Remote ucast table should have the other datacenter sriov mac reachable via the datacenter gateway nexthop.

   Assume the following datapath and control path connectivity respectively.

   VM1(MAC1) <-> TOR1 <-> DC-GW1(DATACENTER-GATEWAY) <-> DC-GW2 <-> TOR2 <-> VM2(MAC2)
   
   TOR1 <-> ODL1 <-> DC-GW1 <-> DC-GW2 <-> ODL2 <-> TOR2

   The following table programing is performed to achieve the datapath connectivity.

   Remote Mcast Table of TOR1
    unknown-mac Logicalswitch1 [DC-GW1-TEP, OTHER-COMPUTES-TEPS-ON-SAME-NETWORK]

   Remote Ucast Table of TOR1
    MAC2 Logicalswitch1 DC-GW1-TEP

   Remote Mcast Table of TOR2
    unknown-mac Logicalswitch1 [DC-GW2-TEP, OTHER-COMPUTES-TEPS-ON-SAME-NETWORK]

   Remote Mcast Table of TOR1
    MAC1 Logicalswitch1 [DC-GW2-TEP]

   **ODL1 will advertize RT2 route to DC-GW1 for MAC1 with TOR1 TEP  as the the next hop**

   **ODL2 will advertize RT2 route to DC-GW2 for MAC2 with TOR2 TEP  as the the next hop**

   The following is the sequence of steps an ARP packet will take.

   - Initial ARP packet from VM1 will be looked up in Remote mcast table of TOR1 and will be flooded to DC-GW1
   - DC-GW1 will flood it to DC-GW2
   - DC-GW2 will flood it to TOR2
   - TOR2 will flood it to VM2
   - VM2 will respond with unicast ARP response.
   - Unicast ARP response will be looked up in Remote ucast table of TOR2 and will be forwarded to DC-GW2 (vxlan encapsulated with l2vni)
   - DC-GW2 will look up in mac-vrf table forward it to DC-GW1
   - DC-GW1 will look up in mac-vrf table and forward it to TOR1
   - TOR1 will look up the mac in Local Ucast Table and send it to VM1

   The following is the sequence of steps a Unicast packet will take.
   
   - Unicast IP packets will be simply looked up in Remote Ucast table of the TOR1 and forwarded to DC-GW1 (vxlan encapsulated with l2vni)
   - DC-GW1 will lookup in mac-vrf table and forward it to DC-GW2
   - DC-GW2 will look up in mac-vrf table and forward it to TOR2
   - TOR2 will look up the mac in Local Ucast Table and forward it to VM2

2) Intra subnet communication between sriov vm and compute vm across datacenters.
---------------------------------------------------------------------------------

   The procedure is very much similar to the above use case except that the packet will be forwarded based on the openflow pipeline on the destination compute vm
   as defined in the RT2 spec https://git.opendaylight.org/gerrit/#/c/51693/.

   Assume the following datapath and control path connectivity respectively.

   VM1(MAC1) <-> TOR1 <-> DC-GW1(DATACENTER-GATEWAY) <-> DC-GW2 <-> COMPUTE2 <-> VM2(MAC2)
   
   TOR1 <-> ODL1 <-> DC-GW1 <-> DC-GW2 <-> ODL2 <-> COMPUTE2

   The following table programing is performed to achieve the datapath connectivity.

   Remote Mcast Table of TOR1
    unknown-mac Logicalswitch1 [DC-GW1-TEP, OTHER-COMPUTES-TEPS-ON-SAME-NETWORK]

   Remote Ucast Table of TOR1
    MAC2 Logicalswitch1 DC-GW1-TEP

   Remote Broadcast Group buckets of Logicalswitch1(Network1) on Compute2
    [DC-GW2-TEP, OTHER-COMPUTES-TEPS-ON-SAME-NETWORK]

   DMac Table (51) of Compute2
    MAC1 Logicalswitch1(Network1) [DC-GW2-TEP]

   **ODL1 will advertize RT2 route to DC-GW1 for MAC1 with TOR1 TEP  as the the next hop**

   **ODL2 will advertize RT2 route to DC-GW2 for MAC2 with COMPUTE2 TEP  as the the next hop**

   The following is the sequence of steps an ARP packet will take.

   - Initial ARP packet from VM1 will be looked up in Remote mcast table of TOR1 and will be flooded to DC-GW1
   - DC-GW1 will flood it to DC-GW2
   - DC-GW2 will flood it to COMPUTE2
   - COMPUTE2 will flood it to VM2
   - VM2 will respond with unicast ARP response.
   - Unicast ARP response will be looked up in Dmac Table (51) of COMPUTE2 and will be forwarded to DC-GW2 (vxlan encapsulated with l2vni)
   - DC-GW2 will lookup in mac-vrf table and forward it to DC-GW1
   - DC-GW1 will lookup in mac-vrf table and forward it to TOR1
   - TOR1 will look up the mac in Local Ucast Table and send it to VM1

   The following is the sequence of steps a Unicast packet will take.

   - Unicast IP packets will be simply looked up in Remote Ucast table of the TOR1 and forwarded to DC-GW1 (vxlan encapsulated with l2vni)
   - DC-GW1 will lookup in mac-vrf table and forward it to DC-GW2
   - DC-GW2 will look up in mac-vrf table and forward it to COMPUTE2
   - COMPUTE2 will look up in Dmac Table and forward it to VM2


3) Intra subnet communication between baremetals behind l2gw across datacenters.
---------------------------------------------------------------------------------

   This is similar to case1.

4) Intra subnet communication between baremetal behind l2gw  and compute vm across datacenters.
------------------------------------------------------------------------------------------------

   This is similar to case2.


Proposed change
===============

Pipeline changes
----------------
No Pipeline changes are required.

Yang changes
------------
There are no yang changes required.


Solution considerations
-----------------------

Proposed change in OpenDaylight-specific features
+++++++++++++++++++++++++++++++++++++++++++++++++

The following components within OpenDaylight Controller needs to be enhanced:

* ELAN Manager

The following actions are performed If the network is attached to evpn

Upon L2gw connection creation.
    Program remote mcast table of l2gw device with all the external teps of the elan instance.

    Program remote ucast table of l2gw device with all the external macs from mac vrf table of the elan instance.

    Advertize vtep of the l2gw device in RT3 route

Upon L2gw connection deletion.
    Delete remote mcast table entry for this elan instance of l2gw device.

    Clear all the macs in mac vrf table of the elan instance from remote ucast table of l2gw device.

    Withdraw RT3 route containing vtep of the l2gw device

Upon Local ucast add
    advertize the RT2 route for the added ucast mac

Upon Local ucast delete
    withdraw the RT2 route for the added ucast mac

Upon Receiving RT2 route
    Program remote ucast mac table of l2gw device with the mac received from RT2 route

Upon Receiving RT2 route withdraw
    Delete mac from remote ucast mac table of l2gw device

Upon Receiving RT3 route
    Program remote mcast mac table of l2gw device to include the tep

Upon Receiving RT3 route withdraw
    Program remote mcast mac table of l2gw device to exclude the tep

Upon attaching network to evpn
    Advertize local ucast macs of l2gw device as RT2 routes

    Advertize vtep of the l2gw device in RT3 route

Upon detaching network from evpn
    Withdraw RT2 routes containing local ucast macs of l2gw device

    Withdraw vtep of the l2gw device in RT3 route

Reboot Scenarios
^^^^^^^^^^^^^^^^
This feature support all the following Reboot Scenarios for EVPN:

*  Entire Cluster Reboot
*  Leader PL reboot (PL : payload node: one of the nodes in the cluster)
*  Candidate PL reboot
*  OVS Datapath reboots
*  Multiple PL reboots
*  Multiple Cluster reboots
*  Multiple reboots of the same OVS Datapath.
*  Openstack Controller reboots


Configuration impact
--------------------
N.A.

Clustering considerations
-------------------------
The feature should operate in ODL Clustered environment reliably.

Other Infra considerations
--------------------------
N.A.

Security considerations
-----------------------
N.A.

Scale and Performance Impact
----------------------------
Not covered by this Design Document.

Targeted Release
----------------
Fluorine.

Alternatives
------------
N.A.

Usage
=====

Features to Install
-------------------
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  K.V Suneelu Verma <k.v.suneelu.verma@ericsson.com>

  Vyshakh Krishnan C H <vyshakh.krishnan.c.h@ericsson.com>


Work Items
----------
https://jira.opendaylight.org/browse/NETVIRT-1247

Dependencies
============
Requires a DC-GW that is supporting EVPN RT2 & RT3 on BGP Control plane.

Testing
=======
Capture details of testing that will need to be added.

Unit Tests
----------
Appropriate UTs will be added for the new code.

Integration Tests
-----------------
There won't be any Integration tests provided for this feature.

CSIT
----
CSIT will be enhanced to cover this feature by providing new CSIT tests.

1) create l2gw connection, verify that received routes from datacenter gateway are programmed in the l2gw device
2) create l2gw connection, verify that the l2gw device tep is advertized in RT3 route
3) delete l2gw connection, verify that received dcgw routes are deleted from the l2gw device
4) delete l2gw connection, verify that the RT3 route withdraw message is published
5) attach network to evpn, verify that l2gw local ucast macs are advertized as RT2 routes
6) detach network from evpn, verify that l2gw local ucast macs are withdrwan
7) receive RT2 route, verify that the received mac is programmed in l2gw device
8) upon receiving withdraw of RT2 route, verify that the received mac is deleted from l2gw device
9) receive RT3 route, verify that l2gw device mcast includes the dcgw nexthop ip
10) upon receiving withdraw of RT3 route, verify that l2gw device mcast excludes the dcgw nexthop ip


Documentation Impact
====================
This will require changes to User Guide and Developer Guide.

References
==========
[1] `BGP MPLS-Based Ethernet VPN <https://tools.ietf.org/html/rfc7432>`_
