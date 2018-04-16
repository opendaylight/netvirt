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
message containing MP-BGP attributes required for reaching the VM.
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
IPv4 is prefered. This implies that any nexthop set for a prefix in FIB will be
IPv4.

Within BGP RIB table, for each L3VPN entry, the nexthop and label are key
elements for creating MPLS/GRE tunnel endpoints, and the prefix is used for
programming netvirt pipeline. When a VM is spawned, the prefix advertised by BGP
is 128 bits long and the nexthop carried along within UPDATE message is the ip
address of the DPN interface used for DCGW connection.
Since DCGW can be proprietary device, it may not support MPLS/GRE tunnel endpoint
setup according to its internal BGP table. A static configuration of such tunnel
endpoint may be required.

Use Cases
---------

Inter Datacenter IPv6 external connectivity for VMs spawned on tenant networks,
routes exchanged between BGP speakers using same Route Distinguisher.

Steps in both data centers :

  - Configure ODL and Devstack networking-odl for BGP VPN.
  - Create a tenant network with IPv6 subnet using GUA prefix or an
    admin-created-shared-ipv6-subnet-pool.
  - This tenant network is separated to an external network where the DCGW is
    connected. Separation between both networks is done by DPN located on compute
    nodes. The subnet on this external network is using the same tenant as an IPv4
    subnet used for MPLS over GRE tunnels endpoints between DCGW and DPN on
    Compute nodes. Configure one GRE tunnel between DPN on compute node and
    DCGW.
  - Create a Neutron Router and connect its ports to all internal subnets that
    will belong to the same L3 BGPVPN identified by a Route Distinguisher.
  - Start BGP stack managed by ODL, possibly on same host as ODL.
  - Create L3VPN instance.
  - Associate the Router with the L3VPN instance.
  - Spawn VM on the tenant network, L3 connectivity between VMs located on
    different datacenter sharing same Route Distinguisher must be successful.

When both data centers are set up, there are 2 use cases per data center:

  - Traffic from DC-Gateway to Local DPN (VMS on compute node)
  - Traffic from Local DPN to DC-Gateway

Proposed change
===============

ODL Controller would program the necessary pipeline flows to support IPv6
North South communication through MPLS/GRE tunnels out of compute node.

BGP manager would be updated to process BGP RIB when entries are IPv6 prefixes.

FIB manager would be updated to take into acount IPv6 prefixes.

Thrift interface between ODL and BGP implementation (Quagga BGP) must be
enhanced to support new AFI=2. Thrift interface will still carry IPv4 Nexthops,
and it will be the Quagga duty to transform this IPv4 Nexthop address into an
IPv4-mapped IPv6 address in every NLRI fields. Here is the new api proposed :

::

 enum af_afi {
     AFI_IP = 1,
     AFI_IPV6 = 2,
 }
 i32 pushRoute(1:string prefix, 2:string nexthop, 3:string rd, 4:i32 label,
               5:af_afi afi)
 i32 withdrawRoute(1:string prefix, 2:string rd, 3:af_afi afi)
 oneway void onUpdatePushRoute(1:string rd, 2:string prefix,
                               3:i32 prefixlen, 4:string nexthop,
                               5:i32 label, 6:af_afi afi)
 oneway void onUpdateWithdrawRoute(1:string rd, 2:string prefix,
                                   3:i32 prefixlen, 4:string nexthop,
                                   5:af_afi afi)
 Routes getRoutes(1:i32 optype, 2:i32 winSize, 3:af_afi afi)

BGP implementation (Quagga BGP) announcing (AFI=2,SAFI=128) capability as well
as processing UPDATE messages with such address family. Note that the required
changes in Quagga is not part of the design task covered by this blueprint.

Pipeline changes
----------------

Regarding the pipeline changes, we can use the same BGPVPNv4 pipeline
(Tables Dispatcher (17), DMAC (19), LFIB (20), L3FIB (21), and NextHop Group
tables) and enhance those tables to support IPv6 North-South communication
through MPLS/GRE.

Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Classifier Table (0) =>
| LFIB Table (20) ``match: tun-id=mpls_label set vpn-id=l3vpn-id, pop_mpls label, set output to nexthopgroup-dst-vm`` =>
| NextHopGroup-dst-vm: ``set-eth-dst dst-mac-vm, reg6=dst-vm-lport-tag`` =>
| Lport Egress Table (220) ``Output to dst vm port``

Please note that ``vpn-subnet-gateway-mac-address`` stands for MAC address of
the neutron port of the internal subnet gateway router.

