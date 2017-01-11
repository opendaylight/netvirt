Discovery of directly connected PNFs in Flat/VLAN provider networks
===================================================================
https://git.opendaylight.org/gerrit/#/q/topic:directly_connected_pnf_discovery

This features enables discovering and directing traffic to Physical Network Functions (PNFs)
in Flat/VLAN provider and tenant networks, by leveraging Subnet-Route feature.

Problem description
===================
PNF is a device which has not been created by Openstack but connected to the hypervisors
L2 broadcast domain and configured with ip from one of the neutron subnets.

Ideally, L2/L3 communication between VM instances and PNFs on flat/VLAN networks
would be routed similarly to inter-VM communication. However, there are two main issues
preventing direct communication to PNFs.

* Communication between a VM and a PNF that is located in the same Flat/VLAN provider network.
  The only way for VMs to communicate with a PNF is via additional hop which is the external gateway,
  instead of directly.

* East/West communication between VMs in a tenant network and a PNF (L3 connectivity)
  which is not supported and have two problems.
  First, traffic initiated from a VMs towards a PNF is dropped because there isn't
  an appropriate rule in FIB table (table 21) to route that traffic.
  Second, in the other direction, PNFs are not able to resolve their default gateway,
  because the ARP responder flow for router-interface addresses is programmed per VM port.

We want to leverage the Subnet-Route and Aliveness-Monitor features in order to address
the above issues.

Subnet-Route
------------
Today, Subnet-Route feature enables ODL to route traffic to a destination IP address,
even for ip addresses that have not been statically configured by OpenStack,
in the FIB table.
To achieve that, the FIB table contains a flow that match all IP packets in a given subnet range.
How that works?

* A flow is installed in the FIB table, matching on subnet prefix and vpn-id of the network,
  with a goto-instruction directing packets to table 22. There, packets are punted to the controller.

* ODL hold the packets, and initiate an ARP request towards the destination IP.
* Upon receiving ARP reply, ODL installs exact IP match flow in FIB table to direct
  all further traffic towards the newly learnt MAC of the destination IP

Current limitations of Subnet-Route feature:

* Works for external VPN only
* Works on networks that are not configured as "external".
* May cause traffic lost due to "swallowing" the packets punted from table 22.
* Reuses the source MAC and source IP from the punted packet.

Aliveness monitor
-----------------
After ODL learns a mac that is associated with an ip address,
ODL schedule an arp monitor task, with the purpose of verifying that the device is still alive
and responding. This is done by periodically sending arp requests to the device.

Current limitation:
Aliveness monitor was not designed for monitoring devices behind flat/VLAN provider network ports.

* determining the source ip/mac of the ARP requests,
  as the device can be reached from multiple provider ports.
* preventing mac movements in the network

Therefore, no arp monitoring support for PNFs in external networks.

Use Cases
---------
L2 PNF - communication between VMs in tenant networks and PNFs in provider networks.

L3 PNF - communication between VMs and PNFs in different tenant networks.

Proposed change
===============

Subnet-route
------------
* Upon OpenStack configuration of a Subnet in a provider network,
  a new vrf entry with subnet-route augmentation will be created.
* Upon associataion of neutron router with a subnet in a tenant network,
  a new vrf entry with subnet-route augmentation will be created.
* Upon receiving ARP reply, install exact IP match flow in FIB table to direct all
  further traffic towards the newly resolved PNF, on all relevant computes nodes,
  which will be discussed later
* Packets that had been punted to controller will be resubmitted to the openflow pipeline
  after installation of exact match flow.

L2 PNF
------

In this scenario a VM in a private tenant network wants to communicate with a PNF in the provider network

* The controller will hold the packets, and initiate an ARP request towards the PNF IP.
  an ARP request will have source MAC and IP the router gateway
  and will be sent from the NAPT switch.
* ARP packets will be punted from the NAPT switch only.
* Upon receiving ARP reply, install exact IP match flow in FIB table to direct all further
  traffic towards the newly resolved PNF, on all compute nodes that are associated
  with the external network.
* leveraging Aliveness monitor feature to monitor PNFs.
  The controller will send ARP requests from the primary switch of the router,
  which is the NAPT switch in this case


