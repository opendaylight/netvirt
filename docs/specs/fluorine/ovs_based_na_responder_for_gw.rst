.. contents:: Table of Contents
         :depth: 3

================================================
OVS Based NA Responder for IPv6 default Gateway
================================================

https://git.opendaylight.org/gerrit/#/q/topic:ovs_based_na_responder_for_gw

This spec addresses OVS Based Neighbor Advertisement(NA) responder for IPv6 default Gateway.
Neighbor Solicitation(NS) request which has initiated from IPv6 configured/enabled Data Center(DC) VMs are served by OVS based NA responder.


Problem description
===================

In IPv6, whenever a VM wants to communicate to a VM in a different subnet, the MAC address of the own IPv6 subnet gateway must be resolved. For that VM generates a Neighbor Solicitation(NS) message to resolve the IPv6 subnet gateway MAC address, which is currently being served by ODL controller which responds with Neighbor Advertisement(NA) message.

Having ODL controller dependency for resolving IPv6 subnet gateway MAC address would result in L3 forwarding failures whenever control plane is down (or if that OVS is disconnected from the Controller). To resolve this issue, it must be possible to resolve the gateway MAC in OVS itself.

This feature targets to provide OVS based NA responder to respond to NS message with GW MAC address during ODL controller downtime to achieve IPv6 L3 forwarding function to be continued.


Use Cases
---------
1. OVS based NA responder for gateway MAC address resolution.

2. OVS based NA responder for gateway MAC address for reachability analysis.

3. OVS based NA responder for hidden IPv6 address and MAC address.

4. Programming NA responder flows in OVS for IPv6 subnet gateway.


Proposed change
===============

OVS based NA responder for gateway MAC address resolution
----------------------------------------------------------

Background: OVS based ARP Responder for IPv4 gateway MAC resolution
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The current VPNv4 implementation ensures that OVS responds to ARP requests for resolving IPv4 default gateway MAC address. This will ensure that the L3 services continue to function even if ODL controller is down or OVS is disconnected from the ODL controller.


Proposal: 
^^^^^^^^^
OVS based IPv6 NA responder needs to be implemented to resolve the default gateway MAC address which will be similar to IPv4 OVS based ARP responder.


Configuration Variable to enable/disable OVS based NA responder:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Following configuration variable will be added to ipv6service module so that ODL controller must continue to support both controller based and OVS switch based NA responder.

::

  <ipv6service-config xmlns="urn:opendaylight:netvirt:ipv6service-config">
    <na-responder-mode>switch</na-responder-mode> or <na-responder-mode>controller</na-responder-mode>
  </ipv6service-config>

Default NA responder mode will be set it as switch mode.  

  
OVS based NA responder for gateway MAC address resolution:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
When a particular VM is booted in a network containing IPv6 subnet and the subnet is associated with a neutron router, the ODL controller will do the following match criteria and will install the appropriate open flow rules in the ARP_RESPONDER_TABLE (table 81) when responding to the NS request which has initiated from the IPv6 configured/enabled VMs.
 
Currently, NS packets for resolving gateway MAC address are punted to the ODL controller from IPV6_TABLE(table 45).

    .. code-block:: bash
	
       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0, priority=50,icmp6,metadata=0x138b000000/0xffff000000,icmp_type=135,icmp_code=0,nd_target=2001:db8:0:2:0:0:0:1 actions=CONTROLLER:65535
	
The action for the above flow needs to be changed to forward the NS packets to ARP_RESPONDER_TABLE(table 81) which will respond to the NS request for resolving gateway MAC address. For doing this NS to NA translation at ARP_RESPONDER_TABLE(table 81), it is required to change icmpv6_type from 135(NS) to 136(NA) and icmpv6_options_type to 2(TLL)

    .. code-block:: bash
	
       cookie=0x4000000, duration=3053.224s, table=45, n_packets=0, n_bytes=0, priority=50,icmp6,metadata=0x138b000000/0xffff000000,icmp_type=135,icmp_code=0,nd_target=2001:db8:0:2:0:0:0:1 actions=set_field:136->icmpv6_type,set_field:0->icmpv6_code,set_field:2->icmpv6_options_type,goto_table:81

