.. contents:: Table of Contents
         :depth: 3

========================
L3VPN Dual Stack for VMs
========================

https://git.opendaylight.org/gerrit/#/q/topic:l3vpn-dual-stack-vms

In this specification we will be discussing the high level design of
handling dual stack IPv4/IPv6 VMs through BGP VPN.

Problem description
===================

To support dual stack VMs, it must be possible associate IPv6 extra-routes
and IPv6 subnets with IPv6 routers and IPv4 extra-routes and IPv4 subnets with
IPv4 routers.

Providing separately VPN connectivity for L3VPN IPv4 VMs and distinct L3VPN
IPv6 VMs is already achieved. This can be done by using two distinct VPNs.

The problem arises when the administrator wants to handle both IPv4 and IPv6
in the same VPN.



Following schema could help :

::

 +---------------------+                  
 | +-----------------+ |                  
 | |VM1              | +---+              
 | | Subnet C::4/64  | |   |              
 | | Subnet a.b.c.1/j| |   |              
 | +-----------------+ |OVS|              
 | +-----------------+ | A |              
 | |VM2              | |   |              
 | | Subnet D::4/64  | |   |
 | | Subnet d.e.f.2/i| +-+-+              
 | +-----------------+ | |     +------+       
 +---------------------+ |     |      |       
                         +-----+      |       
                               | DCGW +--WAN--
 +---------------------+ +-----+      |       
 | +-----------------+ | |     |      |       
 | |VM3              | +-+-+   +------+       
 | | Subnet C::5/64  | |   |
 | | Subnet d.e.f.1/i| |   |
 | +-----------------+ |OVS|
 | +-----------------+ | B |
 | |VM4              | |   |
 | | Subnet D::5/64  | |   |
 | | Subnet a.b.c.2/j| +---+
 | +-----------------+ |    
 +---------------------+    


One can see that there are 4 subnetworks identified:
 - 2 IPv4 networks : a.b.c.x/j and d.e.f.x/i
 - 2 IPv6 networks : C::x/64 and D::x/64 

Each VM has both an IPv4 and an IPv6 addressing scheme.
That means that the VPN allocation scheme will not apply the same IPv4 and IPv6
network for each VM.
On the scheme, we have 4 VMS. One can imagine having VM1 involved in IPv4 network 1,
and IPv6 network 2, while an other VM in IPv4 netwokr 2, and IPv6 network 2.

Actually, nothing prevents that kind of configuration from BGPVPN point of view. However,
there are some limitations on ODL that are the following ones:
- one can not associate to the same port both IPv4 and IPv6 addresses.
- it is not possible to associate one VPN to two different routers.

  
Use Cases
---------

There is no change in the use cases described in [6] and [7], except that the a single VPN
is used for both IPs

Inter DC Access
~~~~~~~~~~~~~~~

The inter DC for single stack is described in [7]. In dual stack VM case, an
IPv4 and IPv6 subnet will be associated with 2 routers, one for IPv4 and the
other for IPv6.
The same VPN will be attached to both routers. The VPN will distinguish which
router is IPv4, and will create the appropriate FIB IPv4 entries associated to
that VPN entry. The same will happen to the IPv6

  
External Internet Connectivity
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Any dual stack VM with a IPv4 and IPv6 subnet will be associated with 2 routers
(one for IPv4 and the other for IPv6). The IPv4 network will obtain the external
network connectivity as before using SNAT. However, the IPv6 external network
connectity would be based on use of a fall-through as explained in [6].
To that end, the ECM must create an internet VPN which would be associated with
the external network associated with the router. The external network will have
SNAT disabled. Now, packets from the regular VPN will fall-through to Internet
VPN to get to Internet. In the downstream direction, packets are directly
forwarded to the neutron port based on the label matches since the IPv6 addresses
are globally unique.

Proposed change
===============

Quagga BGP and BGP Manager changes
----------------------------------

To support IPv6, the BGP manager would have to modify the thrift interface. In
addition, to support dual-stack VMs, the thrift api addvrf must be modified to
include the address family and subsequent address family information.
This is to distinguish between IPv4 and IPv6 vrf tables with the same RD/iRT/eRT.

::

 enum af_afi {
     AFI_IP = 1,
     AFI_IPV6 = 2,
   }

  i32 addVrf(1:layer_type l_type, 2:string rd, 3:list<string> irts, 4:list<string> erts,
             5:af_afi afi, 6:af_safi afi),
 i32 delVrf(1:string rd, 2:af_afi afi, 3:af_safi safi)


This is also to prevent from the various calls from ODL that may lead to confusions for QBGP.
As example, associate an IPv4 router to VPN1, then associate IPv6 router to VPN2 will lead to
two addVRF() calls:

