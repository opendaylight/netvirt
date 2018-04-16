.. contents:: Table of Contents
         :depth: 3

=====================================
Dual Stack VM support in OpenDaylight
=====================================

https://git.opendaylight.org/gerrit/#/q/topic:l3vpn-dual-stack-vms

In this specification we will introduce a support of basic L3 forwarding for
dualstack VMs connectivity over L3 in NetVirt. Dualstack VM is a virtual machine
that has at least two IP addresses with different ethertypes: IPv4 address and
IPv6 address.

In addition to this, the specification ensures initial support of dualstack VMs
inside L3 BGPVPN. L3 forwarding for dualstack VMs connectivity inside L3 BGPVPN
will be provided for the following variations of L3 BGPVPN:

A. L3 BGPVPN constructed purely using networks;
B. L3 BGPVPN constructed purely using a router;
C. L3 BGPVPN constructed using multiple networks and a router.

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
dualstack VMs is important. Usage of IPv6 GUA addresses has increased during the
last couple years. Administrators want to deploy services, which will be
accessible from traditional IPv4 infrastructures and from new IPv6 networks as
well.

Dualstack VM should be able to connect to other VMs, be they are of IPv4 (or)
IPv6 ethertypes.
So in this document we can handle following use cases:

- Intra DC, Inter-Subnet basic L3 Forwarding support for dualstack VMs;

- Intra DC, Inter-Subnet L3 Forwarding support for dualstack VMs within L3 BGPVPN.

Current L3 BGPVPN allocation scheme picks up only the first IP address of
dualstack VM Neutron Port. That means that the L3 BGPVPN allocation scheme will
not apply both IPv4 and IPv6 network configurations for a port. For example, if
the first allocated IP address is IPv4 address, then L3 BGPVPN allocation scheme
will only apply to IPv4 network configuration. The second IPv6 address will be
ignored.

Separate VPN connectivity for singlestack VMs within IPv4 subnetworks and within
IPv6 subnetworks is already achieved by using distinct L3 BGPVPN instances. What
we want is to support a case, when the same L3 BGPVPN instance will handle both
IPV4 and IPv6 VM connectivity.

Regarding the problem description above, we would propose to implement in
OpenDaylight two following solutions, applying to two setups

1. **two-router** setup solution

One router belongs to IPv4 subnetwork, another one belongs to IPv6 subnetwork.
This setup brings flexibility to manage access to external networks. More
specifically, by having two routers, where one is holding IPv4 subnet and
another is holding IPv6 subnet, customer can tear-down access to external
network for IPv4 subnet ONLY or for IPv6 subnet ONLY by doing a
router-gateway-clear on a respective router.

Now this kind of orchestration step entail us to put a Single VPN Interface
(representing the VNIC of DualStack VM) in two different Internal-VPNs, where
each VPN represents one of the routers.  To achive this we will use L3 BGPVPN
concept. We will extend existing L3 BGPVPN instance implementation to give it an
ability to be associated with two routers. As consequence, IPv4 and IPv6
subnetworks, added as ports in associated routers and, hence, IPv4 and IPv6 FIB
entries, would be gathered in one L3 BGPVPN instance.

L3 BGPVPN concept is the easiest solution to federate two routers in a single L3
BGPVPN entity. From the orchestration point of view and from the networking
point of view, there is no any reason to provide IPv4 L3VPN and IPv6 L3VPN
access separately for dualstack VMs. It makes sense to have the same L3 BGPVPN
entity that can handle both IPv4 and IPv6 subnetworks.

The external network connectivity using L3 BGPVPN is not in scope of this
specification. Please, find more details about this in [6]. Right now, this
configuration will be useful for inter-subnet and intra-dc routing.

2. **dualstack-router** setup solution

The router with 2 ports (one port for IPv4 subnet and another one for IPv6
subnet) is attached to a L3 BGPVPN instance.

The external network connectivity using L3 BGPVPN is not in the scope of this
specification.

Setup Presentation
==================

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
 - IPv4 subnet: a.b.c.x/i
 - IPv6 subnet: C::x/64

Each VM will receive IPs from these two defined subnets.

Following schemes stand for conceptual representation of used neutron
configurations for each proposed solution.

