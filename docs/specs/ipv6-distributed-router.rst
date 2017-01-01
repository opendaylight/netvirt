.. contents:: Table of Contents
         :depth: 3

==============================================================
IPv6 Distributed Router for Flat/VLAN based Provider Networks.
==============================================================

https://git.opendaylight.org/gerrit/#/q/topic:ipv6-distributed-router

In this specification we will be discussing the high level design of
IPv6 North-South support in OpenDaylight for VLAN/FLAT provider network
use-case.

Problem description
===================

Let us begin with how IPv4 external connectivity is supported today for tenant
VMs (behind a Neutron Virtual Router) connected to a FLAT/VLAN based provider
network use-case in OpenDaylight.

For IPv4 external connectivity, we have two use-cases.

1. VMs with floatingips: When a VM port is associated with a floatingip, we
   use the floatingip details (like its ipAddress and MAC) for all the
   external traffic.
2. VMs without floatingip: For VMs that are not associated with floatingips,
   but are connected to an external network, outbound traffic from VMs would
   be source NATed to the router gateway interface and sent out.

For the floating-ip use-case, we have a one-to-one mapping for floatingip to
VM-ip, and ODL supports a distributed router where outbound traffic from VM is
routed directly from the compute node hosting the VM. In case of non-floatingip
(i.e., SNAT) use-case, currently SNAT is done on a centralized NAPT switch
(which is scheduled by the ODL Controller) and traffic from VMs hosted on
other hypervisors is forwarded to the centralized NAPT switch from where it is
routed to the external network.

OpenDaylight currently supports IPv6 IPAM (IP Address Management) and a fully
distributed east-west router. IPv6 external connectivity is not yet supported.

Unlike IPV4, for IPv6, we would like to target a fully distributed router
solution instead of taking a centralized router approach. This is not only
for performance reasons but also because in IPv6, we do not support
NAT/FloatingIPs. The expectation in OpenStack is that Tenant IPv6 subnets are
created with Globally Unique Addresses (GUA) that are routable by the external
physical IPv6 gateway in the datacenter. So, when tenants create IPv6 subnets
that are Globally routable, ODL IPV6 router is expected to forward the
traffic without doing any NAT.

In order to support a fully distributed IPv6 North-South communication, there
are few things to look at. The main challenge we have while implementing a
fully distributed router is that OpenStack Neutron creates only a single
router gateway port (i.e., port with device owner as network:router_gateway)
when the router is associated with the external network. When implementing
a distributed router, we cannot use the same router gateway port MAC address
from multiple Compute nodes as it could create issues in the underlying
physical switches. For more details on the problems that would arise, you
can read the HOST MACs section of the following blog [3].

To address this issue, OpenStack DVR Router uses an HOST_MAC for every
compute node. However, this HOST_MAC is allocated by Neutron Server only when
explicitly requested by the agent running on the compute node and not while
associating the Neutron router with the external network. OpenDaylight
currently does not use any agents on the compute nodes and also there is no
return channel to Neutron Server from ODL Controller.

So, the first requirement is to allocate an HOST_MAC per compute node in
OpenDaylight similar to Neutron DVR router. This HOST_MAC is not only a
requirement for IPv6, but would be necessary even for a distributed IPv4
router use-case.

You might ask, how IPv6 east-west traffic is handled in a fully distributed
manner in the absence of HOST_MACs. Currently, for east-west routing of both
IPv4 and IPv6 traffic, we are not updating the source MAC to the router
interface MAC when traffic is sent out of the compute node. That means, in a
setup where you have two tenant networks (network1 and network2) connected to
the same Neutron router, and a VM spawned on each of the network. When VM1 in
network1 sends a ping request to VM2 in network2, VM2 would see the source MAC
of VM1 and not the router interface MAC of network2. This is a known issue and
to mitigate the problem to some extent, the following spec is proposed [4].

Use Cases
---------

IPv6 external connectivity (north-south) for VMs spawned on tenant networks,
when the external network is of type FLAT/VLAN based.

Steps:

- Create a tenant network with IPv6 subnet using GUA prefix or an
  admin-created-shared-ipv6-subnet-pool.
- Create an external network of type FLAT/VLAN with an IPv6 subnet where the
  gateway_ip points to the LLA of external/physical IPv6 gateway.
- Create a Neutron Router.
- Associate the Router with the internal and external networks.
- Spawn VMs on the tenant networks.

IPv6 external connectivity for the BGPVPN use-case would be handled via a
different spec.


Proposed change
===============

ODL Controller would program the necessary pipeline flows to support IPv6
North South communication.

To address the HOST_MAC requirement, ODL Controller would allocate a HOST_MAC
per compute node and use that in OVS flows to update the source MAC for the
external IPv6 North-South traffic.

