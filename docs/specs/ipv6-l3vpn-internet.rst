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
This document relies on VPN concepts to provide the same.

The document explores how VPN could be configured so as to provide IPv6 external
connectivity. The document explores a solution for Only IPv6 Globally Unique
Address (eg /128).

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
subnet, then it should be possible for that subnet to be imported to that VPN.
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

Proposal based on VPN semantics
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A first proposal has been done, based on [7], option 2. Option 2 is external network
connectivity option 2 from [7]). That method implies 2 VPNs, and is based on VPN semantics.

To summarise, this solution is using the leaking facility when configuring VPN. It is possible
to leak private VPN entries into internet VPN, so that the private VPN can have internet access.
Reversely, a private VPN that has no grant access to Internet will not have the leak mechanism
put in place.

Below scheme can help. This is a logical representation with openstack objects.
As you can see, on one side, one configure private network, and VPN private.
Both are associated, either through a router or through the network-vpn association itself.
On the other side, a network is associated to VPN internet.
The arrows indicate the leaking from VPN private to VPN Internet.

::

   +----+
   | VM |     +----------+
   +----+-----| Subnet A |--+--------------+
              +----------+  | Router 1     |        +--------------+
              | Network N|  +--------------+        | Network      |
              +----------+  | VPN private  |        +--------------+
                            +--------------+        | VPN internet |
                                     |              +--------------+
                                     |                  ^  ^
                                     -------------------|  |
   +----+                                                  |
   | VM |     +--------------+                             |
   +----+-----| Subnet B     |                             |
              +--------------+                             |
              | Network M    |                             |
              +--------------+                             |
              | VPN private  |------------------------------
              +--------------+

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
The FIB table will be used for that. If the packet's destination address does not
match any route in the private VPN, then it may be matched against the internet VPN
forwarding table.

For packets going from the DC-GW to the VM, the VM IP address will be a prefix entry.
The reachability of the VM will be available from the two VPNs. Indeed, the BGP update
messages from ODL/QBGP is received by DC-GW. That BGP update contains 2 export communities
that stand for each VPN. The information of the BGP update is duplicated in each VPN.
If the packet comes from the Internet, or from an InterDC, there will be a tunnel to use
to reach the compute node that hosts the VM. A label will be associated, as well as the
compute node nexthop IP.
As the information is the same for both VMs, the VM will be reached in both cases.


Configuration steps in a datacenter:

- Configure ODL and Devstack networking-odl for BGP VPN.

- Create a private network with IPv6 subnet using GUA prefix or an admin-created-shared-ipv6-subnet-pool.

- This private network is connected to an external network where the DCGW is connected.
  Separation between both networks is done by DPN located on compute nodes.
  The subnet on this external network is using the same tenant as an IPv4 subnet used for MPLS over GRE tunnels
  endpoints between DCGW and DPN on Compute nodes. Configure one GRE tunnel between DPN on compute node and DCGW.

- Create a Neutron Router and connect its ports to all internal subnets

- Create a transport zone to declare that a tunneling method is planned to reach an external IP: the IPv6 interface of the DC-GW

- The neutron router subnetworks will be associated to two L3 BGPVPN instance. The step create the L3VPN instances and associate
  the instances to the router.
  Especially, two VPN instances will be created, one for the VPN, and one for the internetVPN.
  There are three workflows to handle when configuring VPNs:

  - 1st workflow is "first the private VPN is created, then the internet VPN".

::

   neutron bgpvpn-create --route-distinguishers <vpn1>
     --route-targets <vpn1>,<internetvpn> --tenant-id b954279e1e064dc9b8264474cb3e6bd2 --name vpn1
   neutron bgpvpn-create --route-distinguishers <internetvpn>
     --route-targets <internetvpn> --tenant-id b954279e1e064dc9b8264474cb3e6bd2 --name internetvpn

  - 2nd workflow is "first the private VPN, then the internet VPN, then private VPN update".
  Private VPN already exists. It is initially not configured to export entries to an other VPN.
  In that case, the private VPN is updated through bgpvpn update command

::

   neutron bgpvpn-create --route-distinguishers <vpn1>
     --route-targets <vpn1> --tenant-id b954279e1e064dc9b8264474cb3e6bd2 --name vpn1
   neutron bgpvpn-create --route-distinguishers <internetvpn>
     --route-targets <internetvpn> --tenant-id b954279e1e064dc9b8264474cb3e6bd2 --name internetvpn
   neutron bgpvpn-update <ID of bgpvpn> --route-targets <vpn1> ,<internetvpn>

  - 3rd workflow is "first the internet VPN, then the private VPN".