::

    setup 1: two singlestack routers, associated with one BGPVPN
             ("two-router" solution)

                                           +---------------+
                                           | Network N3    |
                                           +---------------+
          +-----+     +---------------+    | Subnet C IPv4 |
          | VM1 |-----| Network N     |    +---------------+
          +-----+  +--|               |           |
                   |  +---------------+    +---------------+
                   |  | Subnet A IPv4 |----| Router 1      |-----+
                   |  +---------------+    +---------------+     |
                   |  | Subnet B IPv6 |           |              |   +--------+
                   |  +---------------+    +---------------+     |   |        |
                   |          |            | Subnet E IPv4 |     |---+ BGPVPN |
                   |          |            +---------------+     |   |        |
                   |          |            | Network N2    |     |   +--------+
                   |          |            +---------------+     |
                   |  +---------------+                          |
                   |  | Router 2      |--------------------------+
          +-----+  |  +---------------+
          | VM2 |--+          |
          +-----+     +---------------+
                      | Subnet D IPv6 |
                      +---------------+
                      | Network N1    |
                      +---------------+

Network N gathers 2 subnetworks, subnet A IPv4 and subnet B IPv6. This makes
possible to create Neutron Ports, which will have 2 IP addresses and whose
attributes will inherit information (extraroutes, etc) from these 2 subnets A
and B.

Router1 and Router2 are connected to Subnet A and Subnet B respectively and will
be attached to a same L3 BGPVPN instance. Routers 1 and 2 can also have other
ports, but they always should stay singlestack routers, otherwise this
configuration will not be still supported. See the chapter "Configuration
impact" for more details.

::

    setup 2: one dualstack router associated with one BGPVPN
             ("dualstack-router" solution)

           +-----+     +---------------+
           | VM1 |-----| Network N     |
           +-----+  +--|               |
                    |  +---------------+         +----------+   +--------+
                    |  | Subnet A IPv4 |---------|          |   |        |
                    |  +---------------+         | Router 1 |---+ BGPVPN |
                    |  | Subnet B IPv6 |---------|          |   |        |
                    |  +---------------+         +----------+   +--------+
           +-----+  |
           | VM2 |--+
           +-----+

Network N gathers 2 subnetworks, subnet A IPv4 and subnet B IPv6. This makes
possible to create Neutron Ports, which will have 2 IP addresses and whose
attributes will inherit information (extraroutes, etc) from these 2 subnets A
and B.

Router 1 is connected to Subnet A and Subnet B, and it will be attached to a L3
BGPVPN instance X. Other subnets can be added to Router 1, but this
configurations will not be still supported. See the chapter "Configuration
impact" for more details.

::

    setup 3: networks associated with one BGPVPN

           +-----+     +------------------+      +--------+
           | VM1 |-----| Network N1       |------| BGPVPN |
           +-----+  +--|                  |      |        |
                    |  +------------------+      +--------+
                    |  | Subnet A IPv4 (1)|          |
           +-----+  |  +------------------+          |
           | VM2 |--+  | Subnet B IPv6 (2)|          |
           +-----+     +------------------+          |
                                                     |
                                                     |
           +-----+     +------------------+          |
           | VM3 |-----+ Network N2       |----------+
           +-----+     |                  |
                       +------------------+
                       | Subnet C IPv4 (3)|
                       +------------------+
                       | Subnet D IPv6 (4)|
                       +------------------+

Network N1 gathers 2 subnets, subnet A with IPv4 ethertype and subnet B with
IPv6 ethertype. When Neutron Port was created in the network N1, it has 1 IPv4
address and 1 IPv6 address. If user lately will add others subnets to the
Network N1 and will create the second Neutron Port, anyway the second VPN port,
constructed for a new Neutron Port will keep only IP addresses from subnets (1)
and (2). So valid network configuration in this case is a network with only 2
subnets: IPv4 and IPv6. See the chapter "Configuration impact" for more details.
Second dualstack network N2 can be added to the same L3 BGPVPN instance.

It is valid for all schemes: in dependency of chosen ODL configuration, either
ODL, or Neutron Dhcp Agent will provide IPv4 addresses for launched VMs. Please
note, that currently DHCPv6 is supported only by Neutron Dhcp Agent. ODL
provides only SLAAC GUA IPv6 address allocation for VMs launched in IPv6 private
subnets attached to a Neutron router.

