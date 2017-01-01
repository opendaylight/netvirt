.. contents:: Table of Contents
         :depth: 3

============================================================
IPv6 L3 North-South support for Flat/VLAN Provider Networks.
============================================================

https://git.opendaylight.org/gerrit/#/q/topic:ipv6-cvr-north-south

In this specification we will be discussing the high level design of
IPv6 North-South support in OpenDaylight for VLAN/FLAT provider network
use-case.

Problem description
===================

OpenDaylight currently supports IPv6 IPAM (IP Address Management) and a fully
distributed east-west router. IPv6 external connectivity is not yet supported.
This SPEC captures the implementation details of IPv6 external connectivity for
VLAN/FLAT provider network use-cases.

We have a separate SPEC [3] that captures external connectivity for L3VPN use-case.

The expectation in OpenStack is that Tenant IPv6 subnets are created with Globally
Unique Addresses (GUA) that are routable by the external physical IPv6 gateway in
the datacenter for external connectivity. So, there is no concept of NAT or
Floating-IPs for IPv6 addresses in Neutron. An IPv6 router is hence expected to do
a plain forwarding.

Initially, we would like to pursue a Centralized IPv6 router (CVR) use-case and
look into a fully distributed router via a future spec. One of the main reasons
for pursuing the CVR over DVR is that OpenStack Neutron creates only a single
router gateway port (i.e., port with device owner as network:router_gateway)
when the router is associated with the external network. When implementing
a distributed router, we cannot use the same router gateway port MAC address
from multiple Compute nodes as it could create issues in the underlying physical
switches. In order to implement a fully distributed router, we would ideally
require a router-gateway-port per compute node. We will be addressing the
distributed router in a future spec taking into consideration both IPv4 and IPv6
use-cases.

Use Cases
---------

IPv6 external connectivity (north-south) for VMs spawned on tenant networks,
when the external network is of type FLAT/VLAN based.

Steps:

- Create a tenant network with IPv6 subnet using GUA/ULA prefix or an
  admin-created-shared-ipv6-subnet-pool.
- Create an external network of type FLAT/VLAN with an IPv6 subnet where the
  gateway_ip points to the Link Local Address (LLA) of external/physical IPv6
  gateway.
- Create a Neutron Router and associate it with the internal subnets and external
  network.
- Spawn VMs on the tenant network.

::


            +------------------+
            |                  |
            |                  +------->Internet
            |   External IPv6  |
            |      Gateway     |
            |                  |
            |                  |
            +------------------+
                |LLA of IPv6 GW
                |
                |                          Flat/VLAN External Network: 2001:db8:0:1::/64
        +------------------------------------------------------------------------------+
                |                               |                          |
                |                               |                          |
                |     -----------------------------------------------------------------+
                |      |  Internal Tenant N/W   |     |                    |      |
  router-gw-port|      |                        |     |                    |      |
    +------------------------+   +-------------------------+  +-------------------------+
    | +--------------------+ |   |                         |  |                         |
    | | Virtual IPv6 Router| |   |                         |  |                         |
    | |  using OVS Flows   | |   |                         |  |                         |
    | +--------------------+ |   |                         |  |                         |
    |                        |   |                         |  |                         |
    |                        |   |                         |  |                         |
    | +--------------------+ |   | +---------------------+ |  | +---------------------+ |
    | | VM1                | |   | | VM2                 | |  | | VM3                 | |
    | | Tenant IPv6 Subnet | |   | |                     | |  | |                     | |
    | | 2001:db8:0:2::10/64| |   | | 2001:db8:0:2::20/64 | |  | | 2001:db8:0:2::30/64 | |
    | +--------------------+ |   | +---------------------+ |  | +---------------------+ |
    +------------------------+   +-------------------------+  +-------------------------+
    Compute Node-1 designated           Compute Node-2               Compute Node-3
    as NAPT Switch for router1


Proposed change
===============

ODL Controller would implement the following.

* Program the necessary pipeline flows to support IPv6 forwarding
* Support Neighbor Discovery for Router Gateway port-ips on the external network.
  i.e., When the upstream/external IPv6 Gateway does a Neighbor Solicitation for the
  router-gateway-ip, ODL-Controller/ipv6service would respond with a Neighbor Advertisement
  providing the target link layer address.
* Enhance IPv6Service to learn the MAC-address of external-subnet-gateway-ip by framing
  the necessary Neighbor Solicitation messages and parsing the corresponding response.
  The APIs in IPv6Service would be triggered from Gateway MAC resolver code and the
  information obtained will be used while programming the ProviderNetworkGroup entries.

The implementation would be aligned with the existing IPv4 SNAT support we have
in Netvirt. ODL controller would designate one of the compute nodes (also referred
as NAPT Switch), one per router, to act as an IPv6/IPv4-SNAT router, from where the
tenant traffic is routed to the external network. External traffic from VMs hosted
on the NAPT switch is forwarded directly, whereas traffic from VMs hosted on other
compute nodes would have to do an extra hop to NAPT switch before hitting the
external network. If a router has both IPv4 and IPv6 subnets, the same NAPT Switch
for the router will be used for IPv4-SNAT and IPV6 external-packet forwarding.

Pipeline changes
----------------

**Flows on NAPT Switch for Egress traffic from VM to the internet**

| Classifier Table (0) =>
| LPORT_DISPATCHER_TABLE (17) ``l3vpn service: set: vpn-id=router-id`` =>
| L3_GW_MAC_TABLE (19) ``priority=20, match: vpn-id=router-id, dst-mac=router-internal-interface-mac`` =>
| L3_FIB_TABLE (21) ``priority=10, match: ipv6, vpn-id=router-id, default-route-flow`` =>
| PSNAT_TABLE (26) ``priority=5, match: ipv6, vpn-id=router-id, unknown-sip`` =>
| OUTBOUND_NAPT_TABLE (46) ``priority=10, match: ipv6, vpn-id=router-id, ip-src=vm-ip set: src-mac=external-router-gateway-mac-address, vpn-id=external-net-id,`` =>
| NAPT_PFIB_TABLE (47) ``priority=6, match: ipv6, vpn-id=external-net-id, src-ip=vm-ip`` =>
| ProviderNetworkGroup: ``set dst-mac=ext-subnet-gw-mac, reg6=provider-lport-tag`` =>
| EGRESS_LPORT_DISPATCHER_TABLE (220) output to provider network

**Flows on NAPT Switch for Ingress traffic from internet to VM**

| Classifier Table (0) =>
| LPORT_DISPATCHER_TABLE (17) ``l3vpn service: set: vpn-id=ext-net-id`` =>
| L3_GW_MAC_TABLE (19) ``priority=20, match: vpn-id=ext-net-id, dst-mac=router-gateway-mac`` =>
| L3_FIB_TABLE (21) ``priority=138, match: ipv6, vpn-id=ext-net-id, dst-ip=vm-ip`` =>
| INBOUND_NAPT_TABLE (44) ``priority=10, match: ipv6, vpn-id=ext-net-id, dst-ip=vm-ip set: vpn-id=router-id`` =>
| NAPT_PFIB_TABLE (47) ``priority=5, match: ipv6, vpn-id=router-id set: in_port=0`` =>
| L3_FIB_TABLE (21) ``priority=138, match: ipv6, vpn-id=router-id, dst-ip=vm-ip`` =>
| Local Next-Hop group: ``set: src-mac=router-intf-mac, dst-mac=vm-mac,reg6=vm-lport-tag`` =>
| Egress table (220) output to VM port

**Flows for VMs hosted on Compute node that is not acting as an NAPT Switch**

| Same egress pipeline flows as above until L3_FIB_TABLE (21).
| PSNAT_TABLE (26) ``priority=5, match: ipv6, vpn-id=router-id set: tun_id=<tunnel-id>`` =>
| TunnelOutputGroup: ``output to tunnel-port`` =>
| OnNAPTSwitch (for Egress Traffic from VM)
|     INTERNAL_TUNNEL_TABLE (36): ``priority=10, match: ipv6, tun_id=<tunnel-id-set-on-compute-node> set: vpn-id=router-id, goto_table:46``
|     Rest of the flows are common.
| OnNAPTSwitch (for Ingress Traffic from Internet to VM)
|     Same flows in ingress pipeline shown above until NAPT_PFIB_TABLE (47) =>
|     L3_FIB_TABLE (21) ``priority=138, match: ipv6, vpn-id=router-id, dst-ip=vm-ip set: tun_id=<tunnel-id>, dst-mac=vm-mac, output: <tunnel-port>`` =>


Yang changes
------------
IPv6Service would implement the following YANG model.