::

   neutron bgpvpn-create --route-distinguishers <internetvpn>
     --route-targets <internetvpn> --tenant-id b954279e1e064dc9b8264474cb3e6bd2 --name internetvpn
   neutron bgpvpn-create --route-distinguishers <vpn1>
     --route-targets <vpn1>,<internetvpn> --tenant-id b954279e1e064dc9b8264474cb3e6bd2 --name vpn1

- The DC-GW configuration will also include 2 BGP VPN instances.
    Below is a configuration from QBGP using vty command interface.

::

     vrf rd "internetvpn"
     vrf rt both "internetvpn"
     vrf rd "vpn1"
     vrf rt both "vpn1" "internetvpn"

- Spawn VM and bind its network interface to a subnet, L3 connectivity between VM in datacenter and a host on WAN  must be successful.
  More precisely, a route belonging to VPN1 will be associated to VM GUA.
  Then, it will be sent to remote DC-GW.
  DC-GW will import the entry to both "vpn1" and "internetvpn" so that the route will be known on both vpns.
  Reversely, because DC-GW knows internet routes in "internetvpn", those routes will be sent to QBGP.
  ODL will get those internet routes, only in the "internetvpn" vpn.
  For example, when a VM will try to reach a remote, a first lookup will be done in "vpn1" FIB table.
  If none is found, a second lookup will be found in the "internetvpn" FIB table.
  The second lookup should be successfull, thus trigerring the encapsulation of packet to the DC-GW.


When the data centers is set up as above, there are 2 use cases:
  - Traffic from Local DPN to DC-Gateway
  - Traffic from DC-Gateway to Local DPN

The use cases are slightly different from [5], on the Tx side.

Proposal based on External Network
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Using an external network can be used on two different ways:

- method 1: configuring a default IPv4 gateway that is the IP address of the DC-GW.
This method does not use external VPN.

- method 2: configuring an external VPN associated to the external network.

Following scheme can help. It shows a logical overview of what needs to be configured on openstack point of view.
As you can see, router is the object that makes relationship between internal and external world.
On internal view, you can configure either subnetwork with router, directly.
You can also associate a private VPN to a second private network ( here subnet B). This is for inter DC purposes.
Even, you can associate router ( here router 2) with a private VPN 2, for inter DC purposes.

In all cases, to reach the external connectivity, you need to configure an external network, using one of the two
methods described.
- if access method is using external VPN, then the external VPN will be associated to external network.
  internal flows, IPv4 or IPv6, will be imported in that external VPN.
  On controlplane point of view, IPv6 GUA will be imported to that VPN. Also, only IPv4 public IPs will be
  imported. In our case, for IPv4, it is needed to configure external IPv4 subnetwork, so that it is imported
  to that VPN.
  On dataplane point of view, IPv6 will be encapsulated in the MPLSoGRE tunnel for being sent to the DC-GW.
  IPv4 flow, after being translated by NAT, will be send to the MPLSoGRE tunnel too. Note that it is not necessary
  to configure a default gateway IP, because all traffic is encapsulated into MPLSoGRE tunnel.

- if access method is using external subnetwork, then the north south communication will be applied. In the case
  of IPv4, it is natively supported for a long time. The private networks are NAtted when being forwarded through the
  router. For IPv6 traffic, spec [7] applies, and traffic is forwarded without translation to the IPv6 DC-GW.
  It is necessary here to configure default gateway IPv4 or IPv6, depending on the need.

Both methods should work against config 1, config 2, and config 3.
Later on this document, we focus on describing the configs using external VPN method.

::
   config 1:
   +----+
   | VM |     +----------+                    +--------------------+            +-------+
   +----+-----| Subnet A |--+-----------+     | Subnet D           |------------| DC-GW |
              +----------+  | Router 1  |-----+--------------------+            +-------+
              | Network N|  +-----------+     | Network External 1 |
              +----------+   |                +--------------------+
                             |                | VPN internet 1     |
                             |                +--------------------+
   config 2:                 |
   +----+                    |
   | VM |     +-----------+  |
   +----+-----| Subnet B  |--+
              +-----------+
              | Network N |
              +-----------+
              |VPN private|
	      +-----------+

   config 3:
   +----+                   +------------+
   | VM |     +----------+  |VPN private2|     +------------------+             +-------+
   +----+-----| Subnet C |--+------------+     | Subnet E         |-------------| DC-GW |
              +----------+  | Router 2   |-----+------------------+             +-------+
              | Network L|  +------------+     | Network External |
              +----------+                     +------------------+
                                               | VPN internet 2   |
                                               +------------------+


