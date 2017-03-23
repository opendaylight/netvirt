.. contents:: Table of Contents
         :depth: 3

=================================================================
IPv6 Inter-DC L3 connectivity using L3VPN provider network types.
=================================================================

https://git.opendaylight.org/gerrit/#/q/topic:ipv6-interdc-evpn_rt5

In this specification we will be discussing the high level design of
IPv6 Inter-Datacenter North-South connectivity support in OpenDaylight
using L3 connectivity over VXLAN, and taking advantage of EVPN as the
BGP Control Plane mecanism.

Problem description
===================

An explanation on how to provide IPv6 L3 connectivity using BGP has been
done in [2]. This document has the same goals, but will vary on following
points:

- the BGP protocol used is EVPN protocol. More especially a specific route
type will be used: route type 5. More information is give in [3] and [4].
The EVPN RT5 can be used for several purposes. The specification uses that
entry to do the same as it has been done for [2].

- the tunnel that establishes between compute nodes and DC-GW is a VXLAN tunnel.
It implies that a L3VNI will be used ( as it is defined in [5]). Also the packet
that is to be carried is a whole Ethernet packet.

What will not vary is the usage of route targets. This mechanism remains the same
whatever the BGP protocol used.

On our case, IPv6 Globally Unique Address (eg /128), but also ExtraRoutes will be
advertised.

In scope
--------

This new type of L3VPN will support the following:
* Intra-subnet connectivity within a DataCenter over VXLAN tunnels.

Out of scope
------------

This new type of L3VPN will not support the following:
* The same points mentioned by [5]
* Inter-subnet connectivity is inter subnet routing, and for IPv6, there may be some conflicts with [6]
* Supporting VLAN Aware VMs
* InterVPNLink support for EVPN  
* VM Mobility with RT5
    
Use Cases
---------

Datacenter access from another Datacenter over WAN via respective DC-Gateways (L3 DCI)
+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

Providing IPv6 inter-subnet connectivity between two DataCenters.
As described in [5], Tenant VMs in one datacenter will be able to communicate with tenant
VMs on the other datacenter. Both the tenant VMS are part of the same L3VPN, though
datacenters can be managed by different ODL Controllers, but the L3VPN configured on
both ODL Controllers will use identical RDs and RTs.

Proposed change
===============

* BGP Quagga Stack to support EVPN with RouteType 5 NLRI, supporting IPv6.
* DC-Gateway BGP Neighbour that supports EVPN with RouteType 5 NLRI supporting IPv6.
    OpenDayLight Controller (NetVirt), including:
    BGP adaptations for Quagga BGB Stack interface
  


Pipeline changes
----------------

INTRA DC
+++++++++
Intra Subnet, Local DPN: VMs on the same subnet, same VPN, same DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are no changes

Intra Subnet, Remote DPN: VMs on two different DPNs, both VMs on the same subnet and same VPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are no changes

Inter Subnet, Local DPN: VMs on different subnet, same VPN, same DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are no changes

Inter Subnet, Remote DPN: VMs on two different DPNs, both VMs on different subnet, but same VPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The flow-rule will use the LPortTag as the vxlan-tunnel-id.
There are no changes, compared to [5].

INTER DC
+++++++++

Intra Subnet
^^^^^^^^^^^^^

Not supported in this Phase.
This feature needs some enhancements, in relationship to IPv6 neighbouring across DCs.

Inter Subnet
^^^^^^^^^^^^

