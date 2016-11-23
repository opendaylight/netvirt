=========================================================
OpenDaylight support for IPv6 IPAM without Neutron Router
=========================================================

https://git.opendaylight.org/gerrit/#/q/topic:router-independent-ipv6-ipam

Current implementation of OpenDaylight Netvirt IPv6 requires neutron subnets
to be always associated with a neutron router to enable IPv6 IPAM (IP Address
Management) procedures for those networks. This blueprint proposes a solution
to enable IPv6 IPAM functionality in OpenDaylight for neutron IPv6 networks
independent of a neutron router is attached to that network or not.


Problem description
===================

Current implementation of OpenDaylight Netvirt supports creation of different types 
of virtual networks and provide layer-2 and layer-3 services to those networks

In many cases, for better performance and reliability, operators want to 
leverage their physical network infrastructure for layer-3 operations while 
continuing to use the layer-2 functionality of OpenDaylight.

Below are the typical use cases of IPv4 neutron virtual networks:

- **IPv4:Use case 1**: Layer 2 only tenant(vxlan) networks

  - Operator creates neutron virtual tenant networks/subnets and attaches the hosts to them

    - Create neutron tenant network
    - Create a neutron subnet in the network
    - Create neutron ports (VM instances) in the subnet

  - An isolated layer 2 (broadcast) segment is created for each tenant network
  - If dhcp is enabled for a subnet, dhcp server functionality is provided 
    either using neutron-dhcp-agent or using controller dhcp service such that
    hosts in that subnet can acquire the IP addresses
  - Hosts in the same subnet can communicate each other 
  - BUM traffic from any host in the subnet is broadcasted to all hosts in that subnet
  - Hosts from two different subnets can not communicate
  - Hosts in the tenant network will not get any external (internet) connectivity

- **IPv4:Use case 2**: Layer 3 services using Neutron/OpenDaylight

  - When a neutron router is created and attached to tenant networks, OpenDaylight provides
    DVR (Distributed Virtual Router) functionality to those tenant networks to enable
    connectivity between hosts in different subnets (layer 3 east-west) 
  - Hosts in the tenant networks use the router interface address as the default gateway for
    any traffic destined outside of their subnet. The traffic takes the following path:

    - (host1 from subnet1) --> (router interface of subnet1) --> (router interface of subnet2)
      --> (host2 from subnet2)

  - When external (internet) connectivity is needed to tenant networks (layer 3 north-
    south), operator creates an external provider network of type flat or vlan with an
    associated public IP subnet and gateway pointing to a physical router in the physical
    infrastructure and attaches it to neutron router. In this configuration, OpenDaylight 
    provides layer 3 SNAT/DNAT services to all tenant traffic to/from internet

- **IPv4:Use case 3**: Layer 3 services using a physical router connected via L2GW

  - This is the scenario where operator do not want to use Neutron/OpenDaylight for 
    layer 3 services to tenant networks, instead wanted to use physical router existing 
    in their legacy infrastructure while continuing OpenDaylight for layer 2 services
  - In this scenario, tenant neutron networks are created as defined in use case 1 that 
    creates a layer 2 (broadcast) segment for the subnets along with DHCP server functionality 
    (if needed).  
  - Operator does not create a neutron router, instead creates a neutron layer 2 gateway (L2GW)
    on a hardware VTEP (HWVTEP) device with physical router directly attached to 
    HWVTEP using vlans.
  - Operator then associate the neutron tenant layer 2 network to the L2GW that creates
    a VXLAN to VLAN gateway on the HWVTEP device
  - With this configuration, the physical router will be on the same layer 2 (broadcast) 
    segment as the tenant network and hosts in the tenant network will have direct layer 2
    connectivity to the physical router
  - The interface where physical router is attached to the neutron tenant network via L2GW should 
    have the IP address from the same tenant subnet (either manually assigning this IP address or 
    via DHCP running on the tenant network)
  - The gateway of the tenant neutron subnet should be configured with interface IP address of 
    the physical router where it is attached to that network.
  - Hosts in the tenant networks use the physical router interface address as the default 
    gateway for any traffic destined outside of their subnet. 
  - The physical router will be configured to provide inter subnet forwarding (layer 3 east-west) 
    and external (internet) connectivity (layer 3 north-south) to the hosts in the 
    tenant networks
  - The inter subnet traffic takes the following path with two L2GW connections: 
    L2GW1=(subnet1, vlan1), L2GW2=(subnet2, vlan2)

    - (host1 from subnet1) —-> (vlan1 interface of physical router) —-> (vlan2 interface of 
      physical router) —-> (host2 from subnet2)

  - NOTE: In this use case, the layer 2 services and IP address management is still 
    provided by OpenDaylight      

