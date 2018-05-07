
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

Neutron provides QoS [2]_ [6]_ feature which is partially supported in OpenDaylight today. This feature is
to bring ODL implementation on parity with Neutron QoS API, esp OVS Agent implementation as detailed in
[3]_. This will require changes in networking-odl, Neutron Northbound and Netvirt.

.. note::
   - Ingress/Egress in Neutron is from VM's perspective.
   - Ingress/Egress in OVS/ODL is from switch perspective.
   - Egress from VM is Ingress on switch and vice versa

Problem description
===================
Support for QoS was added to OpenDaylight in Boron release and it was later updated during
Carbon release [1]_. Neutron has updated QoS since then to support more features and
they are already implemented in Neutron OVS Agent.

Following table captures current state of QoS support in Neutron and OpenDaylight as well
as comparison with current support in OVS Agent.

======================= ===== ===== ==========
Feature                 N-ODL ODL   OVS Agent
======================= ===== ===== ==========
Bandwidth Limit Egress  |yes| |yes| |yes|
Bandwidth Limit Ingress |no|  |no|  |yes|
Min Bandwidth Egress    |no|  |no|  SRIOV only
Min Bandwidth Ingress   |no|  |no|  |no|
DSCP Marking Egress     |no|  |yes| |yes|
DSCP Marking Ingress    |no|  |no|  |no|
FIP QoS                 |no|  |no|  |yes|
======================= ===== ===== ==========

Use Cases
---------
Neutron QoS API support for Bandwidth Limit Ingress and FIP QOS will require adding followiung
changes to networking-odl driver and OpenDaylight:

- Bandwidth Limit Ingress
- FIP QoS

Following use cases will not be supported:

- Min Bandwidth Ingress/Egress
- DSCP Marking Ingress

Proposed change
===============
This will require changes to multiple components.

Bandwidth Limit Ingress
-----------------------
Ingress Limit is not as simple as egress. This will require creation of a QoS Profile and adding
the port to that profile [5]_. Then we appply limiters on that queue. OVSDB Plugin already supports
creating QoS Profiles. OpenFlow rules for that port will then direct traffic to that queue.

Creation of profile varies for DPDK vs Kernel datapath [9]_

.. code-block:: none
   :caption: Sample OVS QoS configuration Kernel datapath
   :emphasize-lines: 6-12

    ovs-vsctl -- \
        add-br br0 -- \
        add-port br0 eth0 -- \
        add-port br0 vif1.0 -- set interface vif1.0 ofport_request=5 -- \
        add-port br0 vif2.0 -- set interface vif2.0 ofport_request=6 -- \
        set port eth0 qos=@newqos -- \
        --id=@newqos create qos type=linux-htb \
            other-config:max-rate=1000000000 \
            queues:123=@vif10queue \
            queues:234=@vif20queue -- \
        --id=@vif10queue create queue other-config:max-rate=10000000 -- \
        --id=@vif20queue create queue other-config:max-rate=20000000


