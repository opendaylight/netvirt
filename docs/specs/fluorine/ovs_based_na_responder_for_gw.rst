.. contents:: Table of Contents
         :depth: 3

================================================
OVS Based NA Responder for IPv6 default Gateway
================================================

https://git.opendaylight.org/gerrit/#/q/topic:ovs_based_na_responder_for_gw

This spec addresses OVS Based Neighbor Advertisement(NA) responder for IPv6 default Gateway.
Neighbor Solicitation(NS) request which has initiated from IPv6 configured/enabled
Data Center(DC) VMs are served by OVS based NA responder.


Problem description
===================

In IPv6, whenever a VM wants to communicate to a VM in a different subnet, the MAC address of the
IPv6 subnet gateway must be resolved. For that VM generates a Neighbor Solicitation(NS)
message to resolve the IPv6 subnet gateway MAC address, which is currently being served by ODL
controller which responds with Neighbor Advertisement(NA) message.

Having ODL controller dependency for resolving IPv6 subnet gateway MAC address would result in L3
forwarding failures whenever control plane is down (or if that OVS is disconnected from the
Controller). To resolve this issue, it must be possible to resolve the gateway MAC in OVS itself.

This feature targets to provide OVS based NA responder to respond to NS message with GW MAC
address during ODL controller downtime to achieve IPv6 L3 forwarding function to be continued.


Use Cases
---------
1. OVS based NA responder for gateway MAC address resolution.

2. OVS based NA responder for gateway MAC address for reachability detection.

3. OVS based NA responder for hidden IPv6 address and MAC address.


Proposed change
===============

OVS based NA responder for gateway MAC address resolution
----------------------------------------------------------

Background: OVS based ARP Responder for IPv4 gateway MAC resolution
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The current VPNv4 implementation ensures that OVS responds to ARP requests for resolving IPv4
default gateway MAC address. This will ensure that the L3 services continue to function even
if ODL controller is down or OVS is disconnected from the ODL controller.


Proposal:
^^^^^^^^^
OVS based IPv6 NA responder needs to be implemented to resolve the default gateway MAC address
which will be similar to IPv4 OVS based ARP responder.


Configuration Variable to enable/disable OVS based NA responder:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Following configuration variable will be added to ipv6service module so that ODL controller
must continue to support both controller based and OVS switch based NA responder.

::

  <ipv6service-config xmlns="urn:opendaylight:netvirt:ipv6service-config">
    <na-responder-mode>switch</na-responder-mode> or <na-responder-mode>controller</na-responder-mode>
  </ipv6service-config>

Default NA responder mode will be set it as switch mode.

Openflow Plugin Changes:
^^^^^^^^^^^^^^^^^^^^^^^^
The OF plugin in ODL will be enhanced to support below OVS extension in
OF plugin master branch.

    * OFPXMT_OFB_ICMPV6_ND_RESERVED
       * Options-(Router[R], Solicitation[S], Override[O])

    * OFPXMT_OFB_ICMPV6_NS_OPTION_TYPE
       * Options-(1->SLL, 2->TLL)


OVS based NA responder for gateway MAC address resolution:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
When a VM is booted in a network containing IPv6 subnet and the subnet is associated
with a neutron router, the ODL controller will do the following match criteria and will install
the appropriate open flow rules in the ARP_RESPONDER_TABLE (table 81) when responding to the NS
request which has initiated from the IPv6 configured/enabled VMs.

Currently, NS packets for resolving gateway MAC address are punted to the ODL controller from
IPV6_TABLE(table 45).

The Neutron Router port has two IPs. One from the Subnet CIDR and the other which is the Link Local Address(LLA)

 * Neutron router port having IPv6 subnet CIDR IP.

    .. code-block:: bash

       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0,
       priority=50,icmp6,metadata=0x900004138a000000/0xfffffffffffffffe,icmp_type=135,icmp_code=0,
       nd_target=2001:db8:0:2:0:0:0:1 actions=CONTROLLER:65535

 * Neutron router port having IPv6 Link Local Address(LLA).

    .. code-block:: bash

       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0,
       priority=50,icmp6,metadata=0x900004138a000000/0xfffffffffffffffe,icmp_type=135,icmp_code=0,
       nd_target=fe80::f816:3eff:fecc:9e83 actions=CONTROLLER:65535