It is to be noted that today, setup 3 can not be executed for VPNv6 with the above
allocation scheme previously illustrated. Indeed, only a neutron router is able to
send router advertisements, which is the corner stone for DHCPv6 allocation. Either
IPv6 fixed IPs will have to be used for this setup, or an extra enhancement for providing
router advertisements for such a configuration will have to be done. The setup 3 will be
revisited in future.

Known Limitations
=================

Currently, from Openstack-based Opendaylight Bgpvpn driver point-of-view, there
is a check, where it does not allow more than one router to be associated to a
single L3 BGPVPN.  This was done in Openstack, because actually entire ODL
modeling and enforcement supported only one router per L3 BGPVPN by design.

From Netvirt point of view, there are some limitations as well:

- We can not associate VPN port with both IPv4 and IPv6 Neutron Port addresses
  at the same time. Currently, any first Neutron Port IP address is using to
  create a VPN interface. If a Neutron Port possesses multiple IP Addresses,
  regardless of ethertype, this port might not work properly with ODL.

- It is not possible to associate a single L3 BGPVPN instance with two different
  routers.

Use Cases
=========

There is no change in the use cases described in [6] and [7], except that the
single L3 BGPVPN instance serves both IPv4 and IPv6 subnets.

Inter DC Access
~~~~~~~~~~~~~~~

1. **two-router** solution

IPv4 subnet Subnet A is added as a port in Router 1, IPv6 subnet Subnet B is
added as a port in Router 2. The same L3 BGPVPN instance will be associated with
both Router 1 and Router 2.

The L3 BGPVPN instance will distinguish ethertype of router ports and will
create appropriate FIB entries associated to its own VPN entry, so IPv4 and IPv6
enries will be gathered in the same L3 BGPVPN.

2. **dualstack-router** solution

IPv4 subnet Subnet A is added as a port in Router 1, IPv6 subnet Subnet B is
added as a port in Router 1 as well. L3 BGPVPN instance will be associated with
Router 1.

The L3 BGPVPN instance will distinguish ethertype of routers ports and will
create appropriate FIB entries associated to its own VPN entry as well.
Appropriate BGP VRF context for IPv4 or IPv6 subnets will be also created.

External Internet Connectivity
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

External Internet Connectivity is not in the scope of this specification.

Proposed changes
================

All changes we can split in two main parts.