A new model, ``odl-nat:odl-host-mac-dpnid-mapping``, will be introduced to
store this mapping. We can extend the use of ODL allocated HOST_MAC to other
use-cases in future. The focus of this SPEC is IPv6 north-south. So, we will
not be discussing about the other use-cases in this specification.

ODL Controller would also support Neighbor Discovery for the VM GUAs on the
external network providing the HOST_MAC (on which the VM is spawned) as the
Target Link Layer address.

Pipeline changes
----------------

Regarding the pipeline changes, we can use the same IPv4 Floatingip pipeline
(Table 21, 28, 25, 27 and Group tables) or introduce new IPv6 tables for the
North-South communication. Currently, for IPv6 east-west support, we use the
same pipeline as IPv4 east-west pipeline. So, it makes sense to follow the
same approach even for IPv6 North-South, particularly since IPv6 North-South
use-case is similar to IPv4 FloatingIP use-case (except that we do not modify
the source IP in case of IPv6).

IMO, programming the necessary IPv6 flows in the IPv4 Floating IP pipeline
could be good idea. But, I would like to hear your comments on this.
Any feedback would be very much appreciated.

Based on the review comments, I will share the pipeline changes in the next
patch.

Yang changes
------------
odl-nat module will be enhanced with the following container

::

  container odl-host-mac-dpnid-mapping {
    description "Mapping of ODL allocated HOST MAC and the corresponding DPN-ID";
    list odl-host-mac-dpnid-list  {
      key dpn-id;
        leaf dpn-id {
          type uint64;
        }
        leaf mac-address {
          type string;
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
In the proposed implementation, ODL Controller supports IPv6 Neighbor
Discovery on the external network. This can cause some scalability issues.

In a future patch, we would enhance the implementation to use BGP for
advertising the necessary routes to the external physical IPv6 gateway.
By doing this, we can avoid the Neighbor Discovery for VM addresses on the
external network.

We would also explore OpenFlow support to auto-respond to Neighbor Discovery
packets without using the Controller.

Targeted Release
-----------------
Carbon

Alternatives
------------
An alternate solution instead of having a distributed router is to
implement a Centralized IPv6 router. While this is one of the possible
solutions, we feel supporting a distributed router would be more optimal.

Regarding the HOST_MAC requirement for the compute nodes, an alternate
solution is to make an explicit request to Neutron Server to allocate the
HOST_MAC. However, in the absence of a return communication path to Neutron
Server, introducing the return channel to Neutron Server for this use-case
could be an overkill.

Usage
=====

* Create an external FLAT/VLAN network with an IPv6 (or dual-stack) subnet.

::

 neutron net-create public-net -- --router:external --is-default
 --provider:network_type=flat --provider:physical_network=public

 neutron subnet-create --ip_version 6 --name ipv6-public-subnet
 --gateway <LLA-of-external-ipv6-gateway> --ipv6-address-mode slaac
 <public-net-uuid> 2001:db8:0:1::/64

* Create an internal tenant network with an IPv6 (or dual-stack) subnet.

::

 neutron net-create private-net
 neutron subnet-create --name ipv6-int-subnet --ip-version 6
 --ipv6-ra-mode slaac --ipv6-address-mode slaac private-net 2001:db8:0:2::/64

* Manually configure a downstream onlink route in the external IPv6 gateway
  for the IPv6 subnet "2001:db8:0:2::/64" on the interface that connects to
  the external public-net.

::

 Example (on Linux host acting as an external IPv6 gateway):
 ip -6 route add 2001:db8:0:2::/64  scope link dev <interface-on-public-net>

* Create a router and associate the external and internal subnets.

::

 neutron router-create router1
 neutron router-gateway-set router1 public-net
 neutron router-interface-add router1 ipv6-int-subnet

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

* Implement necessary APIs to allocate a HOST_MAC for a compute node with
  DPN-ID as the primary key.
* Enhance Gateway MAC resolver code to send out Neighbor Solicitation requests
  for the external/physical IPv6 gateway-ip.
* Support controller based Neighbor Discovery for VM GUAs on the external
  network providing the HOST_MAC of the compute node where the VM is hosted.
* Program necessary pipeline flows to support IPv6 North-South communication.

Dependencies
============
None

Testing
=======

Unit Tests
----------
TBD

Integration Tests
-----------------
TBD

CSIT
----

Documentation Impact
====================
Necessary documentation would be added on how to use this feature.

References
==========
[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

[3] `HOST MACs in OpenStack DVR Router <https://assafmuller.com/2015/04/15/distributed-virtual-routing-overview-and-eastwest-routing>`_

[4] `Setup SMAC on routed packets destined to virtual endpoints <https://git.opendaylight.org/gerrit/#/c/49807>`_

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
