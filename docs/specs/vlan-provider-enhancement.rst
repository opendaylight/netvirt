.. contents:: Table of Contents
      :depth: 3

=============================================
Enhancement to VLAN Provider Network Support
=============================================

This feature aims to enhance the support for VLAN provider network.
The enhancement is targeted for VLAN provider networks not having an
external router attached to it.

Problem description
===================

Current ODL implementation supports all configured VLAN segments corresponding to
VLAN provider networks on a particular patch port on all Open vSwitch which are
part of the network. This could have adverse performance impacts because every
provider patch port will receive and processes broadcast traffic for all configured
VLAN segments even in cases when the switch doesn't have a VM port in the network.
Furthermore, for unknown SMACs it leads to unnecessary punts to controller for
source MAC learning from all the switches.

Use Cases
---------
* Drop broadcast packets in VLAN provider network
  Switch pipeline should not process broadcast traffic on a VLAN for which
  it doesn't have an associated VM port.

* Avoid punts to controller for source MAC learning
  Since switch pipeline currently processes packet despite not having a VM
  in the provider network, packets with unknown source MAC are punted to
  the controller from all the switches in the VLAN. With this change, punts
  to the controller will be avoided since switch will drop the packet.

Proposed change
===============
Instead of creating the VLAN member interface on the patch port at the time of
network creation, VLAN member interface creation will be deferred until a VM port
comes up in the switch in the VLAN provider network. This will be applicable to
VLAN provider network without external router attrbute set.
Elan service binding will also be done at the time of VLAN member interface
creation. Since many neutron ports on same switch/DPN can belong to a single
VLAN provider network, the flow rule should be created only once when first VM
comes up and should be deleted when there are no more neutron ports in the DPN
for the VLAN provider network.

Pipeline changes
----------------
None.

Yang changes
------------
None.


Configuration impact
---------------------
None

Clustering considerations
-------------------------
None

Other Infra considerations
--------------------------
N.A.

Security considerations
-----------------------
None.

Scale and Performance Impact
----------------------------
Performance will improve because of the following:

1. Switch will drop packets if it doesn't have a VM port in the VLAN on
   which packet is received.
2. Unnecessary punts to the controller for source mac learning will be
   prevented.

Targeted Release
-----------------
Carbon.

Alternatives
------------
N.A.

Usage
=====

Features to Install
-------------------
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

REST API
--------

CLI
---

Implementation
==============

Assignee(s)
-----------
Primary assignee:
 - Ravindra Nath Thakur (ravindra.nath.thakur@ericsson.com)
 - Naveen Kumar Verma (naveen.kumar.verma@ericsson.com)


Other contributors:
 - Ravi Sundareswaran (ravi.sundareswaran@ericsson.com)

Work Items
----------
N.A.

Dependencies
============
This doesn't add any new dependencies.


Testing
=======
Capture details of testing that will need to be added.

Unit Tests
----------

Integration Tests
-----------------

CSIT
----

Documentation Impact
====================
This feature will not require any change in User Guide.


References
==========

[1] https://trello.com/c/A6Km6J3D/110-flat-and-vlan-network-type