The external VPN proposal is still assuming the fact that the user wants to deploy IPv6 GUA.
Whenever a subnetwork, IPv4 or IPv6, wants to reach the outside, it uses openstack neutron
router. With IPv6, it only needs to configure an external network. If IPv4 is also needed, then
it needs to configure a neutron sub-network. Because this method is used, no default gateway is
needed, since the VPN handles the forwarding to the DC-GW.

If the IPv4 traffic is used, then the NAT mechanism will be put in place by "natting" the
private network with the outgoing IP address of the external router.
If the IPv6 traffic is used, then the users that want to provide internet connectivity, will
use L3VPN feature to import private IP to a VPN that has been created for internet connectivity.
That VPN could be called "Internet VPN", and could be associated to the external network
defined in the router. As the "Internet VPN" also imports internet routes provided by DC-GW, that VPN
is able to create the necessary pipeline rules ( the necessary MPLS over GRE tunnels), so that the
various VMs that are granted, can access to the Internet.

Configuration steps in a datacenter, based on config 1 described above:

- Configure ODL and Devstack networking-odl for BGP VPN.

- Create a private network with IPv6 subnet using GUA prefix

- This network is connected to an external network through the neutron router.

- Create a transport zone to declare that a tunneling method is planned to reach an external IP:
  the IPv6 interface of the DC-GW

- Create a Neutron Router

- Create an external network, and IPv4 subnetwork, as illustrated in example below.
  The IPv4 subnetwork is in the case of dual stack router, the IPv4 traffic must be
  imported to that VPN.

::

      neutron net-create --router:external=true gateway_net
      neutron subnet-create gateway_net <SubnetPrivateIPv4> --name ipv6-public-subnet

- The step create the L3VPN instances. As illustration, the route distinguisher and route target
  are set to 100:1.

::

      neutron bgpvpn-create --route-distinguishers <internetvpn>
         --route-targets <internetvpn> --tenant-id b954279e1e064dc9b8264474cb3e6bd2 --name internetvpn


- step (1) : Connect the router ports to the internal subnets that need to access to the internet.

::

      neutron router-interface-add router4 subnet_private4

- step (2) : The external network will be associated with the "internet VPN" instance.

::

     operations:neutronvpn:associateNetworks ( "network-id":"<uuid of external network gateway_net >"
                                               "vpn-id":"<uuid of internetvpn>")

- step (3) : The external network will be associated to the router.

::

     neutron router-gateway-set router5 GATEWAY_NET

The last 3 operations on configuration steps have a step number: step (x) for example.
Note that step-ids (1), (2), and (3) can be combined in different orders.

Proposed change
===============

The proposal based on external network is the one chosen to do changes.
The change relies on config 1 and config 3 described above.

The changes consist in :

- extending the neutronvpn.yang subnet structure so as to link the external vpn to the private subnetwork.

- each sub-network, IP, from existing external sub-network is imported to the external VPN. This is the case for
IPv4 subnetwork, as it has been described above. This can also be the case for IPv6 sub-networks.

- for each new VM, extra route, subnet new to the private network or the private VPN, only the IPv6 information
is imported to the external VPN.

- providing a mechanism in the data path, so that the router recognizes there is an external VPN to use,
  if the condition of having external network connectivity in the router is necessary.
  Also, the condition of having a VPN associated to the external network is necessary.
  If above conditions are not met, IPv6 packets will be dropped.

For doing L3 forwarding, the packet will be transported to either the neutron router, or the private VPN.
In both cases, the packet will reach table 17, for L3forwarding.
If there is no private VPN attached, then the packet is transported to the table 17, using vpn-id=router-id.
If there is a private VPN attached, then the packet is transported to table 17, using vpn-id=vpn-private-id.
Then, a check will be done against external VPN.

In all cases, the external VPN will be a fallback mechanism against IPv6 packets so that they go to the Internet.
For that, a fallback mechanism similar to option 2 from [7] will be put in place.
Note that the fallback mechanism is put in place only for IPv6 packets.