L3 PNF
------

In this scenario a VM and a PNF, in different private network of the same tenant, wants to communicate.

* Upon configuring a subnet, a flow will be installed in the FIB table,
  matching the subnet prefix and vpn-id of the router.
* ARP request will has a source MAC and IP of the router interface, and will be sent to the provider port
  in the primary switch of the router.
* ARP packets will be punted from the NAPT switch only.
* Upon receiving an ARP reply, install exact IP match flow in FIB table to direct all
  further traffic towards the newly resolved PNF, on all computes related to the router
* ARP responder flow: a new ARP responder flow will be installed in the primary switch of the router.
  This flow will response for ARP requests from a PNF and the response MAC
  will be the router interface MAC. This flow will use the LPort-tag of the provider port.
* Split Horizon protection disabling: traffic from PNFs,
  arrives to the primary switch(via a provider port) due to the ARP responder rule described above,
  and will need to be directed to the proper compute of the designated VM (via a provider port).
  This require disabling the split horizon protection.
  In order to protects against infinite loops, the packet TTL will be decreased.
* leveraging Aliveness monitor, the controller will send ARP requests from the primary switch
  of the router.

ARP packet-ins
--------------
ARP messages in the Flat/Vlan provider networks will be punted from
the primary switch only.


Pipeline changes
----------------
L2 PNF use-case depends on hairpinning spec [2], the flows presented here reflects that dependency.

Egress traffic from VM with floating IP to an unresolved PNF in external network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- Packets in FIB table after translation to FIP, will match on subnet flow
  and will be punted to controller from Subnet Route table.
  Then, ARP request will be generated and be sent to the PNF.
  No flow changes are required in this part.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id,src-ip=vm-ip
    set vpn-id=ext-subnet-id,src-ip=fip`` =>
  | SNAT table (28) ``match: vpn-id=ext-subnet-id,src-ip=fip set src-mac=fip-mac`` =>
  | FIB table (21) ``match: vpn-id=ext-subnet-id, dst-ip=ext-subnet-ip`` =>
  | Subnet Route table (22):  => Output to Controller
  |

- After receiving  ARP response from the PNF a new exact IP flow will be installed in table 21.
  No other flow changes are required.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id,src-ip=vm-ip 
    set vpn-id=ext-subnet-id,src-ip=fip`` =>
  | SNAT table (28) ``match: vpn-id=ext-subnet-id,src-ip=fip set src-mac=fip-mac`` =>
  | FIB table (21) ``match: vpn-id=ext-subnet-id, dst-ip=pnf-ip,
    set dst-mac=pnf-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider port
  |

Egress traffic from VM using NAPT to an unresolved PNF in external network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- Ingress-DPN is not the NAPT switch, no changes required.
  Traffic will be directed to NAPT switch and directed to the outbound NAPT table straight
  from the internal tunnel table

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | NAPT Group ``output to tunnel port of NAPT switch``
  |

- Ingress-DPN is the NAPT switch. Packets in FIB table after translation to NAPT,
  will match on subnet flow and will be punted to controller from Subnet Route table.
  Then, ARP request will be generated and be sent to the PNF. No flow changes are required.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | Outbound NAPT table (46) ``match: src-ip=vm-ip,port=int-port 
    set src-ip=router-gw-ip,vpn-id=router-gw-subnet-id,port=ext-port`` =>
  | NAPT PFIB tabl (47) ``match: vpn-id=router-gw-subnet-id`` =>
  | FIB table (21) ``match: vpn-id=ext-subnet-id, dst-ip=ext-subnet-ip`` =>
  | Subnet Route table (22)  => Output to Controller
  |

- After receiving  ARP response from the PNF a new exact IP flow will be installed in table 21.
  No other changes required.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | Outbound NAPT table (46) ``match: vpn-id=router-id TBD set vpn-id=external-net-id`` =>
  | NAPT PFIB table (47) ``match: vpn-id=external-net-id`` =>
  | FIB table (21) ``match: vpn-id=ext-network-id, dst-ip=pnf-ip 
    set dst-mac=pnf-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider port
  |

