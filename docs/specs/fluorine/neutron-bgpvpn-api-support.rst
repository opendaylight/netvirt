.. contents:: Table of Contents
      :depth: 5

========================================================
Support Neutron BGPVPN API on OpenDaylight L3VPN service
========================================================

https://git.opendaylight.org/gerrit/#/q/topic:neutron-bgpvpn-api-support

Enhance current L3VPN service to realize VPNs consistently when
VPNs are managed through the Openstack Neutron BGPVPN Plugin.

Problem description
===================

As the current L3VPN service stands end-of-Oxygen release,
it is able to realize a VPN orchestrated by Neutron BGPVPN API.

However, this orchestration is subjected heavily to timing 
and nature of how the orchestration was performed.

To explain, piecemeal creating a Neutron BGPVPN using 
Openstack BGPVPN REST API (or) CLI and making
sure it is realized and then deleting the same Neutron BGPVPN
(via RESTAPI or CLI) would succeed.

However, when the orchestration pattern on Openstack BGPVPN 
API is turned to be driven through an automation script  (or)
if the script uses Heat templates to automate and manage BGPVPNs,
the runs will expose failure to properly realize such orchestrated
VPNs in the OpenDaylight control plane (and thence data plane too).

The failures themselves will go unnoticed to the Openstack 
Orchestrator (or user) since the Openstack Neutron will
provide a consistent view of available (or active) VPNs
while the VPNs being realized in OpenDaylight would be
either less than the active VPNs (or) might sometimes
even be more (due to stale VPNs not cleaned up).

In scope
---------
The following types of VPNs are scoped into this effort:
a. Network-associated L3 BGPVPNs
   Networks can have either IPv4 (or) IPv6 (or) both IPv4 and IPv6 subnets in them.
b. Router-associated L3 BGPVPNs
   Router can have either IPv4 (or) IPv6 (or) both IPv4 and IPv6 subnets attached to them.
c. Plain Neutron Router (Internal VPN)
   Router can have either IPv4 (or) IPv6 (or) both IPv4 and IPv6 subnets attached to them.
d. Plain External Network (Internal VPN)
   External network used as a VPN to provide external connectivity over FLAT / VLAN mechanisms.
e. ExternalNetwork-associated L3 BGPVPNs
   L3BGPVPN representing connectivity to external networks via MPLSOverGRE tunnels.

Out of scope
------------
a. Network-associated EVPNs 
b. Router-associated EVPNs

Use Case Summary
----------------

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

UC 1:  A net-driven BGPVPN creation with Network association 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (single HOT template)
template 1 - Create a VPN1 with RD1
             Create Network1, Create Subnet1, Boot VMs on the Network1, Associate Network1 into VPN1

Create stack with template 1, make sure that VPN1 appears in the ODL controller.
Also make sure all of the VM IPs (and their secondaries) must be realized into VPN1.
Delete stack with template 1, make sure VPN1 disappears from the ODL controller.

UC 2:  A net-driven BGPVPN cycled through multiple Networks association 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create Subnet1, Boot VMs on the Network1, Associate Network1 into VPN1
template 3 - Create Network2, Create Subnet2, Boot VMs on the Network2, Associate Network2 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.

Create stack with template 2 and template 3 concurrently.
Make sure all of the VM IPs (and their secondaries) on Network1 and Network2 (and their secondaries)
must be realized into VPN1.

Delete stack with template 2, template 3 concurrently.
Make sure all of the VM IPs (and their secondaries) on Network1 and Network2 must be removed from VPN1.

Delete stack with template 1, VPN1 must disappear from the ODL controller.

UC 2.3:  A net-driven BGPVPN cycled through concurrent Networks
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
association and disassociation (no overlapping cidrs on networks)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create Subnet1, Boot VMs on the Network1, Associate Network1 into VPN1
template 3 - Create Network2, Create Subnet2, Boot VMs on the Network2, Associate Network2 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, all of the VM IPs (and their secondaries) on Network1
must be realized into VPN1.

Delete stack with template 2 and concurrently create stack with template 3.
All of the VM IPs (and their secondaries) on Network1 must be removed from VPN1.
All of the VM IPs (and their secondaries) on Network2 must be realized into VPN1.

Delete stack with template 3, make sure that Network2 must be removed from VPN1.
Delete stack with template 1, VPN1 must disappear from the ODL controller.

UC 2.4:  A net-driven BGPVPN cycled through concurrent Networks
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
association and disassociation (overlapping cidrs on networks)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create Subnet1, Boot VMs on the Network1, Associate Network1 into VPN1
template 3 - Create Network2, Create Subnet2 (same CIDR as Subnet1), Boot VMs on the Network2, Associate Network2 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, all of the VM IPs (and their secondaries) on Network1
must be realized into VPN1.