That means that in such configuration, if a dual stack router is configured with both IPv4 and IPv6, then the VPN would
only consider IPv4 public addresses and IPv6. IPv4 privte traffic should follow default rules applied to the router.
That is to say that if NAT is needed, then NAT will be applied. Then if the new IPv4 packet is eligible to be encapsulated
to the MPLSoGRE tunnel, then as for IPv6, the IPv4 packet will be sent.

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
  A check is done on the nature of the subnet: IPv6.
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
This happens if there is external connectivity access, by using VPN associated to external network.
This applies also only to IPv6 traffic.

Packets going out from VM will match against either L3 forwarding in the DC, or L3 forwarding using L3VPN.
Assuming this, once in table 21, the packet will be tested against an IPv6 packet.
If it is the case, the packet will be resubmitted to table 21, to see if it matches some entries of the internet VPN table.
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
| L3 FIB Table (21) ``match: dl_type=0x86dd, vpn-id=router-id, set vpn-id=internetvpn-id, resubmit(,21)`` =>
| L3 FIB Table (21) ``match: vpn-id=internetvpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>

For IPv4 traffic, if a subnetwork is configured, following pipeline should apply:

| Lport Dispatcher Table (17) ``match: LportTag l3 service: set vpn-id=router-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac vpn-id=router-id`` =>
| L3 FIB Table (21) ``match: vpn-id=router-id`` =>
| PSNAT Table (26) ``match: vpn-id=router-id`` =>
| Outbound NAPT Table (46) ``match: nw-src=vm-ip,port=int-port set src-ip=router-gateway-ip,src-map=external-router-gateway-mac-address,vpn-id=external-vpn-id,port=ext-port`` =>
| NAPT PFIB Table (47) ``match: vpn-id=external-vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-id=external-vpn-id, set vpn-id=internetvpn-id, resubmit(,21)`` =>
| L3 FIB Table (21) ``match: vpn-id=internetvpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>


Case VM to DC-GW with VPN internet configured, and Inter-DC VPN configured (config 3)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Note that this rule is available only for IPv6 traffic.

| Classifier Table (0) =>
| Lport Dispatcher Table (17) ``match: LportTag l3vpn service: set vpn-id=l3vpn-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=router-internal-interface-mac vpn-id=l3vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=<IP-from-vpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>
| L3 FIB Table (21) ``match: dl_type=0x86dd, vpn-id=l3vpn-id, set vpn-id=internetvpn-id, resubmit(,21)`` =>
| L3 FIB Table (21) ``match: vpn-id=internetvpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>

For IPv4 traffic, if a subnetwork is configured, following pipeline should apply:

| Lport Dispatcher Table (17) ``match: LportTag l3 service: set vpn-id=l3vpn-id`` =>
| DMAC Service Filter (19) ``match: dst-mac=vpn-internal-interface-mac vpn-id=l3vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id`` =>
| PSNAT Table (26) ``match: vpn-id=l3vpn-id`` =>
| Outbound NAPT Table (46) ``match: nw-src=vm-ip,port=int-port set src-ip=vpn-gateway-ip,src-map=external-router-gateway-mac-address,vpn-id=external-vpn-id,port=ext-port`` =>
| NAPT PFIB Table (47) ``match: vpn-id=external-vpn-id`` =>
| L3 FIB Table (21) ``match: vpn-id=external-vpn-id, set vpn-id=internetvpn-id, resubmit(,21)`` =>
| L3 FIB Table (21) ``match: vpn-id=internetvpn-id, nw-dst=<IP-from-internetvpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>


Case VM to DC-GW with VPN internet configured, and 2 multipath External IP
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In the case multipath entries are detected, the new rule to be added should take into account of the group settings. An example of the pipeline rules is presented below:

| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=<IP-from-vpn> set tun-id=mpls_label output to MPLSoGRE tunnel port`` =>
| L3 FIB Table (21) ``match: dl_type=0x86dd, vpn-id=l3vpn-id, set vpn-id=internetvpn-id, resubmit(,21)`` =>
| L3 FIB Table (21) ``match: vpn-id=internetvpn-id, nw-dst=<IP-from-internetvpn> set group=150111`` =>
| Group_id=150111 ``type=select,bucket=weight:50,set tun-id=mpls_label1 output to MPLSoGRE tunnel port``
                  ``bucket=weight:50,set tun_id=mpls_label2 output to MPLSoGRE tunnel port``

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
[5]

Testing
=======
The configurations 1 and 2 will be used.
For each of the configs used, the external VPN method will be used.
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