::

   addVrf(LAYER_3, "64:1", AFI_IP, SAFI_MPLSVPN)
   addVrf(LAYER_3, "64:1", AFI_IPV6, SAFI_MPLSVPN)


Problem currently arises when disassociate command is triggered for VPNv6 only. This does not mean
that the VRF context must be erased. Only the VPNv6 part should be removed. This is the reason why
it is necessary to add that change.

   
Netvirt Neutron changes
-----------------------
When a port is created, a list of IPs is retrieved from openstack neutron port-create.
Potentially, there can be more than one IP address attached to that port.
Currently, the neutron handler in charge of getting the IPs only cares about the first available subnet. The change proposal is to modify handleNeutronPortCreated() function and create as many subnetmap entries as there are IPs attached to the Port.

When associating a VPN to a router, a check is done against an already attached Router.
It must be possible, for a given VPN, to associate 2 routers.
Changes include:
- a yang change in neutronvpn.yang to replace router-id leaf with a leaf-list.
  The vpnMaps structure will use a router-id list instead.
- subsequent changes for all the java code that uses that structure vpnMaps.
- The RPC command associateRouter() will be modified so as to permit configuring two routers for one VPN.
The command will be refused when more than 2 routers are associated to the same VPN.
This is done because we restrict the usage to dual stack limitation.
    
  
Netvirt VPNManager Changes
--------------------------
As a neutron port is associated with a single network, because the VM supports dual stack, it must
be possible to associate both IPv4 and IPv6 subnets to the same VM port.
This change include:
- yang modification on vpnmanager, on odl-l3vpn.yang file, on port-op-data container.
- subsequent changes for all the java code that uses that structure port-op-data

The changes have to be done when following event comes to VPN.
When a VPN has new router to parse ( or new network), The VPN will do the following:
upon the first network updated:
- the nature of the subnetwork is identified: IPv4 or IPv6.
- this leads to set an attribute to VPN to IPv4 or IPv6
- this leads to call BGPManager for VRF configuration, with IPv4 or IPv6 attribute
upon other network updated:
- if the nature of the subnetwork is already set, then nothing new
- if the nature of the subnetwork is IPv6, whereas first one is IPv4, then the attribute set is added
- the same for calling BGP Manager for VRF configuration with appropriate IPv4/IPv6 attribute.

When a VPN is being requested to declare a new network through BGP, then the processing will act as
today.

  
Pipeline changes
----------------

There is no change in the pipeline, reagarding the changes already done in [6] and [7].
However, an illustration is given in order to explain what happens in above example given.
Only the inter DC use case is depicted.

Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The DC-GW has the information that says into which label and into which underlay destination IP, the packet coming from the internet or from an other DC has to go.


| Classifier Table (0) =>
| LFIB Table (20) ``match: tun-id=mpls_label set vpn-id=l3vpn-id, pop_mpls label, set output to nexthopgroup-dst-vm`` =>
| NextHopGroup-dst-vm: ``set-eth-dst dst-mac-vm, reg6=dst-vm-lport-tag`` =>
| Lport Egress Table (220) ``Output to dst vm port``


Traffic from Local DPN to DC-Gateway (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Classifier Table (0) =>
| Lport Dispatcher Table (17) ``match: LportTag l3vpn service: set vpn-id=l3vpn-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac l3vpn service: set vpn-id=l3vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=ext-ipv4-address set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=ext-ipv6-address set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>

Please note that ``router-internal-interface-mac`` stands for MAC address of
the neutron port of the internal subnet gateway router.

Yang changes
------------

file neutronvpn.yang
~~~~~~~~~~~~~~~~~~~~

--- a/vpnservice/neutronvpn/neutronvpn-api/src/main/yang/neutronvpn.yang
+++ b/vpnservice/neutronvpn/neutronvpn-api/src/main/yang/neutronvpn.yang
@@ -173,7 +173,7 @@ module neutronvpn {
description "The UUID of the tenant that will own the subnet.";
}

-            leaf router-id {
+            leaf-list router_ids {
	     type    yang:uuid;
	     description "UUID of router ";
	     }
	     
file odl-l3vpn
~~~~~~~~~~~~~~

--- a/vpnservice/vpnmanager/vpnmanager-api/src/main/yang/odl-l3vpn.yang
+++ b/vpnservice/vpnmanager/vpnmanager-api/src/main/yang/odl-l3vpn.yang
@@ -395,9 +395,9 @@ module odl-l3vpn {
type  string;
description "UUID in string format representing the port ";
}
-            leaf subnet-id {
+            leaf-list subnet-id-list {
	     type  yang:uuid;
-                description "Back reference to obtain the subnet for a port ";
+                description "Back references to obtain the subnets for a port ";
    	     }
	     leaf dpnId {
	     type uint64;

Configuration impact
---------------------
None

Clustering considerations
-------------------------
None

Other Infra considerations
--------------------------
None

Security considerations
-----------------------
None

Scale and Performance Impact
----------------------------
None

Targeted Release
-----------------
Carbon

Alternatives
------------
None

Usage
=====

* create network settings
  - create Network1
  - create Network2
  - declare Subnetwork IPv4 for Network1 and Network2
  - declare Subnetwork IPv6 for Network1 and Network2
  - create two ports for Network1 and 2 ports for Network2
    Each port will inherit a dual IP configuration

* create the router settings
  - create two routers. each router will respectively be used
  for IPv4 and IPv6.
  - add an IPv4 interface to IPv4 router and link to IPv4 subnetwork   
  - add an IPv6 interface to IPv6 router and link to IPv6 subnetwork   

* Create the ComputeNode to DC-GW settings
 Because the transportation tunnel to the DC-GW is MPLS over GRE,
 the appropriate settings must be done.
 An ITM context is created whose termination endpoint is the DC-GW.
 Its nature is MPLS over GRE.

* create the DC-GW VPN settings
 - create a VPN context. This context will have the same settings as in [7].
  note that for the [6] case, the VPN should be slightly modified.
 - some entries are injected into the DC-GW. Those entries are simulated
 in our case. both IPv4 and IPv6 prefixes will be injected in the same VPN. 
  
* create the ODL VPN settings
  - create a BGP context.
   This step permits to start QBGP module depicted in [8] and [9].
   ODL has an API that permits interfacing with that external software.
  The BGP creation context handles the following:
    o start of BGP protocol
    o declaration of remote BGP neighbor with the AFI/SAFI affinities
  ( in our case, VPNv4 and VPNv6 addresses families will be used).
  - create a VPN. this VPN will have a name and will contain the VRF settings

* associate the VPN created to both routers
  - associate router1 to the VPN
  - associate router2 to the VPN
    
* Spawn a VM in the tenant network
 The VM will inherit from dual stack configuration

* Observation:
 The ODL FIB will dump both IPv4 and IPv6 entries for the same VPN.   


Features to Install
-------------------
odl-netvirt-openstack

REST API
--------

CLI
---

A new option ``--afi`` will be added to command ``odl:bgp-vrf``:

.. code-block:: none

 opendaylight-user@root>
 odl:bgp-vrf --rd <> --import-rt <> --export-rt <> --afi <1|2> add|del


Implementation
==============

Assignee(s)
-----------
Primary assignee:
  Philippe Guibert <philippe.guibert@6wind.com>

Other contributors:
  Valentina Krasnobaeva <valentina.krasnobaeva@6wind.com>
  Noel de Prandieres <prandieres@6wind.com>


Work Items
----------

* QBGP Changes
* BGPManager changes
* VPNManager changes
* NeutronVpn changes


Dependencies
============
Quagga from 6WIND is publicly available at the following url

 * https://github.com/6WIND/quagga
 * https://github.com/6WIND/zrpcd

Testing
=======

Unit Tests
----------
Some BGP VPNv4/v6 testing may have to be done.
Complementary specification will be done

Integration Tests
-----------------
TBD

CSIT
----
CSIT specific testing will be done so as to test this specific dual configuration.
Basically, all IPv4/IPv6 vpnservice will be retested together with a single BGPVRF

Draft - Issues to Solve
=======================
- What happens when one router is configured with both IPv4 and IPv6
  and one tries to associate a VPN
- It seems that the VPN context creation is enough to create the BGP VRF context.
  The proposed configuration tends to think of following change:
  When a VPN knows it is associated to IPv4 or IPv6, the respective VRF should be created in the QBGP.
  This is where the afi parameter should be useD.

Documentation Impact
====================
Necessary documentation would be added on how to use this feature.

References
==========
[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

[3] http://docs.openstack.org/developer/networking-bgpvpn/overview.html

[4] `Spec to support IPv6 North-South support for Flat/VLAN Provider Network.
<https://git.opendaylight.org/gerrit/#/q/topic:ipv6-cvr-north-south>`_

[5] `BGP-MPLS IP Virtual Private Network (VPN) Extension for IPv6 VPN
<https://tools.ietf.org/html/rfc4659>`_

[6] `Spec to support IPv6 DC to Internet L3VPN connectivity using BGPVPN
<https://git.opendaylight.org/gerrit/#/c/54050/>`_

[7] `Spec to support IPv6 Inter DC L3VPN connectivity using BGPVPN
<https://git.opendaylight.org/gerrit/#/c/50359/>`_

[8] `Zebra Remote Procedure Call
<https://github.com/6WIND/zrpcd/>`_

[9] `Quagga BGP protocol
<https://github.com/6WIND/zrpcd/>`_