- **IPv4:Use case 4**: Layer 3 services using a physical router attached to VLAN/FLAT provider network types

  - In this scenario, operator creates one or more neutron provider networks of type 
    vlan/flat(untagged) based on the available underlying physical infrastructure. 
    These provider networks can be used by tenants 
  - The physical router will be attached to all the provider networks to which layer 3 
    services are needed.   
  - Like in use case 3, the the physical router will be available on the same 
    layer 2 (broadcast) segment as the tenant network and hosts in the tenant network 
    will have direct layer 2 connectivity to the physical router
  - Rest of the use case flow is similar to use case 3.


When it comes to IPv6 virtual networks orchestration, below are the use cases supported
by Neutron/OpenDaylight

- **IPv6:Use case 1**: IPv6 IPAM (IP Address Management), Layer 2 services by OpenDaylight

  - Operator creates neutron IPv6 tenant networks/subnets

    - Create neutron tenant network
    - Create a neutron subnet with ``ipv6_ra_mode`` as slaac/dhcp-stateful/dhcp-stateless
    - Subnet is configured with IPv6 ``prefix`` and ``ipv6_address_mode`` to 
      be used by that network
    - Create a neutron router
    - Associate the IPv6 network with the router
    - Create neutron ports (VM instances) in the subnet

  - When the subnet is attached to neutron router, OpenDaylight provides 
    IPv6 RA (Router Advertisement) functionality to the hosts in the network
  - Based on the RA messages published by OpenDaylight, hosts in the subnets auto 
    assigns the IPv6 address to their interfaces or acquire them from 
    neutron/OpenDaylight DHCPv6 service
  - OpenDaylight also announces the LLA (Link Local Address) of router interface 
    as the default gateway for that subnet in RAs
  - Other layer 2 services are provided similar to IPv4 scenario

- **IPv6:Use case 2**: IPv6 Layer 3 services by OpenDaylight

  - When multiple subnets are attached to the same neutron router, OpenDaylight provides
    DVR (Distributed Virtual Router) functionality to those tenant networks to enable
    connectivity across hosts in different subnets (layer 3 east-west) 


- **IPv6:Use case 3**: IPv6 IPAM and Layer 3 services by external router using L2GW or VLAN provider network types

  - Operator creates neutron IPv6 tenant networks/subnets

    - Create neutron tenant network
    - Create a neutron subnet in the network with ipv6_ra_mode as ``not-specified``
    - Subnet is configured with IPv6 prefix to be used by that network
    - Create neutron ports (VM instances) in the subnet

  - In this scenario, OpenDaylight does not provide any IPv6 RA functionality because 
    of ipv6_ra_mode set to ``not_specified``
  - Instead, a physical router will be connected to the tenant network at 
    layer 2 either using HWVTEP/L2GW or using the VLAN provider networks mechanisms
  - The external router will be configured with the same subnet configuration 
    (prefix and v6 addressing mode) that was used while creating neutron subnets
  - The external router will be configured to provide IPv6 RA functionality post which
    it announces the subnet prefix, default gateway and flags information in the RA 
    messages
  - The subnet configuration at external router and neutron should exactly match or 
    otherwise the IPv6 addresses assigned to VM interfaces do not match with those 
    derived for neutron ports by neutron and security group rules will not be 
    configured properly
  - The programming of layer2 forwarding rules will still be done by OpenDaylight 
    in this scenario
  - The physical router will be configured to provide inter subnet forwarding 
    (layer 3 east-west) and external (internet) connectivity (layer 3 north-south) 
    to the hosts in the tenant networks
     

As described above, in case of IPv4, the neutron router comes into the picture only 
when operator want to use Neutron/OpenDaylight for layer 3 services (use case 2).

However, when it comes to IPv6, the current implementation of OpenDaylight Netvirt
requires neutron subnets to be always attached to a neutron router in order for 
it to provide IPAM functionality to those networks even though layer 3 services 
are not needed from OpenDaylight.

This approach has following short coming:

- Can not support deployment scenarios where operators can leverage the existing 
  physical infrastructure for IPv6 layer 3 services while continue using 
  OpenDaylight for IPv6 IPAM functionality. Using the IPv6 IPAM functionality 
  at OpenDaylight would avoid additional subnet configurations at the physical 
  router thereby avoiding any potential configuration mismatch errors in
  such deployments

Additionally, there is no information that is not available in neutron
network/subnet data model that warrants a neutron router to be configured to
perform IPv6 IPAM functionality in IPv6 L2 only networks

Use Cases
---------

This proposal will allow the use cases as described below:

