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
|                         |  IP Translation Table     |  NETVIRT - NAT                   |
+-------------------------+---------------------------+----------------------------------+
|             26          |  Internal IP to FIP/      |                                  |
|                         |  External IP Translation  |  NETVIRT - NAT                   |
|                         |  Table                    |                                  |
+-------------------------+---------------------------+----------------------------------+
|                         |  Intermediate Pre-FIB     |                                  |
|             27          |  Table after Reverse      |  NETVIRT - NAT                   |
|                         |  Translation              |                                  |
+-------------------------+---------------------------+----------------------------------+
|             28          |  Intermediate Pre-FIB     |                                  |
|                         |  Table after Forward      |  NETVIRT - NAT                   |
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
|             210         |  Ingress ACL Anti-spoofing|                                  |
|                         |  table                    |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             211         |  Ingress ACL Conntrack    |                                  |
|                         |  classifier table         |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             212         |  Ingress ACL Conntrack    |                                  |
|                         |  sender table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             213         |  Applying ACL for existing|                                  |
|                         |  Ingress traffic table    |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             214         |  Ingress ACL Filter       |                                  |
|                         |  cum dispatcher table     |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             215         |  Ingress ACL              |                                  |
|                            filter table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             216         |  Ingress Remote ACL       |                                  |
|                         |  filter table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             217         |  Ingress ACL              |                                  |
|                         |  committer table          |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             220         |  Interface Egress         |                                  |
|                         |  Dispatcher Table         |  GENIUS - INTERFACEMANAGER       |
+-------------------------+---------------------------+----------------------------------+
|             239         |  Clear Egress conntrack   |                                  |
|                         |  state table              |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             240         |  Egress ACL Anti-spoofing |                                  |
|                         |  table                    |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             241         |  Egress ACL Conntrack     |                                  |
|                         |  classifier table         |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             242         |  Egress ACL Conntrack     |                                  |
|                         |  sender table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             243         |  Applying ACL for existing|                                  |
|                         |  Egress traffic table     |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             244         |  Egress ACL Filter cum    |                                  |
|                         |  dispatcher table         |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             245         |  Egress ACL               |                                  |
|                         |  filter table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             246         |  Egress Remote ACL        |                                  |
|                         |  filter table             |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+
|             247         |  Egress ACL               |                                  |
|                         |  committer table          |  NETVIRT - ACL                   |
+-------------------------+---------------------------+----------------------------------+

Traffic Flows in Netvirt
========================


ELAN Traffic Flow
=================

+-------------------------+--------------------------------------------------------------+
| Traffic Type            |                        FLOW                                  |
+=========================+===========================+==================================+
|  Known unicast traffic  |    Table 0 => Table 17 => Table 43 => Table 48 =>            |
|  flow(both direction)   |    Table 49 => Table 50 => Table 51 => Table 220 =>          |
|                         |    Output Port                                               |
+-------------------------+--------------------------------------------------------------+
|  Unknown unicast/       |    Table 0 => Table 17 => Table 43 =>                        |
|  multicast/broadcast    |    Table 50 => Table 51 => Table 52 => Remote BC Group =>    |
|  traffic                |    Local BC Group => Table 55 => Output Port                 |
+-------------------------+--------------------------------------------------------------+

L3VPN Traffic Flow
==================

+-------------------------+--------------------------------------------------------------+
| Traffic Type            |                        FLOW                                  |
+=========================+===========================+==================================+
|  L3VPN Traffic Flow     |    Table 0 => Table 17 => Table 19 => Table 21 =>            |
|  within same DPN        |    Local nexthop Group => Table 220 => output VM port        |
+-------------------------+--------------------------------------------------------------+
|  L3VPN Traffic Flow     |    Table 0 => Table 17 => Table 19 => Table 21 => Table 220  |
   across DPNs within     |    => Output tunnel port                                     |
