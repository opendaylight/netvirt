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

Provide IPv6 external connectivity to virtual machines located in Data center
can be achieved through use of Globally Unique Addresses and usage of BGP VPN concepts.
Even if VPN IPv6 is made to interconnect hosts without the help of any NAT mechanisms,
routing to the external network for internet should be easily configured.

Keep in mind that key aspects of configuring IPv6 external connectivity should rely on
Openstack and VPN concepts.

There are already solutions to provide north south communication for IPv6 as depicted in [6].
This document relies on L3VPN concepts to provide the same behaviour.

The document explores how VPN could be configured so as to provide IPv6 external
connectivity. The document explores a solution for Only IPv6 Globally Unique
Address.

Some caution need to be taken care with the solution chosen.
As this is private VPN, that means that it should be possible to use a VPN for both
usages, that is to say inter-DC and IPv6 external connectivity.
Also, some security concerns must be taken care.
Because VPN interacts with external equipment, the internal prefixes that are not
authorised to access to the internet, should not be made visible to the DC-GW.

Following schema stands for what happens on the flows on the datacenter.
For instance, the same MPLSoGRE tunnel can be used for both Inter-DC and
IPv6 external connectivity.

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

Let's say an operator can configure data center gateways with a VPN dedicated to
Internet connectivity.

Based on this configuration, if a virtual machine is spawned within a data center
subnet, then it should be possible for that IPv6 GUA subnet to be imported to that VPN.
As consequence of this relationship, a BGP UPDATE message containing MP-BGP attributes
required for reaching the VM outside the datacenter would be sent to the DC-GW.
In the same manner, adding extra-route or declaring subnetworks will trigger the same.

There are several techniques for tenant VMs to access the Internet, through usage of VPNs.
Those methods are described in [8], on section 11.
Also a note describes in [7] the different techniques that could be applied to
the DC-GW case. Note that not all solutions are compliant with the RFC.
One of the solutions from [7] are discussed in sub-chapter 'Proposal based on VPN
semantics'. It is demonstrated that [7] is not correct.

An other solution, described in [9], on slides 41, and 42, discusses the problem
differently. It relies on openstack neutron concepts. It proposes that IPv6 local entries
could be exported to an external network, whenever that network is attached to a
neutron router, and that external network is associated to an internet VPN.
Solution is exposed in sub-chapter 'Proposal based on External Network'.

Solution described in [9] will be the chosen one.
Consecutive chapters will describe how to implement [9], slide 41, 42.

Proposal based on External Network
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Neutron configuration perspective
*********************************

Configuring an external network and associate an internet VPN to that external network is
the solution the specification wants to promote for IPv6 GUA.

Following scheme can help. It shows a logical overview of what needs to be configured on openstack point of view.
As you can see, router is the object that makes relationship between internal and external world.
On internal view, you can configure either subnetwork with router, directly.
You can also associate an external BGPVPN to a second private network ( here subnet B). This is for inter DC purposes.
Even, you can associate router ( here router 2) with an external BGPVPN 2, for inter DC purposes.

The drawing illustrates also the dual stack example, because the router we are working on may be Dual stack. That
is to say that it may host both IPv4 and IPv6 subnetworks.

Also, an other use case (config 4) involves a two router solution, with one IPv4 router , one IPv6 router solution.
The customer can choose to tear-down access to external network for IPv4 ONLY (or) for IPv6 ONLY subnets for such
DualStack VM, by doing a router-gateway-clear on the respective router. This provides good flexibility.

In all cases, to reach the external connectivity, you need to configure an external network, using one of the two
methods described.

The following order will be used to support external network connectivity:

- config 1: IPv6 network connectivity using BGP VPN in single router solution

- config 2: IPv6 network connectivity using BGP VPN in dual stack router solution

- config 4: IPv6 network connectivity using BGP VPN in a two router solution

::

   config 1:  
   +----+     
   | VM |     +-------------+  +-----------+
   +----+-----| Subnet A(v6)|--|router-id-1|
              +-------------+  | Router 1  |-----+--------------------+
              | Network N   |  +-----------+     | Network External 1 |
              +-------------+                    +--------------------+
                                                 | internet-vpn-1     |
                                                 +--------------------+


   config 2:
   +----+                      +--------------+
   | VM |     +-------------+  |external-vpn-1|     +------------------+             +-------+
   +----+-----| Subnet C(v6)|--+--------------+     | Subnet E (IPv4)  |-------------| DC-GW |
              +-------------+  | Router 2     |-----+------------------+             +-------+
              | Subnet F(v4)|  +--------------+     | Network External |
              +-------------+                       +------------------+
              | Network L   |                       | internet-vpn-2   |
              +-------------+                       +------------------+

   config 3:                   +--------------+
   +----+                      |router-id-3   |
   | VM |     +-------------+  |Router 3(IPv6)|     +------------------+             +-------+
   +----+-----| Subnet N(v6)|--+--------------+-+---| Subnet P (IPv4)  |-------------| DC-GW |
              +-------------+                   |   +------------------+             +-------+
              | Network M   |                   |   | Network External |
              +-------------+                   |   +------------------+
              | Subnet O(v4)|--+--------------+ |   | internet-vpn-3   |
              +-------------+  |Router 4(IPv4)|-+   +------------------+
                               |router-id-3   |
                               +--------------+
	      