The action for the above flow needs to be changed to forward the NS packets to
ARP_RESPONDER_TABLE(table 81) which will respond to the NS request for resolving gateway
MAC address. For doing this NS to NA translation at ARP_RESPONDER_TABLE(table 81),
it is required to change icmpv6_type from 135(NS) to 136(NA) and icmpv6_options_type to 2 as
Target Link Layer Address (TLL)

    .. code-block:: bash

       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0,
       priority=50,icmp6,metadata=0x4138a000000/0xfffffffff000000,icmp_type=135,icmp_code=0,
       nd_target=2001:db8:0:2:0:0:0:1, nd_sll=fa:16:3e:55:ad:df
       actions=set_field:136->icmpv6_type,set_field:0->icmpv6_code,set_field:2->icmpv6_options_type,goto_table:81

       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0,
       priority=50,icmp6,metadata=0x4138a000000/0xfffffffff000000,icmp_type=135,icmp_code=0,
       nd_target=fe80::f816:3eff:fecc:9e83, nd_sll=fa:16:3e:55:ad:df
       actions=set_field:136->icmpv6_type,set_field:0->icmpv6_code,set_field:2->icmpv6_options_type,goto_table:81


For each VM port (Also for hidden IPs), OVS based NA responder flow will be programmed in
ARP_RESPONDER_TABLE(table 81) as mentioned below.

Neighbor Solicitation(NS) messages can be classified into two types

 * NS message having valid source IPv6 address (e.g., 2001:db8:0:2:f816:3eff:feef:c47a) and source MAC address
   (e.g., 00:11:22:33:44:55)

    In this case ODL controller will program the NA responder flow with Unicast
    destination IPv6 address (Which is NS source IPv6 address). In this case
    NS request will contain the VMs vNIC MAC address information in the ICMPv6
    option field Source Link Layer Address(SLL).

    Example:

    .. code-block:: bash

       cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6,
       icmp_type=136,icmp_code=0, metadata=0x4138a000000/0xfffffffff000000,nd_target=2001:db8:0:2:0:0:0:1
       actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:00:23:15:d3:22:01->eth_src,
       move:NXM_NX_IPV6_SRC[]->NXM_NX_IPV6_DST[],set_field:2001:db8:0:2:0:0:0:1->ipv6_src,
       set_field:00:23:15:d3:22:01->nd-tll,set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,
       load:0->NXM_OF_IN_PORT[],output:2

       cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6,
       icmp_type=136,icmp_code=0, metadata=0x4138a000000/0xfffffffff000000,nd_target=fe80::f816:3eff:fecc:9e83
       actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:00:23:15:d3:22:01->eth_src,
       move:NXM_NX_IPV6_SRC[]->NXM_NX_IPV6_DST[],set_field:fe80::f816:3eff:fecc:9e83->ipv6_src,
       set_field:00:23:15:d3:22:01->nd-tll,set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,
       load:0->NXM_OF_IN_PORT[],output:2

Note:
In this case following NA flags will be set
Router -> 1
Solicitation -> 1
Override -> 1


 * NS message having unspecified (::) source IPv6 address

    In this case NS request needs to be redirecting the packets to the ODL controller for responding
    to the NS request. Since without SLL option from the NS request OVS switch may not be set TLL filed
    in NA response packet.

    Example:

    .. code-block:: bash

       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0,
       priority=50,icmp6,metadata=0x900004138a000000/0xfffffffffffffffe,icmp_type=135,icmp_code=0,
       nd_target=2001:db8:0:2:0:0:0:1 actions=CONTROLLER:65535

       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0,
       priority=50,icmp6,metadata=0x900004138a000000/0xfffffffffffffffe,icmp_type=135,icmp_code=0,
       nd_target=fe80::f816:3eff:fecc:9e83 actions=CONTROLLER:65535

Note:
In this case if there is no specific match found in IPV6_TABLE(table 45) for NS packet, it will be redirecting to the ODL controller matching with elan tag value in metadata field.

All the mentioned example flows in the spec will require changes in the OVS to support new attributes(OFPXMT_OFB_ICMPV6_ND_RESERVED and OFPXMT_OFB_ICMPV6_NS_OPTION_TYPE) and we will be working on getting those changes into OVS community.