|  Data Center(source DPN)|                                                              |
+-------------------------+--------------------------------------------------------------+
|  L3VPN Traffic Flow     |    Table 0 => Table 36 => Table 220 => Output VM port        |
|  across DPNs within     |                                                              |
|  DC(destination)        |                                                              |
+-------------------------+--------------------------------------------------------------+
|  L3VPN Traffic Flow     |    Table 0 => Table 17 => Table 19 => Table 21 =>            |
|  across DC(towards DC)  |    push MPLS, => Table 220 => output tunnel port             |
+-------------------------+--------------------------------------------------------------+
|  L3VPN Traffic Flow     |    Table 0 => Table 20 => Local nexthop group =>             |
|  across DC(from DC)     |    Table 220 => output tunnel port                           |
+-------------------------+--------------------------------------------------------------+

NAT Traffic Flow
================

DNAT Traffic Flow
-----------------

+-------------------------+--------------------------------------------------------------+
| Traffic Type            |                        FLOW                                  |
+=========================+===========================+==================================+
|  DNAT Traffic Flow      |   Table 0 => Table 20 => Table 25 => Table 27 =>             |
|  on source DPN          |   Table 21 => Local nexthop Group => Table 220 =>Output port |
+-------------------------+--------------------------------------------------------------+
|  DNAT Traffic Flow      |   Table 0 => Table 17 => Table 21 =>                         |
|  on destination DPN     |   Table 26 => Table 28 => Table 21 => External Tunnel Groups |
+-------------------------+--------------------------------------------------------------+


SNAT Traffic Flow
-----------------

* SNAT VM Residing on the NAPT vSwitch

+-------------------------+--------------------------------------------------------------+
| Traffic Type            |                        FLOW                                  |
+=========================+===========================+==================================+
|  DPN (source traffic)   |   Table 0  => Table 17 => Table 21 =>                        |
|                         |   Table 26 => Table 46 => Table 47 => Table 21 =>            |
|                         |   External Tunnel Groups                                     |
+-------------------------+--------------------------------------------------------------+
|  DPN (reverse traffic)  |   Table 0 => Table 20 => Table 44 => Table 47 =>             |
|                         |   Table 21 => Local nexthop Group => Table 220 => output port|
+-------------------------+--------------------------------------------------------------+


* SNAT VM Residing on non-NAPT vSwitch (Source Traffic)

+-------------------------+--------------------------------------------------------------+
| Traffic Type            |                        FLOW                                  |
+=========================+===========================+==================================+
|  DPN (source traffic)   |   Table 0 => Table 17 => Table 21 =>                         |
|                         |   Table 26 => Internal Tunnel Group => Table 220 =>          |
|                         |   output tunnel port                                         |
+-------------------------+--------------------------------------------------------------+
|  NAPT DPN               |   Table 0 => Table 36 => Table 46 => Table 47 =>             |
|  (reverse traffic)      |   Table 21 => External Tunnel Group => Table 220 =>          |
|                         |   Output port                                                |
+-------------------------+--------------------------------------------------------------+


* SNAT VM Residing on non-NAPT vSwitch (Reverse Traffic)

+-------------------------+--------------------------------------------------------------+
| Traffic Type            |                        FLOW                                  |
+=========================+===========================+==================================+
|  NAPT DPN               |    Table 0 => Table 20 => Table 44 => Table 47 =>            |
|  (source traffic)       |    Table 21 => Internal Tunnel Group => Table 220 =>         |
|                         |    output port                                               |
+-------------------------+--------------------------------------------------------------+
|  DPN                    |   Table 0 ⇒ Table 36 ⇒ Local nexthop Group => Table 220 =>  |
|  (reverse traffic)      |   output port                                                |
+-------------------------+--------------------------------------------------------------+

* Conntrack Based SNAT Traffic Flow

<TBD>

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
* Manu B
* N Vivekanandan
* Shashidhar Raja