1. Distinguish IPv4 and IPv6 VRF tables with the same RD/iRT/eRT

    1.1 Changes in neutronvpn

        To support a pair of IPv4 and IPv6 prefixes for each launched dualstack VM we
        need to obtain information about subnets, where dualstack VM was spawned and
        information about extraroutes, enabled for these subnets. Obtained information
        will be stored in vmAdj and erAdjList objects respectively. These objects are
        attributes of created for new dualstack VM VPN interface. Created VPN port
        instance will be stored as part of already existed L3 BGPVPN node instance in
        MDSAL DataStore.

        When we update L3 BGPVPN instance node (associate/dissociated router or
        network), we need to provide information about ethertype of new
        attached/detached subnets, hence, Neutron Ports. New argument flags **ipv4On**
        and **ipv6On** will be introduced for that in **NeutronvpnManager** function
        API, called to update current L3 BGPVPN instance (*updateVpnInstanceNode()*
        method).  *UpdateVpnInstanceNode()* method is also called, when we create a new
        L3 BGPVPN instance. So, to provide appropriate values for **ipv4On**, **ipv6On**
        flags we need to parse subnets list. Then in dependency of these flags values we
        will set either **Ipv4Family** attribute for the new L3 BGPVPN instance or
        **Ipv6Family** attribute, or both attributes.  **Ipv4Family**, **Ipv6Family**
        attributes allow to create ipv4 or/and ipv6 VRF context for underlayed
        vpnmanager and bgpmanager APIs.

    1.2. Changes in vpnmanager

        When L3 BGPVPN instance is created or updated, VRF tables must be created for
        QBGP as well. What we want, is to introduce separate VRF tables, created
        according to **IPv4Family/IPv6Family** VPN attributes, i.e. we want to
        distinguish IPv4 and IPv6 VRF tables, because this will bring flexibility in
        QBGP. For example, if QBGP receives an entry IPv6 MPLSVPN on a router, which is
        expecting to receive only IPv4 entries, this entry will be ignored. The same for
        IPv4 MPLSVPN entries respectively.

        So, for creating **VrfEntry** objects, we need to provide information about L3
        BGPVPN instance ethertype (**Ipv4Family/Ipv6Family** attribute), route
        distinguishers list, route imports list and route exports lists
        (**RD/iRT/eRT**). **RD/iRT/eRT** lists will be simply obtained from subnetworks,
        attached to the chosen L3 BGPVPN. Presence of **IPv4Family**, **IPv6Family** in
        VPN will be translated in following VpnInstanceListener class attributes:
        **afiIpv4**, **afiIpv6**, **safiMplsVpn**, **safiEvpn**, which will be passed to
        *addVrf()* and *deleteVrf()* bgpmanager methods for creating/deleting either
        **IPv4 VrfEntry** or **IPv6 VrfEntry** objects.

        **RD/iRT/eRT** lists will be the same for both **IPv4 VrfEntry** and **IPv6
        VrfEntry** in case, when IPv4 and IPv6 subnetworks are attached to the same L3
        BGPVPN instance.

    1.3  Changes in bgpmanager

        In bgpmanager we need to change signatures of *addVrf()* and *deleteVrf()*
        methods, which will trigger signature changes of underlying API methods
        *addVrf()* and *delVrf()* from *BgpConfigurationManager* class.

        This allows *BgpConfigurationManager* class to create needed IPv4 VrfEntry and
        IPv6 VrfEntry objects with appropriate **AFI** and **SAFI** values and finally
        pass this appropriate **AFI** and **SAFI** values to *BgpRouter*.

        *BgpRouter* represents client interface for thrift API and will create needed
        IPv4 and IPv6 VRF tables in QBGP.

    1.4 Changes in yang model

        To support new attributes **AFI** and **SAFI** in bgpmanager classes, it should
        be added in ebgp.yang model:

            ::

               list address-families {
                key "afi safi";
                 leaf afi {
                   type uint32;
                   mandatory "true";
                 }
                 leaf safi {
                   type uint32;
                   mandatory "true";
                 }
               }

    1.5 Changes in QBGP thrift interface

        To support separate IPv4 and IPv6 VRF tables in QBGP we need to change
        signatures of underlying methods *addvrf()* and *delvrf()* in thrift API as
        well.  They must include the address family and subsequent address families
        informations:

            ::

                enum af_afi {
                    AFI_IP = 1,
                    AFI_IPV6 = 2,
                }

                i32 addVrf(1:layer_type l_type, 2:string rd, 3:list<string> irts, 4:list<string> erts,
                           5:af_afi afi, 6:af_safi afi),
                i32 delVrf(1:string rd, 2:af_afi afi, 3:af_safi safi)