Egress traffic from VM in private network to an unresolved PNF in another private network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- Packet from a VM is punted to the controller, no flow changes are required.

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
  | FIB table (21) ``match: vpn-id=router-id dst-ip=pnf-ip 
    set dst-mac=pnf-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider port
  |

Ingress traffic to VM in private network from a PNF in another private network
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- New flow in table 19, to distinguish our new use-case,
  in which we want to decrease the TTL of the packet

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: lport-tag=provider-port, vpn-id=router-id, dst-mac=router-interface-mac,
    set split-horizon-bit = 0, decrease-ttl`` =>
  | FIB table (21) ``match: vpn-id=router-id dst-ip=vm-ip 
    set dst-mac=vm-mac reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider port
  |


Yang changes
------------
- ``l3vpn/vpn-instances/vpn-instance``
  yang model will be enhanced with a boolean field to represent external or internal vpn.
  This is done because the current deduction of external or internal vpn based on 
  VpnId != RouterId does not hold for external networks.

::

   list vpn-instance {
      key "vpn-instance-name"
      leaf vpn-instance-name {
          type string;
      }
      ...

      leaf external-vpn {
          type boolean;
      }
      ....
    }	


Configuration impact
---------------------
A configuration mode will be available to turn this feature ON/OFF.

Clustering considerations
-------------------------
None

Other Infra considerations
--------------------------
None

Security considerations
------------------------------
None

Scale and Performance Impact
----------------------------
As of today, there is one primary switch per router. In L3 PNF scenario,
all PNFs traffic, across all private networks connected to the same router,
will be directed to the same single switch, which could be a performance issue.
In such case, the primary switch mechanism could be changed to a primary switch per network,
which will cause all traffic from PNFs on the same network to be sent to a single switch,
but different switch per network.

Targeted Release
-----------------
Carbon

Alternatives
------------
None

Usage
=====
Create external network with a subnet
-------------------------------------
::

 neutron net-create public-net -- --router:external --is-default --provider:network_type=flat
 --provider:physical_network=physnet1
 neutron subnet-create --ip_version 4 --gateway 10.64.0.1 --name public-subnet1 <public-net-uuid> 10.64.0.0/16
 -- --enable_dhcp=False

Create internal networks with subnets
-------------------------------------

::

 neutron net-create private-net1
 neutron subnet-create --ip_version 4 --gateway 10.0.123.1 --name private-subnet1 <private-net1-uuid>
 10.0.123.0/24
 neutron net-create private-net2
 neutron subnet-create --ip_version 4 --gateway 10.0.124.1 --name private-subnet2 <private-net2-uuid>
 10.0.124.0/24

Create a router instance and connect it to an internal subnet and an external subnet
------------------------------------------------------------------------------------
This will allow communication with PNFs in provider network
::

 neutron router-create router1
 neutron router-interface-add <router1-uuid> <private-subnet1-uuid>
 neutron router-gateway-set --fixed-ip subnet_id=<public-subnet1-uuid> <router1-uuid> <public-net-uuid>

Create a router instance and connect to it to two internal subnets
------------------------------------------------------------------
This will allow East/West communication between VMs and PNFs
::

 neutron router-create router1
 neutron router-interface-add <router1-uuid> <private-subnet1-uuid>
 neutron router-interface-add <router1-uuid> <private-subnet2-uuid>

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
* Configure subnet-route flows upon ext-net configuration / router association
* Solve traffic lost issues of punted packets from table 22
* Enable aliveness monitoring on external interfaces.
* Add ARP responder flow for L3-PNF
* Add ARP packet-in from primary switch only
* Disable split-horizon and enable TTL decrease for L3-PNF

Dependencies
============
This feature depends on hairpinning feature [2]

Testing
=======

Unit Tests
----------
Unit tests will be added for the new functionality

Integration Tests
-----------------

CSIT
----
Will need to see if a PNF could be simulated in CSIT

Documentation Impact
====================
References
==========
[1] https://docs.google.com/presentation/d/1ByvEQXUtIyH-H7Bin6OBJNrHjOv-3hpHYzU6Sf6hDbA/edit#slide=id.g11657174d1_0_31
[2] http://docs.opendaylight.org/en/latest/submodules/netvirt/docs/specs/hairpinning-flat-vlan.html