OVS based NA responder for gateway MAC address for reachability analysis
-------------------------------------------------------------------------
After the MAC address for a particular gateway is resolved, the IPv6 VM periodically
generates NS requests to ensure the neighbor is reachable.

    * This message can arrive as a Unicast message addressed to the Gateway MAC
        * NS can be sent from both Neutron ports and hidden IPs.

    * The message format can be different than the broadcast/multicast NS message
        * The option field MAY/MAY NOT contain source link layer address.

    * For such messages, a response must be generated. However, the response NEED NOT include the MAC address
        * With proposal, gateway MAC address is not included in the NA response.


Programming NA responder flows in OVS for IPv6 subnet gateway:
--------------------------------------------------------------
The following cases needs to be handled for programming/un-programming the OVS based NA
responder flows.

1) Router Association to subnet
2) Router disassociation from subnet
3) VM boot-up on a OVS
4) VM shutdown
5) VM Migration
6) VM Port Update
7) OVS disconnections


Pipeline changes
----------------
Flow needs to be programmed in IPV6_TABLE(table 45) for redirecting the Neighbor Solicitation(NS)
packets to ARP_RESPONDER_TABLE(table 81) matching with ND target address as IPv6 subnet GW IP.

    .. code-block:: bash

       cookie=0x4000000, duration=506.885s, table=17, n_packets=0, n_bytes=49916, priority=10,
       metadata=0xc60000000000/0xffffff0000000000 actions=write_metadata:0x900004138a000000/0xfffffffffffffffe,
       goto_table:45

       cookie=0x4000000, duration=506.974s, table=45, n_packets=0, n_bytes=0, priority=50, icmp6,
       metadata=0x4138a000000/0xfffffffff000000, icmp_type=135, icmp_code=0, nd_target=<Subnet_CIDR_GW_IP>,
       nd_sll=fa:16:3e:55:ad:df
       actions=set_field:136->icmpv6_type,set_field:0->icmpv6_code,set_field:2->icmpv6_options_type,goto_table:81

       cookie=0x4000000, duration=506.974s, table=45, n_packets=0, n_bytes=0, priority=50, icmp6,
       metadata=0x4138a000000/0xfffffffff000000, icmp_type=135, icmp_code=0, nd_target=<Router_port_LLA>,
       nd_sll=fa:16:3e:55:ad:df
       actions=set_field:136->icmpv6_type,set_field:0->icmpv6_code,set_field:2->icmpv6_options_type,goto_table:81


OVS NA responder flow for GW MAC resolution for NS packet which contains SLL option field and
valid IPv6 source address:

    .. code-block:: bash

       cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80,icmp6,
       icmp_type=136, metadata=<matches elan + lport tag>, nd_target=<Subnet_CIDR_GW_IP>
       actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],
       set_field:<GW-Mac-Address>->eth_src,move:NXM_NX_IPV6_SRC[]->NXM_NX_IPV6_DST[],
       set_field:<Subnet_CIDR_GW_IP>->ipv6_src,set_field:<GW-mac-Address>->nd-tll,
       set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,load:0->NXM_OF_IN_PORT[],output:<VM port>

       cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80,icmp6,
       icmp_type=136, metadata=<matches elan + lport tag>, nd_target=<Router_port_LLA>
       actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],
       set_field:<GW-Mac-Address>->eth_src,move:NXM_NX_IPV6_SRC[]->NXM_NX_IPV6_DST[],
       set_field:<Router_port_LLA>->ipv6_src,set_field:<GW-mac-Address>->nd-tll,
       set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,load:0->NXM_OF_IN_PORT[],output:<VM port>

OVS NA responder flow for GW MAC address reachability checking for NS packet without containing Option SLL
field and valid IPv6 source address:

    .. code-block:: bash

       cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6, icmp_type=136,
       metadata=<matches elan + lport tag>,nd_target=<Subnet_CIDR_GW_IP>
       actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],
       set_field:<GW-Mac-Address>->eth_src,move:NXM_NX_IPV6_SRC[]->NXM_NX_IPV6_DST[],
       set_field:<Subnet_CIDR_GW_IP>->ipv6_src,
       set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,load:0->NXM_OF_IN_PORT[],output:<VM port>

       cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6, icmp_type=136,
       metadata=<matches elan + lport tag>,nd_target=<Router_port_LLA>
       actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],
       set_field:<GW-Mac-Address>->eth_src,move:NXM_NX_IPV6_SRC[]->NXM_NX_IPV6_DST[],
       set_field:<Router_port_LLA>->ipv6_src,
       set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,load:0->NXM_OF_IN_PORT[],output:<VM port>

