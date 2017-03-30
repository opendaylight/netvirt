.. contents:: Table of Contents
         :depth: 3

========================
L3VPN Dual Stack for VMs
========================

https://git.opendaylight.org/gerrit/#/q/topic:l3vpn-dual-stack-vms

In this specification we discuss the high level design of dualstack VM network
connectivity through BGP VPN. The specification proposes to implement
L3-forwarding support for dualstack VM in NetVirt. Dualstack VM is a virtual
machine that has at least 2 IP addresses with different Ethertypes: IPv4 address
and IPv6 address.

In addition to providing initial support of dualstack VM, this specification
ensures that dualstack network connectivity works with BGPVPNs.

Problem description
===================

As a dualstack VM, we assume a VM which has one neutron port, i.e. one VNIC,
that inherits two IPs addresses with different ethertypes: one IPv4 address and
one IPv6 address. We also will use in this document a term singlestack VM to
describe a VM, which VNIC possesses either IPv4 or IPv6 address, but not both
simultaneously.

So, dualstack VM has two IP addresses with different ethertypes. This could be
achieved by two ways:

1. VM was initially created with one VNIC, i.e. one neutron port from network
with IPv4 subnet. Second VNIC, corresponded to a neutron port from another
network with IPv6 subnet, was added to this machine after its creation.

2. VM has one neutron port from a network, which contains 2 subnets: IPv4 subnet
and IPv6 subnet.

The first way is not in the scope of this specification. For the second way we
don't want to discuss the use-case of having more than one IPv4 and more than
one IPv6 subnets in a one neutron network, so neutron port will possess several
IPv4 and several IPv6 addresses.

Since there are more and more services that use IPv6 by default, support of
dualstack VMs is important. Usage of IPv6 GUA addresses increases drastically
during the last years. Administrators want to deploy services, which will be
accessible from traditional IPv4 infrastructures and from new IPv6 networks as
well.

Dualstack VM should be able to connect to other VMs, whatever their location is.
So in this document we can handle following use cases:

- (1) inter DC, inter subnet (or intra subnet) using BGPVPN;

- (2) IPv4/IPv6 access to Internet using BGPVPN;

For setups (1) and (2), we already know, that there are some issues that prevent
doing a testing.

Dualstack is not supported across BGPVPN. This is the central problematic of
this specification.  Current VPN allocation scheme picks up only the first IP
address of dualstack VM neutron port. That means that the VPN allocation scheme
will not apply both IPv4 and IPv6 network configurations for a port.  For
example, if the first allocated IP address is IPv4 address, then VPN allocation
scheme will only apply to IPv4 network configuration. The second IPv6 address
will be ignored.

Separate VPN connectivity for singlestack VMs within IPv4 subnetworks and within
IPv6 subnetworks is already achieved by using distinct VPNs.  What we want is to
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
possible to create neutron ports, which will have 2 IP addresses and whose
attributes will inherit information (extraroutes, etc) from these 2 subnets A
and B. VM, which will launched with such neutron port will use IPv4 and an IPv6
addressing schemes.

Router1 and Router2 are connected to Subnet A and Subnet B respectively and will
be attached to a same VPN instance. Routers 1 and 2 can also have other ports,
but these ports must have the same ethertype as ports, which were added in
router, before it was attached to a VPN.  Assuming for the scheme above, that
Router1 has as a port Subnet A and then it was associated with VPN. In this case
Router 1 can have Subnet E as a port, because it has the same ethertype IPv4 as
the already presented Subnet A. But we can't add as a port to this router Subnet
D.  See the chapter "Configuration impact" for more details.

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
possible to create neutron ports, which will have 2 IP addresses and whose
attributes will inherit information (extraroutes, etc) from these 2 subnets A
and B. VM, which will launched with such neutron port will use IPv4 and an IPv6
addressing schemes.

Router1 is connected to Subnet A and Subnet B, and it will be attached to a VPN
instance. We can not add other ports to this router. See the chapter
"Configuration impact" for more details.

It is valid for both schemes: in dependency of chosen ODL configuration, either
ODL, or neutron dhcp-agent will provide the DHCP addresses allocation for
launched VMs. About DHCP allocation, note that currently DHCPv6 is supported
only by neutron dhcp-agent and not by ODL.

Known Limitations
~~~~~~~~~~~~~~~~~
Currently, from the BGPVPN point of view, the BGPVPN code has been locked to
prevent to associate more than one router to a single VPN. This is a limitation
was made to obtain a compatibility with ODL.

From Netvirt point of view, there are some limitations as well:

