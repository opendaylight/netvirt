.. contents:: Table of Contents
         :depth: 3

================================================================================
IPv6 DC-Internet L3 North-South connectivity using L3VPN provider network types.
================================================================================

https://git.opendaylight.org/gerrit/#/q/topic:ipv6-l3vpn-internet

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
message containing MP-BGP attributes required for reaching the VM outside the
datacenter. In the same manner, adding extra-route or declaring subnetworks will
trigger the same.
Such behavior can be achieved by configuring a neutron router an internet public
VPN address. For the following of the document, we focus to GUA/128 addresses that
are advertised, when one VM start. Indeed, most of the requirements are dealing with
VM access to internet.

Only IPv6 Globally Unique Address (eg /128) are advertised, this is not a scaling
architecture since it implies as much routes to process as the number of spawned
VMs, but with such BGP routing information base, DCGW can select the Compute Node
to which a packet coming from the WAN should be forwarded to.

The following covers the case where a VM connects to a host located in the internet,
and the destination ip address of packets is not part of the list of advertised
prefixes (see spec [6]).


Following schema could help :

::

                                      OVS A flow:
                                      IP dst not in advertised list
                     VPN configuration explained in use case chapter
                                                 +-----------------+
                                                 | +-------------+ |
                                             +---+ |VM1          | |
                 BGP table                   |   | | Subnet A::2 | |
                 Prefix Subnet A::2          |OVS| +-------------+ |
 +-------+       Label L2                    | A | +-------------+ |
 |       |       Next Hop OVS A              |   | |VM2          | |
 | Host  |                                   +-+-+ | Subnet B::2 | |
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

There are several techniques for VPNs to access the Internet. Those methods are
described in [8], on section 11.
Also a note describes in [8] the different techniques that could be applied to
the DC-GW case. Note that not all solutions are compliant with the RFC. Also,
we make the hypothesis of using GUA.

The method that will be described more in detail below is the option 2. Option 2
is external network connectivity option 2 from [8]). That method implies 2 VPNs.
One VPN will be dedicated to Internet access, and will contain the Internet Routes,
but also the VPNs routes. The Internet VPN can also contain default route to a gateway.
Having a separated VPN brings some advantages:
- the VPN that do not need to get Internet access get the private characteristic
  of VPNs.
- using a VPN internet, instead of default forwarding table is  enabling
  flexibility, since it coud permit creating more than one internet VPN.
  As consequence, it could permit applying different rules ( different gateway
  for example).

Having 2 VPNs implies the following for one packet going from VPN to the internet.
The FIB table will be used for that. If the packet's destination address does no
match any route in the first VPN, then it may be matched against the internet VPN
forwarding table.
Reversely, in order for traffic to flow natively in the opposite direction, some
of the routes from the VPN will be exported to the internet VPN.

Configuration steps in a datacenter:

  - Configure ODL and Devstack networking-odl for BGP VPN.
  - Create a tenant network with IPv6 subnet using GUA prefix or an
  admin-created-shared-ipv6-subnet-pool.
  - This tenant network is connected to an external network where the DCGW is
    connected. Separation between both networks is done by DPN located on compute
    nodes. The subnet on this external network is using the same tenant as an IPv4
    subnet used for MPLS over GRE tunnels endpoints between DCGW and DPN on
    Compute nodes. Configure one GRE tunnel between DPN on compute node and DCGW.

  - Create a Neutron Router and connect its ports to all internal subnets

  - Create a transport zone to declare that a tunneling method is planned to reach an external IP:
  the IPv6 interface of the DC-GW

  - The neutron router subnetworks will be associated to two L3 BGPVPN instance.
   The step create the L3VPN instances and associate the instances to the router.
   Especially, two VPN instances will be created, one for the VPN, and one for the
   internetVPN.

   operations:neutronvpn:createL3VPN ( "route-distinguisher" = "vpn1"
                                       "import-RT" = ["vpn1","internetvpn"]
                                       "export-RT" = ["vpn1","internetvpn"])
   operations:neutronvpn:createL3VPN ( "route-distinguisher" = "internetvpn"
                                       "import-RT" = "internetvpn"
                                       "export-RT" = "internetvpn")

  - The DC-GW configuration will also include 2 BGP VPN instances.
    Below is a configuration from QBGP using vty command interface.

  vrf rd "internetvpn"
  vrf rt both "internetvpn"
  vrf rd "vpn1"
  vrf rt both "vpn1" "internetvpn"

  - Spawn VM and bind its network interface to a subnet, L3 connectivty between
  VM in datacenter and a host on WAN  must be successful.
  More precisely, a route belonging to VPN1 will be associated to VM GUA.
  and will be sent to remote DC-GW. DC-GW will import the entry to both "vpn1" and "internetvpn"
  so that the route will be known on both vpns.
  Reversely, because DC-GW knows internet routes in "internetvpn", those routes will be sent to
  QBGP. ODL will get those internet routes, only in the "internetvpn" vpn.
  For example, when a VM will try to reach a remote, a first lookup will be done in "vpn1" FIB
  table. If none is found, a second lookup will be found in the "internetvpn" FIB table. The
  second lookup should be successfull, thus trigerring the encapsulation of packet to the DC-GW.