Discussion of the various setups solutions
******************************************

In all cases, the following happens:

- All subnetworks from external network will be imported into the VPN as before.
  In our case, as we have an IPv4 provider network, the IPv4 public IP address will be imported.

- Second, all IPv6 subnets attached to the router that use that external network will be imported in that internet VPN.
  Note that in the case of a dual stack router, IPv4 subnets are not concerned, since those IPv4 subnets are private.

- Note that it is not necessary to configure a default gateway IP, because all traffic is encapsulated into MPLSoGRE tunnel.

To summarise, the proposal impacts only IPv6 private subnets, even in dual stack routers, and two router solution.
There are no changes for IPv4 subnets, and floating IPs ( related to IPv4).
The implementation should be OK independently of the various orchestration choices used.

About the solution involving single stack IPv6 router, the admin must create an external IPv4 network.
This is the necessary condition to have IPv6 encapsulated in MPLSoGRE IPv4 tunnel.

About the solution involving a two router solution, a work is in progress in [10]. Testing will be possible on
such solution, only when [10] will be made available.


Discussion on internet VPN impact with IPv4
*******************************************

The internet VPN proposal is still assuming the fact that the user wants to deploy IPv6 GUA.
Whenever a subnetwork, IPv4 or IPv6, wants to reach the outside, it uses openstack neutron
router. With IPv6, it only needs to configure an external network. If IPv4 is also needed, then
it needs to configure a neutron sub-network. Because this method is used, no default gateway is
needed, since the VPN handles the forwarding to the DC-GW.

If the IPv4 traffic is used, then the NAT mechanism will be put in place by "natting" the
private network with the outgoing IP address of the external router. All subnets from external
network will be imported into the internet VPN.
If the IPv6 traffic is used, then the users that want to provide internet connectivity, will
use L3VPN feature to import private IP to the VPN that has been created for internet connectivity.
That VPN could be called "Internet VPN", and must be associated to the external network
defined in the router. That association will be administratively configured by using command
"neutron bgpvpn-assoc-create" command, so as to associate external network with BGPVPN.
Note also that using this command does not control the private IPv6 subnets that will be imported
by that BGPVPN. The IPv6 subnetworks can be either GUA or LUA, since no control is done for that.
It will be up to the administrator to be cautious regarding the configuration, and use only
IPv6 subnetworks.
As the "Internet VPN" also imports internet routes provided by DC-GW, that VPN
is able to create the necessary pipeline rules ( the necessary MPLS over GRE tunnels), so that the
various VMs that are granted, can access to the Internet.

Configuration steps
*******************

Configuration steps in a datacenter, based on config 1 described above:

- Configure ODL and Devstack networking-odl for BGP VPN.

- Create a transport zone to declare that a tunneling method is planned to reach an external IP:
  the IPv6 interface of the DC-GW

- Create a network and an IPv6 GUA subnetwork private, using GUA prefix

::

      neutron net-create private-net
      neutron subnet-create --name ipv6-int-subnet --ip-version 6 --ipv6-ra-mode slaac
            --ipv6-address-mode slaac private-net <GUA prefix>


- Create a Neutron Router

::

      neutron router-create <router>

- Create an external network. No IPv4 or IPv6 subnetwork needs to be configured.

::

      neutron net-create --router:external=true gateway_net

- The step create the L3VPN instances. As illustrated, the route distinguisher and route target
  are set to 100:1.

::

      neutron bgpvpn-create --route-distinguishers <internetvpn>
         --route-targets <internetvpn> --tenant-id b954279e1e064dc9b8264474cb3e6bd2 --name internetvpn


- step (1) : Connect the router ports to the internal subnets that need to access to the internet.

::

      neutron router-interface-add <router> ipv6-int-subnet

- step (2) : The external network will be associated with the "internet VPN" instance.