- **IPv6:Use case 4**: IPv6 IPAM by OpenDaylight and Layer 3 services by External Router via L2GW

  - Create a neutron network
  - Create a neutron IPv6 subnet with ``ipv6_ra_mode`` and ``ipv6_address_mode`` as
    one of slaac/dhcpv6-staeful/dhcpv6-stateless
  - Create a neutron layer 2 gateway (L2GW)on a hardware VTEP (HWVTEP) device where 
    physical router directly attached to HWVTEP via vlans.
  - Associate the neutron tenant IPv6 network to the L2GW such that it sets up a 
    layer 2 connectivity between the tenant network and external router
  - With the L2GW configuration, OpenDaylight programs the MAC forwarding rules in 
    virtual switches and HWVTEP devices such that any BUM traffic in the subnet 
    will be broadcasted to all nodes in the subnet 
  - The hosts in the tenant network will derive the IPv6 address for their interfaces
    based on the prefix information and RA flags announced in RA messages by 
    OpenDaylight
  - The interface where external router is attached to L2GW will be assigned with
    IPv6 GUA address from the same IPv6 subnet (currently only manual IPv6 address 
    assignment can be supported for devices connected via L2GW)
  - The interface where external router is attached to L2GW will also be assigned with
    default IPv6 Link Local Address (LLA)
  - The hosts in the tenant network will discover LLA of the physical router as the
    default gateway for that subnet when they receive the RA message announced by
    the physical router and use that gateway for any off-link communication
  - The physical router will be configured to provide IPv6 inter subnet 
    forwarding (layer 3 east-west) and external (internet) connectivity 
    (layer 3 north-south) to the hosts in the tenant networks
  - The rest of the use case flow is similar to IPv4:Use case 3

- **IPv6:Use case 5**: IPv6 IPAM by OpenDaylight and Layer 3 services by External Router via VLAN provider network type

  - Very similar to IPv6:Use case 4, instead of physical router connected to 
    neutron tenant network via L2GW, a FLAT/VLAN provider network will be 
    created with physical router and tenant VMs directly attached to that network
  - Rest of the message flow remain same as IPv6:Use case 4 


Proposed change
===============

In order to support the IPv6:Use case 4 & 5, OpenDaylight should support IPv6 
IPAM functionality to tenant networks without the need to configure a neutron router.

i.e. If a IPv6 neutron subnet is defined without associated to a neutron router,
OpenDaylight should still provide IPAM functionality to the hosts in that
network by sending a RA (Router Advertisement) message with the following
information:

- {A,M,O} flags as defined in IPv6 subnet configuration
- Prefix information as defined in IPv6 subnet configuration or None if
  other than SLAAC is used
- Router Lifetime = 0 (OpenDaylight does not act as default router for this subnet)

This type of configuration is supported as per IPv6 RFC 4861 (some excerpts below):

::

  A router might want to send Router Advertisements without advertising itself
  as a default router.  For instance, a router might advertise prefixes for
  stateless address auto configuration while not wishing to forward packets.
  Such a router sets the Router Lifetime field in outgoing advertisements to zero.

  When multiple routers are present, the information advertised collectively by
  all routers may be a superset of the information contained in a single Router
  Advertisement.

  On receipt of a valid Router Advertisement, a host extracts the source address
  (LLA - link local address) of the packet and does the following:
  -  If the address is not already present in the host's Default Router List,
     and the advertisement's Router Lifetime is non-zero, create a new entry in
     the list, and initialize its invalidation timer value from the
     advertisement's Router Lifetime field

So with this support, it should be possible for OpenDaylight to send the RAs
with configured prefixes and Router Lifetime as Zero and let the physical
router in the infrastructure network to announce a Router Advertisement using its
LLA with non-zero Router Lifetime value on that network, such that hosts will use
it as default next hop for all off-link destinations.

However, when the IPv6 network/subnets are attached to a neutron router, the
current behavior kicks-in where OpenDaylight will start sending RAs with its
LLA as default gateway for those networks and should provide IPv6 L3 services.

Pipeline changes
----------------
When IPv6 networks are created, OpenDaylight would program openflow rules in
**Table 45** to punt the Neighbor Discovery (ND) packets destined for router's
LLA and GUA interfaces. With this proposal, OpenDaylight would not program
Neighbor Discovery(ND) openflow rules for router interfaces when the neutron
networks are not associated with neutron router.

Yang changes
------------
None

Configuration impact
---------------------
This proposal does not add or modify the tenant network/subnet configuration
parameters, however it changes the existing orchestration behavior.

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
Refer to use cases section

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
None

CLI
---
None

Implementation
==============

Assignee(s)
-----------
Primary assignee:
  <TBD>

Other contributors:
  <TBD>


Work Items
----------
TBD


Dependencies
============
None

Testing
=======
Capture details of testing that will need to be added.

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
TBD

References
==========
[1] http://docs.opendaylight.org/en/latest/documentation.html
[2] https://tools.ietf.org/html/rfc4861

.. note::

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode

