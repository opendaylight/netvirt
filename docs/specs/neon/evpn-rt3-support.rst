.. contents:: Table of Contents
      :depth: 5

=========================================================
Support for RT3 route type in EVPN
=========================================================

https://git.opendaylight.org/gerrit/#/q/topic:EVPN_RT3

Enable support for RT3 route type in evpn deployment.

Problem description
===================

OpenDaylight NetVirt service today supports EVPN over VXLAN tunnels.
L2DCI communication was made possible with this.

This spec attempts to enhance EVPN service in NetVirt by adding support for RT3 route type.

Type 3 routes are required for Broadcast, Unknown Unicast and Multicast (BUM) traffic delivery across EVPN networks.
Type 3 advertisements provide information that should be used to send BUM traffic.
Without Type 3 advertisements, ingress router would not know how to deliver BUM traffic to other PE devices that comprise given EVPN instance.

Today BUM traffic is sent to all the datacenter gateways from which RT2 is received.
Similarly datacenter gateway is excluded from BUM traffic when the last RT2 route is withdrawn from it.

Instead of relying on the RT2 routes to forward BUM traffic, RT3 route type is used to update the BUM traffic destination endpoints.


The format of Type 3 advertisement is as follows

               +---------------------------------------+
               |  Route Distinguisher (8 octets)       |
               +---------------------------------------+
               |  Ethernet Tag ID (4 octets)           |
               +---------------------------------------+
               |  IP Address Length (1 octet)          |
               +---------------------------------------+
               |  Originating Router's IP Address      |
               |          (4 or 16 octets)             |
               +---------------------------------------+

Type 3 route also carries a Provider Multicast Service Interface (PMSI) Tunnel attribute.

               +---------------------------------------+
               |  Flags (1 octet)                      |
               +---------------------------------------+
               |  Tunnel Type (1 octets)               |
               +---------------------------------------+
               |  MPLS Label (3 octets)                |
               +---------------------------------------+
               |  Tunnel Identifier (variable)         |
               +---------------------------------------+

      The following are the Tunnel Types:

      + 0 - No tunnel information present
      + 1 - RSVP-TE P2MP LSP
      + 2 - mLDP P2MP LSP
      + 3 - PIM-SSM Tree
      + 4 - PIM-SM Tree
      + 5 - BIDIR-PIM Tree
      + 6 - Ingress Replication
      + 7 - mLDP MP2MP LSP

In scope
--------

Only Ingress Replication tunnel type will be supported for now.
MPLS Label and Tunnel Identifier present in the received RT3 route will be ignored.

While advertizing MPLS Label and Tunnel Identifier will be set to a value of zero.
While sending BUM traffic l2vni of the vpn instance is used in the vxlan packet.

Supports only ipv4 for Originating Router's IP Address field. Ipv6 support will be added later.

Out of scope
------------

Tunnel types other than Ingress Replication will be ignored.

Use Cases
---------

All the use cases supported by RT2 feauture will be supported.
RT2 spec: http://docs.opendaylight.org/en/stable-nitrogen/submodules/netvirt/docs/specs/l2vpn-over-vxlan-with-evpn-rt2.html


Proposed change
===============

Pipeline changes
----------------
No Pipeline changes are required.

Yang changes
------------
There are no yang changes required.

The list ``external-teps`` is updated in elan container every time an RT3 route is received/withdrawn.

.. code-block:: none
   :caption: elan.yang

    container elan-instances {
        list elan-instance {
            key "elan-instance-name";
            leaf elan-instance-name {
                type string;
            }
            //omitted other existing fields
            list external-teps {
                key tep-ip;
                leaf tep-ip {
                    type inet:ip-address;
                }
            }
        }
    }

Solution considerations
-----------------------

Proposed change in BGP Quagga Stack
++++++++++++++++++++++++++++++++++++

The following thrift apis would be added to communicate to quagga.

.. code-block:: none
   :caption: elan.yang

    pushEvpnRT(EvpnConfigData evpnConfig)
    onupdatePushEvpnRT(EvpnConfigData evpnConfig)

    EvpnConfigData evpnConfig {
        1: required byte evpnConfigDataVersion = 1;
        2: required byte  routeType;
        3: required string rd;
        4: required long ethTag;
        5: required string esi;
        6: required byte tunnelType;
        7: required string tunnelId;
        8: required i32 label;
    }

Proposed change in OpenDaylight-specific features
+++++++++++++++++++++++++++++++++++++++++++++++++

The following components within OpenDaylight Controller needs to be enhanced:

* ELAN Manager
* BGP Manager

Upon receiving the RT3 route, the elan instance associated to the evpn instance is identified.
On that particular elan instance, external tep-ips field is added with the value of Originating Router's IP Address.
This external tep-ips list is used for constructing the elan broadcast group.

The following actions will be performed against each step in the orchestration.

1) create evpn1, evpn2 instances.

2) associate network1 with evpn1 instance and network2 with evpn2.

3) spawn network1_vm1 on compute1. At this step RT3 route is advertized with the tep ip of compute1 (using rd of evpn1).

4) spawn network1_vm2 on compute1. No RT3 route needs to be advertized for compute1 as it is already done in step1.

5) spawn network1_vm3 on compute2. At this step RT3 route is advertized with the tep ip of compute2.

6) spawn network1_vm4 on compute2. No RT3 route needs to be advertized for compute2.

7) spawn network2_vm1 on compute1. At this step RT3 route is advertized with the tep ip of compute1 (using rd of evpn2).

8) spawn network2_vm2 on compute1. No RT3 route needs to be advertized for compute1.

9) spawn network2_vm3 on compute2. At this step RT3 route is advertized with the tep ip of compute2.

10) spawn network2_vm4 on compute2. No RT3 route needs to be advertized for compute2.

11) delete network1_vm1 from compute1. No action taken as compute1 still has a vm from network1 (network1_vm2)

12) delete network1_vm2 from compute1. Send withdraw RT3 route using rd of evpn1 and compute1 tep.

13) delete network2_vm1 from compute1. No action taken as compute1 still has a vm from network1 (network2_vm2)

14) delete network2_vm2 from compute1. Send withdraw RT3 route using rd of evpn2 and compute1 tep.

15) detach network1 from evpn1. Send withdraw RT2 for network1 (vm1, vm2, vm3 , vm4) . Send withdraw RT3 for compute1 and compute2 (using rd of evpn1).

Reboot Scenarios
^^^^^^^^^^^^^^^^
This feature support all the following Reboot Scenarios for EVPN:

*  Entire Cluster Reboot
*  Leader PL reboot (PL : PayLoad Node : one of the cluster nodes where ODL is running in cluster)
*  Candidate PL reboot
*  OVS Datapath reboots
*  Multiple PL reboots
*  Multiple Cluster reboots
*  Multiple reboots of the same OVS Datapath.
*  Openstack Controller reboots
*  Vm migration and evacuation


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
Fluorine

Alternatives
------------
No Alternatives.

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
https://jira.opendaylight.org/browse/NETVIRT-1243

Dependencies
============
Requires a DC-GW that is supporting EVPN RT3 on BGP Control plane.

Testing
=======

Unit Tests
----------
Appropriate UTs will be added for the new code.

Integration Tests
-----------------
There won't be any Integration tests provided for this feature.

CSIT
----
CSIT will be enhanced to cover this feature by providing new CSIT tests.

1) advertize RT3 route from datacenter gateway , verify that remote broad cast group on all computes which host that network is updated to include the datacenter gateway nexthop.
2) withdraw RT3 route from datacenter gateway , verify that remote broad cast group on all computes which host that network is updated to exclude the datacenter gateway nexthop.
3) bring up first elan vm on a compute, verify that RT3 route is advertized.
4) bring up second elan vm on the same compute, verify that RT3 route is not advertized again.
5) bring down first elan vm on a compute, verify that RT3 route is not withdrawn.
6) bring down last elan vm on a compute, verify that RT3 route is withdrawn.
7) Disassociate network from evpn, verify that all computes broadcast groups are updated to exclude the datacenter gateway nexthops.


Documentation Impact
====================
This will require changes to User Guide and Developer Guide.

References
==========
[1] `BGP MPLS-Based Ethernet VPN <https://tools.ietf.org/html/rfc7432>`_