.. code-block:: none
   :caption: Sample OVS QoS configuration DPDK
   :emphasize-lines: 1-3

   ovs-vsctl set port vhost-user0 qos=@newqos -- \
       --id=@newqos create qos type=egress-policer other-config:cir=46000000 \
       other-config:cbs=2048`


Networking-odl
^^^^^^^^^^^^^^
With [7]_ nothing else should be needed in n-odl.

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
``QOS_EGRESS_TABLE [232]`` will be added.

.. note:: TBD - QoS Egress Service priority

Netvirt
^^^^^^^
Existing QoS code in Netvirt will be enhanced to support Ingress rules, bind/unbind Egress service,
create OVS QoS profiles and add port to the QoS queues.


FIP QoS
-------
Similar to Ingress Limit, QoS profiles will be used to create queue per FIP and then use
OpenFlow flows to direct traffic to the specific queue [4]_.

Networking-odl
^^^^^^^^^^^^^^
Nothing should be needed once [7]_ and [8]_ are merged.

NeutronNorthbound
^^^^^^^^^^^^^^^^^
qos-policy-id will be added to ``L3-floatingip-attributes`` in neutron-L3.yang

OVSDB
^^^^^
Nothing needs to be done here.

Genius
^^^^^^
A new ``QOS_FIP_INGRESS [91]`` table will be added to Ingress L3 pipeline to add set queue
for Egress Limit. For Ingress Limit, ``QOS_FIP_EGRESS [233]`` will be added to set queue.

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
QoS FIP Ingress [91]   Ethtype == IPv4 or IPv6 AND IP         SetQueue
QoS Port Egress [232]  Ethtype == IPv4 or IPv6 AND LPort tag  SetQueue
QoS FIP Egress  [233]  Ethtype == IPv4 or IPv6 AND IP         SetQueue
=====================  =====================================  ===========================

Yang changes
------------
No yang changes needed for Ingress support in QoS. Ingress is already supported in NeutronNorthbound and
[7]_ will enable it as part of networking-odl.

Changes will be needed to neutron-qos-ext.yang to augment floating-ip.

.. code-block:: none
   :caption: neutron-qos-ext.yang
   :emphasize-lines: 1-10

    augment "/neutron:neutron/neutron:floating-ips/neutron:ip" {
        description "This module augments the floating-ips container
            in the neutron-floating-ips module with qos information";
        // ext:augment-identifier value needs to unique as name of the generated classes
        // is derived from this string
        ext:augment-identifier "qos-floating-ip-extension";
        leaf qos-policy-id {
            description "The floating-ips to which the Qos Policies can be applied";
            type yang:uuid;
        }


Configuration impact
--------------------
This doesn't introduce any new configuration parameters.

Clustering considerations
-------------------------
None

Other Infra considerations
--------------------------
None

Security considerations
-----------------------
None

Scale and Performance Impact
----------------------------
None

Targeted Release
----------------
Flourine

Alternatives
------------
N.A.

Usage
=====
Nothing extra needs to be done to enable these features other than already captured in [1].

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
None in ODL other than yang changes. For Neutron API refer [2]__

CLI
---
[3]_
[4]_

Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a
primary assignee and other contributors.

Primary assignee:
  Vishal Thapar, <#vthapar>, <vthapar@redhat.com>

Other contributors:
  TBD. (volunteers welcome)

Work Items
----------
1. Changes in networking-odl (mostly testing)
2. Changes in NeutronNorthbound
3. Add listeners in Netvirt
4. Genius changes for modified pipeline
5. Netvirt implementation for Bandwidth Limit Ingress
6. Netvirt changes for FIP Egress
7. Netvirt changes for FIP Ingress
8. Add CSIT (depends on CSIT for existing QoS features)
9. Documentation

Dependencies
============
This has dependencies on other projects:

  * Neutron - Rocky
  * Networking-Odl - Rocky
  * Neutron Northbound - Flourine
  * OVSDB - Flourine
  * Genius - Flourine

Testing
=======
This will update existing tests for QoS for newer use cases

Unit Tests
----------
Existing UTs, if any, will be updated to provide coverage for new code.

Integration Tests
-----------------
N.A.

CSIT
----
QoS use cases are currently not covered by CSIT. Once CSIT is added for QoS, more test cases
will be added to cover these use cases.

Once [7]_ and [8]_ are done, we can also enable QoS tempest tests in existing Netvirt CSIT.

Documentation Impact
====================
Existing documenation will be updated to reflect the new changes.

References
==========
.. [1] `Quality of Service - Oxygen spec <http://docs.opendaylight.org/projects/netvirt/en/stable-oxygen/specs/qos.html>`__
.. [2] `Neutron QoS <hhttp://specs.openstack.org/openstack/neutron-specs/specs/liberty/qos-api-extension.html>`__
.. [3] `Neutron Configuration Guide - QoS <https://docs.openstack.org/neutron/queens/admin/config-qos.html>`__
.. [4] `Floating IP Rate Limit <https://specs.openstack.org/openstack/neutron-specs/specs/pike/layer-3-rate-limit.html>`__
.. [5] `OVS QoS FAQ <http://docs.openvswitch.org/en/latest/faq/qos/>`__
.. [6] `QoS Spec <https://docs.openstack.org/neutron/newton/devref/quality_of_service.html>`__
.. [7] `<https://review.openstack.org/#/c/567626/>`__
.. [8] `<https://review.openstack.org/#/c/504182/>`__
.. [9] `<http://docs.openvswitch.org/en/latest/topics/dpdk/qos/>`__
