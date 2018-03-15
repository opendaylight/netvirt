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
* Speed up Router-association and Router-disassociation to a VPN lifecycle
* Graceful ECMP handling (i.e, ExtraRoute with multiple nexthops)
    - Modify an existing extra-route with additional nexthops.
    - Modify an existing extra-route by removing a nexthop from a list of configured nexthops.
* Rapid VIP movement (i.e., arp handling robustness)

If you take a closer look, the first three bullets above require vpn-interface-based serialization across the cluster for
consistently driven control-plane + data-plane semantics.

Similarly if you look at next two bullets above (bullet 4 and 5), your will notice that those require IP-driven
serialization across the cluster for consistently driven control-plane + data-plane semantics.

However, today the entire VPN Engine unfortunately is interface-driven and not IP-driven.
As a result, bullets 4 and 5 would never be realized with constant consistentcy.

Bullet 3 case deals with vpn-interface (and so IP Addresses within them) movement across two VPNs, one being
router-vpn and other being the bgpvpn-with-that-router.  And this is extremely slow today and will be unable to
catch up with orchestration of any kind (be it via ODL RPCs) or via Openstack Neutron BGPVPN APIs in a scaled
cloud.

In addition to the above 5 use-cases (which all are driven agnostic to using Neutron Bgpvpn API)
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
UC 1.1: VM Migration (cold migration)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VM Cold Migrations are made robust within L3VPN Engine.
If you notice this mimics a vpninterface moving to different designated location in the cloud.
Has the following sub-cases:
UC 1.1 - Migrate a single dualstack VM residing on a vpn
UC 1.2 - Migrate multiple dualstack VMs residing on different vpns

UC 1.2: Evacuation of VM (voluntary / involuntary shutdown of the compute host)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VM evacuation cycles are made robust within L3VPN Engine.
This mimics a vpninterface moving to different location in the cloud, but triggered via failing
a compute node.  Has the following sub-cases:
Has the following sub-cases:
UC 2.1 -  Evacuate a single dualstack VM from a single vpn from a compute host
UC 2.2 -  Evacuate multiple dualstack VMs across multiple vpns from the same compute host

UC 1.3: An Extra-route serviced by one or more VMs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure ECMP handling robustness within L3VPN Engine. 
This mimics an ipv4 address being reachable from multiple nexthops (or multiple vpninterfaces).
Has the following sub-cases:
UC 3.1 -  Append a nexthop to an existing IPv4 extra-route
UC 3.2 -  Remove a nexthop from an existing IPv4 extra-route with multiple nexthops
UC 3.3 - Clear away an IPv4 extra-route with multiple nexthops from a router, altogether
UC 3.4 - Delete the VM holding the nexthop of an extra-route
UC 3.5 - Delete a VM and add another new VM holding the same nexthop-ip of an existing extra-route

UC 1.4: VIP (or MIP) movement between two VMs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VIP/MIP handling robustness within L3VPN Engine.
This mimics an ipv4 address moving to different vpninterfaces.
Has the following sub-cases:
UC 4.1 - Move a MIP from one VM port to another VM port, wherein both the VMs are on the same subnet.
UC 4.2 - When a MIP is shared by two VM ports (active / standby), delete the VM holding the MIP.

UC 1.5: IP visibility across related VPNs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure that ip reachability across two related vpns is made robust within L3VPN Engine.
Has the following sub-cases:
UC 5.1 - Peering VPNs being configured and initiating migration of VMs on one of the peering VPNs
UC 5.2 - Delete peering VPNs simultaneously

In general the above tests sufficiently trigger IP Serialization enforcement and this will enable us
to remove the 2 seconds sleep() from within the ArpProcessingEngine (ArpNotificationHandler).

UC 1.6: Speed up Router-Association and Router-Disassociation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is about attempting to speed up the swap of Router into L3VPN and vice-versa.
The idea is to eliminate the 2 seconds sleep present within swap logic, thereby increasing
the rate of servicing vpn-interfaces in the router for the swap cases.

Area 2:
+++++++

This deals with making BGPVPN workflows inside ODL more robust when used with Neutron
BGPVPN APIs. To add, when such Neutron BGPVPN APIs are applied through HOT templates, 
the VPNs have to be realized consistently in both the control-plane datastores and
data-plane flows/routes.

RIB - Routing Information Base - Represents the routes held inside the ODL controller Quagga BGP.
FIB - Forwarding Information Base - Represents the routes held inside ODL controller. 

