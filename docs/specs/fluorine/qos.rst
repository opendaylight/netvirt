
.. contents:: Table of Contents
   :depth: 3

.. |yes| unicode:: U+2713
.. |no| unicode:: U+2715
.. |YES| unicode:: U+2714
.. |NO| unicode:: U+2716

================================
Neutron QoS (Quality of Service)
================================

https://git.opendaylight.org/gerrit/#/q/topic:qos

Neutron provides QoS [2]_ feature which is partially supported in OpenDaylight today. This feature is
to bring ODL implementation on parity with Neutron QoS API, esp OVS Agent implementation. This
will require changes in networking-odl, Neutron Northbound and Netvirt.

Problem description
===================
Support for QoS was added to OpenDaylight in Boron release and it was later updated during
Carbon release [1]_. Neutron has updated QoS since then to support more features and
they are already implemented in Neutron OVS Agent.


Use Cases
---------
Neutron QoS API support including:

 - Ingress Rate Limiting
 - FIP QoS

Proposed change
===============
Following table captures current state of QoS support in Neutron and OpenDaylight

    ======================= ===== =====
    Feature                 N-ODL ODL
    ======================= ===== =====
    Bandwidth Limit Egress  |yes| |yes|
    Bandwidth Limit Ingress |no|  |no|
    Min Bandwidth Egress    |no|  |no|
    Min Bandwidth Ingress   |no|  |no|
    DSCP Marking Egress     |yes| |yes|
    DSCP Marking Ingress    |no|  |no|
    FIP QoS                 |no|  |no|
    ======================= ===== =====


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
[3]_

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
  * Neutron Northbound - Flourine

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
.. [1] `Quality of Service - Oxygen spec <http://docs.opendaylight.org/projects/netvirt/en/stable-oxygen/specs/qos.html>`__
.. [2] `Neutron QoS <http://docs.openstack.org/developer/neutron/devref/quality_of_service.html>`__
.. [3] `Neutron Configuration Guide - QoS <https://docs.openstack.org/neutron/pike/admin/config-qos.html>`__