When the data centers is set up, there are 2 use cases:
  - Traffic from Local DPN to DC-Gateway
  - Traffic from DC-Gateway to Local DPN
The use cases are slightly different from [6], on the Tx side.

Proposed change
===============

Similar as with [6], plus a specific processing on Tx side.
An additionnal processing in DPN is required. When a packet is received by a
neutron router associated with L3VPN, with destination mac address is the subnet
gateway mac address, and the destination ip is not in the FIB (default gateway)
of local DPN, then the packet should do a second lookup in the second VPN configured.
So that the packet can enter the L3VPN netvirt pipeline.
The MPLS label pushed on the IPv6 packet is the one configured to provide access
to Internet at DCGW level.

Pipeline changes
----------------

FIB Manager will be modified so as to implement the fallback mechanism.
The FIB tables of the import-RTs VPNs from the default VPN created will be parsed.
In our case, a match will be found in the "internetVPN" FIB table.
To make the relationship between the first VPN, and the second internetVPN entry, a
fallback entry will overwrite the vrf-id to the new internetVPN id, and will resubmit
the packet to the table 21.

Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Since the IPv6 addresses are GUA, the specific VPN associated with the packet becomes less relevant.
In the downstream direction, the MPLS label uniquely identifies the neutron port associated with the destination IP.
This label can be used to send the packet directly on the neutron port independent of the VPN in which the packet arrives.

| Classifier Table (0) =>
| LFIB Table (20) ``match: tun-id=mpls_label set vpn-id=l3vpn-id, pop_mpls label, set output to nexthopgroup-dst-vm`` =>
| NextHopGroup-dst-vm: ``set-eth-dst dst-mac-vm, reg6=dst-vm-lport-tag`` =>
| Lport Egress Table (220) ``Output to dst vm port``