For all the router-driven BGPVPNs below, please assume that two subnets (one IPv4 and one IPv6) are
associated to the router.
For all the network-driven BGPVPNs below, please assume that only a single IPv4 subnet is present on the
network.

UC 2.1: Two net-driven BGPVPNs cycled through with same RouteDistinguisher and same Network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (be it via HOT or otherwise)
Create a VPN1 with RD1
Associate Network1 into VPN1
Delete VPN1 with RD1
Create another VPN2 with RD1
Associate Network1 back into VPN2

After these steps, all the VM IPs (and their secondaries) must be realized correctly into VPN2 both in FIB and RIB.

UC 2.2: Two net-driven BGPVPNs cycled through with same RouteDistinguisher and same Network ending up with network disassociation 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Network1 into VPN1
Delete VPN1 with RD1
Create another VPN2 with RD1
Associate Network1 back into VPN2
Disassociate Network1 from VPN2

After these steps, none of the VM IPs (and their secondaries) must be hanging around in any of the two VPNs at all.

UC 2.3: On a given net-driven BGPVPN, order VPN deletion after all VMs removal 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Network1 into VPN1
Remove all VMs on Subnet1 in Network1
Delete VPN1
Delete Subnet1 from Network1
Delete Network1

After these steps, none of the VM IPs (and their secondaries) must not be hanging around in the VPN at all.

UC 2.4: On a given net-driven BGPVPN, order VPN deletion after network association
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Network1 into VPN1
Delete VPN1
Remove all VMs on Subnet1 in Network1
Delete Subnet1 from Network1
Delete Network1

UC 2.5: On a given net-driven BGPVPN, order VPN deletion after network deletion
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Network1 into VPN1
Remove all VMs on Subnet1 in Network1
Delete Subnet1 from Network1
Delete Network1
Delete VPN1

UC 2.6: Two router-driven BGPVPNs cycled through with same RouteDistinguisher and same Router 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Router1 into VPN1
Delete VPN with RD1
Create another VPN2 with RD1
Associate Router1 back into VPN2

UC 2.7: Two router-driven BGPVPNs cycled through with same RouteDistinguisher and same Router ending up with router disassociation 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Router1 into VPN1
Delete VPN with RD1
Create another VPN2 with RD1
Associate Router1 back into VPN2
Disassociate Router1 from VPN2

UC 2.8: On a given router-driven BGPVPN, order VPN deletion before router deletion 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Router1 into VPN1
Delete VPN1
Delete Router Interfaces on Router1
Delete Router1

UC 2.9: On a given router-driven BGPVPN, order VPN deletion after router interface removal 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Router1 into VPN1
Delete Router Interfaces on Router1
Delete VPN1
Delete Router1

UC 2.10: On a given router-driven BGPVPN, order VPN deletion after router deletion 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Router1 into VPN1
Delete Router Interfaces on Router1
Delete Router1
Delete VPN1

UC 2.11: On a given router-driven BGPVPN, order vpn deletion in quick succession of disassociation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Router1 into VPN1
Disassociate Router1 from VPN1
Delete VPN1

UC 2.12: On a given router-driven BGPVPN, order vpn associations and disassociations in quick succession
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create a VPN1 with RD1
Associate Router1 into VPN1
Disassociate Router1 from VPN1
Associate Router1 into VPN1
Disassociate Router1 from VPN1

CSITs is unable catch the failures in Area 1: 
a. CSIT does not have some of the above use-cases as tests
b. Existing CSIT tests that touch the periphery of above use-case
   are still a one-time run where the problem would not appear.

Area 2 is completely untriggered by current CSITs as current CSITs always use ODL provided
RPCs to manage BGPVPNs instead of using Neutron BGPVPN API.

Proposed change
===============
Area 1:
+++++++
We brainstormed many proposals (or ways) to enforce IP Serialization (within the scope of router / vpn)
and ended up with agreeing with the following proposal.

The proposal chosen was about enforcing IP Serialization by processing all
the VPNs (and then all VPNInterfaces and IPAddresses within them)
in the ODL system through a single node.

Please note that the L3VPN Service consists of the following engines:
* VPNEngine (everything under VpnManager-Api and VpnManager-Impl)
* FIBEngine (everything under FibManager-Api and FibManager-Impl)
* VPNTunnelEngine (TunnelInterfacexxxListener and TEPListener)
* SubnetRouteEngine (VpnSubnetRouteHandler)
* ARPLearningEngine (ArpNotificationHandler and AlivenessMonitor)
All the above engines will be effected/affected as part of implementing Area 1.

