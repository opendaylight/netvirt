.. contents:: Table of Contents
         :depth: 3

================================================================================
IPv6 DC-Internet L3 North-South connectivity using L3VPN provider network types.
================================================================================

https://git.opendaylight.org/gerrit/#/q/topic:ipv6-interdc-l3vpn

In this specification we will be discussing the high level design of
IPv6 Datacenter to Internet North-South connectivity support in OpenDaylight
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
Assuming an operator can configure data center gateways with a
Route Distinguisher dedicated to Internet connectivity and a set of imported
Route Targets, each time a virtual machine is spawned within a data center subnet
associated with that Route Distinguisher, it will trigger the send of a BGP UPDATE
message containing MP-BGP attributes requiered for reaching the VM outside the
datacenter.
Such behavior can be achieved by configuring a neutron router an internet public
VPN address.

Only IPv6 Globally Unique Address (eg /128) are advertised, this is not a scaling
architecture since it implies as much routes to process as the number of spawned
VMs, but with such BGP routing information base, DCGW can select the Compute Node
to which a packet coming from the WAN should be forwarded to.

This spec covers the case where a VM connects to a host located in the internet,
and the destination ip address of packets is not part of the list of advertised
prefixes (see spec for IPv6 Interdata center connectivity using L3VPN).


Following schema could help :

::

                                      OVS A flow:
                                      IP dst not in advertised list
                                      => use VPN with RD RDS
                                                 +-----------------+
                                                 | +-------------+ |
                                             +---+ |VM1          | |
                 BGP table                   |   | | Subnet A::2 | |
                 Prefix Subnet A::2          |OVS| +-------------+ |
 +-------+       Label L2                    | A | +-------------+ |
 |       |       Next Hop OVS A              |   | |VM2          | |
 | Host  |       RD RDS                      +-+-+ | Subnet B::2 | |
 +---+---+           +-------+                 | | +-------------+ |
     |               |       |                 | +-----------------+
     |               |       +-----------------+
     +--Internet-----+ DCGW  |
                     |       +-----------------+ +-----------------+
                     |       |                 | | +-------------+ |
                     +-------+               +-+-+ |VM3          | |
                                             |   | | Subnet A::3 | |
                                             |OVS| +-------------+ |
                                             | B | +-------------+ |
                                             |   | |VM4          | |
                                             +---+ | Subnet B::2 | |
                                                 | +-------------+ |
                                                 +-----------------+


Use Cases
---------

Datacenter IPv6 external connectivity to/from Internet for VMs spawned on tenant
networks.

Steps in data center :

  - Configure ODL and Devstack networking-odl for BGP VPN.
  - Create a tenant network with IPv6 subnet using GUA prefix or an
    admin-created-shared-ipv6-subnet-pool.
  - This tenant network is connected to an external network where the DCGW is
    connected. Separation between both networks is done by DPN located on compute
    nodes. The subnet on this external network is using the same tenant as an IPv4
    subnet used for MPLS over GRE tunnels endpoints between DCGW and DPN on
    Compute nodes. Configure one GRE tunnel between DPN on compute node and
    DCGW.
  - Create a Neutron Router and connect its ports to all internal subnets that
    will belong to the same L3 BGPVPN identified by a Route Distinguisher.
  - Start BGP stack managed by ODL, possibly on same host as ODL.
  - Create L3VPN instance.
  - Associate the Router with the L3VPN instance, default gateway for reaching
    external networks must be the Data center gateway.
  - Spawn VM and bind its network interface to a subnet, L3 connectivty between
    VM in datacenter and a host on WAN  must be successful.

When the data centers is set up, there are 2 use cases:

  - Traffic from Local DPN to DC-Gateway
  - Traffic from DC-Gateway to Local DPN (same use case as [6])

Proposed change
===============

Same as [6].
An additionnal processing in DPN is required. When a packet is received by a
neutron router associated with L3VPN, with destination mac address is the subnet
gateway mac address, and the destination ip is not in the FIB (default gateway)
of local DPN, then the packet should enter the L3VPN netvirt pipeline.
The VPN id choosen is the one configured to provide access to Internet.

Pipeline changes
----------------

Regarding the pipeline changes, we can use the same pipeline changes as [6]
(Table L3FIB (21), Internal Tunnel (36), IPv6(45) and Group tables).


Traffic from Local DPN to DC-Gateway (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Classifier Table (0) =>
| Lport Dispatcher Table (17) =>
| DMAC Service Filter (19) ``match: dst-mac=vpn-subnet-gateway-mac-address l3vpn service: set vpn-id=l3vpn-id`` =>
| IPv6 (45) `` no match: dst-ip found in FIB as remote: set vpn-id=l3vpn-id-for-internet`` =>
| Lport Dispatcher Table (17) =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id-for-internet: set tun-id=mpls_label_for_internet`` =>
| GRE Port


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
Same scaling limitation as [6].
Impact on scaling inside datacenter essentially grow with the number of VM
connected to subnet associated with the L3VPN.
Another impact may be the fact that the whole fib should be parsed to fallback
the no destination ip match rule in local DPN to enter the L3VPN flow.

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


* Create an internal tenant network with an IPv6 (or dual-stack) subnet.

::

 neutron net-create private-net
 neutron subnet-create --name ipv6-int-subnet --ip-version 6
 --ipv6-ra-mode slaac --ipv6-address-mode slaac private-net 2001:db8:0:2::/64

* Create a router and associate the external and internal subnets.

::

 neutron router-create router1
 neutron router-gateway-set router1 public-net
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

* Spawn a VM in the tenant network

::

 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net> VM1

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
[6]

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
CSIT provided for the BGPVPNv6 versions will be enhanced to also support
connectivity to Internet.


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

[6] `Spec to support IPv6 Inter DC L3VPN connectivity using BGPVPN.
<https://git.opendaylight.org/gerrit/#/c/50359>`_
.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