There are no changes, compared to [5].
The specificity relies in the configuration where a VXLAN tunnel will be created between the compute
node and the DC-GW.
As the tunnel is set up with the DC-GW, when receiving a VXLAN packet, the VNI must be identified
against the **L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE (Table 23)**.
Ther are no changes in the pipeline process when sourcing a packet from the VM to the DC-GW

Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Classifier Table (0) =>
| L3VNI External Tunnel Demux Table (23) ``match: tun-id=l3vni set vpn-id=l3vpn-id`` =>
| L3 Gateway MAC Table (19) ``match: vpn-id=l3vpn-id, dst-mac=vpn-subnet-gateway-mac-address`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=dst-vm-ip-address set output to nexthopgroup-dst-vm`` =>
| NextHopGroup-dst-vm: ``set-eth-dst dst-mac-vm, reg6=dst-vm-lport-tag`` =>
| Lport Egress Table (220) ``Output to dst vm port``

Traffic from Local DPN to DC-Gateway (SYMMETRIC IRB)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| Classifier Table (0) =>
| Lport Dispatcher Table (17) ``l3vpn service: set vpn-id=l3vpn-id`` =>
| L3 Gateway MAC Table (19) ``match: vpn-id=l3vpn-id, dst-mac=vpn-subnet-gateway-mac-address`` =>
| L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=ext-ip-address set eth-dst-mac=dst-mac-address,
                     tun-id=l3vni, output to ext-vxlan-tun-port``


Access to External Network Access over VXLAN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This is part of an other specification document.
Indeed, this case will handle GUA IPv6 packets to reach an external network, like the Internet.
As per the nature of packets, there is no NAT translation.


Yang changes
------------

There are no specifities for IPv6 to bring in this specification.
However, it depends on 2 other specs.
- Changes from [5] will be used, especially for the VNI configuration.
- Changes defined in [2] will also be used, especially the additional afi paramter that qualifies what is
    the nature of the entry: IPv4 or IPv6.


changes in BGP Quagga Stack
---------------------------

The BGP Quagga Stack is enhanced to embrace EVPN with Route Type 5.
Ability to configure EVPN IPv6 RT5 entries.
Ability to receive ( via updates) EVPN IPv6 RT5 entries.


Netvirt - BGP adaptations for Quagga BGB Stack interface
----------------------------------------------

BGP adaptations depend from [2] by including the following:
- thrift changes in BGP manager
- add afi paramter to FIB entries
- VPN Engine APi calls
  
In addition to already done above changes, BGP Manager will be enhanced so as to support the following:
- some missing API calls will be enhanced so as to support the case where IPv6 entry may be configured.
- ability to configure EVPN from the following shell commands:
  o bgp-network ( add l3vni). l3vni value will be the l3label value passed
    as param to BGP. the l2vni value will be set to 0.
  o the vni detection will set gateway mac address to default,
      and tunnel type to vxlan.
  o bgp-nbr capability to support evpn ( add evpn address-family)


Netvirt - other - TO BE CHECKED
-------------------------------

Feature to check
^^^^^^^^^^^^^^^^

- IPv6 SubnetRoute support on EVPN
- IPPrefixInfo ( as it should work for LabelRouteInfo)


ARP request/response and MIP handling Support for EVPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Will not support IPv6 neighbouring across DCs, as we donot support intra-subnet inter-DC scenarios.

* For intra-subnet intra-DC scenarios, the NDs will be serviced by existing ELAN pipeline.
* For inter-subnet intra-DC scenarios, the ARPs will be processed by ARP Responder implementation that
    is already pursued in Carbon.
* For inter-subnet inter-DC scenarios, ARP requests won’t be generated by DC-GW.  Instead the DC-GW will
   use ‘gateway mac’ extended attribute MAC Address information and put that directly into DSTMAC field of
   Inner MAC Header by the DC-GW for all packets sent to VMs within the DC.
* As quoted, intra-subnet inter-DC scenario is not a supported use-case as per this Implementation Spec.

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
Not covered by this Design Document.

Targeted Release
-----------------
Carbon

Alternatives
------------
None

Usage
=====

Features to Install
-------------------
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

REST API
--------
The procedure used will be the same as the one described in [5].
However, the following workflow is rewritten here in an inter-DC case
where both the DCs are managed by two different Openstack Controller
Instances, the workflow will be to do the following:

1. Configure the DC-GW2 facing OSC2 and DC-GW1 facing OSC1 with the same BGP configuration parameters.
2. On first Openstack Controller (OSC1) create an L3VPN1 with RD1 and L3VNI1
3. Create a network Net1 and Associate that Network Net1 to L3VPN1
4. On second Openstack Controller (OSC2) create an L3VPN2 with RD1 with L3VNI2
5. Create a network Net2 on OSC2 and associate that Network Net2 to L3VPN2.
6. Spin-off VM1 on Net1 in OSC1.
7. Spin-off VM2 on Net2 in OSC2.
8. Now VM1 and VM2 should be able to communicate.

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

* Implement the complement netvirt code
* Uses the Quagga BGP IPv6 available stack.
* Unit Test implementation as it has been done for EVPN RT5, if necessary adaptation for IPv6.
    

Dependencies
============
Quagga from 6WIND is publicly available at the following url from [7].


Testing
=======

Unit Tests
----------
Unit tests provided for the BGP-EVPNv4 versions will be enhanced to also support
BGP-EVPNv6. No additional unit tests will be proposed.

Integration Tests
-----------------
Testing the main IPv6 use cases.

CSIT
----
CSIT provided for the BGPVPNv4 versions will be enhanced to also support
BGPVPNv6. No additional CSIT will be proposed.


Documentation Impact
====================
Necessary documentation would be added on how to use this feature.
User Guide should explain that it is possible to create IPv6 networks within BGPVPN vxlan VPNs.
EVPN RT5 IPv6 Use case description will be done so as to explain how one can connect IPv6 VPNs through EVPN RT5 BGPVPN.

References
==========
[1] `Spec to support IPv6 North-South support for Flat/VLAN Provider Network.
<https://git.opendaylight.org/gerrit/#/q/topic:ipv6-cvr-north-south>`_

[2] `IPv6 Inter-DC L3 North-South connectivity using L3VPN provider network types.
<https://git.opendaylight.org/gerrit/#/c/50359/>`_

[3] `EVPN_RT5 <https://tools.ietf.org/html/draft-ietf-bess-evpn-prefix-advertisement-03>`_

[4] `BGP MPLS-Based Ethernet VPN <https://tools.ietf.org/html/rfc7432>`_

[5] `Spec for L3Vpn Over Vxlan With Evpn RT5
<https://git.opendaylight.org/gerrit/#/c/48524/>`_

[6] `Spec to support IPv6 North-South support for Flat/VLAN Provider Network.
<https://git.opendaylight.org/gerrit/#/c/49909/>`_

[7] `Zebra Remote Procedure Call <https://github.com/6WIND/zrpcd/>`__

