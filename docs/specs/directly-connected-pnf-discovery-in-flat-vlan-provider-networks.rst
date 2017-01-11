
ints to consider:
  * Use RST format. For help with syntax refer http://sphinx-doc.org/rest.html
  * Use http://rst.ninjs.org/ a web based WYSIWYG RST editor.
  * For diagrams, you can use http://asciiflow.com to make ascii diagrams.
  * MUST READ http://docs.opendaylight.org/en/latest/documentation.html and follow guidelines.
  * Use same topic branch name for all patches related to this feature.
  * All sections should be retained, but can be marked None or N.A.
  * Set depth in ToC as per your doc requirements. Should be at least 2.

.. contents:: Table of Contents
         :depth: 3

================================================================
Directly connected PNFs discovery in Flat/VLAN provider networks
================================================================

[link to gerrit patch]
This features enables discovering and directing traffic to PNFs (Physical Network Function) in Flat/VLAN provider networks (private and external networks), by leveraging Subnet-Route feature.

Problem description
===================
PNF is a device which we don't know its IP address or behind which interface it is connected.
There are two main issues today regarding PNFs:

* Communication between a VM and a PNF that is located in an external network. The only way for VMs to communicate with a PNF is via additional hop which is the external gateway, instead of directly.

* Communication between a VM in a private network and a PNF in a different private network (L3 connectivity) which is not supported. Traffic initiated from a VM don't have appropriate rule in FIB table (table 21), and PNF is not able to resolve its default gateway.

Use Cases
---------
L2 PNF - communication between VMs in private networks and PNFs in external networks.

L3 PNF - communication between VMs and PNFs in different private networks.

Proposed change
===============
We want to leverage the subnet-route feature in order to address the above issues.

L2 PNF
------

Let H1 be a VM on private network prv-net.
Let PNF be a PNF on external network ex-net.
H1 want to communicate with with PNF.

* Upon configuring a subnet, a flow will be installed in the FIB table (table 21), matching the subnet prefix and vpn-id of router(?), and directing packets to table 22. There, packets will be punted to the controller.
* The controller will hold the packets, and initiate an ARP request towards the PNF IP. For PNF in external network,  the ARP request will have source MAC and IP of a floating IP if such FIP is configured, otherwise source MAC and IP of the router gateway.
* The ARP request will be sent via the external group.
* Upon receiving ARP replay, install exact IP match flow in FIB table to direct all further traffic towards the newly resolved PNF, on all computes.
* After installation of exact mac flows, all packets from H1 to PNF that had been accumulated will be resubmitted to the pipeline for processing.
* leveraging Aliveness monitor feature to monitor PNFs. The controller will send ARP requests from the primary switch of the router, which is the NAPT switch.

L3 PNF
------

Let H1 be a VM on private network prv-net1.
Let PNF be a PNF on a different privat network prv-net2
H1 want to communicate with with PNF.

* Upon configuring a subnet, a flow will be installed in the FIB table, matching the subnet prefix and vpn-id of router(?), and directing packets to table 22. There, packets will be punted to the controller.
* The controller will hold the packets, and initiate an ARP request towards the PNF IP. For PNF in private network, the ARP request will have source MAC and IP of router interface.
* The ARP request will be sent via a broadcast group for prv-net2.
* Upon receiving ARP replay, install exact IP match flow in FIB table to direct all further traffic towards the newly resolved PNF, on all computes.
* After installation of exact mac flows, all packets from H1 to PNF that had been accumulated will be resubmitted to the pipeline for processing.
* ARP responder flow: for PNFs in private network, a new ARP responder flow will be installed in the primary switch of the router. This flow will response for ARP requests from a PNF and the response MAC will be the router interface MAC.
* Split Horizon protection disabling: traffic from PNF in private networks, arrives to the primary switch(via a provider port) due to the ARP responder rule described above, and will need to be directed to the proper compute of the designated VM (via a provider port). This requires disabling the split horizon protection. In order to protects against infinite loops, the packet TTL will be decreased. 

Pipeline changes
----------------
Egress traffic from VM with floating IP to an unresolved PNF in external network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- Packets in FIB table after translation to FIP, will match on subnet flow and will be punted to controller from Subnet Route table. Then, ARP request will be generated and be sent to the PNF.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id,src-ip=vm-ip set vpn-id=external-network-id,src-ip=fip`` =>
  | SNAT table (28) ``match: vpn-id=external-network-id,src-ip=fip set src-mac=fip-mac`` =>
  | FIB table (21) ``match: vpn-id=external-network-id, dst-ip=external-subnet-ip`` =>
  | Subnet Route table (22):  => Output to Controller
  |

