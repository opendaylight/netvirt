.. contents:: Table of Contents
      :depth: 3

==============================
Quality of Service for NetVirt
==============================

QoS patches: https://git.opendaylight.org/gerrit/#/q/topic:qos

This feature completes the initial implementation of Neutron QoS [#]_ support
for NetVirt.  Egress bandwidth rate limiting was introduced in the Boron release.
This release will include DSCP Marking support.

Problem description
===================

It is important to be able to configure QoS attributes of workloads on
virtual networks.  The Neutron QoS API provides a method for defining
QoS policies and associated rules which can be applied to Neutron Ports
and Networks.  These rules include:

- Egress Bandwidth Rate Limiting
- DSCP Marking

As a Neutron provider for ODL, NetVirt will provide the ability to report
back to Neutron its QoS rule capabilties and provide the ability to
configure and manage the supported QoS rules on supported backends
(e.g. OVS, ...).  The key change in the Carbon release will be the
addition of support for the DSCP Marking rule.

Use Cases
---------

Neutron QoS API support, including:

- Ingress rate limiting -
  Drop traffic that exceeeds the specified rate parameters for a
  Neutron Port or Network.

- DSCP Marking -
  Set the DSCP field for ingress IP packets from Neutron Ports
  or Networks.

- Reporting of QoS capabilities -
  Report to Neutron which QoS Rules are supported.

Proposed change
===============

To handle DSCP marking, listener support will be added to the
neutronvpn service to respond to changes in DSCP Marking
Rules in QoS Policies in the Neutron Northbound QoS models [#]_ [#]_ .

To implement DSCP marking support, a new ingress QoS
Service is defined in Genius.  When DSCP Marking rule
changes are detected, a rule in a new OpenFlow table for
QoS rules will be updated.


Pipeline changes
----------------
A new QoS table is added to support the new QoS Service:

=====   ==========  ===========================
Table   Match       Action
=====   ==========  ===========================
90      Src IP      Mark packet with DSCP value
=====   ==========  ===========================

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
Additional OpenFlow packets will be generated to configure DSCP marking rules in response
to QoS Policy changes coming from Neutron.

Targeted Release
-----------------
Carbon

Alternatives
------------
Use of OpenFlow meters was desired, but the OpenvSwitch datapath implementation
does not support meters (although the OpenvSwitch OpenFlow protocol implementation
does support meters).

Usage
=====
The user will use the QoS support by enabling and configuring the
QoS extension driver for networking-odl.  This will allow QoS Policies and
Rules to be configured for Neuetron Ports and Networks using Neutron.

Perform the following configuration steps:

-  In */etc/neutron/neutron.conf* enable the QoS service by appending **qos** to
   the **service_plugins** configuration:
   ::

     service_plugins = odl-router, qos

-  Add the QoS notification driver to the */etc/neutron/neutron.conf* file as follows:
   ::

     [qos]
     notification_drivers = odl-qos

-  Enable the QoS extension driver for the core ML2 plugin.
   In file */etc/neutron/plugins/ml2/ml2.conf.ini* append **qos** to **extension_drivers**
   ::

     [ml2]
     extensions_drivers = port_security,qos

Features to Install
-------------------
Install the ODL Karaf feature for NetVirt (no change):

- odl-netvirt-openstack

REST API
--------
None.

CLI
---
Refer to the Neutron CLI Reference [#]_ for the Neutron CLI command syntax
for managing QoS policies and rules for Neutron networks and ports.

Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a
primary assigne and other contributors.

Primary assignee:

-  Poovizhi Pugazh <poovizhi.p@ericsson.com>

Other contributors:

-  Eric Multanen <eric.w.multanen@intel.com>
-  Praveen Mala <praveen.mala@intel.com> (possible CSIT contributor)


Work Items
----------
Task list in Carbon Trello: https://trello.com/c/bLE2n2B1/14-qos

Dependencies
============
Genius project - Code [#]_ to support QoS Service needs to be added.

Neutron Northbound - provides the Neutron QoS models for policies and rules (already done).


Following projects currently depend on NetVirt:
 Unimgr

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
Documentation to describe use of Neutron QoS support with NetVirt
will be added.

OpenFlow pipeline documentation updated to show QoS service table.

References
==========

http://specs.openstack.org/openstack/neutron-specs/specs/newton/ml2-qos-with-dscp.html 

ODL gerrit adding QoS models to Neutron Northbound: https://git.opendaylight.org/gerrit/#/c/37165/

.. [#] Neutron QoS http://docs.openstack.org/developer/neutron/devref/quality_of_service.html
.. [#] Neutron Northbound QoS Model Extensions https://github.com/opendaylight/neutron/blob/master/model/src/main/yang/neutron-qos-ext.yang
.. [#] Neutron Northbound QoS Model https://github.com/opendaylight/neutron/blob/master/model/src/main/yang/neutron-qos.yang
.. [#] Neutron CLI Reference http://docs.openstack.org/cli-reference/neutron.html#neutron-qos-available-rule-types
.. [#] Genius code supporting QoS service https://git.opendaylight.org/gerrit/#/c/49084/