Traffic from Local DPN to DC-Gateway (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
When the packet is received from the VM, the destIP in the packet is a external network address.
So, when the packet is matched against the addresses in the VPN, there will NOT be any matches.
So, the VRF will have a default match entry which is to change the VRF ID in the metadata to that of the Internet VPN and resubmit the packet to the FIB.
In the resubmit phase, the packet DestIP, InternetVPN VRF fields are matched and the actions defined for this match is executed.
This would typically be to send the packet to one of the DC-GWs.

| Classifier Table (0) =>
| Lport Dispatcher Table (17) ``match: LportTag l3vpn service: set vpn-id=l3vpn-id` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac l3vpn service: set vpn-id=internet-l3vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=<IP-from-vpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>
| L3 FIB Table (21) ``match: dl_type=0x806, vpn-id=l3vpn-id, set vpn-id=internetvpn-id, resubmit(,21) =>
| ...
| L3 FIB Table (21) ``match: vpn-id=internetvpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>

Fib Manager changes
-------------------

Ingress traffic from internet to VM
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
As with [6], the traffic from internet to VM will be conditioned with Label and destination IP.
This is set by the DC-GW.
So the workflow does not change with [6].

Egress traffic from VM to internet
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Because the VPN manager has a list of VPNs to import information into, the list of import VPNS is parsed.
- if the imported VPN matches the VPN RD, and the correct prefix, then the default basic pipeline rule will be created.
  This is what will happen for each new prefix added to the FIB.
- if the imported VPN matches an other VPN, then an additional rule will be added.
  This entry will be added at the VPN creation.
  A check will have to be done so that the additional rule is checked lastly.
  That means that if 3 entries are appended on the VPN, then a 4th rule will be tested in OVS, if the first 3 entries
  are not matching incoming packet. That additional rule will set the vrf-id to the new internetvpn-id, and a
  resubmit to table 21 will be applied.


Yang changes
------------
None

Configuration impact
---------------------
The configuration will require to create 2 VPN instances.

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
The number of entries will be duplicated, compared with [6].
This is the cost in order to keep some VPNs private, and others kind of public.
Another impact is the double lookup that may result, when emitting a packet.
This is due to the fact that the whole fib should be parsed to fallback
to the next VPN, in order to make an other search, so that the packet can enter
in the L3VPN flow.

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
  family and neighboring. In our case, as example, following values will be used:
    - rd="100:2" # internet VPN
      - import-rts="100:2"
      - export-rts="100:2"
    - rd="100:1" # vpn1
      - import-rts="100:1 100:2"
      - export-rts="100:1 100:2"

::

 POST config/ebgp:bgp
 {
     "ebgp:as-id": {
           "ebgp:stalepath-time": "360",
           "ebgp:router-id": "<ip-bgp-stack>",
           "ebgp:announce-fbit": "true",
           "ebgp:local-as": "<as>"
     },
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

* Use neutronvpn:createL3VPN REST api to create L3VPN

::

 POST /restconf/operations/neutronvpn:createL3VPN

 {
    "input": {
       "l3vpn":[
          {
             "id":"vpnid_uuid_1",
             "name":"internetvpn",
             "route-distinguisher": [100:2],
             "export-RT": [100:2],
             "import-RT": [100:2],
             "tenant-id":"tenant_uuid"
          }
       ]
    }
 }

 POST /restconf/operations/neutronvpn:createL3VPN

 {
    "input": {
       "l3vpn":[
          {
             "id":"vpnid_uuid_2",
             "name":"vpn1",
             "route-distinguisher": [100:1],
             "export-RT": [100:1, 100:2],
             "import-RT": [100:1, 100:2],
             "tenant-id":"tenant_uuid"
          }
       ]
    }
 }

* Associate L3VPN To Network

::

 POST /restconf/operations/neutronvpn:associateNetworks

 {
    "input":{
      "vpn-id":"vpnid_uuid_1",
      "network-id":"network_uuid"
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
         "routeDistinguisher": <rd-uuid_1>
       },
       {
         "routeDistinguisher": <rd_vpn1>,
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
       {
         "routeDistinguisher": <rd-uuid_2>
       },
       {
         "routeDistinguisher": <rd_vpninternet>,
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
  Noel de Prandieres <prandieres@6wind.com>
  Valentina Krasnobaeva <valentina.krasnobaeva@6wind.com>
  Philippe Guibert <philippe.guibert@6wind.com>

Work Items
----------

* Validate proposed setup so that each VM entry is duplicated in 2 VPN instances
* Implement FIB-Manager fallback mechanism for output packets

Dependencies
============
[6]

Testing
=======

Unit Tests
----------
Unit tests related to fallback mechanism when setting up 2 VPN instances configured
as above.

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

[7] `Spec to support IPv6 North-South support for Flat/VLAN Provider Network.
<https://git.opendaylight.org/gerrit/#/c/49909/>`_

[8] `External Network connectivity in IPv6 networks.
<https://drive.google.com/file/d/0BxAspfn9mEi8OEtvVFpsZXo0ZlE/view>`_

[9] `BGP/MPLS IP Virtual Private Networks (VPNs)
<https://tools.ietf.org/html/rfc4364#section-11>`_

