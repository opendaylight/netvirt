
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
to bring ODL implementation on parity with Neutron QoS API, esp OVS Agent implementation as detailed in
[3]_. This will require changes in networking-odl, Neutron Northbound and Netvirt.

.. note::
   - Ingress/Egress in Neutorn is from VM's perspective.
   - Ingress/Egress in OVS/ODL is from switch perspective.
   - Egress from VM is Ingress on switch and vice versa

Problem description
===================
Support for QoS was added to OpenDaylight in Boron release and it was later updated during
Carbon release [1]_. Neutron has updated QoS since then to support more features and
they are already implemented in Neutron OVS Agent.

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

Use Cases
---------
Neutron QoS API support for Bandwidth Limit Ingress and FIP QOS will require adding followiung
changes to networking-odl driver and OpenDaylight:

- Bandwidth Limit Ingress
- FIP QoS

Proposed change
===============
This will require changes to multiple components.

Bandwidth Limit Ingress
-----------------------
Ingress Limit is not as simple as egress. This will require creation of a QoS Profile and adding
the port to that profile.[5] Then we appply limiters on that queue. OVSDB Plugin already supports
creating QoS Profiles. OpenFlow rules for that port will then direct traffic to that queue.

Networking-odl
^^^^^^^^^^^^^^
Direction field has already been added to networking-odl but it only supports ``egress`` for now.
Support for Bandwidth Limit Ingress will require adding ``ingress`` direciton and additional
checks for policies that won't support ingress yet [DSCP marking].

Neutron Northbound
^^^^^^^^^^^^^^^^^^
Direction field is already present in neutron-qos.yang so no yang changes needed for this.
However, SPI still needs to add code to populate mdsal with direction field. Corresponding
changes to NeutronQos POJO will also be done.

OVSDB
^^^^^
Nothing extra needs to be done here, OVSDB already supports QoS queues.

Genius
^^^^^^
To support Ingress limiting, QoS needs to bind as an Egress service in Genius. For this a new
``QOS_EGRESS_TABLE [231]`` will be added.

.. note:: TBD - QoS Egress Service priority

Netvirt
^^^^^^^
Existing QoS code in Netvirt will be enhanced to support Ingress rules, bind/unbind Egress service,
create OVS QoS profiles and add port to the QoS queues.


FIP QoS
-------
Similar to Ingress Limit, QoS profiles will be used to create queue per FIP and then use
OpenFlow flows to direct traffic to the specific queue.

Networking-odl
^^^^^^^^^^^^^^
For FIP QoS, support for FIP as an attribute to QoS will be added as per[4]_

NeutronNorthbound
^^^^^^^^^^^^^^^^^
qos-policy-id will be added to ``L3-floatingip-attributes`` in neutron-L3.yang

OVSDB
^^^^^
Nothing needs to be done here.

Genius
^^^^^^
A new ``QOS_FIP_INGRESS [71]`` table will be added to Ingress L3 pipeline to add set queue
for Egress Limit. For Ingress Limit, ``QOS_FIP_EGRESS [232]`` will be added to set queue.

Netvirt
^^^^^^^
FloatingIp listeners will be enhanced to track QoS configuration and invoke QoS API
to configure flow rules for the FloatingIp. QoS API will create OVS profiles for QoS
rules and when applied to FIP or port, will program appropriate flows.

Pipeline changes
----------------
A new QoS Egress table will be added to support for Ingress rules on port and another
for FIP.

=====================  =====================================  ===========================
Table                  Match                                  Action
=====================  =====================================  ===========================
QoS FIP Ingress [71]   Ethtype == IPv4 or IPv6 AND IP         SetQueue
QoS Port Egress [231]  Ethtype == IPv4 or IPv6 AND LPort tag  SetQueue
QoS FIP Egress  [232]  Ethtype == IPv4 or IPv6 AND IP         SetQueue
=====================  =====================================  ===========================

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
.. [3] `Neutron Configuration Guide - QoS <https://docs.openstack.org/neutron/queens/admin/config-qos.html>`__
.. [4] `Floating IP Rate Limit <https://specs.openstack.org/openstack/neutron-specs/specs/pike/layer-3-rate-limit.html>`__
.. [5] `OVS QoS FAQ <http://docs.openvswitch.org/en/latest/faq/qos/>`__
