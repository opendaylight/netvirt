Enable discovery of Virtual IPs (or Movable IPs) holding Virtual MACs
==========================================

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

Problem description
===================
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


Proposed change
===============
- GARP/ARP-response packet handler: shall detect movement of movable IP between port based on connected OF-port & DPN combination.
- Packet destined to movable-IP, shall contain the destination MAC as vMAC published by VNF (which was received in GARP/ARP-response).

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
Connectivity to all movable ip (VNF's) remain intact even after cluster reboot.
Connectivity to all movable ip (VNF's) remain intact even after single node failure (leader/non-leader).
Movement of movable ip (VNF's) remain intact even after cluster reboot.
Movement of movable ip (VNF's with vMAC) remain intact even after cluster reboot.

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


Dependencies
============
none.

Unit Tests
----------
- verification of MAC movement (using generated MAC, not port MAC).
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

