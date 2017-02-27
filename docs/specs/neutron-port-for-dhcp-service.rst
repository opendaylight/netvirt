========================================
Neutron Port Allocation For DHCP Service
========================================

This feature will enable the Neutron DHCP proxy service within controller
to reserve and use a Neutron port per subnet for communication with
Neutron endpoints.

Problem description
===================

The DHCP service currently assumes availability of the gateway router IP,
which may or may not be available to the controller. This can lead to service
unavailability.

Problem - 1: L2 Deployment with 3PP gateway
===========================================
There can be deployment scenario in which L2 network is created with no distributed
Router/VPN functionality. This deployment can have a separate g/w for the network
such as a 3PP LB VM, which acts as a TCP termination point and this LB VM is
configured with a default gateway IP. But the current DHCP proxy service in controller
hijacks gateway IP address for serving DHCP discover/request messages. If the LB is up,
this can continue to work, DHCP broadcasts will get hijacked by the CSC, and responses
sent as PKT_OUTs with SIP = GW IP

However, if the LB is down, and the VM ARPs for the same IP as part of a DHCP renew
workflow, the ARP resolution can fail, due to which renew request will not be
generated. This can cause the DHCP lease to lapse.

Problem - 2: Designated DHCP for SR-IOV VMs via HWVTEP
======================================================
In this Deployment scenario, L2 network is created with no distributed Router/VPN
functionality, and HWVTEP for SR-IOV VMs. DHCP flood requests from SR-IOV VMs
(DHCP discover, request during bootup), are flooded by the HWVTEP on the ELAN,
and punted to the controller by designated vswitch. DHCP offers are sent as unicast
responses from Controller, which are forwarded by the HWVTEP to the VM. DHCP renews
can be unicast requests, which the HWVTEP may forward to an external g/w entity as
unicast packets. Designated vswitch will never receive these pkts, and thus not be
able to punt them to the controller, so renews will fail.


High-Level Components:
======================

The following components of the Openstack - ODL solution need to be enhanced to provide
port allocation for DHCP service.

* Openstack ODL Mechanism Driver
* OpenDaylight Controller (NetVirt VpnService)
We will review enhancements that will be made to each of the above components in following
sections.

Proposed change
===============

The following components within OpenDaylight Controller needs to be enhanced:
* Neutron VPN module
* DHCP module
* ELAN and L3VPN modules

Opendaylight controller needs to preserve a Neutron port for every subnet it is providing
DHCP proxy services to in Openstack deployments. Neutron DHCP agent does a port allocation
in every subnet, with device owner set to ‘ODL:Netvirt’ and uses this port for all outgoing
messages. Since this port gets a distinct IP/MAC from the router g/w port IP/MAC,
both problem-1 and problem-2 will be solved.

Pipeline changes
----------------

<TODO>

ARP Changes for DHCP port
-------------------------
1. Client VM ARP requests for DHCP server IP need to be answered in L2 as well
as L3 deployment.
2. Create ARP responder table flow entry for DHCP server IP in computes nodes
on which ELAN footprint is available.
3. Currently ARP responder is part of L3VPN pipeline, however no L3 service
may be available in an L2 deployment to leverage the current ARP pipeline,
for DHCP IP ARP responses. To ensure ARP responses are sent in L2 deployment,
ARP processing needs to be migrated to the ELAN pipeline.
4. ELAN service to provide API to other services needing ARP responder entries
including L3VPN service (for router MAC, router-gw MAC and floating IPs,
and EVPN remote MAC entries).
5. ELAN service will be responsible for punting a copy of each ARP packet to the
controller if the source MAC address is not already learned.

Assumptions
-----------
Support for providing port allocation for DHCP service is available from
Openstack ODL Mechanism Driver

Reboot Scenarios
----------------
This feature support all the following Reboot Scenarios for EVPN:
    *  Entire Cluster Reboot
    *  Leader PL reboot
    *  Candidate PL reboot
    *  OVS Datapath reboots
    *  Multiple PL reboots
    *  Multiple Cluster reboots
    *  Multiple reboots of the same OVS Datapath.
    *  Openstack Controller reboots

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
Carbon.

Alternatives
------------
Alternatives considered and why they were not selected.

Usage
=====

Features to Install
-------------------
This feature doesn't add any new karaf feature.

REST API
--------

Implementation
==============

Assignee(s)
-----------

Primary assignee:
   Dayavanti Gopal Kamath <dayavanti.gopal.kamath@ericsson.com>

Other contributors:
  Periyasamy Palanisamy <periyasamy.palanisamy@ericsson.com>

Work Items
----------


Dependencies
============

Testing
=======
Capture details of testing that will need to be added.

Unit Tests
----------
Appropriate UTs will be added for the new code coming in once framework is in place.

Integration Tests
-----------------
There won't be any Integration tests provided for this feature.

CSIT
----
CSIT will be enhanced to cover this feature by providing new CSIT tests.

Documentation Impact
====================
This will require changes to User Guide and Developer Guide.

References
==========