Details about the proposal is given below to facilitate implementors.

1. The service name used will be 'L3VPN' and for that service name the VPN-Feature will
   choose a leader node from the 3 nodes in the ODL cluster.  If there is only one node,
   then that node will be considered the leader.

2. How and which node is chosen as a leader will be decoupled from the VPN Engine.
   All the engines within the L3VPN Service will only be consumers of interface exposed by
   a new entity 'VpnLeadershipTracker' ,and this new entity will be responsible to tag
   the leader node by using 'L3VPN' service name as the key.
   While initially we will use cluster-singleton to choose a node as the leader for 'L3VPN'
   service, the tracker will also be expanded as a later review to choose that node which is
   holding the Default Operational Shard as the leader, as #ofreads and #ofwrites to the
   Default Operational Shard is higher by all the engines within the L3VPN Service.

3. A new `VPNEvent` POJO will be made available to store immutable information pertaining to an event of interest
   to the L3VPN service.  This pojo is not a datastore entity and will be constructed dynamically only on the node
   which is the current leader of service name 'L3VPN'.  Also the VPNEvent POJO will contain all the information
   required to process an event by all the engines within the L3VPN service.

4. All along in today's L3VPN Service, the FIBEngine is responsible for processing a VRFEntry creation/deletion/updation.
   Fundamentally, converting an action on a VRFEntry to flow additon/ group addition/ flow removal / group removal is
   driven by FIBEngine (aka VrfEntryListener).
   Going foward, while the FIBEngine will continue to hold this responsibility, the implementation will change the
   lifecycle for VRFEntry handling being triggered synchronously by the VPNEngine instead of asynchronously driven by the FIBEngine.
   This will provide the benefit of passing all the required information synchronously to the methods in the FIBEngine for
   VRFEntry handling by the VPNEngine.  The VRFEntry would become an artifact instead of a trigger source.
   This type of design also enables use of DJC to enforce IP Serialization within a given VPNInstance.
   This change additionally provides elimination of backpulls from the FIBEngine towards VPNEngine and other
   modules for non-BGP-imported-rows   

5. Other than imported BGP routes, all other types of route processing (VM ports, exported VM ports, extra-routes, MIPs), will
   be done in the way quoted in point 4 above.  The BGP imported routes from other neighbours will continue to be
   driven from within the VRFEntryListener.

6. Re-use as much existing handlers within the engines of L3VPN Service in order to contain the robustness effort
   
7. All the VPN Datastores will continued to be made available the same way for other external interfaces and consumers of
   external interfaces to remain unaffected.  Most specifically implementing this area will not effect NATEngine,
   InterVPNLinksEngine and BGPEngine.

8. All BGP Advertisements and Withdrawals (for all routes managed by ODL itself - i.e., non-BGP-imported routes), will be
   done within the VRFEntryListener rather than by the VPN Engine.

9. The VPNInstance creation/deletion/updation handling will be driven on whichever node is the leader
   for 'L3VPN' service as per VpnLeadershipTracker.  The handler for VPNInstance will continue to
   use JobCoordinator keyed on 'VPN-<vpn-name>' to process creation/updation/deletion.

10. The VpnInterface creation/deletion/updation handling will also be driven on whichever node is the leader
    for 'L3VPN' service as per VpnLeadershipTracker.  The following jobKeys for JobCoordinator will be applied
    for VpnInterfaceEvents:
    a. The jobKey of 'vpnid-dpnid' will be used for populateFibOnNewDpn and cleanupFibOnNewDpn (and its related methods)
       Here vpnid is dataplane representation of the VPN (and not the control plane vpnuuid).

    b. b1. The jobKey of 'VPN-<vpn-interface-name>' will continued to be used to serialize all events for a specific vpn-interface.
       b2. Within the 'VPN-<vpn-interface-name>' run, a nested job will be fired with key of 'VPN-rd-prefix' to serialize handler
           run for all IPAddresses within a given vpn (identified by rd). The 'prefix' here can be primary-ip, extra-route-prefix,
           or a discovered mip-prefix.

There are `Thread.sleep` in mulitiple places inside ODL Netvirt projects.  This spec attempts to eliminate the sleep invocations in
the following files:
VpnInterfaceManager  - sleep introduced to allow batch movement of vpn-interfaces from router-to-bgpvpn and vice-versa
ArpNotificationHandler (arpcachetimeout) - The timeout here was actually added to facilitate vpn-lifecycle for a MIP.
NexthopManager - ClusterLock with sleep used to safe management of l3nexthop (createLocalNextHop).