Traffic from Local DPN to DC-Gateway (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Classifier Table (0) =>
| Lport Dispatcher Table (17) ``match: LportTag l3vpn service: set vpn-id=l3vpn-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac l3vpn service: set vpn-id=l3vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=ext-ip-address set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>

Please note that ``router-internal-interface-mac`` stands for MAC address of
the neutron port of the internal subnet gateway router.

Yang changes
------------

Changes will be needed in ``ebgp.yang`` to start supporting IPv6 networks
advertisements.


EBGP YANG changes
~~~~~~~~~~~~~~~~~
A new leaf afi will be added to container ``networks``

.. code-block:: none
   :caption: ebgp.yang

   list networks {
       key "rd prefix-len";

       leaf rd {
             type string;
       }

       leaf prefix-len {
             type string;
       }

       leaf afi {
             type uint32;
             mandatory "false";
       }

       leaf nexthop {
             type inet:ipv4-address;
             mandatory "false";
       }

       leaf label {
             type uint32;
             mandatory "false";
       }
   }

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
Impact on scaling inside datacenter essentially grow with the number of VM
connected to subnets associated with the L3VPN.
Since Globally Unique Address are used and there is no NAT involved in the
datapath, it implies prefixes advertised are all /128.
At the end, it means that every prefix advertised will have its entry
in BGP RIB of all ODL controllers and DCGW involved in L3VPN (ie all bgp aware
equipment will handle all prefixes advertised wihtin a Route Distinguisher).

This may imply BGP table with very high number of entries. This also implies a
high number of entries in ODL routing table and equivalent number of flows
inserted in OVS, since prefix advertised add matching ip destination in OVS
tables.

This fact also impact the scaling of the BGP speaker implementation (Quagga
BGP) with many thousands of BGPVPNv4 and BGPVPNv6 prefixes (as much as number
of spawned VMs) with best path selection algorithm on route updates, graceful
restart procedure, and multipath.

Targeted Release
-----------------
Carbon

Alternatives
------------
None

Usage
=====

* Configure MPLS/GRE tunnel endpoint on DCGW connected to public-net network

* Configure neutron networking-odl plugin

* Configure BGP speaker in charge of retrieving prefixes for/from data center
  gateway in ODL through the set of vpnservice.bgpspeaker.host.name in
  etc/custom.properties. No REST API can configure that parameter.
  Use config/ebgp:bgp REST api to start BGP stack and configure VRF, address
  family and neighboring

::

 POST config/ebgp:bgp
 {
     "ebgp:as-id": {
           "ebgp:stalepath-time": "360",
           "ebgp:router-id": "<ip-bgp-stack>",
           "ebgp:announce-fbit": "true",
           "ebgp:local-as": "<as>"
     },
     "ebgp:vrfs": [
      {
        "ebgp:export-rts": [
          "<export-rts>"
        ],
        "ebgp:rd": "<RD>",
        "ebgp:import-rts": [
          "<import-rts>"
        ]
      }
    ],
    "ebgp:neighbors": [
      {
        "ebgp:remote-as": "<as>",
        "ebgp:address-families": [
          {
            "ebgp:afi": "2",
            "ebgp:peer-ip": "<neighbor-ip-address>",
            "ebgp:safi": "128"
          }
        ],
        "ebgp:address": "<neighbor-ip-address>"
      }
    ],
 }

* Configure BGP speaker on DCGW to exchange prefixes with ODL BGP stack. Since
  DCGW should be a vendor solution, the configuration of such equipment is out of
  the scope of this specification.


* Create an internal tenant network with an IPv6 (or dual-stack) subnet and
  connect ports.

::

 neutron net-create private-net
 neutron subnet-create private-net 2001:db8:0:2::/64 --name ipv6-int-subnet
 --ip-version 6 --ipv6-ra-mode slaac --ipv6-address-mode slaac
 neutron port-create private-net --name port1_private1

* Create a router and associate it to internal subnets.

::

 neutron router-create router1
 neutron router-interface-add router1 ipv6-int-subnet

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

* Create MPLSoGRE tunnel between DPN and DCGW

::

 POST /restconf/operations/itm-rpc:add-external-tunnel-endpoint
 {
   "itm-rpc:input": {
     "itm-rpc:destination-ip": "dcgw_ip",
     "itm-rpc:tunnel-type": "odl-interface:tunnel-type-mpls-over-gre"
   }
 }

* Spawn a VM in the tenant network

::

 nova boot --image <image-id> --flavor <flavor-id> \
      --nic net-id=port1_private1_uuid VM1

* Dump ODL BGP FIB

::

 GET /restconf/config/odl-fib:fibEntries
 {
   "fibEntries": {
     "vrfTables": [
       {
         "routeDistinguisher": <rd-uuid>
       },
       {
         "routeDistinguisher": <rd>,
         "vrfEntry": [
           {
             "destPrefix": <IPv6_VM1/128>,
             "label": <label>,
             "nextHopAddressList": [
               <DPN_IPv4>
             ],
             "origin": "l"
           },
         ]
       }
     ]
   }
 }


Features to Install
-------------------
odl-netvirt-openstack

REST API
--------

CLI
---

A new option ``--afi`` will be added to command ``odl:bgp-network``:

.. code-block:: none

 opendaylight-user@root>
 odl:bgp-network --prefix 2001:db8::1/128 --rd 100:1 --nexthop 192.168.0.2
                 --label 700 --afi 2 add/del


Implementation
==============

Assignee(s)
-----------
Primary assignee:
  Julien Courtat <julien.courtat@6wind.com>

Other contributors:
  Noel de Prandieres <prandieres@6wind.com>
  Valentina Krasnobaeva <valentina.krasnobaeva@6wind.com>
  Philippe Guibert <philippe.guibert@6wind.com>

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
Quagga from 6WIND is publicly available at the following url

 * https://github.com/6WIND/quagga
 * https://github.com/6WIND/zrpcd

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

[4] `Spec to support IPv6 North-South support for Flat/VLAN Provider Network.
<https://git.opendaylight.org/gerrit/#/q/topic:ipv6-cvr-north-south>`_

[5] `BGP-MPLS IP Virtual Private Network (VPN) Extension for IPv6 VPN
<https://tools.ietf.org/html/rfc4659>`_

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