OVS NA responder flow for GW MAC resolution for NS packet without containing Option SLL field and
unspecified IPv6 source address:

    In this case NS request needs to be redirecting the packets to the ODL controller for responding
    to the NS request. Since without SLL option field from the NS request OVS switch may not be able to
    set TLL filed in NA response packet.

    .. code-block:: bash

       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0,
       priority=50,icmp6,metadata=0x900004138a000000/0xfffffffffffffffe,icmp_type=135,icmp_code=0,
       nd_target=2001:db8:0:2:0:0:0:1 actions=CONTROLLER:65535

       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0,
       priority=50,icmp6,metadata=0x900004138a000000/0xfffffffffffffffe,icmp_type=135,icmp_code=0,
       nd_target=fe80::f816:3eff:fecc:9e83 actions=CONTROLLER:65535


Yang changes
------------
For the new configuration knob a new yang ipv6service-config shall be added in IPv6 service,
with the container for holding the IPv6 NA responder mode configured. It will have two options
controller and switch, with switch being the default.

::

  container ipv6service-config {
    config true;
    leaf na-responder-mode {
        type enumeration {
            enum "controller";
            enum "switch";
        }
        default "switch";
    }
  }

Limitations
-----------
ODL controller dependency is still required for one of the corner UC as below.

    * NS packet without containing Option SLL field and unspecified IPv6 source address (::)

Configuration impact
--------------------
The proposed change requires the IPv6 service to provide a configuration knob to switch between the
controller based/switch based implementation. A new configuration file
netvirt-ipv6service-config.xml shall be added with default value switch.

::

  <ipv6service-config xmlns="urn:opendaylight:netvirt:ipv6service-config">
    <na-responder-mode>switch</na-responder-mode>
  </ipv6service-config>

The dynamic update of na-responder-mode will not be supported. To change the na-responder-mode
the controller cluster needs to be restarted after changing the na-responder-mode. On restart the
IPv6 NA responder for gateway MAC address lifecycle will be reset and after the controller comes up
in the updated na-responder-mode, a new set of ovs flows will be installed on the openvswitch and
it can be different from the ones that were forwarding traffic earlier.

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
The new OVS based NA responder implementation is expected to improve the performance when compared
to the existing one and will reduce the overhead of the ODL controller.

Targeted Release
-----------------
Fluorine

Alternatives
------------
None

Usage
=====

Create Internal Networks and Subnets
------------------------------------

::

 openstack network create vpn6_net_1
 openstack network create vpn6_net_2

 openstack subnet create --network vpn6_net_1 --subnet-range 2001:db8:0:2::/64 vpn6_sub_1 --ip-version=6 --ipv6-address-mode=slaac --ipv6-ra-mode=slaac

 openstack subnet create --network vpn6_net_2 --subnet-range 2001:db8:0:3::/64 vpn6_sub_2 --ip-version=6 --ipv6-address-mode=slaac --ipv6-ra-mode=slaac

Create router
-------------
::

 openstack router create vpn6_router

Attach IPv6 Subnets to Router
-----------------------------
::

 openstack router add subnet vpn6_router vpn6_sub_1
 openstack router add subnet vpn6_router vpn6_sub_2

Create VPNv6 Security Group
-----------------------------
::

 openstack security group create vpn6_sg
 openstack security group rule create vpn6_sg --ingress --ethertype IPv6 --dst-port 1:65535 --protocol tcp
 openstack security group rule create vpn6_sg --egress --ethertype IPv6 --dst-port 1:65535 --protocol tcp
 openstack security group rule create vpn6_sg --ingress --ethertype IPv6 --protocol icmp
 openstack security group rule create vpn6_sg --egress --ethertype IPv6 --protocol icmp
 openstack security group rule create vpn6_sg --ingress --ethertype IPv6 --dst-port 1:65535 --protocol udp
 openstack security group rule create vpn6_sg --egress --ethertype IPv6 --dst-port 1:65535 --protocol udp

