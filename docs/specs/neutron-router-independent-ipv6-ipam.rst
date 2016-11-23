=========================================================
OpenDaylight support for IPv6 IPAM without Neutron Router
=========================================================

https://git.opendaylight.org/gerrit/#/c/48604/

Current implementation of OpenDaylight Netvirt IPv6 requires neutron subnets
to be always associated with a neutron router to enable IPv6 IPAM (IP Address
Management) procedures for those networks. This blueprint proposes a solution
to enable IPv6 IPAM functionality in OpenDaylight for neutron IPv6 networks
independent of a neutron router is attached to that network or not.


Problem description
===================

Current implementation of OpenDaylight Netvirt IPv6 requires neutron subnets
to be always associated with a neutron router to enable IPv6 IPAM (IP Address
Management) procedures for those networks. This approach has following
short coming:

- There are deployment scenarios where neutron IPv6 networks are configured
  with L2 connectivity to a physical router (DC-GW) in the datacenter either
  using HWVTEP/L2GW or using the VLAN provider networks in order to leverage
  L3 forwarding services of the physical router (DC-GW).
- Currently in OpenDaylight, this type of deployment is allowed by configuring
  ``ipv6_ra_mode`` subnet attribute as ``Not specified`` while creating the neutron
  IPv6 subnets which would completely disable built-in IPv6 functionality in
  OpenDaylight and necessitate additional subnet configuration at physical
  router (DC-GW) for it to perform the IPv6 IPAM procedure. This would
  unnecessarily opens up for potential configuration mismatch errors in
  such deployments.
- So, in these deployments, it is still desirable to perform IPv6 IPAM functionality
  at neutron/OpenDaylight and leverage the physical router (DC-GW) for IPv6 L3
  forwarding only.

Additionally, there is no information that is not available in neutron
network/subnet data model that warrants a neutron router to be configured to
perform IPv6 IPAM functionality in IPv6 L2 only networks

Use Cases
---------

This proposal will allow the use case as described below:
- Create a neutron network
- Create a neutron IPv6 subnet with ``ipv6_ra_mode`` and ``ipv6_address_mode`` as
  one of slaac/dhcpv6-staeful/dhcpv6-stateless
- Create a L2GW connection between neutron IPv6 network and DC-GW (or using
  VLAN provider network type)
- OpenDaylight enables the VMs in the neutron network to be able to assign
  IPv6 address based on the configured subnet
- Neutron VMs use the DC-GW configured in that IPv6 L2 network as default
  gateway for any off-link communication

Proposed change
===============

OpenDaylight should support performing IPv6 IPAM procedures for neutron IPv6
networks/subnets without the need to configure a neutron router.

i.e. If a IPv6 neutron subnet is defined without associated to a neutron router,
OpenDaylight should still provide IPAM functionality to the hosts in that
network by sending a RA (Router Advertisement) message with the following
information:

- {A,M,O} flags as defined in IPv6 subnet configuration
- Prefix information as defined in IPv6 subnet configuration or None if
  other than SLAAC is used
- Router Lifetime = 0 (does not act as default router)

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
router (DC-GW) to announce a Router Advertisement with non-zero Router Lifetime
value on that network, such that hosts will use DC-GW's LLA as default next hop
for all off-link destinations.

However, when the IPv6 network/subnets are attached to a neutron router, the
current behavior kicks in where OpenDaylight will start sending RAs with its
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
[1] https://tools.ietf.org/html/rfc4861

