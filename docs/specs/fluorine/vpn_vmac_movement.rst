.. contents:: Table of Contents
      :depth: 3

=====================================================================
Enable discovery of Virtual IPs (or Movable IPs) holding Virtual MACs
=====================================================================

https://git.opendaylight.org/gerrit/#/q/topic:vpn_vmac_movement

Enable discovery of Movable (or Virtual IPs) which have their own sticky
Virtual MACs, in an L3VPN domain.

The discovery of these virtual IPs will be triggered by either IP traffic
originated from a source outsidethe DC (same L3VPN domain) towards such Virtual
IPs (or) from IP traffic originated from a source within theDC
(again within same L3VPN domain) towards such Virtual IPs.

Also ensure to enforce discovery of new locations of such Virtual IPs
(along with their Virtual MACs) whenthey move inside the DC and program
the routes appropriately thereby providing continual L3 connectivity
towards such Virtual IPs, both from within and from outside DC.

Virtual IP and Moveable IP are used interchangeably in this spec and both \
of them simply represent a moveable virtual IP.

Problem description
===================

Discovery of Virtual IPs , which hold the MAC address of their hosted Virtual Ports
(instead of holding their own Virtual MAC) is already supported by L3VPN service today
via the SubnetRoute feature.

In NFVI production environments that run VNFs, for providing high-availability for those
VNFs , VRRP is run between two such VNF instances.  With VRRP configuration, a Virtual IP
is chosen and that Virtual IP is used to access the VNF by the tenants.   This Virtual IP
will move around between the two VNF instances based on whichever is alive (or whichever has higher priority).
If only a Virtual IP is used by VRRP with the MAC being the same as the hosting VNF interface MAC,
then that use-case is already supported by SubnetRoute feature.

However, it is possible to configure Virtual MAC to such Virtual IPs wherein those Virtual
MACs are different from the VNF hosting interface MAC Addresses.
When this is being done, L3VPN service is unable to provide L3 connectivity to such IPs because
L3VPN service points up the incorrect hosting interface MAC Address (in lieu of using the Virtual MAC itself)
in the packets sent to Virtual IP which makes the Virtual IP ignore such packets.

This feature is thence an attempt to enhance the existing VIP discovery (and L3 plumbing of such VIPs)
such that L3VPN service can support Virtual IPs that carry their own Virtual MACs.  In addition to the
same this feature will track  movement of such Virtual IPs in the cloud will be tracked and appropriate
flows would be pushed in, to provide continual L3 connectivity (intra+inter-dc) to such Virtual IPs within
an L3VPN domain.

VNF movement inside a Data Center is supported today. There are VNF's which do
not change the MAC address even after movement between OF-port's.

The netvirt/vpnManaager expects a different MAC address when same VNF
(which was earlier detected by ARP-response/GARP). This satisfies requirements of
few VNF's, but VNF's like "load-balancer, high-availability", require the vMAC/IP
remain same even after multiple incarnation at different OF-ports.
ODL MUST provide seamless connectivity to all these VNF's.

Use Cases
---------
1. Discovery of Virtual IPs that have sticky Virtual MACs ending up with providing
   L3 connectivity for such Virtual IPs.

2. Discovery of new location of Virtual IPs (with sticky Virtual MACs),
   when such Virtual IPs move within an L3VPN domain in the cloud,
   ending up with continual L3 connectivity maintained for such Virtual IPs.

3. Virtual IPs with sticky Virtual MACs to work on L3VPNs that have export/import
   relationship with other L3VPNs configured in the cloud, ending up with L3 connectivity
   for such Virtual IPs from related L3VPNs.

4. When a Virtual IP belongs to only one VM port and VM migrates to a new location
   carrying along the Virtual IP, ending up with L3 connecitivty for such Virtual
   IPs retained intact.

5. Tear down of such Virtual IPs (ie., VIP route) when the only hosting VM port is
   deleted, ending up with removal of routes representing the Virtual IP.

6. If a VM port (and its Virtual IP) remained intact during cluster reboot, then both
   during cluster reboot and after cluster reboot there should be no dataplane traffic
   loss to/from the Virtual IP within the L3VPN domain in the cloud.

