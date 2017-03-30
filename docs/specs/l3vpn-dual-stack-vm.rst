.. contents:: Table of Contents
         :depth: 3

=====================================
Dual Stack VM support in OpenDaylight
=====================================

https://git.opendaylight.org/gerrit/#/q/topic:l3vpn-dual-stack-vms

In this specification we will introduce a support of basic L3 forwarding for dualstack VMs
connectivity over L3 in NetVirt. Dualstack VM is a virtual
machine that has at least two IP addresses with different ethertypes: IPv4 address
and IPv6 address.

In addition to this, the specification ensures initial support of dualstack VMs
inside L3 BGPVPN.  L3 forwarding for dualstack VMs connectivity inside L3 BGPVPN
will be provided for the following variations of L3 BGPVPN:
A. L3BGPVPN constructed purely using networks;
B. L3BGPVPN constructed purely using a router;
C. L3BGPVPN constructed using multiple networks and a router.

Problem description
===================

As a dualstack VM, we assume a VM which has one Neutron Port, i.e. one VNIC,
that inherits two IPs addresses with different ethertypes: one IPv4 address and
one IPv6 address. We also will use in this document a term singlestack VM to
describe a VM, which VNIC possesses either IPv4 or IPv6 address, but not both
simultaneously.

So, dualstack VM has two IP addresses with different ethertypes. This could be
achieved by two ways:

1. VM was initially created with one VNIC, i.e. one Neutron Port from network
with IPv4 subnet. Second VNIC, corresponded to a Neutron Port from another
network with IPv6 subnet, was added to this machine after its creation.

2. VM has one Neutron Port from a network, which contains 2 subnets: IPv4 subnet
and IPv6 subnet.

OpenDaylight has already provided a support for the first way, so this use-case
is not in the scope of the specification.  For the second way the specification
doesn't intend to cover a use-case when, Neutron Port will possess several IPv4
and several IPv6 addresses. More specifically this specification covers only the
use-case, when Neutron Port has only one IPv4 and one IPv6 address.

Since there are more and more services that use IPv6 by default, support of
dualstack VMs is important. Usage of IPv6 GUA addresses has increased during
the last couple years. Administrators want to deploy services, which will be
accessible from traditional IPv4 infrastructures and from new IPv6 networks as
well.

Dualstack VM should be able to connect to other VMs, be they are of IPv4 (or)
IPv6 ethertypes.
So in this document we can handle following use cases:

- (1) Intra DC , Inter-Subnet L3 Forwarding support for DualStack VM;

- (2) Intra DC, Inter-Subnet  L3 Forwarding support for DualStack VM that is in
  an L3 BGPVPN.

For setups (1) and (2), we already know, that there are some issues that prevent
doing a testing:

Current VPN allocation scheme picks up only the first IP
address of dualstack VM Neutron Port. That means that the VPN allocation scheme
will not apply both IPv4 and IPv6 network configurations for a port. For
example, if the first allocated IP address is IPv4 address, then VPN allocation
scheme will only apply to IPv4 network configuration. The second IPv6 address
will be ignored.

Separate VPN connectivity for singlestack VMs within IPv4 subnetworks and within
IPv6 subnetworks is already achieved by using distinct VPNs. What we want is to
support a case, when the same VPN will handle both IPV4 and IPv6 VM
connectivity.

Regarding the problem description above, we would propose to implement in
OpenDaylight two following solutions:

1. "two-router" solution

One router belongs to IPv4 subnetwork, another one belongs to IPv6 subnetwork.
A single VPN would be associated to both routers.  As consequence, IPv4 and IPv6
FIB entries would be gathered in the same VPN.  Separation of IPv4 and IPv6
neutron router configurations in this case permits to have an external network
access.

2. "dualstack-router" solution

The router with only 2 ports (one port for IPv4 subnet and another one for IPv6
subnet) is attached to L3VPN instance.  We do not support in this configuration
external network access. However, this configuration will be useful for
inter-subnet routing.

In each described configuration, by extension, the following should also be
possible: * In addition to VM VPN allocation scheme, extra-routes and subnet
configuration would benefit from it.  * both IPv4 and IPv6 entries would belong
to the same VPN.

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

We identify there 2 subnets:
 - 1 IPv4 subnet: a.b.c.x/j ( aka IPv4 Subnet 1)
 - 1 IPv6 subnet: C::x/64 ( aka IPv6 Subnet 1)
