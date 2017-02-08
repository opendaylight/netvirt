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
networks. We make the hypothesis of using GUA, for IPv6.

There are several techniques for VPNs to access the Internet. Those methods are
described in [9], on section 11.
Also a note describes in [8] the different techniques that could be applied to
the DC-GW case. Note that not all solutions are compliant with the RFC.
One of the solutions from [8] are discussed in sub-chapter 'Proposal based on VPN
semantics'. It is demonstrated that [8] is not correct.

An other solution, described in [10], on slides 41, and 42, discusses the problem
differently. It relies on openstack neutron concepts. It proposes that IPv6 local entries
could be exported to an external network, whenever that network is attached to a
neutron router, and that external network is associated to an internet VPN.
Solution is exposed in sub-chapter 'Proposal based on External Network'.

Solution described in [10] will be the chosen one.
Consecutive chapters will describe how to implement [10], slide 41, 42.

Proposal based on VPN semantics
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A first proposal has been done, based on [8], option 2. Option 2 is external network
connectivity option 2 from [8]). That method implies 2 VPNs, and is based on VPN semantics.

To summarise, this solution is using the leaking facility when configuring VPN. It is possible
to leak private VPN entries into internet VPN, so that the private VPN can have internet access.
Reversely, a private VPN that has no grant access to Internet will not have the leak mechanism
put in place.

The drawback of this solution is that the VPN leak mechanism processes both IPv4 and IPv6 entries
independently, and that subsequently, private IPv4 addresses for this private VPN could be exposed
to public, which may not be wished if IPv4 uses NAT.
This solution could be used, provided that the administrator ensures that the openstack neutron router
configured only processes IPv6 traffic.

One VPN will be dedicated to Internet access, and will contain the Internet Routes,
but also the VPNs routes. The Internet VPN can also contain default route to a gateway.
Having a separated VPN brings some advantages:

- the VPN that do not need to get Internet access get the private characteristic of VPNs.

- using a VPN internet, instead of default forwarding table is  enabling flexibility.
  Actually, it could permit creating more than one internet VPN.
  As consequence, it could permit applying different rules ( different gateway for example).

Having 2 VPNs implies the following for one packet going from VPN to the internet.
The FIB table will be used for that. If the packet's destination address does no
match any route in the first VPN, then it may be matched against the internet VPN
forwarding table.

Reversely, in order for traffic to flow natively in the opposite direction, some
of the routes from the VPN will be exported to the internet VPN.

Configuration steps in a datacenter:

- Configure ODL and Devstack networking-odl for BGP VPN.
- Create a tenant network with IPv6 subnet using GUA prefix or an admin-created-shared-ipv6-subnet-pool.
- This tenant network is connected to an external network where the DCGW is connected.
  Separation between both networks is done by DPN located on compute nodes.
  The subnet on this external network is using the same tenant as an IPv4 subnet used for MPLS over GRE tunnels
  endpoints between DCGW and DPN on Compute nodes. Configure one GRE tunnel between DPN on compute node and DCGW.
- Create a Neutron Router and connect its ports to all internal subnets
- Create a transport zone to declare that a tunneling method is planned to reach an external IP: the IPv6 interface of the DC-GW
- The neutron router subnetworks will be associated to two L3 BGPVPN instance. The step create the L3VPN instances and associate
  the instances to the router.
  Especially, two VPN instances will be created, one for the VPN, and one for the internetVPN.
  There are two workflows to handle when configuring VPNs:

  - 1st workflow is "first the private VPN is created, then the internet VPN".
  - 2nd workflow is "first the internet VPN, then the private VPN".

::

     operations:neutronvpn:createL3VPN ( "route-distinguisher" = "vpn1"
                                       "import-RT" = ["vpn1"]
                                       "export-RT" = ["vpn1","internetvpn"])
     operations:neutronvpn:createL3VPN ( "route-distinguisher" = "internetvpn"
                                       "import-RT" = "internetvpn"
                                       "export-RT" = "internetvpn")

- The DC-GW configuration will also include 2 BGP VPN instances.
    Below is a configuration from QBGP using vty command interface.

::

     vrf rd "internetvpn"
     vrf rt both "internetvpn"
     vrf rd "vpn1"
     vrf rt both "vpn1" "internetvpn"

- Spawn VM and bind its network interface to a subnet, L3 connectivty between VM in datacenter and a host on WAN  must be successful.
  More precisely, a route belonging to VPN1 will be associated to VM GUA.
  Then, it will be sent to remote DC-GW.
  DC-GW will import the entry to both "vpn1" and "internetvpn" so that the route will be known on both vpns.
  Reversely, because DC-GW knows internet routes in "internetvpn", those routes will be sent to QBGP.
  ODL will get those internet routes, only in the "internetvpn" vpn.
  For example, when a VM will try to reach a remote, a first lookup will be done in "vpn1" FIB table.
  If none is found, a second lookup will be found in the "internetvpn" FIB table.
  The second lookup should be successfull, thus trigerring the encapsulatio of packet to the DC-GW.


When the data centers is set up as above, there are 2 use cases:
  - Traffic from Local DPN to DC-Gateway
  - Traffic from DC-Gateway to Local DPN

The use cases are slightly different from [6], on the Tx side.

Proposal based on External Network
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

That second proposal is still assuming the fact that the user wants to deploy IPv6 GUA.
Whenever a subnetwork, IPv4 or IPv6, wants to reach the outside, it uses openstack neutron
router. Then in that router, an external network with a default gateway ( DC-GW for instance)
is declared.
If the IPv4 traffic is used, then the NAT mechanism will be put in place by "natting" the
private network with the outgoing IP address of the external router.
If the IPv6 traffic is used, then the users that wants to provide internet connectivity, will
have two options to do this:
- option a: benefit from [4] so that centralised virtual router is used to provide IPv6 connectivity
- option b: use L3VPN feature to import private IP to a VPN that has been created for internet
connectivity. That VPN could be called "Internet VPN", and could be associated to the external network
defined in the router. As the "Internet VPN" also imports internet routes provided by DC-GW, that VPN
is able to create the necessary pipeline rules ( the necessary MPLS over GRE tunnels), so that the
various VMs that are granted, can access to the Internet.

This is option b that we discuss later.

Configuration steps in a datacenter:

  -1- Configure ODL and Devstack networking-odl for BGP VPN.

  -2- Create a tenant network with IPv6 subnet using GUA prefix

  -3- This tenant network is connected to an external network where the DCGW is connected.
    Separation between both networks is done by DPN located on compute nodes.
    The subnet on this external network is using the same tenant as an IPv4 subnet used for MPLS over GRE
    tunnels endpoints between DCGW and DPN on Compute nodes.
    Configure one GRE tunnel between DPN on compute node and DCGW.

  -4- Create a transport zone to declare that a tunneling method is planned to reach an external IP:
  the IPv6 interface of the DC-GW

  -5- Create a Neutron Router

  -6- Create an external network, as illustrated in example below.

::

      neutron net-create --router:external=true gateway_net

  -7- The step create the L3VPN instances. As illustration, the route distinguishe and route target
   are set to 100:1.

::

      operations:neutronvpn:createL3VPN ( "name":"internetvpn"
                                        "route-distinguisher" = "100:1",
                                        "import-RT" = ["100:1"]
                                        "export-RT" = ["100:1"])

  -8- Connect the router ports to the internal subnets that need to access to the internet.

::

      neutron router-interface-add router4 subnet_private4

  -9- The external network will be associated with the "internet VPN" instance.

::

     operations:neutronvpn:associateNetworks ( "network-id":"<uuid of external network gateway_net >"
                                               "vpn-id":"<uuid of internetvpn>")

  -10- The external network will be associated to the router.

::

     neutron router-gateway-set router5 GATEWAY_NET

Note that steps (h), (i), and (j) can be combinet in different orders.
The proposal based on external network is the one chosen to do changes

Proposed change
===============

The changes consist in :

- extending the neutronvpn.yang subnet structure so as to link the external vpn to the private subnetwork.

- providing a mechanism in the data path, so that the router recognizes there is an external VPN to use
For doing L3 forwarding, the packet will first be transported to the VPN instance.
Note that the VPN instance can be either the neutron router ( in case no other L3VPN configured).
Or the VPN instance can be then real L3VPN instance.
Then, a check will be done against external VPN.

In all cases, the external VPN will be a fallback mecanism against IPv6 packets so that they go to the Internet.
For that, a fallback mecanism similar to option 2 from [8] will be put in place.


Neutron VPN Changes
-------------------

Those are theorical changes that should be done.
This chapter should be reviewed.

VPN - IPv6 Subnetwork Relationship established
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The 3 following conditions must be met, so that prefixes importation to the internet VPN will occur.
- on that subnet, some routing information is bound: ( VMs allocated IPs, extra route or subnet-routing configured)
- the same router has an external network configured
- the external network is being associated a VPN.

NeutronVPN listens for events that involve change of the above, that is to say:

- attach a subnetwork from router.
  A check is done on the nature of the subnetwork: IPv6.
  A check is done also to see on the list of external networks configured on the router,
  if there are any attached VPN.

- attach an external network to router.
  A check is done on the presence of a VPN to the external router or not.

- associate network to VPN.
  If the network associated is external, a check is done on the routers that use that network.

If above condition is met, VPN Manager will be called.

VPN - IPv6 Subnetwork Relationship unestablished
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If above condition is not met, the following will be triggered, depending on the incoming events.

- for a detached subnetwork from router, a check is done if a VPN is associated to the external network
  of that router.

- for an external network detached from router, a check is done to see if that network had a VPN instance.

- for a VPN disassociated from a network, the VPN instance is elected.

If above condition is met, VPNManager will be called.

VPN - IPv6 Subnetwork Other Events
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Other events will be listened. The following events are the following ones:

- VM allocated or disallocated

- extra route configured or unconfigured

- subnetrouting configured or not configured.

If the condition described above about the case where a VPN is associated to an IPv6 private subnetwork,
by using a neutron router, then the following will be triggered in VPNManager:

- there will be an importation of the associated data if a new VM just went on, or a new configuration just has been added.

- there will be a removal of the associated entry if a VM just went off, or a configuration has been flushed.

VPN Manager Changes
-------------------

The VPN Manager is responsible of providing the following APIs:
For a given pair (VPN, subnetwork), and a status on the relationship ( established, non established), do the following:

- if relationship is non established, parse the VPN associated, and remove all or the associated information in relationship to the selected
  IPv6 subnetwork. If no specific subnetwork is selected, all entries of the VPN will be flushed:

  o IPS of the VMS previously allocated

  o extra routes configured, bound to that subnetwork (or to all subnetworks)

  o subnetwork if subnet routing is configured

- if the relationship is established,  parse the IPv6 subnetwork ( from private networks) from the Router for importation to the VPN.

  o IPS of the VMS previously allocated

  o extra routes configured, bound to that subnetwork

  o subnetwork if subnet routing is configured


Pipeline changes
----------------
No pipeline changes for downstream.
Pipeline change for upstream. Indeed, the external VPN will be translated into a fallback rule for external access.
Packets going out from VM will match against either L3 forwarding in the DC, or L3 forwarding using L3VPN.
Assuming this, once in table 21, the packet will be tested against an IPv6 packet.
If it is the case, the packet will be resubmitted to table 21, to see if it matches some entries of the internet VPN table.
If it is the case, then the packet will be encapsulated with the correct MPLSoGRE tag.

Below are illustrated 2 use cases that have been identified.


Case VM to DC-GW with VPN internet configured, and standard Layer 3 routing for intra-DC traffic
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
| Lport Dispatcher Table (17) ``match: LportTag l3 service: set vpn-id=router-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac vpn-id=l3vpn-id`` =>
| L3 FIB Table (21) ``match: dl_type=0x86dd, vpn-id=router-id, set vpn-id=internetvpn-id, resubmit(,21) =>
| L3 FIB Table (21) ``match: vpn-id=internetvpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>


Case VM to DC-GW with VPN internet configured, and Inter-DC VPN configured
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
| Classifier Table (0) =>
| Lport Dispatcher Table (17) ``match: LportTag l3vpn service: set vpn-id=l3vpn-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac vpn-id=l3vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=<IP-from-vpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>
| L3 FIB Table (21) ``match: dl_type=0x86dd, vpn-id=l3vpn-id, set vpn-id=internetvpn-id, resubmit(,21) =>
| L3 FIB Table (21) ``match: vpn-id=internetvpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>

Yang changes
------------
The neutronvpn.yang subnetmap structure will be modified.
subnetmap structure will have a new field called

::

       leaf vpn-external-id {
          type    yang:uuid;
          description "External VPN to which this subnet belongs";
       }

Configuration impact
--------------------
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

* Configure MPLS/GRE tunnel endpoint on DCGW connected to public-net network

* Configure neutron networking-odl plugin

* Configure BGP speaker in charge of retrieving prefixes for/from data center
  gateway in ODL through the set of vpnservice.bgpspeaker.host.name in
  etc/custom.properties. No REST API can configure that parameter.
  Use config/ebgp:bgp REST api to start BGP stack and configure VRF, address
  family and neighboring. In our case, as example, following values will be used:

::

  rd="100:2" # internet VPN
    import-rts="100:2"
    export-rts="100:2"
   rd="100:1" # vpn1
    import-rts="100:1 100:2"
    export-rts="100:1 100:2"


Following operations are done.

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

* Create a neutron router

::

      neutron router-create router1

* Create an external network

::

      neutron net-create --router:external=true gateway_net

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

* Associate the private network with the router

::

      neutron router-interface-add router1 ipv6-int-subnet

* Associate the external network with the router

::

     neutron router-gateway-set router5 GATEWAY_NET

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
  Philippe Guibert <philippe.guibert@6wind.com>

Other contributors:
  Noel de Prandieres <prandieres@6wind.com>

  Valentina Krasnobaeva <valentina.krasnobaeva@6wind.com>

Work Items
----------

* Validate proposed changes - reuse subnetmap
* Implement NeutronVpn and VpnManager
* Testing

Dependencies
============
[6]

Testing
=======
3 operations will trigger the association between private network and external network:
- associate subnet to router
- associate Router to External Network
- associate External Network to Internet VPN

Following worklows should be tested OK
Subnets -> Router, Router -> Ext Net, Ext Net -> Int. VPN
Subnets -> Router, Ext Net -> Int. VPN, Router -> Ext Net
Ext Net -> Int. VPN, Router -> Ext Net, Subnets -> Router
Router -> Ext Net, Ext Net -> Int. VPN, Subnets -> Router
Router -> Ext Net, Subnets -> Router, Ext Net -> Int. VPN
Ext Net -> Int. VPN, Subnets -> Router, Router -> Ext Net

Unit Tests
----------
TBD

Integration Tests
-----------------
TBD

CSIT
----
TBD

Documentation Impact
====================
A design document will be provided.
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