7. Discovery of Virtual IPs (without Virtual MACs) should happen, ending up with L3
   connectivity established for such Virtual IPs.

8. All the above steps will be certified for discovered IPv6 addresses with Virtual
   MAC too as the following spec addresses IPv6 address discovery:
   https://git.opendaylight.org/gerrit/70912

Proposed change
===============
As the current code stands, we are using the same GroupId in the dataplane for both the primary
VM and MIP discovered on that VM.

With this feature, we will be carving out a new Group for specifically managing MIPs and this new
Group will be different from the datapalane Group used to steer traffic to primary hosting interface
for the MIP.

- GARP/ARP-response packet handler: shall detect movement of movable IP between port based on connected
  OF-port & DPN combination.
- Packet destined to movable-IP, shall contain the destination MAC as vMAC published by VNF
  (which was received in GARP/ARP-response).

All along current code has been using the MAC-address in the GARP request packet-Ins to decide if a
VIP movement has happened.   This logic is ok and works for VIPs that donot hold any Virtual MACs of their own.

But going forward as this spec support VIPs holding Virtual MACs,  we will be using the interface-name
(or neutron-port-id) from which the GARP packets are received by the NetVirt ARP Handler service to track
VIP movements in general thereby replacing the logic that used MAC-Addresses in incoming GARP packet used earlier.

Pipeline changes
----------------
none

Yang changes
------------
none


Configuration impact
--------------------
none

Clustering considerations
-------------------------
connectivity to all movable ip (vip) with vmac, should remain intact during cluster reboot.
connectivity to all movable ip (vip) with vmac, should remain intact after cluster reboot.
connectivity to all movable ip (vip) with vmac, should remain intact during cluster upgrade.
connectivity to all movable ip (vip) with vmac, should remain intact after cluster upgrade.
connectivity to all movable ip (vip) without vmac, should remain intact during cluster reboot.
connectivity to all movable ip (vip) without vmac, should remain intact after cluster reboot.
connectivity to all movable ip (vip) without vmac, should remain intact during cluster upgrade.
connectivity to all movable ip (vip) without vmac, should remain intact after cluster upgrade.
Connectivity to all movable ip (VNF's) remain intact even after cluster reboot.
Connectivity to all movable ip (VNF's) remain intact even after single node failure (leader/non-leader).

Other Infra considerations
--------------------------
none

Security considerations
-----------------------
none

Scale and Performance Impact
----------------------------
none

Targeted Release
----------------
Flourine

Alternatives
------------
N.A.

Usage
=====
none

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
none

CLI
---


Implementation
==============


Assignee(s)
-----------
Primary assignee:
  Siva Kumar Perumalla, <sivakumar.perumalla@ericsson.com>

Other contributors:
  Akash Sahu, <a.k.sahu@ericsson.com>

Work Items
----------
- GARP/ARP-response packet handler: shall detect movement of movable IP between port based on connected OF-port & DPN combination.
- Packet destined to movable-IP, shall contain the destination MAC as vMAC published by VNF (which was received in GARP/ARP-response).
- Enhance ArpNotificationHandler to detect MIP movement based on Interface from which the GARP Packet / ARP Response is received.
- Enhance VRFEngine to create and manage a separate group for MIPs (regardless of whether they hold a VMAC or not).
- Make sure this separate group works for Import/Export related VPNs and push any changes are needed for the same.
- Make sure Aliveness Monitor uses the Virtual MAC owned by VirtualIP instead of the hosting interface IP,
  and continues to retain its functionality of VIP expiry logic for these new types of VIPs.


Dependencies
============
none.

Unit Tests
----------
- Verification of MAC movement (using generated MAC, not port MAC).
- Hypervisor disconnection (hosting VNF) from ODL, Data Path shall be intact, till aliveness monitor detects.
- Hypervisor reboot (hosting VNF) from ODL, Data Path shall be intact (hypervisor comes-up within aliveness monitor time interval).
- VNF reboot: data path shall be intact after reboot (assuming VNF generates GARP).



Integration Tests
-----------------
none

CSIT
----
- enhance the current ARP learning suite with the new use-cases quooted in
  the use-case section above thereby providing CSIT coverage for this feature.

Documentation Impact
====================
none

References
==========
