.. contents:: Table of Contents
      :depth: 5

============================
Enhance Robustness of L3VPN 
============================

https://git.opendaylight.org/gerrit/#/q/topic:l3vpn-robustness

Enhance current L3VPN to improve its robustness for specific
use-cases and also enable it to consistently work with
Neutron BGPVPN initiated orchestration lifecycles.

Problem description
===================

Being witness to issues faced in L3VPN with production
deployments of OpenDaylight, it was gradually becoming
clear that piecemeal fixing issues in L3VPN (and all its
related modules) unfortunately doesn't scale.

It was ascertained during fixing such issues (ie., providing
patches for production) that there was scope for improving
robustness in L3VPN.

There were specific cases that may (or) may not succeed most of the time in L3VPN.
* VM Migration
    - Specifically when VM being migrated is a parent of multiple IPAddresses (more than 4) where some are V4 and some V6.
* VM Evacuation
    - Mutliple VMs on a host, each evacuated VM being a parent of multiple IPAddresses, and the host made down.
* Graceful ECMP handling (i.e, ExtraRoute with multiple nexthops)
    - Modify an existing extra-route with additional nexthops.
    - Modify an existing extra-route by removing a nexthop from a list of configured nexthops.
* Rapid VIP movement (i.e., arp handling robustness)

The above cases have behavioural inconsistencies primarily due to lack IP-Address Serialization within L3VPN.

* Speed up Router-association and Router-disassociation to a VPN lifecycle

This case deals with vpn-interface (and so IP Addresses within them) movement across two VPNs, one being
router-vpn and other being the bgpvpn-with-that-router.  And this is extremely slow today and will be unable to
catch up with orchestration of any kind (be it via ODL RPCs) or via Openstack Neutron BGPVPN APIs.

In addition to the above 5 cases (which all are driven agnostic to using Neutron Bgpvpn API)
there are specific use-case scenarios which when driven via Openstack Neutron Bgpvpn API
will result in a failures to consistently realize in dataplane, the intended configuration
driven by the orchestrator in the Openstack BGPVPN control plane.

So the initiative of this specification is for addressing robustness in two major areas:
Area 1:
Improving intrinsic reliability and behavioural consistency within the L3VPN Engine by enforcing IP Serialization.
In addition to that provide a design that can handle faster (but consistent) movement of IPAddresses between
router-vpn and bgpvpn-with-that-router.

Area 2:
Providing necessary enhancements to make L3VPN Engine work more robustly when orchestrated via
Openstack BGPVPN API.

In scope
---------

Out of scope
------------

Use Case Summary
----------------
We have divided the use-cases across both the areas above and they are enlisted below.
And all the use-cases have to be considered with a (3-node Openstack Controller + 3-node ODL cluster).

Area 1:
+++++++
UC 1: VM Migration (cold migration)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VM Cold Migrations are made robust within L3VPN Engine.
If you notice this mimics a vpninterface moving to different designated location in the cloud.
Has the following sub-cases:
UC 1.1 - Migrate a single dualstack VM residing on a vpn
UC 1.2 - Migrate multiple dualstack VMs residing on different vpns

UC 2: Evacuation of VM (voluntary / involuntary shutdown of the compute host)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VM evacuation cycles are made robust within L3VPN Engine.
This mimics a vpninterface moving to different location in the cloud, but triggered via failing
a compute node.  Has the following sub-cases:
Has the following sub-cases:
UC 2.1 -  Evacuate a single dualstack VM from a single vpn from a compute host
UC 2.2 -  Evacuate multiple dualstack VMs across multiple vpns from the same compute host

UC 3: An Extra-route serviced by one or more VMs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure ECMP handling robustness within L3VPN Engine. 
This mimics an ipv4 address being reachable from multiple nexthops (or multiple vpninterfaces).
Has the following sub-cases:
UC 3.1 -  Append a nexthop to an existing IPv4 extra-route
UC 3.2 -  Remove a nexthop from an existing IPv4 extra-route with multiple nexthops
UC 3.3 - Clear away an IPv4 extra-route with multiple nexthops from a router, altogether
UC 3.4 - Delete the VM holding the nexthop of an extra-route
UC 3.5 - Delete a VM and add another new VM holding the same nexthop-ip of an existing extra-route

UC 4: VIP (or MIP) movement between two VMs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VIP/MIP handling robustness within L3VPN Engine.
This mimics an ipv4 address moving to different vpninterfaces.
Has the following sub-cases:
UC 4.1 - Move a MIP from one VM port to another VM port, wherein both the VMs are on the same subnet.
UC 4.2 - When a MIP is shared by two VM ports (active / standby), delete the VM holding the MIP.

UC 5: IP visibility across related VPNs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure that ip reachability across two related vpns is made robust within L3VPN Engine.
Has the following sub-cases:
UC 5.1 - Peering VPNs being configured and initiating migration of VMs on one of the peering VPNs
UC 5.2 - Delete peering VPNs simultaneously

In general the above tests sufficiently trigger IP Serialization enforcement and this will enable us
to remove the 2 seconds sleep() from within the ArpProcessingEngine (ArpNotificationHandler).

UC 6: Speed up Router-Association and Router-Disassociation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is about attempting to speed up the swap of Router into L3VPN and vice-versa.
The idea is to eliminate the 2 seconds sleep present within swap logic, thereby increasing
the rate of servicing vpn-interfaces in the router for the swap cases.

Area 2:
+++++++

CSITs is unable catch the failures in Area 1: 
a. CSIT does not have some of the above use-cases as tests
b. Existing CSIT tests that touch the periphery of above use-case
   are still a one-time run where the problem would not appear.

Area 2 is completely untriggered by current CSITs as current CSITs always use ODL provided
RPCs to manage BGPVPNs instead of using Neutron BGPVPN.

Proposed change
===============
Area 1:
+++++++
We brainstormed atleast 3 proposals (or ways) to enforce IP Serialization and ended up with
agreeing on Proposal 2.


Pipeline changes
----------------
This feature does not introduce any pipeline changes.

Yang changes
------------
Changes will be needed in ``l3vpn.yang`` , ``odl-l3vpn.yang`` , ``odl-fib.yang`` and
``neutronvpn.yang`` to support the robustness improvements.

Solution considerations
-----------------------

Configuration impact
--------------------

Clustering considerations
-------------------------
The feature should operate in ODL Clustered (3-node cluster) environment reliably.

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
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

REST API
--------

Implementation
==============

Assignee(s)
-----------

Primary assignee:

  Vivekanandan Narasimhan (n.vivekanandan@ericsson.com)

Work Items
----------
The Trello cards have already been raised for this feature
under the l3vpn-robustness.

#Here is the link for the Trello Card:
#https://trello.com/c/Tfpr3ezF/33-evpn-evpn-rt5

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
Since these are robustness changes new CSIT testcase additions are not expected.

Documentation Impact
====================

References
==========
