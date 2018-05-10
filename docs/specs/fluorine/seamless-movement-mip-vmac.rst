Support for virtual MAC movement inside DC
==========================================

https://git.opendaylight.org/gerrit/#/q/topic:vpn_vmac_movement

seamless movement of IP's across different OF-ports inside a DC with "same vMAC".

Problem description
===================
VNF movement inside a Data Center is supported today. There are VNF's which do not change the MAC address even after movement between OF-port's.

The netvirt/vpnManaager expects a different MAC address when same VNF (which was earlier detected by ARP-response/GARP). This satisfies requirements of few VNF's, but VNF's like "load-balancer, high-availability", require the vMAC/IP remain same even after multiple incarnation at different OF-ports. ODL MUST provide seamless connectivity to all these VNF's.

Use Cases
---------
- Movable IP: VNF's for vMAC's are not changed even after the movement.


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
Who is implementing this feature? In case of multiple authors, designate a
primary assignee and other contributors.

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
- verification of MAC movement (using generated MAC, not port MAC).

Documentation Impact
====================
none

References
==========