2. Support of two routers, attached to the same L3 BGPVPN

    2.1 Changes in neutronvpn

        **two-router** solution assumes, that all methods, which are using to create,
        update, delete VPN interface or/and VPN instance must be adapted to a case, when
        we have a list of subnetworks and/or list of router IDs to attach. Due to this,
        appropriate changes need to be done in nvpnManager method APIs.

        To support **two-router** solution properly, we also should check, that we do
        not try to associate to L2 BGPVPN a router, that was already associated to that
        VPN instance.  Attached to L3 BGPVPN router list must contain maximum 2 router
        IDs. Routers, which IDs are in the list must be only singlestack routers. More
        information about supported router configurations is available below in chapter
        "Configuration Impact".

        For each created in dualstack network Neutron Port we take only the last
        received IPv4 address and the last received IPv6 address. So we also limit a
        length of subnets list, which could be attached to a L3 BGPVPN instance, to two
        elements. (More detailed information about supported network configurations is
        available below in chapter "Configuration Impact".) Two corresponding
        **Subnetmap** objects will be created in *NeutronPortChangeListener* class for
        attached subnets. A list with created subnetmaps will be passed as argument,
        when *createVpnInterface* method will be called.

    2.2 Changes in vpnmanager

        *VpnMap* structure must be changed to support a list with router IDs. This
        change triggers modifications in all methods, which retry router ID from
        *VpnMap* object.

        *VpnInterfaceManager* structure must be also changed, to support a list of VPN
        instance name. So all methods, which gives VPN router ID from *VpnInterfaceManager*
        should be modified as well.

        As consequence, in operDS, a *VpnInterfaceOpDataEntry* structure is created, inherited
        from *VpnInterface* in configDS. While the latter structure has a list of VPN instance
        name, the former will be instantiated in operDS as many times as there are VPN instances.
        The services that were handling *VPNInterface* in operDS, will be changed to handle
        *VPNInterfaceOpDataEntry*. That structure will be indexed by InterfaceName and by VPNName.
        The services include natservice, fibmanager, vpnmanager, cloud service chain.

        Also, an augment structure will be done for *VPNInterfaceOpDataEntry* to contain the list
        of operational adjacencies. As for *VpnInterfaceOpDataEntry*, the new *AdjacenciesOp*
        structure will replace Adjacencies that are in operDS. Similarly, the services will be
        modified for that.

        Also, *VPNInterfaceOpDataEntry* will contain a *VPNInterfaceState* that stands for the
        state of the VPN Interface. Code change will be done to reflect the state of the interface.
        For instance, if VPNInstance is not ready, associated VPNInterfaceOpDataEntries will  have
        the state changed to INACTIVE. Reversely, the state will be changed to ACTIVE.

    2.3 Changes in yang model

        To provide change in *VpnMap* and in *VpnInterfaceManager* structures, described
        above, we need to modify following yang files.

    2.3.1 neutronvpn.yang

        - Currently, container *vpnMap* holds one router-id for each L3 BGPVPN instance ID. A
          change consists in replacing one router-id leaf by a leaf-list of router-ids.
          Obviously, no more than two router-ids will be used.

        - Container *vpnMaps* is used internally for describing a L3 BGPVPN. Change router-id
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

        - Currently, list vpn-interface holds a leaf vpn-instance-name, which is a
          container for VPN router ID. A change consists in replacing leaf
          vpn-instance-name by a leaf-list of VPN router IDs, because L3 BGPVPN instance can
          be associated with two routers.
          Obviously, no more than two VPN router-IDs will be stored in leaf-list
          vpn-instance-name.

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
                    -       leaf vpn-instance-name {
                    +       leaf-list vpn-instance-name {
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

     2.3.3 odl-l3vpn.yang

           ::

                 augment "/odl-l3vpn:vpn-interface-op-data/odl-l3vpn:vpn-interface-op-data-entry" {
                    ext:augment-identifier "adjacencies-op";
                    uses adjacency-list;
                 }

                 container vpn-interface-op-data {
                    config false;
                    list vpn-interface-op-data-entry {
                       key "name vpn-instance-name";
                       leaf name {
                          type leafref {
                            path "/if:interfaces/if:interface/if:name";
                          }
                        }
                        leaf vpn-instance-name {
                          type string {
                            length "1..40";
                          }
                        }
                        max-elements "unbounded";
                        min-elements "0";
                        leaf dpn-id {
                          type uint64;
                        }
                        leaf scheduled-for-remove {
                          type boolean;
                        }
                        leaf router-interface {
                            type boolean;
                        }
                        leaf vpn-interface-state {
                          description
                           "This flag indicates the state of this interface in the VPN identified by vpn-name.
                            ACTIVE state indicates that this vpn-interface is currently associated to vpn-name
                            available as one of the keys.
                            INACTIVE state indicates that this vpn-interface has already been dis-associated
                            from vpn-name available as one of the keys.";

                            type enumeration {
                             enum active {
                                value "0";
                                description
                                "Active state";
                             }
                             enum inactive {
                                value "1";
                                description
                                "Inactive state";
                             }
                            }
                            default "active";
                       }
                    }
                }

Pipeline changes
================

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
====================

1. Limitations for router configurations

    1.1 Maximum number of singlestack routers that can be associated to a
        L3BGPVPN is limited to 2.  Maximum number of dualstack routers that can be
        associated with a BGPVPN is limited to 1.

    1.2 If a L3 BGPVPN has already associated with a one singlestack router and we
        try to associate this VPN instance again with a dualstack router, exception will
        not be raised.  But this configuration will not be valid.

    1.3 If a singlestack router is already associated to a L3 BGPVPN instance, and
        it has more than one port and we try to add a port to this router with another
        ethertype, i.e.  we try to make this router dualstack, exception will not be
        raised. But this configuration will not be valid and supported.

    1.4 When a different ethertype port is added to a singlestack router, which already
        has only one port and which is already associated to a L3 BGPVPN instance,
        singlestack router in this case becomes dualstack router with only two ports.
        This router configuration is allowed by current specification.

2. Limitations for subnetworks configurations

    2.1 Maximum numbers of different ethertype subnetworks associated to a one L3
        BGPVPN instance is limited to two. If a network contains more than two different
        ethertype subnetworks, exception won't be raised, but this configuration isn't
        supported.

    2.2 When we associate a network with a L3 BGPVPN instance, we do not care if
        subnetworks from this network are ports in some routers and these routers were
        associated with other VPNs. This configuration is not considered as supported as
        well.

3. Limitations for number of IP addresses for a Neutron Port

The specification only targets dual-stack networks, that is to say with 1 IPv4 address and
one IPv6 address only.
For other cases, that is to say, adding subnetworks IPv4 or IPv6, will lead to undefined or
untested use cases. The multiple subnets test case would be handled in a future spec.

ECMP impact
===========

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
L3 BGPVPN and IPv6 L3 BGPVPN (OK). the dual stack VM connectivity should embrace
ECMP

We've included this chapter to remind, that code changes for supporting
dualstack VMs should be tested against ECMP scenario as well.

Clustering considerations
=========================
None

Other Infra considerations
==========================
None

Security considerations
=======================
None

Scale and Performance Impact
============================
None

Targeted Release
================
Carbon

Alternatives
============
None

Usage
=====

Assume, that in the same provider network we have OpenStack installed with 1
controller and 2 compute nodes, DC-GW node and OpenDaylight node.

* create private tenant networks and subnetworks

  - create Network N;
  - declare Subnet A IPv4 for Network N;
  - declare Subnet B IPv6 for Network N;
  - create two ports in Network N;
  - each port will inherit a dual IP configuration.

* create routers

  - **two-router** solution
    + create two routers A and B, each router will be respectively connected to IPv4 and IPv6 subnets;
    + add subnet A as a port to router A;
    + add subnet B as a port to router B.

  - **dualstack-router** solution
    + create router A;
    + add subnet A as a port to router A;
    + add subnet B as a port to router A.

* Create MPLSoGRE tunnel between DPN and DCGW

    ::

     POST /restconf/operations/itm-rpc:add-external-tunnel-endpoint
     {
       "itm-rpc:input": {
         "itm-rpc:destination-ip": "dcgw_ip",
         "itm-rpc:tunnel-type": "odl-interface:tunnel-type-mpls-over-gre"
       }
     }

* create the DC-GW VPN settings

  - Create a L3 BGPVPN context. This context will have the same settings as in
    [7].In dualstack case both IPv4 and IPv6 prefixes will be injected in the same
    L3 BGPVPN.

* create the ODL L3 BGPVPN settings

  - Create a BGP context. This step permits to start QBGP module depicted in [8]
    and [9]. ODL has an API, that permits interfacing with that external software.
    The BGP creation context handles the following:

     + start of BGP protocol;
     + declaration of remote BGP neighbor with the AFI/SAFI affinities. In our
       case, VPNv4 and VPNv6 address families will be used.

  - Create a L3 BGPVPN, this L3 BGPVPN will have a name and will contain VRF
    settings.

* associate created L3 BGPVPN to router

    + **two-router** solution: associate routers A and B with a created L3 BGPVPN;
    + **dualstack-router** solution: associate router A with a created L3 BGPVPN.

* Spawn a VM in a created tenant network:

   The VM will possess IPv4 and IPv6 addresses from subnets A and B.

* Observation: dump ODL BGP FIB entries

   At ODL node, we can dump ODL BGP FIB entries and we should see entries for
   both IPv4 and IPv6 subnets prefixes:

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
===================

odl-netvirt-openstack

REST API
========

CLI
===

A new option ``--afi`` and ``--safi``  will be added to command ``odl:bgp-vrf``:

::

   odl:bgp-vrf --rd <> --import-rt <> --export-rt <> --afi <1|2> --safi <value> add|del


Implementation
==============

Assignee(s)
~~~~~~~~~~~
Primary assignee:
  Philippe Guibert <philippe.guibert@6wind.com>

Other contributors:
  - Valentina Krasnobaeva <valentina.krasnobaeva@6wind.com>
  - Noel de Prandieres <prandieres@6wind.com>


Work Items
~~~~~~~~~~

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
~~~~~~~~~~
Some L3 BGPVPN testing may have be done.
Complementary specification for other tests will be done.

Integration Tests
~~~~~~~~~~~~~~~~~
TBD

CSIT
~~~~

Basically, IPv4 and IPv6 vpnservice functionality have to be validated by
regression tests with a single BGPVRF.

CSIT specific testing will be done to check dualstack VMs connectivity with
network configurations for **two-router** and **dualstack-router** solutions.

**Two-router** solution test suite:

1. Create 2 Neutron Networks NET_1_2RT and NET_2_2RT.

   1.1 Query ODL restconf API to check that both Neutron Network objects were
       successfully created in ODL.

   1.2 Update NET_1_2RT with a new description attribute.

2. In each Neutron Network create one Subnet IPv4 and one Subnet IPv6:
   SUBNET_V4_1_2RT, SUBNET_V6_1_2RT, SUBNET_V4_2_2RT, SUBNET_V6_2_2RT,
   respectively.

   2.1 Query ODL restconf API to check that all Subnetwork objects were
       successfully created in ODL.

   2.2 Update SUBNET_V4_2RT, SUBNET_V6_2RT with a new description attribute.

3. Create 2 Routers: ROUTER_1 and ROUTER_2.

   3.1 Query ODL restconf API to check that all Router objects were successfully
       created in ODL.

4. Add SUBNET_V4_1_2RT, SUBNET_V4_2_2RT to ROUTER_1 and SUBNET_V6_1_2RT,
   SUBNET_V6_2_2RT to ROUTER_2.

5. Create 2 security-groups: SG6_2RT and SG4_2RT. Add appropriate rules to allow
   IPv6 and IPv4 traffic from/to created subnets, respectively.

6. In network NET_1_2RT create Neutron Ports: PORT_11_2RT, PORT_12_2RT, attached
   with security groups SG6_2RT and SG4_2RT; in network NET_2_2RT: PORT_21_2RT,
   PORT_22_2RT, attached with security groups SG6_2RT and SG4_2RT.

   6.1 Query ODL restconf API to check, that all Neutron Port objects were
       successfully created in ODL.

   6.2 Update Name attribute of PORT_11_2RT.

7. Use each created Neutron Port to launch a VM with it, so we should have 4 VM
   instances: VM_11_2RT, VM_12_2RT, VM_21_2RT, VM_22_2RT.

   7.1 Connect to NET_1_2RT and NET_2_2RT dhcp-namespaces, check that subnet
       routes were successfully propagated.

   7.2 Check that all VMs have: one IPv4 address and one IPv6 addresses.

8. Check IPv4 and IPv6 VMs connectivity within NET_1_2RT and NET_2_2RT.

9. Check IPv4 and IPv6 VMs connectivity across NET_1_2RT and NET_2_2RT with
   ROUTER_1 and ROUTER_2.

   9.1 Check that FIB entries were created for spawned Neutron Ports.

   9.2 Check that all needed tables (19, 17, 81, 21) are presented in OVS
       pipelines and VMs IPs, gateways MAC and IP addresses are taken in account.

10. Connect to VM_11_2RT and VM_21_2RT and add extraroutes to other IPv4 and
    IPv6 subnets.

    10.1 Check other IPv4 and IPv6 subnets reachability from VM_11_2RT and
         VM_21_2RT.

11. Delete created extraroutes.

12. Delete and recreate extraroutes and check its reachability again.

13. Create L3VPN and check with ODL REST API, that it was successfully created.

14. Associate ROUTER_1 and ROUTER_2 with created L3VPN and check the presence of
    router IDs in VPN instance with ODL REST API.

15. Check IPv4 and IPv6 connectivity accross NET_1_2RT and NET_2_2RT with
    associated to L3VPN routers.

    15.1 Check with ODL REST API, that VMs IP addresses are presented in VPN
         interfaces entries.

    15.2 Verify OVS pipelines at compute nodes.

    15.3 Check the presence of VMs IP addresses in vrfTables objects with
         ODL REST API query.

16. Dissociate L3VPN from ROUTER_1 and ROUTER_2.

17. Delete ROUTER_1 and ROUTER_2 and its interfaces from L3VPN.

18. Try to delete router with NonExistentRouter name.

19. Associate L3VPN to NET_1_2RT.

20. Dissociate L3VPN from NET_1_2RT.

21. Delete L3VPN.

22. Create multiple L3VPN.

23. Delete multiple L3VPN.

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