- We can not associate VPN port with both IPv4 and IPv6 neutron port addresses
  at the same time. Currently, any first neutron port IP address is using to
  create a VPN port instance.

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

2. "dualstack-router" solution

External connectivity for "dualstack-router" solution with bgpvpn external
network is achieved by enhancing [6].

Proposed change
===============

Quagga BGP and BGP Manager changes
----------------------------------

To support dualstack VMs, we have to modify thrift interface in BGP manager. The
thrift method signatures addvrf and delvrf must be changed to include the
address family and subsequent address family informations.  This is needed to
distinguish IPv4 and IPv6 vrf tables with the same RD/iRT/eRT.

::

 enum af_afi {
     AFI_IP = 1,
     AFI_IPV6 = 2,
   }

  i32 addVrf(1:layer_type l_type, 2:string rd, 3:list<string> irts, 4:list<string> erts,
             5:af_afi afi, 6:af_safi afi),
  i32 delVrf(1:string rd, 2:af_afi afi, 3:af_safi safi)

This will bring flexibility in QBGP. For example, if QBGP receives an entry IPv6
MPLSVPN on a router, that is expecting to receive only IPv4 entries, then this
entry will be ignored.  The same for IPv4 MPLSVPN entries respectively.

Appropriate changes are needed in signatures of these BgpConfigurator class
methods:

::
    addVrf(layer_type l_type, String rd, List<String> irts, List<String> erts, af_afi afi, af_safi safi)
    delVrf(String rd, af_afi afi, af_safi safi)


Netvirt Neutron changes
-----------------------

The change includes following enhancements:

- We should check, that we do not try to associate to VPN a router, that was
  already associated.

- Attached to L3VPN router list can contain maximum 2 routers.

- For each neutron port we take only the last received IPv4 address and the last
  received IPv6 address.  So IP addresses retrieved from a neutron port would be
  always limited to 2.

Change includes:

1. neutronvpn.yang: replace router-id leaf with a leaf-list in vpnMaps container
   and in vpn-instance grouping.  There is no need to change a part of
   neutronvpn.yang, which contains neutronvpn API, used by RPC commands.

2. Add router-ids list size check during configuration process. router-ids list
   size should be is always limited to two.

3. Subsequent changes for all java classes that use vpnMaps structure.

4. handleNeutronPortCreated() method is in charge of getting IPs addresses of
   created neutron port. Currently, it recuperates a whole list of IP addresses,
   but for creating new Subnetmap instance object it takes only the first list
   element. Caution is done to create only two Subnetmap objects, which are
   corresponding to the last neutron port IPv4 address, and the last neutron port
   IPv6 address.  Finally handleNeutronPortCreated() will create a one VPN port
   instance, but it will include Subnetmap objects for the last IPv4 and IPv6
   addresses.

5. When VPN port will be created, NeutronvpnManager has to create IPv4 and IPv6
   contexts for BGP Manager, in dependency of its Subnetmaps ethertypes. For
   example, if IPv4 Subnetmap entry exists in VPN port object instance, IPv4
   contexts will be created, that should trigger the creation of IPv4 VRF in BGP
   Manager and respectively for IPv6.

Netvirt VPNManager Changes
--------------------------
VpnMaps structure is used by VPN Manager.
The changes include following enhancements:

1. l3vpn.yang: change leaf vpn-instance-name to leaf-list vpn-router-ids to
   support "two-routers" solution.

2. modifications, lied to changes in l3vpn.yang.

3. modifications, lied to changes in neutronvpn.yang.

Pipeline changes
----------------

There is no change in the pipeline, regarding the changes already done in [6] and [7].

Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The DC-GW has the information that permits to detect an underlay destination IP
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
the neutron port of the internal subnet gateway router.

Yang changes
------------

1. ebgp.yang:

   add two parameters: afi and safi in vrfs list

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


2. neutronvpn.yang:

Two main changes are needed:

- Currently, container vpnMap holds one router-id for each VPN-ID. The change
  consists in replacing one router-id by a list of router-ids.  Obviously, only 2
  router-ids will be used.

- Container vpnMaps is used internally for describing a VPN. Change router-id by
  router-ids list in this container is necessary as well.

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

3. l3vpn.yang:

Change leaf vpn-instance-name to leaf-list vpn-router-ids to support
"two-routers" solution.

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
Complementary specification for other tests will be done

Integration Tests
-----------------
TBD

CSIT
----
CSIT specific testing will be done to check dualstack VMs connectivity with
neutron network configurations for "two-router" and "dualstack-router" solutions.
Basically, IPv4 and IPv6 vpnservice functionality have to pass regression checks with a single BGPVRF.

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