There are still sleeps in other services like Elan, L2Gateway etc and those sleep removals need to be pursued by respective
components.

Area 2:
+++++++

In this area, we will be enhancing the following engines to enable safe parallel processing of vpn-instances
regardless of whether such vpninstances use the same route-distinguisher or otherwise.

Detailed changes for about the changes here is given below to facilitate implementors.

1. VPNInstanceOpData will be re-keyed with vpnInstanceName being the new key instead of RD being the key.

2. An additional abstraction of VPNInstanceName (along with vpnId) will be added on top of VrfTables.
   More specifically, a VrfEntry will be keyed by (VPNInstance + RD + Prefix) instead of being keyed only with
   (RD + prefix) as it stands today.

3. One more new model that maps a given RD to an Active VpnInstance (when parallel processing happens on two vpnInstances
   sharing the same RD) is required.  This modeling will be used by BgpManager, NatService and IntervpnLinkService.

4. All BGP Advertisements and Withdrawals (for all routes managed by ODL itself - i.e., non-BGP-imported routes), will be
   done within the VRFEntryListener rather than by the VPN Engine.  All such advertisements will be serialized by
   (RD + IP Prefix) as the key.

Pipeline changes
----------------
Area 1:
+++++++
This area realization does not introduce any pipeline changes.

Area 2:
+++++++
This area realization does not introduce any pipeline changes.

Yang changes
------------
Area 1:
+++++++
Changes will be needed in ``l3vpn.yang`` , ``odl-l3vpn.yang`` , ``odl-fib.yang`` and
``neutronvpn.yang`` to support the robustness improvements.

Area 2:
+++++++
Changes will be needed in ``odl-l3vpn.yang`` and ``odl-fib.yang``
to support the robustness for Neutron BGPVPN API.

ODL-L3VPN YANG CHANGES
^^^^^^^^^^^^^^^^^^^^
.. code-block:: none
   :caption: odl-l3vpn.yang


    --- a/vpnmanager/api/src/main/yang/odl-l3vpn.yang
    +++ b/vpnmanager/api/src/main/yang/odl-l3vpn.yang
    @@ -261,7 +261,7 @@ module odl-l3vpn {
     container vpn-instance-op-data {
         config false;
         list vpn-instance-op-data-entry {
     -           key vrf-id;
     +           key vpn-instance-name;
            leaf vpn-id { type uint32;}
            leaf vrf-id {
               description

ODL-FIB YANG CHANGES
^^^^^^^^^^^^^^^^^^^^
.. code-block:: none
   :caption: odl-fib.yang


    --- a/fibmanager/api/src/main/yang/odl-fib.yang
    +++ b/fibmanager/api/src/main/yang/odl-fib.yang
    @@ -100,15 +100,19 @@ module odl-fib {

     container fibEntries {
         config true;
    -        list vrfTables{
    -            key "routeDistinguisher";
    -            leaf routeDistinguisher {type string;}
    -            uses vrfEntries;
    -            uses macVrfEntries;
    -        }
    +        list vpnNames {
    +            key vpnName;
    +            leaf vpnName { type string; }
    +            list vrfTables{
    +                key "routeDistinguisher";
    +                leaf routeDistinguisher {type string;}
    +                uses vrfEntries;
    +                uses macVrfEntries;
    +            }

    -        container ipv4Table{
    -            uses ipv4Entries;
    +            container ipv4Table{
    +                uses ipv4Entries;
    +            }
         }
     }

ODL-L3VPN YANG CHANGES
^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: none
   :caption: odl-l3vpn.yang


   --- a/vpnmanager/api/src/main/yang/odl-l3vpn.yang
   +++ b/vpnmanager/api/src/main/yang/odl-l3vpn.yang
   @@ -184,10 +184,13 @@ module odl-l3vpn {
                }
         }

    -    container evpn-rd-to-networks {
    +    container evpn-to-networks {
             description "Holds the networks to which given evpn is attached";
    -        list evpn-rd-to-network {
    +        list evpn-to-network {
    -           key rd;
    +           key vpn-name;
    +           leaf vpn-name {
    +             type string;
    +           }
                leaf rd {
                  type string;
                }
    @@ -261,7 +264,7 @@ module odl-l3vpn {
         container vpn-instance-op-data {
             config false;
             list vpn-instance-op-data-entry {
    -           key vrf-id;
    +           key vpn-instance-name;
                leaf vpn-id { type uint32;}
                leaf vrf-id {
                  description


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
Fluorine.

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