Each VM will receive IPs from these two defined subnets.

Following schemes stand for conceptual representation of used neutron
configurations for each proposed solution.

::

    setup 1: two router solution for dualstack VM

          +-----+     +---------------+
          | VM1 |-----| Network N     |
          +-----+  +--|               |                       +---------------+
                   |  +---------------+    +--------------+   | Network N3    |
                   |  | Subnet A IPv4 |----| Router 1     |---+---------------+
                   |  +---------------+    +--------------+   | Subnet C IPv4 |
                   |  | Subnet B IPv6 |           |           +---------------+
                   |  +---------------+           |
                   |          |                   |
                   |  +---------------+           |
                   |  | Router 2      |    +---------------+
          +-----+  |  +---------------+    | Subnet E IPv4 |
          | VM3 |--+          |            +---------------+
          +-----+     +---------------+    | Network N2    |
                      | Subnet D IPv6 |    +---------------+
                      +---------------+
                      | Network N1    |
                      +---------------+

Network N gathers 2 subnetworks, subnet A IPv4 and subnet B IPv6. This makes
possible to create Neutron Ports, which will have 2 IP addresses and whose
attributes will inherit information (extraroutes, etc) from these 2 subnets A
and B. VM, which will launched with such Neutron Port will use IPv4 and an IPv6
addressing schemes.

Router1 and Router2 are connected to Subnet A and Subnet B respectively and will
be attached to a same VPN instance. Routers 1 and 2 can also have other ports,
but these ports must have the same ethertype as ports, which were added in
router, before it was attached to a VPN.  Assuming for the scheme above, that
Router1 has as a port Subnet A and then it was associated with a VPN. In this case
Router 1 can have Subnet E as a port, because it has the same ethertype IPv4 as
the already presented Subnet A. But we can't add as a port to this router Subnet
D.  See the chapter "Configuration impact" for more details.

1. Router1 can have more than one IPv4 subnet attached to it, either before the
   first VM was fired.

2. Router1 can have Subnet E attached as a port, when Subnet A was already
   attached to this router.

::

    setup 2: single router solution for dualstack VM

           +-----+     +---------------+
           | VM1 |-----| Network N     |
           +-----+  +--|               |
                    |  +---------------+         +----------+
                    |  | Subnet A IPv4 |---------|          |
                    |  +---------------+         | Router 1 |
                    |  | Subnet B IPv6 |---------|          |
                    |  +---------------+         +----------+
           +-----+  |
           | VM3 |--+
           +-----+

Network N gathers 2 subnetworks, subnet A IPv4 and subnet B IPv6. This makes
possible to create Neutron Ports, which will have 2 IP addresses and whose
attributes will inherit information (extraroutes, etc) from these 2 subnets A
and B. VM, which will launched with such Neutron Port will use IPv4 and an IPv6
addressing schemes.

Router 1 is connected to Subnet A and Subnet B, and it will be attached to a VPN
instance X. Other subnets can be added to Router 1, but prefixes of these subnets
will not be advertised in the VPN instance X. See the chapter
"Configuration impact" for more details.

It is valid for both schemes: in dependency of chosen ODL configuration, either
ODL, or Neutron Dhcp Agent will provide IP addresses for launched VMs. Please
note, that currently DHCPv6 is supported only by Neutron Dhcp Agent. ODL
provides SLAAC IPv6 address allocation for dualstack VMs launched in basic IPv6
subnetworks and dualstack VMs inside L3 BGPVPN

Known Limitations
~~~~~~~~~~~~~~~~~
Currently, from the BGPVPN point of view, the BGPVPN code has been locked to
prevent to associate more than one router to a single VPN. This is a limitation
was made to obtain a compatibility with ODL.

From Netvirt point of view, there are some limitations as well:

- We can not associate VPN port with both IPv4 and IPv6 Neutron Port addresses
  at the same time. Currently, any first Neutron Port IP address is using to
  create a VPN port instance. If a Neutron Port possesses multiple IP Addresses,
  regardless of ethertype, this port might not work properly with ODL.

- It is not possible to associate one VPN instance with two different routers.
  Despite the fact, that configuration permits this association, it is refused by
  neutronvpn submodule, when running associateRouter or associateNetwork API
  commands.  Note, that using two different routers implies that we use dedicated
  VPN router ID instance for each router, and current VPN instance yang model
  doesn't support this as well.

Use Cases
---------

There is no change in the use cases described in [6] and [7], except that the
single L3VPN instance serves both IPv4 and IPv6 subnets.

Inter DC Access
~~~~~~~~~~~~~~~

1. "two-router" solution

IPv4 subnet Subnet A is added as a port in Router 1, IPv6 subnet Subnet B is
added as a port in Router 2. The same L3VPN instance will be associated with
both Router 1 and Router 2.

The VPN will distinguish ethertype of router ports and will create appropriate
FIB entries associated to its own VPN entry, so IPv4 and IPv6 enries will be
gathered in the same VPN.

2. "dualstack-router" solution

IPv4 subnet Subnet A is added as a port in Router 1, IPv6 subnet Subnet B is
added as a port in Router 1 as well. L3VPN instance will be associated with
Router 1.

The VPN will distinguish ethertype of routers ports and will create appropriate
FIB entries associated to its own VPN entry as well. Appropriate BGP VRF context
for IPv4 or IPv6 subnets will be also created.

External Internet Connectivity
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. "two-router" solution

In the IPv4 router: the IPv4 subnet will obtain connectivity to bgpvpn external
network using SNAT.

In the IPv6 router: NAT feature should be disabled for transparent forwarding of
IPv6 traffic to bgpvpn external network.

Advantage of "two-router" solution is that we can disable Internet access over
IPv6 (or) over IPv4, when this is required for some associated with L3BGP VPN
subnetworks.

2. "dualstack-router" solution

External connectivity for "dualstack-router" solution with bgpvpn external
network is achieved by enhancing [6].

Disadvantage of this solution is a lack of flexibility, which we've noticed for
"two-router" solution, when administrator can explicitly disable Internet access
for specific ether-types of traffic.

Proposed change
===============

All changes we can split in two main parts.

1. Distinguish IPv4 and IPv6 VRF tables with the same RD/iRT/eRT

1.1 Changes in neutronvpn

To support a pair of IPv4 and IPv6 prefixes for each launched dualstack VM we
need to obtain information about subnets, where dualstack VM was spawned and
information about extraroutes, enabled for these subnets.  Obtained information
will be stored in vmAdj and erAdjList objects respectively.  These objects are
attributes of created for new dualstack VM VPN port instance.  Created VPN port
instance will be stored as part of already existed VPN node instance in MDSAL
DataStore.

When we update VPN instance node (associate/dissociated router or network), we
need to provide information about ethertype of new attached/detached subnets,
hence, Neutron Ports. New argument flags ipv4On and ipv6On will be introduced
for that in NeutronvpnManager function API, called to update current VPN
instance (updateVpnInstanceNode() method).  UpdateVpnInstanceNode() method is
also called, when we create a new VPN instance.  So to provide appropriate
values for ipv4On, ipv6On flags we need to parce subnets list.  Then in dependency
of ipv4On, ipv6On flags values we will set either
Ipv4Family attribute for the new VPN instance or Ipv6Family attribute, or both
attributes.  Ipv4Family, Ipv6Family attributes allow to create ipv4 or/and ipv6
VRF context for underlayed vpnmanager and bgpmanager APIs.

1.2. Changes in vpnmanager

When VPN instance is created or updated, VRF tables must be created for QBGP as
well. What we want, is to introduce separate VRF tables, created according to
IPv4Family/IPv6Family VPN attributes, i.e. we want to distinguish IPv4
and IPv6 VRF tables, because this will bring flexibility in QBGP. For example,
if QBGP receives an entry IPv6 MPLSVPN on a router, which is expecting to
receive only IPv4 entries, this entry will be ignored. The same for IPv4 MPLSVPN
entries respectively.

So, for creating VrfEntry objects, we need to provide information about VPN
instance ethertype (Ipv4Family/Ipv6Family attribute), route distinguishers list,
route imports list and route exports lists (RD/iRT/eRT). RD/iRT/eRT lists will
be simply obtained from subnetworks, attached to the chosen VPN. Presence of
IPv4Family, IPv6Family in VPN will be translated in following
VpnInstanceListener class attributes: afiIpv4, afiIpv6, safiMplsVpn, safiEvpn,
which will be passed to addVrf() and deleteVrf() bgpmanager methods for
creating/deleting either IPv4 VrfEntry or IPv6 VrfEntry objects.

RD/iRT/eRT lists will be the same for both IPv4 VrfEntry and IPv6 VrfEntry in
case, when IPv4 and IPv6 subnetworks are attached to the same VPN.