For each VM port (Also for hidden IPs), OVS based NA responder flow will be programmed in ARP_RESPONDER_TABLE(table 81) as mentioned below.
	   
Neighbor Solicitation(NS) messages can be classified into two types

    * NS message having valid source IPv6 address (e.g., 2001:db8:0:2:f816:3eff:feef:c47a)
	
	   In this case ODL controller will program the NA responder flow with Unicast destination IPv6 address (Which is NS source IPv6 address). In this case NS request will contain the VMs vNIC MAC address information in the ICMPv6 option field Source Link Layer Address(SLL)
	   
		Example:
		
		.. code-block:: bash
		
		   cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6, icmp_type=136, metadata=0x900001138a000000/0xfffffffffffffffe, ipv6_src=2001:db8:0:2:f816:3eff:feef:c47a, nd_target=2001:db8:0:2:0:0:0:1 actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:00:23:15:d3:22:01->eth_src, move:NXM_NX_IPV6_SRC[]->NXM_NX_IPV6_DST[], set_field:2001:db8:0:2:0:0:0:1->ipv6_src, set_field:136->icmp_type, set_field:00:23:15:d3:22:01->nd-tll, set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,load:0->NXM_OF_IN_PORT[],output:2
	
	* NS message having unspecified (::) source IPv6 address
	
	   In this case ODL controller will program the NA responder flow with all node multicast(ff02::1) group address as destination. In this case NS request should not contain VMs vNIC MAC address information in the ICMPv6 option SLL field.
	   
	   Example:
	
	    .. code-block:: bash
		
		   cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6, icmp_type=136, metadata=0x900001138a000000/0xfffffffffffffffe, ipv6_src=0:0:0:0:0:0:0:0, nd_target=2001:db8:0:2:0:0:0:1 actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:00:23:15:d3:22:01->eth_src, set_field:ff02::1->ipv6_dst, set_field:2001:db8:0:2:0:0:0:1->ipv6_src, set_field:136->icmp_type, set_field:00:23:15:d3:22:01->nd-tll, set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,load:0->NXM_OF_IN_PORT[],output:2
		   


OVS based NA responder for gateway MAC address for reachability analysis
-------------------------------------------------------------------------
After the MAC address for a particular gateway is resolved, the IPv6 VM periodically generates NS requests to ensure the neighbor is reachable. 

    * This message can arrive as a Unicast message addressed to the Gateway MAC
       * NS can be sent from both Neutron ports and hidden IPs
		
    * The message format can be different than the broadcast/multicast NS message
       * The option field MAY/MAY NOT contain source link layer address
		
    * For such messages, a response must be generated. However, the response NEED NOT include the MAC address
	   * With proposal, gateway MAC address is not been included in the NA response.


Programming NA responder flows in OVS for IPv6 subnet gateway:
--------------------------------------------------------------
The following cases needs to be handled for programming/un-programming the OVS based NA responder flows.

1) Router Association to subnet
2) Router disassociation from subnet
3) VM boot-up on a OVS
4) VM shutdown 
5) VM Migration
6) VM Port Update
7) OVS disconnections


Pipeline changes
----------------
Flow needs to be programmed in IPv6 table (45) for redirecting the Neighbor Solicitation(NS) packets to table 81 (ARP_RESPONDER_TABLE) matching with ND target address as IPv6 subnet GW IP.

    .. code-block:: bash
	
       cookie=0x4000000, duration=506.885s, table=17, n_packets=0, n_bytes=49916, priority=10, metadata=0xc60000000000/0xffffff0000000000 actions=write_metadata:0x8000c61422000000/0xfffffffffffffffe, goto_table:45
  
       cookie=0x4000000, duration=506.974s, table=45, n_packets=0, n_bytes=0, priority=50, icmp6, metadata=0x1422000000/0xffff000000, icmp_type=135, icmp_code=0, nd_target=<GW-IP> actions=set_field:136->icmpv6_type,set_field:0->icmpv6_code,set_field:2->icmpv6_options_type,goto_table:81


OVS NA responder flow for GW MAC resolution for NS packet with containing Option SLL field and valid IPv6 source address:
    
	.. code-block:: bash
	
	   cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6, icmp_type=136, metadata=<matches elan + lport tag>, ipv6_src=<VM-IP-Address>, nd_target=<GW-IP>, nd_sll=<VM-MAC-Address> actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:<GW-Mac-Address>->eth_src, move:NXM_NX_IPV6_SRC[]->NXM_NX_IPV6_DST[], set_field:<GW IP>->ipv6_src, set_field:136->icmp_type, set_field:<GW-mac-Address>->nd-tll, set_field:OxE000-> OFPXMT_OFB_ICMPV6_ND_RESERVED,load:0->NXM_OF_IN_PORT[],output:<VM port>
	
OVS NA responder flow for GW MAC address reachability checking for NS packet without containing Option SLL field and valid IPv6 source address:

    .. code-block:: bash
	
       cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6, icmp_type=136, metadata=<matches elan + lport tag>, ipv6_src=<VM-IP-Address>, nd_target=<GW-IP>, nd_sll=<Wildcard the match> actions= move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:<GW-Mac-Address>->eth_src, set_field:<All_Node_Multicast_Address>->ipv6_dst, set_field:<GW IP>->ipv6_src, set_field:136->icmp_type, set_field:OxE000->OFPXMT_OFB_ICMPV6_ND_RESERVED,load:0->NXM_OF_IN_PORT[],output:<VM port>
	   
OVS NA responder flow for GW MAC resolution for NS packet without containing Option SLL field and unspecified IPv6 source address: 

    In this case NS request has to punt the packets to the ODL controller to respond the NA response. Since without SLL option from the NS request OVS switch may not be set TLL filed in NA response packet.

    .. code-block:: bash
	
       cookie=0x12220d57, duration=0.0s, table=81, n_packets=0, n_bytes=0, priority=80, icmp6, icmp_type=136, metadata=<matches elan + lport tag>, ipv6_src=0:0:0:0:0:0:0:0, nd_target=<GW-IP>, actions=CONTROLLER:65535	   

Yang changes
------------
For the new configuration knob a new yang ipv6service-config shall be added in IPv6 service, with the
container for holding the IPv6 NA responder mode configured. It will have two options controller and switch,
with switch being the default.

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

The dynamic update of na-responder-mode will not be supported. To change the na-responder-mode the controller cluster
needs to be restarted after changing the na-responder-mode. On restart the IPv6 NA responder for gateway MAC address lifecycle will be reset and after the controller comes up in the updated na-responder-mode, a new set of ovs flows will be
installed on the openvswitch and it can be different from the ones that were forwarding
traffic earlier.

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
The new OVS based NA responder implementation is expected to improve the performance when compared to the existing
one and will reduce the overhead of the ODL controller.

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
 
 openstack subnet create --network vpn6_net_1 --subnet-range 2001:db8:0:2::/64 vpn6_sub_1 --ip-version=6 --ipv6-address-mode=slaac --ipv6-ra-mode=slaac --allocation-pool start=2001:db8:0:2::2,end=2001:db8:0:2:ffff:ffff:ffff:fffe

 openstack subnet create --network vpn6_net_2 --subnet-range 2001:db8:0:3::/64 vpn6_sub_2 --ip-version=6 --ipv6-address-mode=slaac --ipv6-ra-mode=slaac --allocation-pool start=2001:db8:0:3::2,end=2001:db8:0:3:ffff:ffff:ffff:fffe
 
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
The following changes are required to support this feature in ODL controller.

1) The following OF Plugin Support is required in ODL to support OVS based NA responder solution.

   (i) NA Flags (Router[R], Solicitation[S], Override[O], Reserved).
   (ii) Re-computation of ICMPv6 checksum logic. 

2) OVS changes for supporting NA Flags and icmpv6_options_type TLL set if TLL field is not present in the packet.

3) For two-router use cases, this feature is dependent on [1].

Testing
=======

Unit Tests
----------
Unit test needs to be added for the new OVS based NA responder mode. It shall use the component tests framework

Integration Tests
-----------------
Integration tests needs to be added for the OVS based NA responder flows.

CSIT
----
Run the CSIT with OVS based NA responder configured.

Documentation Impact
====================
Necessary documentation would be added on how to use this feature.

References
==========

[1] `Spec to support L3VPN dual stack for VMs
<https://git.opendaylight.org/gerrit/#/c/54089/>`_