
.. contents:: Table of Contents
   :depth: 3

================================
Neutron QoS (Quality of Service)
================================

https://git.opendaylight.org/gerrit/#/q/topic:qos

Neutron provides QoS feature which is partially supported in ODL today. This feature is to bring ODL implementation on parity with Neutron QoS API, esp OVS Agent implementation. This will require changes in networking-odl, Neutron Northbound and Netvirt.

Problem description
===================
Detailed description of the problem being solved by this feature

Use Cases
---------
TBD.

Proposed change
===============
TBD.

Pipeline changes
----------------
TBD.

Yang changes
------------
TBD.

.. code-block:: none
   :caption: example.yang

    tbd

Configuration impact
--------------------
TBD.

Clustering considerations
-------------------------
TBD.

Other Infra considerations
--------------------------
TBD.

Security considerations
-----------------------
TBD.

Scale and Performance Impact
----------------------------
TBD.

Targeted Release
----------------
Flourine

Alternatives
------------
N.A.

Usage
=====
TBD.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
TBD.

CLI
---
TBD.

Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a
primary assignee and other contributors.

Primary assignee:
  Vishal Thapar, <#vthapar>, <vthapar@redhat.com>

Other contributors:
  TBD.

Work Items
----------
TBD.

Dependencies
============
This has dependencies on other projects:

  * Neutron <version tbd>
  * Networking-Odl <version tbd>
  * Neutron Northbound

Testing
=======
TBD.

Unit Tests
----------
TBD.

Integration Tests
-----------------
TBD.

CSIT
----
TBD.

Documentation Impact
====================
TBD.

References
==========
* `Quality of Service - Oxygen spec <http://docs.opendaylight.org/projects/netvirt/en/stable-oxygen/specs/qos.html>`__


.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
