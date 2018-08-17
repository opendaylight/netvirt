.. contents:: Table of Contents
   :depth: 2

=============================
Support FIPs for Octavia VIPs
=============================

Problem description
===================
An Octavia VIP is a neutron port that is not bound to any VM and is therefor not added to br-int. The VM containing the active haproxy sends gratuitous ARPs for the VIP's IP and ODL intercepts those and programs flows to forward VM traffic to the VMs port. Note that this is my understanding of how this all works, I have not validated it, the opener of this bug confirms that it works.

The ODL code responsible for configuring the FIP association flows on OVS currently relies on a southbound openflow port that corresponds to the neutron FIP port. The only real reason this is required is so that ODL can decide which switch should get the flows. See FloatingIPListener#createNATFlowEntries. In the case of the VIP port, there is no corresponding southbound port so the flows never get configured.

Use Cases
---------
FIP assigned to an Octavia loadbalancer

Proposed change
===============
The basic solution is to use the OF packet-in event to
learn the dpn where the NAT flows need to be programmed.

Overview of code changes:

1. Refactor NatInterfaceStateChangeListener into two classes:
 - NatInterfaceStateChangeListener which just receives events
 - NatSouthboundEventHandlers which contains the logic invoked
   for when an interface state changes (or a garp is received)
2. Implementation of NatArpNotificationHandler which receives
   the garps and invokes the correct methods in
   NatSouthboundEventHandlers
3. neutron-vip-state yang model which is used together with
   VipStateTracker (DataObjectCache) to manage state of the
   discovered VIPs. This is required in cases where the
   associated Ocatavia Amphora VM changes to a different
   compute node. In this case the existing flows must be
   removed from the odl compute node.
4. Tweak VIP learning code to accept neutron ports that are
   owned by "Octavia", previously the code assumed no neutron
   port ever needed to be learned.

Pipeline changes
----------------
None

Yang changes
------------
   :caption: odl-nat.yang

    container neutron-vip-states {
        config false;
        list vip-state {
            key ip;
            leaf ip {
                type string;
            }
            leaf dpn-id {
                type uint64;
            }
            leaf ifc-name {
                type string;
            }
        }
    }

Configuration impact
--------------------
None

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
Unlikely to have any real impact

Targeted Release
----------------
Flourine with a backport as far as Oxygen

Alternatives
------------
N/A

Usage
=====
This feature should "just work" with Octavia. No special usage is required.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
None

CLI
---
None

Implementation
==============

Assignee(s)
-----------
Josh Hershberg, jhershbe, jhershbe@redhat.com

Work Items
----------
https://git.opendaylight.org/gerrit/#/c/75281/

https://git.opendaylight.org/gerrit/#/c/75248/

Dependencies
============
None

Testing
=======

Unit Tests
----------
None

Integration Tests
-----------------
As this is a bug and we are rushing to fix it for now testing will be done manually

CSIT
----
Yes, we really should in the near future.

Documentation Impact
====================
None

References
==========
None
