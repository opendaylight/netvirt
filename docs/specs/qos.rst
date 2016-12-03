..
==============================
Quality of Service for NetVirt
==============================

[https://git.opendaylight.org/gerrit/#/q/topic:qos]

This feature completes the initial implementation of Neutron QoS API support
for NetVirt.  Egress bandwidth rate limiting was introduced in the Boron release.
This release will include DSCP Marking support.



Problem description
===================

It is important to be able to configure QoS attributes of workloads on
virtual networks.  The Neutron QoS API provides a method for definining
QoS policies and associated rules which can be applied to Neutron Ports
and Networks.  These rules include:
  Egress Bandwidth Rate Limiting
  DSCP Marking

As a Neutron provider for ODL, NetVirt will provide the ability to report
back to Neutron its QoS rule capabiltiies and provide the ability to
configure and manage the supported QoS rules on supported backends
(e.g. OVS, ...).  The key change in the Carbon release will be the
addition of support for the DSCP Marking rule.

Use Cases
---------

Neutron QoS API support, including:
- Ingress rate limiting
    Drop traffic that exceeeds the specified rate parameters for a
    Neutron Port or Network.
- DSCP Marking
    Set the DSCP field for ingress IP packets from Neutron Ports
    or Networks.
- Reporting of QoS capabilities
    Report to Neutron which QoS Rules are supported.

Proposed change
===============

To handle DSCP marking, listener support will be added to
neutronvpn service to respond to changes in DSCP Marking
Rules in QoS Policies in the Neutron Northbound models.

To implement DSCP marking support, a new ingress QoS
Service is defined in Genius.  When DSCP Marking rule
changes are detected, a rule in a new OpenFlow table for
QoS rules will be updated.


Pipeline changes
----------------
A new QoS table is added to support the new QoS Service:

| **Table 90** for flows to match IP address and set an action to mark the packet with DSCP value.



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
The user will use the QoS support be enabling and configuring the
QoS extension driver for networking-odl.  This will allow QoS Policies and
Rules to be configured for Neuetron Ports and Networks using Neutron.

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
Who is implementing this feature? In case of multiple authors, designate a
primary assigne and other contributors.

Primary assignee:
  Poovizhi Pugazh <poovizhi.p@ericsson.com>

Other contributors:
  Eric Multanen <eric.w.multanen@intel.com>


Work Items
----------
- Complete QoS blueprint
- Add Genius support for new QoS Service
- Add NetVirt support to configure DSCP rules for port and networks
- Add NetVrit support to report QoS Rulle support to Neutron
- NetVirt QoS documentation
- NetVirt QoS Bandwidth Rate limiting integration test
- NetVirt QoS Dscp marking integration test


Dependencies
============
Genius project - Code to support QoS Service needs to be added.

Neutron Northbound - provides the Neutron QoS models for policies and rules (already done).


Following projects currently depend on Netvirt:
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
Add any useful references. Some examples:


* Links to Summit presentation, discussion etc.
* Links to mail list discussions
* Links to patches in other projects
* Links to external documentation
  [http://specs.openstack.org/openstack/neutron-specs/specs/newton/ml2-qos-with-dscp.html]

[1] http://docs.opendaylight.org/en/latest/documentation.html

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode

