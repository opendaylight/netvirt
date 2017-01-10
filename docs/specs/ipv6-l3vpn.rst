.. contents:: Table of Contents
         :depth: 3

=============================================================================
IPv6 Inter-DC L3 North-South connectivity using L3VPN provider network types.
=============================================================================

https://git.opendaylight.org/gerrit/#/q/topic:ipv6-interdc-l3vpn

In this specification we will be discussing the high level design of
IPv6 Inter-Datacenter North-South connectivity support in OpenDaylight
using L3VPN provider network type use-case.

Problem description
===================

Provide IPv6 connectivity to virtual machines located in different subnets
spread over multiple sites or Data center can be achieved through use of
Globally Unique Addresses and capacity to update enough routing tables to
forge a path between the two. Even if IPv6 is made to interconnect hosts
without the help of any NAT mechanisms, routing with the best efficienty
(shortest path) or policy (route weight, commercial relationships) must
be configured using only few parameters, automatically updating routes
for each VM spawned in new network.

Keep in mind that key aspects of L3VPN connectivity is Route Targets and
VPN-IPv6 address family.
Assuming an operator can configure both data center gateways with same
Route Distinguisher or set of imported Route Targets, each time a virtual
machine is spawned within a new subnet, it will trigger the send of a BGP UPDATE
message containing MP-BGP attributes requiered for reaching  the VM.
Such behavior can be achieved by configuring a neutron router a default gateway.


Only IPv6 Globally Unique Address (eg /128) are advertised, this is not a scaling
architecture since it implies as much routes to process as the number of spawned
VMs, but with such BGP routing information base, DCGW can select the Compute Node
to which a packet coming from the WAN should be forwarded to.


Following schema could help :

::

 +-----------------+                                         +-----------------+
 | +-------------+ |                                         | +-------------+ |
 | |VM1          | +---+                                 +---+ |VM1          | |
 | | Subnet C::4 | |   |              BGP table          |   | | Subnet A::2 | |
 | +-------------+ |OVS|              Prefix Subnet A::2 |OVS| +-------------+ |
 | +-------------+ | A |              Label L1           | A | +-------------+ |
 | |VM2          | |   |              Next Hop OVS A     |   | |VM2          | |
 | | Subnet D::4 | +-+-+                                 +-+-+ | Subnet B::2 | |
 | +-------------+ | |     +------+       +-------+        | | +-------------+ |
 +-----------------+ |     |      |       |       |        | +-----------------+
                     +-----+      |       |       +--------+
                           | DCGW +--WAN--+ DCGW  |
 +-----------------+ +-----+      |       |       +--------+ +-----------------+
 | +-------------+ | |     |      |       |       |        | | +-------------+ |
 | |VM3          | +-+-+   +------+       +-------+      +-+-+ |VM3          | |
 | | Subnet C::5 | |   |                                 |   | | Subnet A::3 | |
 | +-------------+ |OVS|                                 |OVS| +-------------+ |
 | +-------------+ | B |                                 | B | +-------------+ |
 | |VM4          | |   |                                 |   | |VM4          | |
 | | Subnet D::5 | +---+                                 +---+ | Subnet B::3 | |
 | +-------------+ |                                         | +-------------+ |
 +-----------------+                                         +-----------------+

BGP protocol and its MP-BGP extension would do the job as long as all BGP
speakers are capable of processing UPDATE messages containing VPN-IPv6 address
family, which AFI value is 2 and SAFI is 128. It is not required that BGP
speakers peers using IPv6 LLA or GUA, IPv4 will be used to peer speakers
together.

Opendaylight is already able to support the VPN-IPv4 address family (AFI=1,
SAFI=128), and this blueprint focuses on specific requirements to VPN-IPv6.

One big question concerns the underlying transport IP version used with MPLS/GRE
tunnels established between Data center Gateway (DCGW), and compute nodes
(CNs). There is one MPLS/GRE tunnel setup from DCGW to each Compute Node involved
in the L3VPN topology. Please note that this spec doesn't covers the case of
VxLAN tunnels between DCGW and Compute Nodes.

According to RFC 4659 §3.2.1, the encoding of the nexthop attribute in
MP-BGP UPDATE message differs if the tunneling transport version required is
IPv4 or IPv6. In this blueprint spec, the assumption of transport IP version of
IPv4 is prefered. This implies that any nexthop set using neutron or ODL Rest
API will be IPv4.

Within BGP RIB table, for each L3VPN entry, the nexthop and label are key
elements for creating MPLS/GRE tunnel endpoints, and the prefix is used for
programming netvirt pipeline.
Since DCGW can be proprietary device, it may not support MPLS/GRE tunnel endpoint
setup according to its internal BGP table. A static configuration of such tunnel
endpoint may be required.

Use Cases
---------

Inter Datacenter IPv6 external connectivity for VMs spawned on tenant networks,
routes exhanchanged between BGP speakers using same Route Distinguisher.

Steps in both data centers :

  - Configure ODL and Devstack networking-odl for BGP VPN.
  - Create a tenant network with IPv6 subnet using GUA prefix or an
    admin-created-shared-ipv6-subnet-pool.
  - Create an external network of type MPLS/GRE with an IPv4 subnet where the
    tunnel endpoint points to the nexthop advertised by external/physical Datai
    center gateway.
  - Create a Neutron Router and configure it within same VRF on both data
    center.
  - Associate the Router with the internal and external networks, default
    gateway for reaching external networks must be the Data center gateway.
  - Spawn VMs on the tenant networks, L3 connectivty between VMs located on
    different datacenter must be successful.

Proposed change
===============

ODL Controller would program the necessary pipeline flows to support IPv6
North South communication through MPLS/GRE tunnels out of compute node.