::

     operations:neutronvpn:associateNetworks ( "network-id":"<uuid of external network gateway_net >"
                                               "vpn-id":"<uuid of internetvpn>")

- step (3) : The external network will be associated to the router.

::

     neutron router-gateway-set <router> gateway_net

The last 3 operations on configuration steps have a step number: step (x) for example.
Note that step-ids (1), (2), and (3) can be combined in different orders.

Proposed change
===============

The proposal based on external network is the one chosen to do changes.
The change relies on config 1 and config 3 described above.

The changes consist in :

- extending the neutronvpn.yang subnet structure so as to link the internet vpn to the private subnetwork.

- each existing external sub-network is imported to the internet VPN. This is the case for
  IPv4 subnetwork, as it has been described above. This can also be the case for IPv6 sub-networks.

- for each new VM, extra route, subnet new to the private network or the private VPN, only the IPv6 information
  is imported to the internet VPN.

- providing a fallback rule that says that no other rules in routing table of the virtual router is available, then
  a default route is conveyed to that external network.

For doing L3 forwarding, the packet will be transported to either the neutron router, or the private VPN.
In both cases, the packet will reach table 17, for L3forwarding.
If there is no external VPN attached, then the packet is transported to the table 17, using vpn-id=router-id[1/2/3/4].
If there is an external VPN attached, then the packet is transported to table 17, using vpn-id=vpn-external-1.
Then, a check will be done against <internet-vpn-[1/2/3]>.

For IPv6 traffic, the internet VPN will be a fallback mechanism so that they go to the Internet.
A fallback mechanism similar to option 2 from [7] will be put in place, only for IPv6.

That means that in such configuration, if a dual stack router is configured with both IPv4 and IPv6, then the VPN would
only consider IPv4 public addresses and IPv6. IPv4 private traffic should follow NAT rules applied to the router.
Then if the new IPv4 public packet's destination IP address matches addresses from the internet VPN, then the packet
will be encapsulated into the MPLSoGRE tunnel.

Neutron VPN Changes
-------------------

Neutron's role fill in internet VPN information in a subnetmap structure.

VPN - IPv6 subnetwork relationship established
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The 3 following conditions must be met, so that prefixes importation to the internet VPN will occur.
- on that subnet, some routing information is bound: ( VMs allocated IPs, extra route or subnet-routing configured)
- the same router has an external network configured
- the external network is being associated a VPN.
- only IPv6 subnetworks are imported, because IPv4 subnetworks may be private.

NeutronVPN listens for events that involve change of the above, that is to say:

- attach a subnetwork from router.
  A check is done on the nature of the subnet: IPv6.
  A check is done also to see on the list of external networks configured on the router,
  if there are any attached VPN.

- attach an external network to router.
  A check is done on the presence of a VPN to the external network or not.

- associate network to VPN.
  If the network associated is external, a check is done on the routers that use that network.

If above condition is met, NeutronVPN will update subnetmap structure.

VPN - IPv6 Subnetwork Relationship unestablished
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If above condition is not met, the following will be triggered, depending on the incoming events.

- for a detached subnetwork from router, a check is done if a VPN is associated to the external network
  of that router.

- for an external network detached from router, a check is done to see if that network had a VPN instance.

- for a VPN disassociated from a network, the VPN instance is elected.

If above condition is met, NeutronVPN will update subnetmap structure.

VPN Manager Changes
-------------------

Upon subnetmap structure change, VPN manager will create subnetopdataentries structures corresponding to the two kind
of VPN handled by subnetmap structure :  either internet or external VPN.

So that at maximum, for one subnet instance, two subnetopdataentries instances will be created.

Consecutive to that change, VPN manager will add or delete FIB entries according to the information stored on
subnetopdataentry.

A populate of the FIB will be triggered for all adjacencies linked to that subnetID of the subnetOpdataEntry.
The specific route distinguisher of the corresponding VPN will be used.

Pipeline changes
----------------
Associating BGPVPN to external network will act as if a second network was accessible through internet-vpn-id.

Pipeline change for upstream. Indeed, the internet VPN will be translated into a fallback rule for external access.
This happens if there is external connectivity access, by using VPN associated to external network.
This applies only to IPv6 traffic.

Packets going out from VM will match against either L3 forwarding in the DC, or L3 forwarding using L3VPN.
Assuming this, once in table 21 ( L3 FIB table), the packet will be tested against an IPv6 packet.
If it is the case, the packet will be resubmitted to table 21 ( L3 FIB table), to see if it matches some entries of the internet VPN table.
If it is the case, then the packet will be encapsulated with the correct MPLSoGRE tag.

Below are illustrated 3 use cases that have been identified.

- case 1 based on config 1 described above

- case 2 based on config 3 described above

- case 3 based on config 1 with multipath case


Case VM to DC-GW with VPN internet configured, and standard Layer 3 routing (config 1)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Note that this rule is available only for IPv6 traffic.

| Lport Dispatcher Table (17) ``match: LportTag l3 service: set vpn-id=router-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac vpn-id=router-id`` =>
| L3 FIB Table (21) ``priority=0,match: ipv6,vpn-id=router-id, set vpn-id=internetvpn-id, resubmit(,21)`` =>
| L3 FIB Table (21) ``match: vpn-id=internet-vpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>

Case VM to DC-GW with VPN internet configured, and Inter-DC VPN configured (config 3)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Note that this rule is available only for IPv6 traffic.

| Classifier Table (0) =>
| Lport Dispatcher Table (17) ``match: LportTag l3vpn service: set vpn-id=external-l3vpn-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac vpn-id=external-vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-external-vpn-id=external-vpn-id, nw-dst=<IP-from-vpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>
| L3 FIB Table (21) ``priority=0,match: ipv6, vpn-id=l3vpn-id, set vpn-id=internet-vpn-id, resubmit(,21)`` =>
| L3 FIB Table (21) ``match: vpn-id=internet-vpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>

Yang changes
------------
The neutronvpn.yang subnetmap structure will be modified.
subnetmap structure will have a new field called

::

       leaf vpn-external-id {
          type    yang:uuid;
          description "Internet VPN to which this subnet belongs";
       }


The odl-l3vpn.yang subnet-op-data-entry will be modified.
The key for this structure is being added a new field: vpnname.
Vpnname will stand for either the external VPN or the internet VPN.

::

   --- a/vpnservice/vpnmanager/vpnmanager-api/src/main/yang/odl-l3vpn.yang
   +++ b/vpnservice/vpnmanager/vpnmanager-api/src/main/yang/odl-l3vpn.yang
   @@ -346,19 +346,19 @@ module odl-l3vpn {
   container subnet-op-data {
   config false;
   list subnet-op-data-entry {
   -            key subnet-id;
   +            key "subnet-id vpn-name";
                leaf subnet-id {
                type    yang:uuid;
                description "UUID representing the subnet ";
	        }
                leaf vpn-name {
	        type string;
	        description "VPN Instance name";
	        }
                leaf nh-dpnId {
                    type uint64;
                    description "DpnId for the DPN used as nexthop for this subnet";
                }
                leaf vrf-id {
	        type string;
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

* Associate internet L3VPN To Network

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
[5]

Testing
=======
The configurations 1 and 2 will be used.
For each of the configs used, the internet VPN method will be used.
Also, each config will be done with dual stack router, and with IPv6 router only.
3 operations will trigger the association between private network and external network:
- associate subnet to router
- associate Router to External Network
- associate External Network to Internet VPN

Following workflows should be tested OK

- Subnets -> Router, Router -> Ext Net, Ext Net -> Int. VPN

- Subnets -> Router, Ext Net -> Int. VPN, Router -> Ext Net

- Ext Net -> Int. VPN, Router -> Ext Net, Subnets -> Router

- Router -> Ext Net, Ext Net -> Int. VPN, Subnets -> Router

- Router -> Ext Net, Subnets -> Router, Ext Net -> Int. VPN

- Ext Net -> Int. VPN, Subnets -> Router, Router -> Ext Net

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

[4] `BGP-MPLS IP Virtual Private Network (VPN) Extension for IPv6 VPN
<https://tools.ietf.org/html/rfc4659>`_

[5] `Spec to support IPv6 Inter DC L3VPN connectivity using BGPVPN.
<https://git.opendaylight.org/gerrit/#/c/50359>`_

[6] `Spec to support IPv6 North-South support for Flat/VLAN Provider Network.
<https://git.opendaylight.org/gerrit/#/c/49909/>`_

[7] `External Network connectivity in IPv6 networks.
<https://drive.google.com/file/d/0BxAspfn9mEi8OEtvVFpsZXo0ZlE/view>`_

[8] `BGP/MPLS IP Virtual Private Networks (VPNs)
<https://tools.ietf.org/html/rfc4364#section-11>`_

[9] `IPv6 Support in MPLS over GRE overlays
<https://docs.google.com/presentation/d/1Ky-QIrIhdaus0m7e2rIkKDS3rJx7ro-yzTWb89w08pU/edit#slide=id.p7>`_

[10] `Spec to support L3VPN dual stack for VMs
<https://git.opendaylight.org/gerrit/#/c/54089/>`_
