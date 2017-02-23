.. contents:: Table of Contents
      :depth: 3

=============================================
Enhancement to VLAN Provider Network Support
=============================================

https://git.opendaylight.org/gerrit/#/q/topic:vlan-provider-network

This feature aims to enhance the support for VLAN provider networks that are not of type
external.As part of this enhancement, ELAN pipeline processing for the network will be
done on the switch only if there is at least one VM port in the network on the switch.
The behavior of VLAN provider networks of type external and flat networks will remain
unchanged.

Problem description
===================

Current ODL implementation supports all configured VLAN segments corresponding to VLAN
provider networks on a particular patch port on all Open vSwitch which are part of the
network. This could have adverse performance impacts because every provider patch port
will receive and processes broadcast traffic for all configured VLAN segments even in
cases when the switch doesn't have a VM port in the network.  Furthermore, for unknown
SMACs it leads to unnecessary punts from ELAN pipeline to controller for source MAC
learning from all the switches.

Use Cases
---------
L2 forwarding between OVS switches using provider type VLAN over L2 segment of the
underlay fabric

Proposed change
===============

Instead of creating the VLAN member interface on the patch port at the time of network
creation, VLAN member interface creation will be deferred until a VM port comes up in the
switch in the VLAN provider network. Switch pipeline will not process broadcast traffic on
this switch in a VLAN provider network until VM port is added to the network. This will be
applicable to VLAN provider network without external router attribute set.

Elan service binding will also be done at the time of VLAN member interface
creation. Since many neutron ports on same switch can belong to a single VLAN provider
network, the flow rule should be created only once when first VM comes up and should be
deleted when there are no more neutron ports in the switch for the VLAN provider network.

Pipeline changes
----------------
None.

Yang changes
------------
``elan:elan-instances`` container will be enhanced with information whether an external
router is attached to VLAN provider network.

.. code-block:: none
   :caption: elan.yang
   :emphasize-lines: 18-22

   container elan-instances {
        description
           "elan instances configuration parameters. Elan instances support both the VLAN and VNI based elans.";

        list elan-instance {
            max-elements "unbounded";
            min-elements "0";
            key "elan-instance-name";
            description
                "Specifies the name of the elan instance. It is a string of 1 to 31
                 case-sensitive characters.";
            leaf elan-instance-name {
                type string;
                description "The name of the elan-instance.";
            }
            ...

            leaf external {
                description "indicates whether the network has external router attached to it";
                type boolean;
                default "false";
            }
        }
   }


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

1. Switch will drop packets if it doesn't have a VM port in the VLAN on which packet is
   received.
2. Unnecessary punts to the controller from ELAN pipeline for source mac learning will be
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