BGP manager would be updated to process BGP RIB when entries are IPv6 prefixes.

Thrift interface between ODL and BGP implementation (Quagga BGP) must be
enhanced to support new AFI=2 and IPv4-mapped IPv6 address in every nexthop
fields.

BGP implementation (Quagga BGP) announcing (AFI=2,SAFI=128) capability as well
as processing UPDATE messages with such address family.

Pipeline changes
----------------

Regarding the pipeline changes, we can use the same BGPVPNv4 pipeline
(Table BGPoMPLS DHCP(18), BGPoMPLS(38), IPv6(45) and Group tables) and enhance
those tables to support IPv6 North-South communication through MPLS/GRE.

Any feedback would be very much appreciated.

Based on the review comments, I will share the pipeline changes in the next
patch.

Yang changes
------------
None

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
Impact on scaling essentially rely on IPv6 North-South implementation
performances.
In case of distributed routing, prefixes advertised are /128, meaning that every
VM spawned on a compute node will have its entry in BGP RIB of ODL controller
and DCGW. This may imply BGP table with very high number of entries.

This fact also impact the scaling of the BGP speaker implementation (Quagga
BGP) with many thousands of BGPVPNv4 and BGPVPNv6 prefixes (as much as number
of spawned VMs) with best path selection algorithm on route updates, graceful
restart procedure, and multipath. Since there is no NAT involved in the solution,
there is less scaling issues using IPv6 Globally Unique Addresses than for IPv4.

Targeted Release
-----------------
Carbon

Alternatives
------------
None

Usage
=====

* Configure MPLS/GRE tunnel endpoint on DCGW connected to public-net network

* Configure networking-odl plugin with BGPVPN feature through the set of
  NETWORKING_BGPVPN_DRIVER="BGPVPN:OpenDaylight:networking_bgpvpn.neutron.services.
  service_drivers.opendaylight.odl.OpenDaylightBgpvpnDriver:default" in devstack
  local.conf file.

* Configure BGP speaker in charge of retrieving prefixes for/from data center
  gateway in ODL through the set of vpnservice.bgpspeaker.host.name in
  etc/custom.properties.

* Create an external FLAT/VLAN network with an IPv6 (or dual-stack) subnet.

::

 neutron net-create public-net -- --router:external --is-default
 --provider:network_type=flat --provider:physical_network=public

 neutron subnet-create --ip_version 6 --name ipv6-public-subnet
 --gateway <LLA-of-external-ipv6-gateway> --ipv6-address-mode slaac
 <public-net-uuid> 2001:db8:0:1::/64

* Create an internal tenant network with an IPv6 (or dual-stack) subnet.

::

 neutron net-create private-net
 neutron subnet-create --name ipv6-int-subnet --ip-version 6
 --ipv6-ra-mode slaac --ipv6-address-mode slaac private-net 2001:db8:0:2::/64

* Manually configure a downstream onlink route in the external IPv6 gateway
  for the IPv6 subnet "2001:db8:0:2::/64" on the interface that connects to
  the external public-net.

::

 Example (on Linux host acting as an external IPv6 gateway):
 ip -6 route add 2001:db8:0:2::/64  scope link dev <interface-on-public-net>

* Create a router and associate the external and internal subnets.

::

 neutron router-create router1
 neutron router-gateway-set router1 public-net
 neutron router-interface-add router1 ipv6-int-subnet

* Spawn a VM in the tenant network

::

 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net> VM1


* Use neutronvpn:createL3VPN REST api to create L3VPN

::

 POST /restconf/operations/neutronvpn:createL3VPN
 
 {
    "input": {
       "l3vpn":[
          {
             "id":"vpnid_uuid",
             "name":"vpn1",
             "route-distinguisher": [100:1],
             "export-RT": [100:1],
             "import-RT": [100:1],
             "tenant-id":"tenant_uuid"
          }
       ]
    }
 }

* Associate L3VPN To Routers

::

 POST /restconf/operations/neutronvpn:associateRouter
 
 {
   "input":{
      "vpn-id":"vpnid_uuid",
      "router-id":[ "router_uuid" ]
    }
 }

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------

CLI
---


Implementation
==============

Assignee(s)
-----------
Primary assignee:
  Julien Courtat <julien.courtat@6wind.com>

Other contributors:
  TBD

Work Items
----------

* Implement necessary APIs to allocate a transport over IPv6 requirement
  configuration for a given Route Target as the primary key.
* Support of BGPVPNv6 prefixes within MD-SAL. Enhance RIB-manager to support
  routes learned from other bgp speakers, [un]set static routes.
* BGP speaker implementation, Quagga BGP, to support BGPVPN6 prefixes exchanges
  with other BGP speakers (interoperability), and thrift interface updates.
* Program necessary pipeline flows to support IPv6 to MPLS/GRE (IPv4) communication.

Dependencies
============
None

Testing
=======

Unit Tests
----------
Unit tests provided for the BGPVPNv4 versions will be enhanced to also support
BGPVPNv6. No additional unit tests will be proposed.

Integration Tests
-----------------
TBD

CSIT
----
CSIT provided for the BGPVPNv4 versions will be enhanced to also support
BGPVPNv6. No additional CSIT will be proposed.


Documentation Impact
====================
Necessary documentation would be added on how to use this feature.

References
==========
[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

[3] http://docs.openstack.org/developer/networking-bgpvpn/overview.html

[4] `IPv6 Distributed Router for Flat/VLAN based Provider Networks.
<https://git.opendaylight.org/gerrit/#/q/topic:ipv6-distributed-router>`_

[5] `BGP-MPLS IP Virtual Private Network (VPN) Extension for IPv6 VPN
<https://tools.ietf.org/html/rfc4659>`_

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