::

  module ipv6-ndutil {
    yang-version 1;
    namespace "urn:opendaylight:netvirt:ipv6service:ipv6util";
    prefix "ipv6-ndutil";

    import ietf-interfaces {
        prefix if;
    }

    import ietf-inet-types {
        prefix inet; revision-date 2013-07-15;
    }

    import ietf-yang-types {
        prefix yang;
    }

    revision "2017-02-10" {
        description "IPv6 Neighbor Discovery Util module";
    }

    grouping interfaces {
        list interface-address {
           key interface;
           leaf interface {
                type leafref {
                    path "/if:interfaces/if:interface/if:name";
                }
           }
           leaf src-ip-address {
                type inet:ipv6-address;
           }
           leaf src-mac-address {
                type yang:phys-address;
            }
        }
    }

    rpc send-neighbor-solicitation {
        input {
            leaf target-ip-address {
                type inet:ipv6-address;
            }
            uses interfaces;
        }
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

Scale and Performance Impact
----------------------------
* In the proposed implementation, we have to configure a static route
  on the external IPv6 Gateway with next-hop as the router-gateway-ip.
  In a future patch, we would enhance the implementation to use BGP for
  advertising the necessary routes.

* When the external IPv6 Gateway wants to contact the tenant VMs, it
  forwards all the traffic to the router-gateway-port on the designated
  NAPT Switch. To know the target-link-layer address of the router-gw-port,
  the external IPv6 Gateway would send out a Neighbor Solicitation for the
  router-gateway-port-ip. This request would be punted to the Controller
  and ipv6service would respond with the corresponding Neighbor Advertisement.
  In large deployments this can become a bottleneck.
  Note: Currently, OpenFlow does not have support to auto-respond to Neighbor
  Solicitation packets like IPv4 ARP. When the corresponding support is added
  in OpenFlow, we would program the necessary ovs flows to auto-respond to
  the Neighbor Soliciation requests for router-gateway-ports.

Targeted Release
-----------------
Carbon

Alternatives
------------
An alternate solution is to implement a fully distributed IPv6 router and
would be pursued in a future SPEC.

Usage
=====

* Create an external FLAT/VLAN network with an IPv6 (or dual-stack) subnet.

::

 neutron net-create public-net -- --router:external --is-default
 --provider:network_type=flat --provider:physical_network=public

 neutron subnet-create --ip_version 6 --name ipv6-public-subnet
 --gateway <LLA-of-external-ipv6-gateway> <public-net-uuid> 2001:db8:0:1::/64

* Create an internal tenant network with an IPv6 (or dual-stack) subnet.

::

 neutron net-create private-net
 neutron subnet-create --name ipv6-int-subnet --ip-version 6
 --ipv6-ra-mode slaac --ipv6-address-mode slaac private-net 2001:db8:0:2::/64

* Create a router and associate the external and internal subnets.
  Explicitly specify the fixed_ip of router-gateway-port, as it would help us
  when manually configuring the downstream route on the external IPv6 Gateway.

::

 neutron router-create router1
 neutron router-gateway-set --fixed-ip subnet_id=<ipv6-public-subnet-id>,ip_address=2001:db8:0:10 router1 public-net
 neutron router-interface-add router1 ipv6-int-subnet

* Manually configure a downstream route in the external IPv6 gateway
  for the IPv6 subnet "2001:db8:0:2::/64" with next hop address as the
  router-gateway-ip.

::

 Example (on Linux host acting as an external IPv6 gateway):
 ip -6 route add 2001:db8:0:2::/64 via 2001:db8:0:10

* Spawn a VM in the tenant network

::

 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net> VM1

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
  Sridhar Gaddam <sgaddam@redhat.com>

Other contributors:
  TBD

Work Items
----------
https://trello.com/c/cqjOFmow/147-ipv6-centralized-router-l3-north-south-support-for-flat-vlan-provider-networks

* Program necessary pipeline flows to support IPv6 North-South communication.
* Enhance ipv6service to send out Neighbor Solicitation requests
  for the external/physical IPv6 gateway-ip and parse the response.
* Support controller based Neighbor Advertisement for router-gateway-ports
  on the external network.
* Implement Unit and Integration tests to validate the use-case.

Dependencies
============
None

Testing
=======

Unit Tests
----------
Necessary Unit tests would be added to validate the use-case.

Integration Tests
-----------------
Necessary Integration tests would be added to validate the use-case.

CSIT
----
We shall explore the possibility to validate this use-case in CSIT.

Documentation Impact
====================
Necessary documentation would be added on how to use this feature.

References
==========
[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

[3] `Spec to support IPv6 Inter DC L3VPN connectivity using BGPVPN <https://git.opendaylight.org/gerrit/#/c/50359/>`_

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
