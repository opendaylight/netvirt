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

For the template types and their contents, please see CSIT section.

UC 1:  A net-driven BGPVPN creation with Network association 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (single HOT template)
template 1 - Create a VPN1 with RD1
Create Network1, Create Subnet1, Boot VMs on the Network1, Associate Network1 into VPN1

template 1 type - TYPE A

Create stack with template 1, make sure that VPN1 appears in the ODL controller.
Also make sure all of the VM IPs (and their secondaries) must be realized into VPN1.
Delete stack with template 1, make sure VPN1 disappears from the ODL controller.

UC 2:  A net-driven BGPVPN cycled through multiple Networks association 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (multiple HOT templates)
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create Subnet1, Boot VMs on the Network1, Associate Network1 into VPN1
template 3 - Create Network2, Create Subnet2, Boot VMs on the Network2, Associate Network2 into VPN1

template 1 type - TYPE B
template 2 type - TYPE C 
template 3 type - TYPE C 

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

template 1 type - TYPE B
template 2 type - TYPE C 
template 3 type - TYPE C 

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

template 1 type - TYPE B 
template 2 type - TYPE C 
template 3 type - TYPE C 

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
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
template 1 - Create Network1, Create Subnet1, Boot VMs on the Network1
template 2 - Create a VPN1 with RD1 and associate Network1 to VPN1 
template 3 - Create a VPN2 with RD1 and associate Network1 to VPN2

template 1 type - TYPE D 
template 2 type - TYPE E 
template 3 type - TYPE E 

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

template 1 type - TYPE B
template 2 type - TYPE C 

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

template 1 type - TYPE B
template 2 type - TYPE D 
template 3 type - TYPE F 

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
template 1 - Create a VPN1 with RD1.  Create Network1, Create Subnet1, Boot VMs on the Network1, 
Create Router1, Add Subnet1 to Router1 and Associate Router1 into VPN1

template 1 type - TYPE M

Create stack with template 1, make sure that VPN1 appears in the ODL controller.
Also make sure all of the VM IPs (and their secondaries) on the Router1 must be realized into VPN1.
Delete stack with template 1, make sure VPN1 disappears from the ODL controller along with
removal of VM IPs from the VPN1.

UC 2.9:  A router-driven BGPVPN creation with DualStack Router association 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The steps for this use-case (single HOT template)
template 1 - Create a VPN1 with RD1.  Create Network1, Create IPv4 Subnet11, Create IPv6 Subnet12
Create Network2, Create IPv4 Subnet21, Create IPv6 Subnet22, Boot VMs on the Network1
Boot VMs on the Network2, Create Router1, Add Subnet11 to Router1, Add Subnet12 to Router1,
Add Subnet21 to Router1, Add Subnet22 to Router1 and Associate Router1 into VPN1

template 1 type - TYPE N

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

template 1 type - TYPE B
template 2 type - TYPE O 
template 3 type - TYPE Q 

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
template 1 - Create a VPN1 with RD1.
template 2 - Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1.
Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1
template 3 - Associate Router1 into VPN1

template 1 type - TYPE B
template 2 type - TYPE O 
template 3 type - TYPE Q 

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

template 1 type - TYPE B 
template 2 type - TYPE P

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

template 1 type - TYPE B 
template 2 type - TYPE O 
template 3 type - TYPE Q 

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
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
template 1 - Create a VPN1 with RD1
template 2 - Create Network1, Create IPv4 Subnet1, Create IPv6 Subnet2, Boot VMs on the Network1
Create Router1, Add Subnet1 to Router1, Add Subnet2 to Router1, Associate Router1 to VPN1

template 1 type - TYPE B
template 2 type - TYPE P 

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

template 1 type - TYPE O 
template 2 type - TYPE R 
template 3 type - TYPE R 

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

Module Changes
---------------
VPN Engine:
^^^^^^^^^^^
Currently Oper/VpnInstanceOpData is keyed by vrf-id (aka Route Distinguisher) and this kind 
of keying was all OK as we serviced only one VPNInstance at a time that would carry this vrf-id.

As we move to public cloud, the customers will start to use the neutron bgpvpn api
(in lieu of ODL provided RESTful APIs). With the Neutron BGPVPN API interface, it is
very possible to have multiple VPNInstance could be managed by tenants where in such instances carry
the same vrf-id (aka Route Distinguisher). This is because route-distinguisher is not how an VPN is
identified in Neutron BGPVPN, instead the vpn-uuid allocated by Neutron uniquely identifies a VPN.

However, L3VPN Service in ODL is using the route-distinguisher as the key for realizing a vpn
(i.e, VpnInstanceOpData) and so the key for this yang will be changed to use the vpn-instance-uuid
instead of route-distinguisher.

This specific VpnInstanceOpData datastore is referred by many services within ODL and so such services
must be updated on their consumption/production of information into this datastore.

One more new model that maps a given RD (route-distinguisher) to an Active VpnInstance will be 
required for use by the BGPEngine.

VRF Engine:
^^^^^^^^^^^
The VRFEngine does not use vpn-instance-uuid at all and uses only vrfTables as the
primary datastore for representing the FIB. This vrfTables has vrf-id (route-distinguisher)
as the key, to maintain the FIB. 

This engine will be enhanced such that vrfTables become a child to a vpn-instance-uuid keyed
container thereby enabling multiple vpninstances to be managed regardless of whether such vpn-instances
use the same RD or otherwise.

So the VrfEntry will be keyed by (VPNInstance + RD + Prefix) instead of being keyed only with
(RD + prefix) as it stands today.

All BGP Advertisements and Withdrawals (for all routes managed by ODL itself - i.e., non-BGP-imported routes),
will be done within the VRFEngine (i.e, VrfEntryListener) rather than by the VPN Engine.
All such advertisements will be serialized within the VrfEntryListener so that route consistency is
maintained between ODL and its BGP neighbor.

BGP Engine:
^^^^^^^^^^^
The BGP Engine within ODL is primarily responsible for:
a. Pushing a route from its external neighbor into ODL
b. Removing a route from its external neighbor from ODL 
c. Advertising an ODL managed route to its external neighbor
d. Withdrawing an ODL managed route to its external neighbor

Since there can be more than one VpnInstance that can use a given route-distinguisher at a time within
Opendaylight (a situation where previous vpn-instance is being deleted while a new vpn-instance with same
RD being created), BGP Engine needs to know which of the vpn-instances out of the
these to choose to import a route when it comes from an external neighbor (case a). 

This is because, BGP Engine (and Quagga which has real BGP logic) within ODL operates only with 
route-distinguishers and not with vpn-instances.  This is the same case with the other
BGP neighbors too that talk to ODL BGP Engine. 

As a result the BGP Engine will continue to work only with route-distinguishers but it will make a call
to the VpnEngine to figure out to which all vpn-instances an incoming route must be written to (case a).
The same principle applies when an external bgp route is to be removed from within OpenDaylight (case b).

Advertising (or) Withdrawing an ODL managed route will be driven by the VRFEngine towards the BGPEngine
i.e, case c and case d.  The BGPEngine will have no role to play here.

NeutronVPN Engine:
^^^^^^^^^^^^^^^^^^
There are race conditions within the NeutronVPN that gets triggered when HOT templates are executed primarily in the
Neutron BGPVPN management path.  These race conditions are around the Neutron Router, Neutron BGPVPN, Neutron Subnet
and Neutron Network resources access and their management within NeutronVPN.  So this engine will be enhanced to 
close out these race-conditions.

Pipeline changes
----------------
This initiative does not introduce (or) mandate any pipeline changes.

Yang changes
------------
Changes will be needed in ``fib-rpc.yang`` , ``odl-fib.yang`` 
to address this feature.

Changes will be needed in ``odl-l3vpn.yang`` and ``odl-fib.yang``
to support the robustness for Neutron BGPVPN API.

