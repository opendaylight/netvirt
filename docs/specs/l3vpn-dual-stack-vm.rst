.. contents:: Table of Contents
         :depth: 3

========================
L3VPN Dual Stack for VMs
========================

https://git.opendaylight.org/gerrit/#/q/topic:l3vpn-dual-stack-vms

In this specification we will be discussing the high level design of
dual stack IPv4/IPv6 VMs through BGP VPN.
This specification proposes to bring in a feature into NetVirt enabling OpenDaylight controller
to provide L3-forwarding support for DualStack VMs. Dual stack VMs are virtual machines that will
have 2 L3-identities: one with IPv4 and one with IPv6. More specifically, DualStack VMs will have
a single network interface in them (i.e., single VNIC) but that interface will actively hold both
an IPv4 and IPv6 address. In addition to providing initial support for DualStack VMs, this Spec
ensures that such DualStack VMs work on BGPVPNs too.

Problem description
===================

Each VM has both an IPv4 and an IPv6 addressing scheme.

That means that VM can get both IP4 and IPv6 addresses.

What is commonly used is the ability to configure a neutron port that the VM connects to.
On that neutron port, one IP address is received. Often, a new neutron port is added.
So that the VM will benefit from the two neutron ports, and will benefit from the two IPs.
This use case is working fine and is not in the scope of this spec.

Dual stack VM refers to a VM which has one neutron port, that inherits two IPs, one
IPv4 and one IPv6. We also don't want to discuss the specific use case of having several IPv4's
or several IPv6's ( in the case segmentation is used within neutron ports).

Dual Stack VM capability is important, since there are more and more services that are by default
supported in IPv6. Moreover, due to the exhaustion of IPv4 public IP pools, there is an increasing
use of IPv6 addresses. If the administrator wants to deploy a service, it is more interesting to
support both IPv4/IPv6 services on the same VM.

Based on this configuration, the dual stack VM should be able to connect to the other VMS, whatever
their location is.
In this document, we can handle the following use cases:

- (1) inter DC, inter subnet ( or intra subnet) using BGPVPN

- (2) IPv4/IPv6 access to internet using BGPVPN

For setups (1) and (2), we already know that there are some issues that prevent from doing the testing.
This is the central problematic of the spec. Currently dual stack is not supported across BGPVPN.

Here is described the problem with BGPVPN allocation scheme.
Currently, the VPN allocation scheme only picks up the first IP address of the neutron port.
That means that the VPN allocation scheme will not apply the same IPv4 and IPv6 network for each VM.
For instance, if the first allocated IP address is IPv4 address, then the VPN information will apply
to IPv4 only. The second IPv6 address will be ignored.

Providing separately VPN connectivity for L3VPN IPv4 VMs and distinct L3VPN IPv6 VMs is already achieved.
This can be done by using two distinct VPNs.
But then, we want to address the case where the same VPN will handle both IPV4 and IPv6.

As part of this spec, we would like to support neutron ports that belong to multiple subnets specifically
where one subnet is IPv4 and an other subnet is IPv6.
We would like to rely on two specific configuration setup :

- one based on two neutron routers.
One would be for IPv4, and one for IPV6.
A single VPN would be associated to both routers.
As consequence, IPv4 and IPv6 entries would be gathered in the same VPN.
Also separating the neutron router configuration for IPv4 and IPv6 permits having external network access.

- one based on a single neutron router 
The router is attached to L3VPN instance with only 2 ports: one port for IPv4 subnetwork, and another port
for IPv6 subnetwork.
As part of this setup, we do not support external network access.
However, the case dual stack VM will be done for inter-subnet routing.

In each described setup, by extension, the following should also be possible:
In addition to VM VPN allocation schem, Extra-routes and Subnet configuration would benefit from it.
both IPv4 and IPv6 entries would below to the same VPN.

Setup Presentation
~~~~~~~~~~~~~~~~~~

Following drawing could help :

::

 +---------------------+
 | +-----------------+ |
 | |VM1              | +---+
 | | Subnet C::4/64  | |   |
 | | Subnet a.b.c.1/i| |   |
 | +-----------------+ |OVS|
 | +-----------------+ | A |
 | |VM2              | |   |
 | | Subnet C::5/64  | |   |
 | | Subnet a.b.c.2/i| +-+-+
 | +-----------------+ | |                               +------+
 +---------------------+ |                               |      |
             |           +-MPLSoGRE tunnel for IPv4/IPv6-+      |
             |                                           |      |
            Vxlan                                        |      |
            Tunnel                                       |      |
             |                                           | DCGW +--WAN--
 +---------------------+ +-MPLSoGRE tunnel for IPv4/IPV6-+      |
 | +-----------------+ | |                               |      |
 | |VM3              | +-+-+                             +------+
 | | Subnet C::6/64  | |   |
 | | Subnet a.b.c.3/i| |   |
 | +-----------------+ |OVS|
 | +-----------------+ | B |
 | |VM4              | |   |
 | | Subnet C::7/64  | |   |
 | | Subnet a.b.c.4/i| +---+
 | +-----------------+ |
 +---------------------+