Delete stack with template 2 
All of the VM IPs (and their secondaries) on Network1 must be removed from VPN1.

Create stack with template 3
All of the VM IPs (and their secondaries) on Network2 must be realized into VPN1.

Delete stack with template 3, make sure that Network2 must be removed from VPN1.
Delete stack with template 1, VPN1 must disappear from the ODL controller.

UC 2.5: Two net-driven BGPVPNs cycled through with same RouteDistinguisher and same Network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
template 1 - Create Network1, Create Subnet1, Boot VMs on the Network1
template 2 - Create a VPN1 with RD1 and associate Network1 to VPN1 
template 3 - Create a VPN2 with RD1 and associate Network1 to VPN2

Create stack with template 1, make sure all the VMs appears in the ODL controller.
Create stack with template 2, all of the VM IPs (and their secondaries) on Network1
must be realized into VPN1.

Delete stack with template 2 and concurrently create stack with template 3.
All of the VM IPs (and their secondaries) on Network1 must be removed from VPN1.
All of the VM IPs (and their secondaries) on Network1 must be realized into VPN2.

Delete stack with template 3, make sure that Network1 must be removed from VPN2.
Delete stack with template 1, make sure all the VMs disappear from the ODL controller.

UC 2.6: A net-driven BGPVPN ordering VPN deletion before network deletion 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create Subnet1, Boot VMs on the Network1, Associate Network1 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, all of the VM IPs (and their secondaries) on Network1
must be realized into VPN1.

Delete stack with template 1
All of the VM IPs (and their secondaries) on Network1 must be removed from VPN1.
Make sure ELAN traffic continues to work.

Delete stack with template 2 
Make sure all the VMs, Networks disappear from the ODL controller.

UC 2.7: A net-driven BGPVPN ordering network deletion before VPN deletion without disassociating the network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create IPv4 Subnet1, Boot VMs on the Network1
template 3 - Associate Network1 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, ensure all of the VM IPs (and their secondaries) on Router1 
must be realized into VPN1.
Create stack with template 3, and ensure all of the VM IPs (and their secondaries)
on Router1 must be realized into VPN1.

Delete stack with template 2, make sure that Network1 must be removed from VPN1.
Also make sure all of the VMs (and their secondaries) along with Network1 are not hanging around at all.

Delete stack with template 1, VPN1 must disappear from the ODL controller.
Delete stack with template 3,  the stack deletion must fail and this is normal.

UC 2.8:  A router-driven BGPVPN creation with SingleStack Router association 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (single HOT template)
template 1 - Create a VPN1 with RD1
             Create Network1, Create Subnet1, Boot VMs on the Network1, 
             Create Router1, Add Subnet1 to Router1 and Associate Router1 into VPN1

Create stack with template 1, make sure that VPN1 appears in the ODL controller.
Also make sure all of the VM IPs (and their secondaries) on the Router1 must be realized into VPN1.
Delete stack with template 1, make sure VPN1 disappears from the ODL controller along with
removal of VM IPs from the VPN1.

UC 2.9:  A router-driven BGPVPN creation with DualStack Router association 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (single HOT template)
template 1 - Create a VPN1 with RD1
             Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1
             Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1 and Associate Router1 into VPN1

Create stack with template 1, make sure that VPN1 appears in the ODL controller.
Also make sure all of the VM IPv4 and IPv6s (and their secondaries) on the Router1 must be realized into VPN1.
Delete stack with template 1, make sure VPN1 disappears from the ODL controller.

UC 2.10:  A router-driven BGPVPN cycled through with association and disassociation 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1
             Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1
template 3 - Associate Router1 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, all of the VM IPs (and their secondaries) on Network1
must be realized into Router1.

Create stack with template 3, and ensure all of the VM IPs (and their secondaries)
on Router1 must be realized into VPN1.

Delete stack with template 3, make sure that Router1 must be removed from VPN1.
Immediately create stack with template 3, make sure that Router1 is included back into VPN1.
Also make sure all of the VM IPv4 and IPv6s (and their secondaries) on the Router1 must be realized into VPN1.

Delete stack with template 3, Router1 must disappear from VPN1.
Delete stack with template 2, Router1 must disappear from the ODL controller.
Delete stack with template 1, VPN1 must disappear from the ODL controller.

UC 2.10 variation 2 - A router-driven BGPVPN cycled through with association and disassociation 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1
             Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1
template 3 - Associate Router1 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, all of the VM IPs (and their secondaries) on Network1
must be realized into Router1.

Create stack with template 3, and ensure all of the VM IPs (and their secondaries)
on Router1 must be realized into VPN1.

Delete stack with template 3, make sure that Router1 must be removed from VPN1.
Immediately delete stack with template 2, make sure that Router1 disappears from ODL controller.

Delete stack with template 1, VPN1 must disappear from the ODL controller.

UC 2.11: A router-driven BGPVPN ordering router deletion before VPN deletion
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1
             Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1, Associate Router1 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, ensure all of the VM IPs (and their secondaries) on Router1 
must be realized into VPN1.

Delete stack with template 2, make sure that Router2 must be removed from VPN1.
Also make sure all of the VM IPv4 and IPv6s (and their secondaries) along with Router1 are not hanging around at all.

Delete stack with template 1, VPN1 must disappear from the ODL controller.

UC 2.11: variation 2 A router-driven BGPVPN ordering router deletion
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
before VPN deletion without disassociating the router
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1
             Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1
template 3 - Associate Router1 into VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, ensure all of the VM IPs (and their secondaries) on Router1 
must be realized into VPN1.
Create stack with template 3, and ensure all of the VM IPs (and their secondaries)
on Router1 must be realized into VPN1.

Delete stack with template 2, make sure that Router2 must be removed from VPN1.
Also make sure all of the VM IPv4 and IPv6s (and their secondaries) along with Router1 are not hanging around at all.

Delete stack with template 1, VPN1 must disappear from the ODL controller.
Delete stack with template 3,  the stack deletion must fail and this is normal.

UC 2.12:  A router-driven BGPVPN ordering VPN deletion before router deletion 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1
             Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1, Associate Router1 to VPN1

Create stack with template 1, make sure VPN1 appears in the ODL controller.
Create stack with template 2, all of the VM IPs (and their secondaries) on Network1
must be realized into VPN1.

Delete stack with template 1
All of the VM IPs (and their secondaries) on Router1 must be removed from VPN1.
Make sure ELAN traffic and Router1 traffic continues to work.

Delete stack with template 2 
Make sure all the VMs, Routers disappear from the ODL controller.

UC 2.13: Two router-driven BGPVPNs cycled through with same RouteDistinguisher and same Router 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
template 1 - Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1
             Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1
template 2 - Create a VPN1 with RD1 and Associate Router1 into VPN1
template 3 - Create a VPN2 with RD1 and Associate Router1 into VPN2

Create stack with template 1, make sure all the VMs appears in the ODL controller.
Create stack with template 2, all of the VM IPs (and their secondaries) on Router1 
must be realized into VPN1.

Delete stack with template 2.
Immediately create a stack with template 3.
All of the VM IPs (and their secondaries) on Router1 must be removed from VPN1.
All of the VM IPs (and their secondaries) on Router1 must be realized into VPN2.

Delete stack with template 3, make sure that Router1 must be removed from VPN2.
Delete stack with template 1, make sure all the VMs disappear from the ODL controller.

Proposed change
===============
In order to tighten the L3VPN service to make it consistently realize the
Openstack Neutron BGPVPN orchestrated VPNs (and the networks/routers in them)
in OpenDaylight controller, we need to address gaps in various components of
NetVirt.

The following components will be enhanced to ensure a consistent and scaled-out
realization of BGPVPNs onto OpenDaylight NetVirt:
a. NeutronVPN  (NeutronVpnManager)
b. VPN Engine  (VpnInstanceListener and VpnInterfaceManager)
c. BGP Engine  (BGPManager)
d. VRF Engine  (FIBManager)
e. NAT Engine  (NATService) 
f. EVPN Engine (EVPN in ELANManager)

All of the above engines enhancement is to enable safe parallel processing of multiple vpn-instances
regardless of whether such vpninstances use the same route-distinguisher, same ip-address,
same interfaces etc.

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
This initiative does not introduce (or) mandate any pipeline changes.

Yang changes
------------
Changes will be needed in ``l3vpn.yang`` , ``odl-l3vpn.yang`` , ``odl-fib.yang`` and
``neutronvpn.yang`` to support the robustness improvements.

Changes will be needed in ``odl-l3vpn.yang`` and ``odl-fib.yang``
to support the robustness for Neutron BGPVPN API.

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
There were no alternative proposals considered for this initiative.

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
under the neutron-bgpvpn-api-support.

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
A New CSIT suite will be added for this feature, as this starts to use Neutron BGPVPN APIs and
makes it official for ODL platform.

Documentation Impact
====================

References
==========