FIB-RPC YANG CHANGES
^^^^^^^^^^^^^^^^^^^^
.. code-block:: none
   :caption: fib-rpc.yang

    rpc populate-fib-on-dpn {
        description "Populates FIB table in specified DPN";
        input {
            leaf dpid {
                type uint64;
            }
            leaf vpn-id {
                type uint32;
            }
    +       leaf vpn-instance-name {
    +           type string;
    +       }
            leaf rd {
                type string;
            }
        }
    }

    rpc cleanup-dpn-for-vpn {
        description "Removes the VPN Fib entries in a given DPN";
        input {
            leaf dpid {
                type uint64;
            }
            leaf vpn-id {
                type uint32;
            }
    +       leaf vpn-instance-name {
    +           type string;
    +       }
            leaf rd {
                type string;
            }

        }
    }


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
    +        list vpnInstanceNames {
    +            key vpnInstanceName;
    +            leaf vpnInstanceName { type string; }
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
    +           key vpn-instance-name;
    +           leaf vpn-instance-name {
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

    @@ +706,7 +717,7 @@ module odl-l3vpn {
    + container rd-to-vpns {
    +    config false;
    +    list rd-to-vpn {
    +        key "rd";
    +        leaf rd { type string; }
    +        list vpn-instances {
    +            key "vpn-instance-name";
    +            leaf vpn-instance-name { type string; }
    +            leaf active { type boolean; }
    +        }
    +    }
    +}

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
The feature can be excited by Openstack Neutron BGPVPN REST APIs (or) by 
Openstack Neutron BGPVPN CLIs.

As part of this spec implementation, we will actually be exciting Neutron BGPVPN
APIs via the Heat Templates.   Please see more details of the templates and the
types of templates we will use in the CSIT section.

Features to Install
-------------------
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

REST API
--------
No REST APIs are introduced in this feature.

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

Unit Tests
----------
Appropriate UTs will be added for the new code coming in once framework is in place.

Integration Tests
-----------------
There won't be any Integration tests provided for this feature.

CSIT
----
A New CSIT suite will be added for this feature, as this starts to use Neutron BGPVPN APIs
in couple with HOT templates and makes it official for ODL platform.

CSIT will use the HOT Templates referred here.

TYPE A
^^^^^^^
.. code-block:: none
   :caption: TYPE A 

   description: BGPVPN networking example TYPE A (admin)
   heat_template_version: '2013-05-23'

   resources:

       BGPVPN1:
           type: OS::Neutron::BGPVPN
           properties:
               import_targets: [ "100:1001"]
               export_targets: [ "100:1002"]
               route_targets: [ "100:1000" ]
               name: "default VPN"

       Net1:
           type: OS::Neutron::Net
           properties:
               name: "default Net1"

       SubNet1:
           type: OS::Neutron::Subnet
           properties:
               network: { get_resource: Net1 }
               cidr: 20.1.1.0/24

       BGPVPN_NET_assoc1:
           type: OS::Neutron::BGPVPN-NET-ASSOCIATION
           properties:
               bgpvpn_id: { get_resource: BGPVPN1 }
               network_id: { get_resource: Net1 }

TYPE B
^^^^^^^
.. code-block:: none
   :caption: TYPE B

   description: BGPVPN networking example TYPE B (admin)
   heat_template_version: '2013-05-23'

   resources:

       BGPVPN1:
           type: OS::Neutron::BGPVPN
           properties:
               import_targets: [ "100:1001"]
               export_targets: [ "100:1002"]
               route_targets: [ "100:1000" ]
               name: "default VPN"


TYPE C
^^^^^^^
.. code-block:: none
   :caption: TYPE C 

   description: BGPVPN networking example TYPE C (admin)
   heat_template_version: '2013-05-23'

   resources:

       Net1:
       type: OS::Neutron::Net
       properties:
           name: "default Net1"

       SubNet1:
           type: OS::Neutron::Subnet
           properties:
               network: { get_resource: Net1 }
               cidr: 20.1.1.0/24

       BGPVPN_NET_assoc1:
           type: OS::Neutron::BGPVPN-NET-ASSOCIATION
           properties:
               bgpvpn_id: "default VPN"
               network_id: { get_resource: Net1 }

TYPE D
^^^^^^^
.. code-block:: none
   :caption: TYPE D 

   description: BGPVPN networking example TYPE D (admin)
   heat_template_version: '2013-05-23'

   resources:

       Net1:
           type: OS::Neutron::Net
           properties:
               name: "default Net1"

       SubNet1:
       type: OS::Neutron::Subnet
       properties:
           network: { get_resource: Net1 }
           cidr: 20.1.1.0/24

TYPE E
^^^^^^^
.. code-block:: none
   :caption: TYPE E

   description: BGPVPN networking example (admin)
   heat_template_version: '2013-05-23'

   resources:

   BGPVPN1:
       type: OS::Neutron::BGPVPN
       properties:
           import_targets: [ "100:1001"]
           export_targets: [ "100:1002"]
           route_targets: [ "100:1000" ]
           name: "default VPN"

   BGPVPN_NET_assoc1:
       type: OS::Neutron::BGPVPN-NET-ASSOCIATION
       properties:
           bgpvpn_id: "default VPN"
           network_id: "default Net1" 

TYPE F
^^^^^^^
.. code-block:: none
   :caption: TYPE F

   description: BGPVPN networking example (admin)
   heat_template_version: '2013-05-23'

   resources:

       BGPVPN_NET_assoc1:
           type: OS::Neutron::BGPVPN-NET-ASSOCIATION
           properties:
               bgpvpn_id: "default VPN"
               network_id: "default Net1" 

TYPE M
^^^^^^^
.. code-block:: none
   :caption: TYPE M


   description: BGPVPN networking example (admin)
   heat_template_version: '2013-05-23'

   resources:

       BGPVPN1:
           type: OS::Neutron::BGPVPN
           properties:
               import_targets: [ "100:1001"]
               export_targets: [ "100:1002"]
               route_targets: [ "100:1000" ]
               name: "default VPN"


       Net1:
           type: OS::Neutron::Net
           properties:
               name: "default Net1"

    SubNet1:
        type: OS::Neutron::Subnet
        properties:
           network: { get_resource: Net1 }
           cidr: 20.1.10.0/24

    Router:
        type: OS::Neutron::Router
        properties:
            name: "default Router"


    router_interface:
        type: OS::Neutron::RouterInterface
        properties:
           router_id: { get_resource: Router }
           subnet_id: { get_resource: SubNet1 }

    BGPVPN_router_assoc1:
        type: OS::Neutron::BGPVPN-ROUTER-ASSOCIATION
        properties:
            bgpvpn_id: { get_resource: BGPVPN1 }
            router_id: { get_resource: Router }

TYPE N 
^^^^^^^
.. code-block:: none
   :caption: TYPE M

   description: BGPVPN networking example (admin)
   heat_template_version: '2013-05-23'

   resources:

       BGPVPN1:
       type: OS::Neutron::BGPVPN
       properties:
           import_targets: [ "100:1001"]
           export_targets: [ "100:1002"]
           route_targets: [ "100:1000" ]
           name: "default VPN"

       Net1:
           type: OS::Neutron::Net
           properties:
               name: "default Net1"

       SubNet11:
           type: OS::Neutron::Subnet
           properties:
               network: { get_resource: Net1 }
               cidr: 20.1.1.0/24

       SubNet12:
           type: OS::Neutron::Subnet
           properties:
               network: { get_resource: Net1 }
               cidr: 2001:db8:cafe:1e::/64

       Net2:
           type: OS::Neutron::Net
           properties:
               name: "default Net2"

       SubNet21:
           type: OS::Neutron::Subnet
           properties:
               network: { get_resource: Net2 }
               cidr: 30.1.1.0/24
       
       SubNet22:
           type: OS::Neutron::Subnet
           properties:
               network: { get_resource: Net2 }
               cidr: 2002:db8:cafe:1e::/64

       Router:
           type: OS::Neutron::Router
           properties:
               name: "default Router"

       router_interface1:
           type: OS::Neutron::RouterInterface
           properties:
               router_id: { get_resource: Router }
               subnet_id: { get_resource: SubNet11 }

       router_interface2:
           type: OS::Neutron::RouterInterface
           properties:
               router_id: { get_resource: Router }
               subnet_id: { get_resource: SubNet12 }

       router_interface3:
           type: OS::Neutron::RouterInterface
           properties:
               router_id: { get_resource: Router }
               subnet_id: { get_resource: SubNet21 }

       router_interface4:
           type: OS::Neutron::RouterInterface
           properties:
               router_id: { get_resource: Router }
               subnet_id: { get_resource: SubNet22 }

       BGPVPN_router_assoc1:
           type: OS::Neutron::BGPVPN-ROUTER-ASSOCIATION
           properties:
                bgpvpn_id: { get_resource: BGPVPN1 }
                router_id: { get_resource: Router }

TYPE O
^^^^^^^
.. code-block:: none
   :caption: TYPE 0 


   description: BGPVPN networking example (admin)
   heat_template_version: '2013-05-23'

   resources:

       BGPVPN1:
           type: OS::Neutron::BGPVPN
           properties:
               import_targets: [ "100:1001"]
               export_targets: [ "100:1002"]
               route_targets: [ "100:1000" ]
               name: "default VPN"

       Net1:
           type: OS::Neutron::Net
           properties:
               name: "default Net1"

       SubNet11:
           type: OS::Neutron::Subnet
           properties:
               network: { get_resource: Net1 }
               cidr: 20.1.1.0/24

       SubNet12:
           type: OS::Neutron::Subnet
           properties:
              network: { get_resource: Net1 }
              cidr: 2001:db8:cafe:1e::/64

       Router:
           type: OS::Neutron::Router
           properties:
               name: "default Router"

       router_interface1:
           type: OS::Neutron::RouterInterface
           properties:
               router_id: { get_resource: Router }
               subnet_id: { get_resource: SubNet11 }

       router_interface2:
           type: OS::Neutron::RouterInterface
           properties:
               router_id: { get_resource: Router }
              subnet_id: { get_resource: SubNet12 }

TYPE P
^^^^^^^
.. code-block:: none
   :caption: TYPE P


   description: BGPVPN networking example TYPE P (tenant)
   heat_template_version: '2013-05-23'

   resources:

       BGPVPN1:
       type: OS::Neutron::BGPVPN
       properties:
           import_targets: [ "100:1001"]
           export_targets: [ "100:1002"]
           route_targets: [ "100:1000" ]
           name: "default VPN"

       Net1:
           type: OS::Neutron::Net
           properties:
               name: "default Net1"

       SubNet11:
           type: OS::Neutron::Subnet
           properties:
               network: { get_resource: Net1 }
               cidr: 20.1.1.0/24

    SubNet12:
        type: OS::Neutron::Subnet
        properties:
           network: { get_resource: Net1 }
           cidr: 2001:db8:cafe:1e::/64

    Router:
        type: OS::Neutron::Router
        properties:
            name: "default Router"

    router_interface1:
        type: OS::Neutron::RouterInterface
        properties:
           router_id: { get_resource: Router }
           subnet_id: { get_resource: SubNet11 }

    router_interface2:
        type: OS::Neutron::RouterInterface
        properties:
           router_id: { get_resource: Router }
           subnet_id: { get_resource: SubNet12 }

    BGPVPN_router_assoc1:
        type: OS::Neutron::BGPVPN-ROUTER-ASSOCIATION
        properties:
            bgpvpn_id: { get_resource: BGPVPN1 }
            router_id: { get_resource: Router }

TYPE Q
^^^^^^^
.. code-block:: none
   :caption: TYPE Q

   description: BGPVPN networking example TYPE P (tenant)
   heat_template_version: '2013-05-23'

   resources:

   BGPVPN_router_assoc1:
       type: OS::Neutron::BGPVPN-ROUTER-ASSOCIATION
       properties:
           bgpvpn_id: "default VPN"
           router_id: "default Router" 

TYPE R
^^^^^^^
.. code-block:: none
   :caption: TYPE R 

   description: BGPVPN networking example TYPE P (tenant)
   heat_template_version: '2013-05-23'

   resources:

       BGPVPN1:
           type: OS::Neutron::BGPVPN
           properties:
               import_targets: [ "100:1001"]
               export_targets: [ "100:1002"]
               route_targets: [ "100:1000" ]
               name: "default VPN"

       BGPVPN_router_assoc1:
           type: OS::Neutron::BGPVPN-ROUTER-ASSOCIATION
           properties:
               bgpvpn_id: "default VPN"
               router_id: "default Router" 


Documentation Impact
====================

References
==========
[1] https://docs.openstack.org/networking-bgpvpn/latest/user/heat.html#examples