One can see that there are 2 subnetworks identified:
 - 1 IPv4 networks : a.b.c.x/j ( aka IPv4 Subnet 1)
 - 1 IPv6 networks : C::x/64 ( aka IPv6 Subnet 1)
Each VM will receive IPs from the two defined networks.

Following scheme stands for conceptual representation of the network to be used.
We illustrated it by using two openstack routers, one for IPv4, and one for IPv6.

::

   setup a: two router solution for dual stack VM.

              +--------------+
   +----+     | Network A    |
   | VM1|     +--------------+
   +----+-+---| Subnet A IPv4|----------------------+--------------+
          |   +--------------+                      | Router 1     |
          |   | Subnet B IPv6|----+                 +--------------+
          |   +--------------+    |
          |   |                   +--------------+
          |   |                   | Router 2     |
          |   |                   +--------------+
   +----+ |   |
   | VM3| |   |
   +----+-----+

   setup b: single router solution for dual stack VM.

                                                    +--------------+
              +--------------+                      | Router 1     |
   +----+     | Network A    |                      +--------------+
   | VM1|     +--------------+                         |       |
   +----+-+---| Subnet A IPv4|-------------------------+       |
          |   +--------------+                                 |
          |   | Subnet B IPv6|---------------------------------+
          |   +--------------+
          |   |
          |   |
          |   |
   +----+ |   |
   | VM3| |   |
   +----+-----+

   
Each VM has both an IPv4 and an IPv6 addressing scheme.
A single network (mentioned network A above)  gathers 2 subnetworks.
This makes possible to create neutron ports whose attributes will inherit information
(extraroutes, etc) from 2 subnetworks IPv4 and IPv6.
Two routers are created. Each router is connected to separate subnetworks.
The neutron routers provide the DHCP allocation for the VMs that will connect VMs with
appropriate subnets by neutron ports.
For a VM started on a specific port, the VM will get allocated two IP addresses, one from
IPv4 subnetwork, the other one from IPv6 subnetwork.
About DHCP allocation, note that DHCPv6 is not supported in ODL, but will be supported by
neutron-dhcp config. neutron dhcp services will be used.

Known Limitations
~~~~~~~~~~~~~~~~~

Currently, from the BGPVPN point of view, the BGPVPN code has been locked so as to prevent
from association more than one router to a single VPN. This is a limitation because of ODL.

From Netvirt point of view, there are some limitations on ODL:

- One can not associate to the same VPN port both IPv4 and IPv6 addresses.
  Currently, only the first IP address from the neutron port is put inside the neutron VPN port.

- It is not possible to associate one VPN instance to two different routers.
  Despite the configuration permits it, it is refused by neutronvpn submodule, when running
  associateRouter or associateNetwork.
  Note that using two different routers implies that we use one VPN interface for each router.
  There is no restriction about that.

Use Cases
---------

There is no change in the use cases described in [6] and [7], except that the single L3VPN
instance for both IPv4 and IPv6 subnets.

Inter DC Access
~~~~~~~~~~~~~~~

The inter DC for single stack is described in [7].
In dual stack VM case, with dual router solution, IPv4 and IPv6 subnet will be associated
with 2 routers, one for IPv4 and the other for IPv6.
The same L3VPN instance will be attached to both routers. The VPN will distinguish which
router is IPv4, and will create the appropriate FIB IPv4 entries associated to
that VPN entry. The same will happen to the IPv6.
In dual stack VM case, with single router solution, there is a single router with both
subnetworks. The VPN will distinguish which subnetwork is present, and append it to the FIB.
It will create the BGP VRF context IPv4 or IPv6.

External Internet Connectivity
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

As mentioned earlier, the externet internet connectivity in this spec applies only to the
solution with 2 router.
Like before, the following will be applied.
In the IPv4 router, the IPv4 network will obtain the external network connectivity using SNAT.
In the IPv6 router, the IPv6 network will obtain the external network by disabling NAT feature.
Like that the IPv6 will be forwarded transparently.


Proposed change
===============

Quagga BGP and BGP Manager changes
----------------------------------

To support either IPv4 or IPv6, the BGP manager would have to modify the thrift interface.
In addition, to support dual-stack VMs, the thrift api addvrf must be modified to include
the address family and subsequent address family information.
This is to distinguish between IPv4 and IPv6 vrf tables with the same RD/iRT/eRT.

This is to bring flexibility in QBGP. For isntance, if QBGP receives an entry IPv6 MPLSVPN
on a router that is expecting to receive only IPv4 entries, then the entry will be ignored.
The same for IPv4 MPLSVPN entries.

Splitting VRF in two will help in preventing from the various calls from ODL that may
lead to confusions for QBGP.
As example, associate an IPv4 router to VPN1, then associate IPv6 router to VPN2 will lead to
two addVRF() calls:

::

   addVrf(LAYER_3, "64:1", AFI_IP, SAFI_MPLSVPN)
   addVrf(LAYER_3, "64:1", AFI_IPV6, SAFI_MPLSVPN)


Problem currently arises when disassociate command is triggered for VPNv6 only.
This does not mean that the VRF context must be erased.
Only the VPNv6 part should be removed. This is the other reason why it is necessary to add that change.

::

 enum af_afi {
     AFI_IP = 1,
     AFI_IPV6 = 2,
   }

  i32 addVrf(1:layer_type l_type, 2:string rd, 3:list<string> irts, 4:list<string> erts,
             5:af_afi afi, 6:af_safi afi),
  i32 delVrf(1:string rd, 2:af_afi afi, 3:af_safi safi)


On the two router configuration with IPv4 subnetwork for one router, and IPv6 subnetwork for the other
router use case IPv4 or IPv6, not both, then the following happens:

- on attaching the IPv4 router with the VPN instance, the VRF IPv4 MPLSVPN will be created.

- subsequently, on attaching the IPv6 router with the same VPN instance, the VRF IPv6 MPLSVPN
will be created.
Each router associated to 1 VPN port instance, lied with L3VPN instance. This will claim 2 addVRF() calls.

When the VPN is disassociated from one of the router, or the subnetwork is detached from the router, then
the VRF will be suppressed: either IPv4 MPLSVPN, if there are no other IPv4 subnetwork references, or
IPv6 MPLSVPN.

On the single router configuration with dual stack information, because the router is already connected
to both IPv4 and IPv6 subnetworks, then when associating the VPN instance to the router, both VRF will
be created with VRF IPv6 MPLSVPN, and IPv4 MPLSVPN.

A more accurante implementation proposal of changes is done below:

- When RPC commands are received: on associateRouter() or associateNetwork()

- On NeutronPortChangeListener() (that is to say when neutron bgpvpn-net-assoc or neutron
  bgpvpn-router-assoc will be called).
  This may be the case if the same vpn-id ( with the same router-id) is configured on those IPs.

A VPN Interface is created. When VPN Interface creation is detected, vpnmanager module takes the hand.
The List of Subnets and IPs is retrieved.
Depending on then nature of the subnet to submit to BGP Manager, the IPv4 or IPv6 VRF creation will be done.


Netvirt Neutron changes
-----------------------

The changes include the following:

- We should check, that we do not add the same router 2 times

- attached router list can contain up to 2 routers. Even if this is a list, the number of the list is
  limited to two.

- for each neutron port we take only the last received IPv4 address and the last received IPv6 address,
  so IPs retrieved from a neutron port would be always limited to 2.


Changes include:

- a yang change in neutronvpn.yang to replace router-id leaf with a leaf-list, in vpnMaps structure.
  The vpnMaps structure will use a router-id list instead.
  The neutronvpn API used by RPC commands is not changing.
  In the configuration process, care will be done, so that the leaf-list is limited to two router-ids only.

- subsequent changes for all the java code that uses that structure vpnMaps.

- When a port is created, a list of IPs is retrieved from openstack neutron port-create.
  Potentially, there can be more than one IP address attached to that port.
  Currently, the neutron handler in charge of getting the IPs only cares about the first available subnet.
  The change proposal is to modify handleNeutronPortCreated() function and create as many subnetmap entries
  in neutron VPN port, as there are IPs attached to the Neutron Port.
  The change will pick up only the first IPv4 and IPv6 address. Other subsequent addresses are not taken
  into account.
  Caution is done to create only one VPN port instance for the same neutron port. That neutron port
  includes the last IPv4 address, and the last IPv6 address.

Netvirt VPNManager Changes
--------------------------
VpnMaps structure is used by VPN Manager.
The changes include:

- modifications so as to take into account changes in neutronvpn.yang changes.

- VpnInterfaceManager will detect at VPN Interface creation, if there are subnets.
  Upon the nature of the subnets configured, the BGP Manager will be called.
  If IPv4 subnets are configured, then the IPv4 VRF will be called.

Pipeline changes
----------------

There is no change in the pipeline, regarding the changes already done in [6] and [7].
However, an illustration is given in order to explain what happens in above example given.
Only the inter DC use case is depicted.

Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The DC-GW has the information that says into which label and into which underlay destination IP, the
packet coming from the internet or from an other DC has to go.


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

