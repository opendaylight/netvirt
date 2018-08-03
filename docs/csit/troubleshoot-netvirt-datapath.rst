.. contents:: Table of Contents
   :depth: 3

================================
Troubleshooting Netvirt Datapath
================================

Opendaylight Netvirt programs specific flows to OVS, for the various VM connectivity
usecases to work. The purpose of this document is to give a detailed picture of the
various flows that happen on OVS when a packet arrives.

Openflow Table Ownership
========================
+-------------------------+---------------------------+----------------------------------+
| TABLE NUMBER            | TABLE NAME                |            OWNERSHIP             |
+=========================+===========================+==================================+
|             0           |  INTERFACE INGRESS TABLE  |  GENIUS - INTERFACEMANAGER       |
+-------------------------+---------------------------+----------------------------------+
|             17          |  INGRESS DISPATCHER TABLE |  GENIUS - INTERFACEMANAGER       |
+-------------------------+---------------------------+----------------------------------+
|             18          |  EXTERNAL TUNNEL DHCP     |                                  |
|                         |  TABLE                    |  NETVIRT - L2GW SERVICE          |
+-------------------------+---------------------------+----------------------------------+
|             19          |  GATEWAY MAC TABLE        |  NETVIRT - L3VPN                 |
+-------------------------+---------------------------+----------------------------------+
|             20          |  L3 LFIB TABLE            |  NETVIRT - L3VPN                 |
+-------------------------+---------------------------+----------------------------------+
|             21          |  L3 FIB TABLE             |  NETVIRT - L3VPN                 |
+-------------------------+---------------------------+----------------------------------+
|             22          |  L3 SUBNET ROUTE TABLE    |  NETVIRT - L3VPN                 |
+-------------------------+---------------------------+----------------------------------+
|             25          |  Floating IP to Internal  |                                  |
|                         |  IP Translation Table     |  NETVIRT - L3VPN                 |
+-------------------------+---------------------------+----------------------------------+
|             26          |  Internal IP to FIP/      |                                  |
|                         |  External IP Translation  |  NETVIRT - L3VPN                 |
|                         |  Table                    |                                  |
+-------------------------+---------------------------+----------------------------------+
|                         |  Intermediate Pre-FIB     |                                  |
|             27          |  Table after Reverse      |  NETVIRT - L3VPN                 |
|                         |  Translation              |                                  |
+-------------------------+---------------------------+----------------------------------+
|             28          |  Intermediate Pre-FIB     |                                  |
|                         |  Table after Forward      |  NETVIRT - L3VPN                 |
|                         |  Translation              |                                  |
+-------------------------+---------------------------+----------------------------------+
|             36          |  Internal Terminating     |  ALL SERVICES(which require      |
|                         |  Service Table            |  communication over vxlan)       |
+-------------------------+---------------------------+----------------------------------+
|             38          |  External Terminating     |                                  |
|                         |  Service Table            |  NETVIRT - L2GW SERVICE          |
+-------------------------+---------------------------+----------------------------------+
|             44          |  Inbound Translation      |                                  |
|                         |  in NAPT vSwitch          |  NETVIRT - NAT                   |
+-------------------------+---------------------------+----------------------------------+
|             45          |  IPv6 Table               |  NETVIRT - IPV6                  |
+-------------------------+---------------------------+----------------------------------+
|             46          |  Outbound Translation in  |                                  |
|                         |  NAPT vSwitch             |  NETVIRT - NAT                   |
+-------------------------+---------------------------+----------------------------------+
|             47          |  NAPT vSwitch Pre-FIB     |                                  |
|                         |  Table                    |  NETVIRT - NAT                   |
+-------------------------+---------------------------+----------------------------------+
|             48          |  ELAN DestIpToDMac Table  |  NETVIRT - ELAN                  |
+-------------------------+---------------------------+----------------------------------+
|             49          |  Temporary Source MAC     |                                  |
|                         |  Learned Table            |  NETVIRT - ELAN                  |
+-------------------------+---------------------------+----------------------------------+
|             50          |  ELAN SMAC Table          |  NETVIRT - ELAN                  |
+-------------------------+---------------------------+----------------------------------+
|             51          |  ELAN DMAC Table          |  NETVIRT - ELAN                  |
+-------------------------+---------------------------+----------------------------------+
|             52          |  ELAN Unknown DMAC Table  |  NETVIRT - ELAN                  |
+-------------------------+---------------------------+----------------------------------+
|             55          |  ELAN Filter Equals Table |  NETVIRT - ELAN                  |
+-------------------------+---------------------------+----------------------------------+
|             60          |  DHCP Table               |  NETVIRT - DHCP                  |
+-------------------------+---------------------------+----------------------------------+
|             80          |  L3 Interface Table       |  NETVIRT - L3VPN                 |
+-------------------------+---------------------------+----------------------------------+
|             81          |  ARP Responder Table      |  NETVIRT - L3VPN                 |
+-------------------------+---------------------------+----------------------------------+
|             211         |  Ingress ACL Anti-spoofing|                                  |
|                         |  table                    |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             212         |  Ingress ACL Conntrack    |                                  |
|                         |  classifier table         |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             213         |  Ingress ACL Conntrack    |                                  |
|                         |  sender table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             214         |  Ingress ACL Conntrack    |                                  |
|                         |  filter table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             215         |  Ingress ACL Non-conntrack|                                  |
|                            filter table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             220         |  Interface Egress         |                                  |
|                         |  Dispatcher Table         |  GENIUS - INTERFACEMANAGER       |
+-------------------------+---------------------------+----------------------------------+
|             241         |  Egress ACL Anti-spoofing |                                  |
|                         |  table                    |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             242         |  Egress ACL Conntrack     |                                  |
|                         |  classifier table         |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             243         |  Egress ACL Conntrack     |                                  |
|                         |  sender table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             244         |  Egress ACL Conntrack     |                                  |
|                         |  filter table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             245         |  Egress ACL Non-conntrack |                                  |
|                         |  filter table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+

Traffic Flows in Netvirt
========================


ELAN Traffic Flow
=================

L3VPN Traffic Flow
==================

L3VPN Traffic Flow across DPNs within Data Center
-------------------------------------------------

L3VPN Traffic Flows across Data Centers
---------------------------------------

NAT Traffic Flow
================

DNAT Traffic Flow
-----------------

SNAT Traffic Flow
-----------------

* SNAT VM Residing on the NAPT vSwitch

* SNAT VM Residing on non-NAPT vSwitch (Source Traffic)

* SNAT VM Residing on non-NAPT vSwitch (Reverse Traffic)


SubnetRoutes Traffic flow
=========================

SubnetRoute Traffic Flow with ITM – EGRESS FROM A DPN
-----------------------------------------------------

SubnetRoute Traffic Flow with ITM – LEARNING INVISIBLE IP
---------------------------------------------------------

SubnetRoute Traffic Flow with ITM – Subsequent Packet from/to INVISIBLE IP
--------------------------------------------------------------------------


ACL/Security Groups Traffic Flow
================================

VM Egress
---------

VM Ingress with ITM
-------------------

VM Ingress with ITM
-------------------

VM Egress anti-spoofing
-----------------------

VM Ingress anti-spoofing with ITM
---------------------------------


Inputs given by
===============

* Akash Sahu
* Chetan Arakere Gowdru
* Faseela K
* Kiran N Upadhyaya
* N Vivekanandan
* Shashidhar Raja