1.3  Changes in bgpmanager

In bgpmanager we need to change signatures of addVrf() and deleteVrf() methods,
which will trigger signature changes of underlying API methods  addVrf() and
delVrf() from BgpConfigurationManager class.

This allows BgpConfigurationManager class to create needed IPv4 VrfEntry and
IPv6 VrfEntry objects with appropriate AFI and SAFI values and finally pass this
appropriate AFI and SAFI values to BgpRouter.

BgpRouter represents client interface for thrift API and will create needed IPv4
and IPv6 VRF tables in QBGP.

1.4 Changes in yang model

To support new attributes AFI and SAFI in bgpmanager classes, it should be added
in ebgp.yang model:

    leaf afi {
      type uint32;
      mandatory "true";
    }
    leaf safi {
      type uint32;
      mandatory "true";
    }

1.5 Changes in QBGP thrift interface

To support separate IPv4 and IPv6 VRF tables in QBGP we need to change
signatures of underlying methods addvrf() and delvrf() in thrift API as well.
They must include the address family and subsequent address families
informations:

::

 enum af_afi {
     AFI_IP = 1,
     AFI_IPV6 = 2,
   }

  i32 addVrf(1:layer_type l_type, 2:string rd, 3:list<string> irts, 4:list<string> erts,
             5:af_afi afi, 6:af_safi afi),
  i32 delVrf(1:string rd, 2:af_afi afi, 3:af_safi safi)

2. Support of two routers, attached to the same VPN

2.1 Changes in neutronvpn

"two-router" solution assumes, that all methods, which are using to create,
update, delete VPN interface or/and VPN instance must be adapted to a case, when
we have a list of subnetworks and/or list of router IDs to attach.  Due to this,
appropriate changes need to be done in nvpnManager method APIs.

To support "two-router" solution properly, we also should check, that we do not
try to associate to VPN a router, that was already associated to that VPN.
Attached to VPN router list must contain maximum 2 router IDs. Routers, which
IDs are in the list must be only singlestack routers. More information about
supported router configurations is available below in chapter "Configuration
Impact".

For each created in dualstack network Neutron Port we take only the last
received IPv4 address and the last received IPv6 address. So we also limit a
length of subnets list, which could be attached to a VPN, to two elements. (More
detailed information about supported network configurations is available below
in chapter "Configuration Impact".) Two corresponding Subnetmap objects will be
created in NeutronPortChangeListener for attached subnets. A list with created
subnetmaps will be passed as argument, when createVpnInterface method will be called.

2.2 Changes in vpnmanager

VpnMap structure must be changed to support a list with router IDs.  This change
triggers modifications in all methods, which retry router ID from VpnMap object.


2.3 Changes in yang model

To provide change in VpnMap, described above, we need to modify following yang
files.

2.3.1 neutronvpn.yang

- Currently, container vpnMap holds one router-id for each VPN instance ID. A
  change consists in replacing one router-id leaf by a leaf-list of router-ids.
  Obviously, no more than two router-ids will be used.