Create VM ports:
----------------
::

 openstack port create --network vpn6_net_1 vpn6_net_1_port_1 --security-group vpn6_sg
 openstack port create --network vpn6_net_2 vpn6_net_2_port_1 --security-group vpn6_sg

Boot VMs:
---------
::

 openstack server create --image <VM-Image> --flavor <VM-Flavor> --nic port-id=vpn6_net_1_port_1 --availability-zone nova:<Hypervisor-Name> <VM-Name>
 openstack server create --image <VM-Image> --flavor <VM-Flavor> --nic port-id=vpn6_net_2_port_1 --availability-zone nova:<Hypervisor-Name> <VM-Name>

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
No new REST API being added.

CLI
---
No new CLI being added.

Implementation
==============

Assignee(s)
-----------
Primary assignee:
  Karthikeyan Krishnan <karthikeyan.k@altencalsoftlabs.com/karthikeyangceb007@gmail.com>

Other contributors:
  Somashekar Byrappa <somashekar.b@altencalsoftlabs.com>

  Nithi Thomas <nithi.t@altencalsoftlabs.com>


Work Items
----------
* Write a framework which can support multiple modes of NA responder implementation.
* Add support in openflow plugin for OVS based NA responder actions.
* Add support in genius for OVS based NA responder actions.
* Add a config parameter to select between controller based and ovs based NA responder.
* Add the flow programming for OVS based NA responder in netvirt.
* Write Unit tests for OVS based NA responder.

Dependencies
============
The following OVS extensions are required to support this feature on ODL controller.

    * The OVS must implement the OF extensions to support match and set field actions for the
      RESERVED field(OFPXMT_OFB_ICMPV6_ND_RESERVED) of NA message.

    * The OVS must implement the OF extension to modify to the type field of the NS Option
      from SLL to TLL(OFPXMT_OFB_ICMPV6_NS_OPTION_TYPE).


Testing
=======

The test cases for this feature must cover dual-stack and
single-stack VMs and test the OVS based NA responder for both switch and controller mode.
This feature should not break any functionality of the existing controller based NA responder.

Test cases below:

#. Verify the OVS Responder Flows for Gateway MAC resolution.
#. Verify the OVS Responder Flows for Reachability analysis.
#. Verify the L2 Data Traffic(ELAN) with Single OVS.
#. Verify the L2 Data Traffic(ELAN) with Multiple OVS.
#. Verify the L3 Data Traffic(L3VPN) with Router Associated with BGP-VPN.
#. Verify the L3 Data Traffic with IPv6 Subnet added to Router.
#. Verify the OVS Responder Flows when OVS is Disconnected.
#. Verify the L3 Data Traffic(L3VPN) when ODL is Disconnected from OVS.

Unit Tests
----------
Unit test needs to be added for the new OVS based NA responder mode. It shall use the component
tests framework

Integration Tests
-----------------
Integration tests needs to be added for the OVS based NA responder flows.

CSIT
----
The following new CSIT test cases will be added for this feature.

#. Verify the data plane traffic between VM1 and VM2 on same network when ODL controller is down.
#. Verify the data plane traffic between VM1 and VM2 on different network when ODL controller is down.
#. Verify the data plane traffic between VM1 and VM2 on L3 BGP-VPN Scenario when ODL controller is down.
#. Verify the data plane traffic between VM1 and VM2 on same network when ODL controller is Up.
#. Verify the data plane traffic between VM1 and VM2 on different network when ODL controller is Up.
#. Verify the data plane traffic between VM1 and VM2 on same network with single router dual stack network configured VMs.
#. Verify the data plane traffic between VM1 and VM2 on different network with single router dual stack network configured VMs.
#. Verify the data plane traffic between hidden IPv6 configured on VM1 and neutron configured IPv6 on VM2 on same network.
#. Verify the data plane traffic between hidden IPv6 configured on VM1 and neutron configured IPv6 on VM2 on different network.

Documentation Impact
====================
Necessary documentation would be added on how to use this feature.

References
==========

[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] `Neighbor Discovery for IP version 6 (IPv6) <https://tools.ietf.org/html/rfc4861>`__