file ebgp.yang
~~~~~~~~~~~~~~

vrfs list is being added two parameters: afi and safi

::

   list vrfs {
     key "rd";
     leaf rd {
     type string;
   }
   leaf layer-type {
     type layer_type;
     mandatory "true";
   }
   +leaf afi {
   +  type uint32;
   +  mandatory "true";
   +}
   +leaf safi {
   +  type uint32;
   +  mandatory "true";
   +}
   

file neutronvpn.yang
~~~~~~~~~~~~~~~~~~~~

Two main changes are done:
- container vpnmaps describes for each VPN-ID one router-id. The change consists in replacing one router-id by a list of router-id.
  Obviously, only 2 router-ids will be used.
- grouping vpn-instance is used externally as rpc for createL3VPN, and internally for describing the VPN.
  The router-id should be replaced by a list of router-id.
  Internal change is necessary, while external changes may be heavier to change ( external repositories to modify)
  It is open to review that the grouping structure be duplicated so that internal and external structure be different.

::

   --- a/vpnservice/neutronvpn/neutronvpn-api/src/main/yang/neutronvpn.yang
   +++ b/vpnservice/neutronvpn/neutronvpn-api/src/main/yang/neutronvpn.yang
   @@ -1,4 +1,3 @@
   -
   module neutronvpn {

   namespace "urn:opendaylight:netvirt:neutronvpn";
   @@ -120,7 +119,7 @@ module neutronvpn {
   Format is ASN:nn or IP-address:nn.";
   }

   -        leaf router-id {
   +        leaf-list router-ids {
            type    yang:uuid;
	    description "UUID of router ";
	    }
   @@ -173,7 +172,7 @@ module neutronvpn {
   description "The UUID of the tenant that will own the subnet.";
   }

   -            leaf router-id {
   +            leaf-list router_ids {
	        type    yang:uuid;
	        description "UUID of router ";
	        }


Configuration impact
---------------------

This will be possible to configure one single VPN to two routers, with one IPv4, and the other one with IPv6.
This will be possible to associate one single VPN to two subnetworks, one IPv4, and the other one with IPv6.
There are some limitations in Neutron BGPVPN plugin that could be considered to be removed.

The following combinations is not considered ( no further testing will be done for that):

- associate VPN to two IPv4 routers, or dual stack router with IPv4 router

- associate VPN to two IPv6 routers, or dual stack router with IPv6 router ( or IPv6 router)

- associate VPN to two IPv6 router

- associate VPN to two IPv4 subnetworks ( or two IPv6 subnetworks)

- associate VPN to more than 2 subnetworks.

The following restriction is applied:

- associate VPN to more than 2 routers

Also, configuring DHCP servers to provision more than 1 IPv4 address and 1 IPv6 address) is not considered.
For instance, if one configure one DHCP server allocating two IPv6 addresses, the last IP address will be
only considered.


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

  - create two routers. each router will respectively be used for IPv4 and IPv6.
  - add an IPv4 interface to IPv4 router and link to IPv4 subnetwork
  - add an IPv6 interface to IPv6 router and link to IPv6 subnetwork

* Create the ComputeNode to DC-GW settings
  Because the transportation tunnel to the DC-GW is MPLS over GRE, the appropriate settings must be done.
  An ITM context is created whose termination endpoint is the DC-GW.
  Its nature is MPLS over GRE.

* create the DC-GW VPN settings

  - Create a VPN context. This context will have the same settings as in [7].
    Note that for the [6] case, the VPN should be slightly modified.
  - Some entries are injected into the DC-GW. Those entries are simulated
    In our case. both IPv4 and IPv6 prefixes will be injected in the same VPN.

* create the ODL VPN settings

  - Create a BGP context.
    This step permits to start QBGP module depicted in [8] and [9].
    ODL has an API that permits interfacing with that external software.
    The BGP creation context handles the following:

     o start of BGP protocol

     o declaration of remote BGP neighbor with the AFI/SAFI affinities
     In our case, VPNv4 and VPNv6 addresses families will be used).

  - create a VPN. this VPN will have a name and will contain the VRF settings.

* associate the VPN created to both routers

 - associate router1 to the VPN

 - associate router2 to the VPN

* Spawn a VM in the tenant network
   The VM will inherit from dual stack configuration

* Observation:
   The ODL FIB will dump both IPv4 and IP* create the ODL VPN settings

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------

CLI
---

A new option ``--afi`` and ``--safi``  will be added to command ``odl:bgp-vrf``:

::

   odl:bgp-vrf --rd <> --import-rt <> --export-rt <> --afi <1|2> --safi <value> add|del


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
  This is where the afi parameter should be used. The relationship with vpn-instances is not done yet.

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