- Container vpnMaps is used internally for describing a VPN. Change router-id
  leaf by router-ids leaf-list in this container is also necessary.

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
            description "UUID router list";
        }
   @@ -173,7 +172,7 @@ module neutronvpn {
   description "The UUID of the tenant that will own the subnet.";
   }

   -            leaf router-id {
   +            leaf-list router_ids {
                type    yang:uuid;
                description "UUID router list";
            }

2.3.2 l3vpn.yang

Change leaf vpn-instance-name to leaf-list vpn-router-ids for supporting
"two-router" solution.

::

    --- a/vpnservice/vpnmanager/vpnmanager-api/src/main/yang/l3vpn.yang
    +++ b/vpnservice/vpnmanager/vpnmanager-api/src/main/yang/l3vpn.yang
    @@ -795,21 +795,21 @@

    list vpn-interface  {
    key "name";
    max-elements "unbounded";
    min-elements "0";
    leaf name {
        type leafref {
            path "/if:interfaces/if:interface/if:name";
        }
    }
    leaf vpn-instance-name {
        leaf-list vpn-instance-name {
            type string {
                length "1..40";
            }
    }
    leaf dpn-id {
        type uint64;
    }
    leaf scheduled-for-remove {
        type boolean;
    }

Pipeline changes
----------------

There is no change in the pipeline, regarding the changes already done in [6]
and [7].

Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The DC-GW has the information, that permits to detect an underlay destination IP
and MPLS label for a packet coming from the Internet or from anotherr DC-GW.


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

Please, note that ``router-internal-interface-mac`` stands for MAC address of
the internal subnet gateway router port.

Configuration impact
---------------------

1. Limitations for router configurations

This will be possible to configure one single VPN with two associated routers:
"two-router" solution. Routers in "two-router" solution must be singlestack
routers.  If VPN has already associated with a one singlestack router and we try
to associate this VPN again with a dualstack router, exception will be raised.

Assume, that IPv4 singlestack router is already associated to a VPN, and it has
more than one IPv4 subnet's ports. If we try to add IPv6 subnet port to this
router, i.e. if we try to make this router dualstack, exception will be raised.
The same restriction is valid for IPv6 singlestack routers.

This specification authorizes only the case, when a subnet port is added to a
singlestack router, which has already only one subnet's port and which is
already associated to a VPN. Single stack router in this case becomes dualstack
router. This router configuration is allowed by current specification.

Dualstack router, associated to a VPN can have only 2 subnets ports (IPv4 and
IPv6).  If we try to add to this router one more subnet's port, exception will
be raised.

Maximum number of associated singlestack routers, attached to a one VPN is
limited to two.  Maximum number of associated dualstack routers, attached to a
one VPN is limited to one.

2. Limitations for subnetworks configurations

There are some limitations applied to associateNetwork command.  If we try to
associate a network with VPN, and this network has more than two subnetworks
with different ethertypes, exception will be raised.

If we try to create a new subnet in a networt, already associated to a VPN, and
in this network we already have two subnets with different port ethertypes,
exception will be raised as well.

Maximum numbers of networks associated to a one VPN is limited to one.  Maximum
numbers of different ethertype subnetworks associated to a one VPN is limited to
two.  Maximum numbers of singlestack subnetworks associated to a one VPN is not
limited.

This specification doesn't support the case, when DHCP service is configured to
provide more than one IPv4 or more than one IPv6 addresses to ports, which are
only in a single IPv4 or IPv6 subnetworks.  If this case will happen, only the
last IPv4 address and only the last IPv6 address from a Neutron Port list
addresses will be taken in account.

ECMP impact
------------
ECMP - Equal Cost multiple path.

ECMP feature is currently provided for Neutron BGPVPN networks and described in
the specification [10].  3 cases have been cornered to use ECMP feature for
BGPVPN usability.

- ECMP of traffic from DC-GW to OVS (inter-DC case)
- ECMP of traffic from OVS to DC-GW (inter-DC case)
- ECMP of traffic from OVS to OVS (intra-DC case)

In each case, traffic begins either at DC-GW or OVS node. Then it is sprayed to
end either at OVS node or DC-GW.

ECMP feature for Neutron BGPVPN networks was successfully (OK) tested with IPv4
L3VPN and IPv6 L3VPN (OK).  The dual stack VM connectivity should not be
affected by supporting ECMP.

We've included this chapter to remind, that code changes for supporting
dualstack VMs should be tested against ECMP scenario as well.

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

Assume, that in the same provider network we have OpenStack installed with 1 controller and 2 compute nodes.
OpenDaylight is installed in a separate node. DC-GW nodes is also in the same provider network.

* create networks

  - create Network N;
  - declare Subnetwork IPv4 for Network N;
  - declare Subnetwork IPv6 for Network N;
  - create two ports in Network N;
  - each port will inherit a dual IP configuration.

* create router

  - create two routers A and B, each router will be respectively used for IPv4 and IPv6 subnets;
  - add an IPv4 subnetwork as an interface to IPv4 router;
  - add an IPv6 subnetwork as an interface to IPv6 router.

* create transportation tunnel MPLS over GRE
  - create transportation tunnel config in Opendaylight and DC-GW nodes;
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
Quagga from 6WIND is available at the following urls:

 * https://github.com/6WIND/quagga
 * https://github.com/6WIND/zrpcd

Testing
=======

Unit Tests
----------
Some BGP VPNv4/v6 testing may have be done.
Complementary specification for other tests will be done.

Integration Tests
-----------------
TBD

CSIT
----
CSIT specific testing will be done to check dualstack VMs connectivity with
neutron network configurations for "two-router" and "dualstack-router" solutions.
Basically, IPv4 and IPv6 vpnservice functionality have to be validated by regression tests with a single BGPVRF.

Documentation Impact
====================
Necessary documentation would be added if needed.

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