- After receiving  ARP response from the PNF a new exact IP flow will be installed in table 21

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id,src-ip=vm-ip set vpn-id=external-network-id,src-ip=fip`` =>
  | SNAT table (28) ``match: vpn-id=external-network-id,src-ip=fip set src-mac=fip-mac`` =>
  | FIB table (21) ``match: vpn-id=external-network-id, dst-ip=exact-ip-of-pnf`` =>
  | External-Network Group: ``set dst-mac=pnf-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider port(?)
  |

Egress traffic from VM using NAPT to an unresolved PNF in external network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- Packets in FIB table after translation to NAPT, will match on subnet flow and will be punted to controller from Subnet Route table. Then, ARP request will be generated and be sent to the PNF.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | Outbound NAPT table (46) ``match: vpn-id=router-id TBD`` =>
  | NAPT PFIB tabl (47) ``match: vpn-id=router-id`` => 
  | FIB table (21) ``match: vpn-id=external-network-id, dst-ip=external-subnet-ip`` =>
  | Subnet Route table (22)  => Output to Controller
  |
 
- After receiving  ARP response from the PNF a new exact IP flow will be installed in table 21.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | Outbound NAPT table (46) ``match: vpn-id=router-id TBD`` =>
  | NAPT PFIB table (47) ``match: vpn-id=router-id`` => 
  | FIB table (21) ``match: vpn-id=external-network-id, dst-ip=exact-ip-of-pnf`` =>
  | PNF-Group(??): ``set dst-mac=pnf-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider port(?)
  |

Egress traffic from VM in private network to an unresolved PNF in another private network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id dst-ip=subnet-ip`` =>
  | Subnet Route table (22):  => Output to Controller 
  |

- After receiving  ARP response from the PNF a new exact IP flow will be installed in table 21.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id dst-ip=execpt-pnf-ip`` =>
  | PNF-Network Group (?): ``set dst-mac=pnf-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider port(?)

Ingress traffic to VM in private network from a PNF in another private network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: lport-tag=provider-port, vpn-id=router-id, dst-mac=router-interface-mac, set split-horizon-bit = 0, decrease-ttl`` =>
  | FIB table (21) ``match: vpn-id=router-id dst-ip=vm-ip`` =>
  | VM - Group (???): ``set dst-mac=vm-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider port(?)
  |

ARP Responder flow for L3 PNF
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- This flow will be installed on the primary switch of the router, and will send an ARP reply to any PNF

 | ARP Responder table (81) ``match: lport-tag=provider-lport-tag, arp_op=1, arp_tpa=router_interface-ip`` =>
 | Egress table (220) output to provider port(?)


Yang changes
------------
This should detail any changes to yang models.

Configuration impact
---------------------
None

Clustering considerations
-------------------------
None ???

Other Infra considerations
--------------------------
None

Security considerations
-----------------------
None

Scale and Performance Impact
----------------------------
As of today, there is one primary switch per router. In L3 PNF scenario, all PNFs traffic, across all private networks connected to the same router, will be directed to the same single switch, which could be a performance issue. In such case, the primary switch mechanism could be changed to a primary switch per network, which will cause all traffic from PNFs on the same network to be sent to a single switch, but different switch per network.

Targeted Release
-----------------
Carbon

Alternatives
------------
None

Usage
=====
How will end user use this feature? Primary focus here is how this feature
will be used in an actual deployment.

e.g. For most netvirt features this will include OpenStack APIs.

This section will be primary input for Test and Documentation teams.
Along with above this should also capture REST API and CLI.

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
  Tomer Pearl <tomer.pearl@hpe.com>

Other contributors:
  TBD

Work Items
----------
Break up work into individual items. This should be a checklist on
Trello card for this feature. Give link to trello card or duplicate it.


Dependencies
============
None

Testing
=======

Unit Tests
----------

Integration Tests
-----------------
Write something here

CSIT
----

Documentation Impact
====================
References
==========
Add any useful references. Some examples:

* Links to Summit presentation, discussion etc.
* Links to mail list discussions
* Links to patches in other projects
* Links to external documentation

[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

[3] https://docs.google.com/presentation/d/1ByvEQXUtIyH-H7Bin6OBJNrHjOv-3hpHYzU6Sf6hDbA/edit#slide=id.g11657174d1_0_31

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative
