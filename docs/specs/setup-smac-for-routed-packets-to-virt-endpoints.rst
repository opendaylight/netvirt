.. contents:: Table of Contents
   :depth: 3

=========================================================================
Setup Source-MAC-Address for routed packets destined to virtual endpoints
=========================================================================

https://git.opendaylight.org/gerrit/#/q/topic:SMAC_virt_endpoints

All L3 Routed packets destined to virtual endpoints in the datacenter managed by ODL
do not carry a proper source-mac address in such frames put out to virtual endpoints.

This spec makes sure a proper source-mac is updated in the packet at the point where the
packet is delivered to the VM, regardless of the tenant network type. On the actual datapath,
there will be no change in the source mac-addresses and packets continue to use the same
mechanism that is used today.

Addressing the datapath requires unique MAC allocation per OVS Datapath, so that it can be
used as the source MAC for all distributively routed packets of an ODL enabled cloud. It
would be handled in some future spec.

Problem description
===================
Today all L3 Routed packets destined to virtual endpoints in the datacenter either

* Incorrectly carry the source mac-address of the originator (regardless of which network the originator is in)
* Incorrectly carry sometimes the reserved source mac address of 00:00:00:00:00:00

This spec is intended to setup a source-mac-address in the frame of L3 Routed packets just before
such frames are directed into the virtual endpoints themselves.  This enables use-cases where certain
virtual endpoints which are VNFs in the datacenter that are source-mac conscious (or mandate that src-mac
in frames be valid) can become functional on their instantiation in an OpenDaylight enabled cloud.

Use Cases
---------
* Intra-Datacenter L3 forwarded packets within a hypervisor.
* Intra-Datacenter L3 forwarded packets over Internal VXLAN Tunnels between two hypervisors in the datacenter.
* Inter-Datacenter L3 forwarded packets :

  *  Destined to VMs associated floating IP over External VLAN Provider Networks.
  *  Destined to VMs associated floating IP over External MPLSOverGRE Tunnels.
  *  SNAT traffic from VMs over External MPLSOverGRE Tunnels.
  *  SNAT traffic from VMS over External VLAN Provider Networks.


Proposed change
===============
All the L3 Forwarded traffic today reaches the VM via a LocalNextHopGroup managed by
the VPN Engine (including FIBManager).

Currently the LocalNextHopGroup sets-up the destination MAC Address of the VM and forwards the traffic
to EGRESS_LPORT_DISPATCHER_TABLE (Table 220). In that LocalNextHopGroup we will additionally setup
source-mac-address for the frame.  There are two cases to decide what source-mac-address should go
into the frame:

* If the VM is on a subnet (on a network) for which a subnet gatewayip port exists, then the
  source-mac address of that subnet gateway port will be setup as the frame's source-mac
  inside the LocalNextHop group.This is typical of the case when a subnet is added to a router,
  as the router interface port created by neutron will be representing the subnet's gateway-ip address.

* If the VM is on a subnet (on a network), for which there is no subnet gatewayip port but that network
  is part of a BGPVPN , then the source-mac address would be that of the connected mac-address of the
  VM itself.  The connected mac-address is nothing but the mac-address on the ovs-datapath for the VMs
  tapxxx/vhuxxx port on that hypervisor itself.

The implementation also applies to Extra-Routes (on a router) and Discovered Routes as they both use the
LocalNextHopGroup in their last mile to send packets into their Nexthop VM.

We need to note that when a network is already part of a BGPVPN, adding a subnet on such a network to
a router is disallowed currently by NeutronVPN.  And so the need to swap the mac-addresses inside
the LocalNextHopGroup to reflect the subnet gatewayip port here does not arise.

For all the use-cases listed in the USE-CASES section above, proper source mac address will be filled-up
in the frame before it enters the virtual endpoint.

Pipeline changes
----------------
There are no pipeline changes.

The only change is in the NextHopGroup created by VPN Engine (i.e., VRFEntryListener).  In the NextHopGroup we
will additionally fill up the ethernet source mac address field with proper mac-address as outlined in the
'Proposed change' section.

Currently the LocalNextHopGroup is used in the following tables of VPN Pipeline:

* L3_LFIB_TABLE (Table 20)  - Lands all routed packets from MPLSOverGRE tunnel into the virtual endpoint.

* INTERNAL_TUNNEL_TABLE (Table 36)  - Lands all routed packets on Internal VXLAN Tunnel within the DC into the
  virtual end point.

* L3_FIB_TABLE (Table 21) - Lands all routed packets within a specific hypervisor into the virtual endpoint.

.. code-block:: bash

   cookie=0x8000002, duration=50.676s, table=20, n_packets=0, n_bytes=0, priority=10,mpls,mpls_label=70006 actions=write_actions(pop_mpls:0x0800,group:150000)
   cookie=0x8000003, duration=50.676s, table=21, n_packets=0, n_bytes=0, priority=42,ip,metadata=0x222f2/0xfffffffe,nw_dst=10.1.1.3 actions=write_actions(group:150000)
   cookie=0x9011176, duration=50.676s, table=36, n_packets=0, n_bytes=0, priority=5,tun_id=0x11176 actions=write_actions(group:150000)

   NEXTHOP GROUP:
   group_id=150000,type=all,bucket=actions=set_field:fa:16:3e:01:1a:40->eth_src,set_field:fa:16:3e:8b:c5:51->eth_dst,load:0x300->NXM_NX_REG6[],resubmit(,220)

Yang changes
------------
None.

Configuration impact
---------------------
None.

Clustering considerations
-------------------------
None.

Other Infra considerations
--------------------------
None.

Security considerations
-----------------------
None.

Scale and Performance Impact
----------------------------
None

Targeted Release
-----------------
Carbon/Boron

Alternatives
------------
None.

Usage
=====
N/A.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
N/A.

CLI
---
N/A.

Implementation
==============

Assignee(s)
-----------
Primary assignee:

* Achuth Maniyedath (achuth.m@altencalsoftlabs.com)

Other contributors:

* Karthik Prasad (karthik.p@altencalsoftlabs.com)
* Vivekanandan Narasimhan (n.vivekanandan@ericsson.com)

Work Items
----------
https://trello.com/c/IfAmnFFr/110-add-source-macs-in-frames-for-l3-routed-packets-before-such-frames-get-to-the-virtual-endpoint

* Determine the smac address to be used for L3 packets forwarded to VMs.
* Update the LocalNextHopGroup table with proper ethernet source-mac parameter.

Dependencies
============
No new dependencies.

Testing
=======
Verify the Source-MAC-Address setting on frames forwarded to Virtual endpoints in following cases.

Intra-Datacenter traffic to VMs (Intra/Inter subnet).

* VM to VM traffic within a hypervisor.
* VM to VM traffic across hypervisor over Internal VXLAN tunnel.

Inter-Datacenter traffic to/from VMs.

* External access to VMs using Floating IPs on MPLSOverGRE tunnels.
* External access to VMs using Floating IPs over VLAN provider networks.
* External access from VMs using SNAT over VLAN provider networks.
* External access from VMs using SNAT on MPLSOverGRE tunnels.

Unit Tests
----------
N/A.

Integration Tests
-----------------
N/A.

CSIT
----
* Validate that router-interface src-mac is available on received frames within the VM when that VM is on a router-arm.
* Validate that connected-mac as src-mac available on received frames within the VM when that VM is on a network-driven L3 BGPVPN.

Documentation Impact
====================
N/A

References
==========
N/A
