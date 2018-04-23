.. contents:: Table of Contents
      :depth: 6

==================================================
Retain Elected Napt Switch After Upgrade for SNAT
==================================================

https://git.opendaylight.org/gerrit/#/q/topic:retain-napt-switch-after-upgrade-for-snat

**Important**: All gerrit links raised for this feature will have topic name as
**retain-napt-switch-after-upgrade-for-snat**

This feature attempts to retain the earlier elected Napt-Switch for a given Router(VRF) before
upgrade as Napt-Switch even after the upgrade of ``OpenDaylight Controller`` in order to minimize
dataplane churn for existing SNAT sessions.

Problem description
===================

For each of the router's in controller-Based SNAT, one of the OVS will be elected as Napt-Switch
from the list of candidate OVS(in which Router has it's presence). New Napt-Switch for this router
gets re-elected under following scenarios.

* When the elected Napt Switch goes down or rebooted.
* When the last VM on the elected Napt Switch is deleted/moved.
* Cluster Reboot scenario.
* When VM booted on Non-Napt Switch and if the Napt-Switch is down at that time.

With current implementation, whenever a VM is booted on any OVS for a given router, then first
we check if there already elected Napt-Switch and in connected state currently. If not, then
the current OVS gets itself elected as Napt Switch and all NAT flows for the on-going SNAT session
will be installed into the newly elected Napt Switch.

As part of upgrade, the Configation DataStore back-up will be taken and the same will be restored
after upgrade. When the OVS are connected back to ODL-controller, if the Non-Napt Switch gets
connected first before Napt-Switch, its gets re-elected as Napt-Switch as its finds earlier elected
Napt-Switch is in disconnected state. As a result, the other Non-Napt Switches(which are yet to be connected)
continue to send the traffic to older Napt-switch resulting in failure for the ongoing SNAT sessions
until these Non-napt Switches are connected and updated with flow pointing to newly elected Napt Switch.

Also, there are high possbility of a same OVS getting elected as Napt-Switch for many of the routers
(where OVS has the VM presence for these routers) after upgrade.

The workflow will be changed to prevent re-election of Napt Switch as part of upgrade for
controller-based SNAT.

Use Cases
---------
- Validate SNAT functionality before and after upgrade.

Proposed change
===============

Currently, we have a utility flag ``upgradeInProgress`` to track whether or not upgrade in progress.
This flag will be set to true using suitable REST call before start any OVS's are getting connected
back ODL-controller.

When OVS gets connnected which is a Non-Napt Switch and if the corresponding Napt-switch is not yet
connected and if the ``upgradeInProgress`` is set true, it will not try to re-elect itself as
Napt-Switch and continue as Non-Napt Switch.

After all the OVS are connected back, this flag will be set to false during which NAT will again go
through the list of earlier elected Napt Switches and validate there connectivity status. If any of
the earlier elected Napt Switch is not connected back, then NAT will trigger re-election to find
and re-elect a new Napt-Switch.

Pipeline changes
----------------
None

YANG changes
------------
None

Configuration impact
--------------------
None

Clustering considerations
-------------------------
No specific additional clustering considerations to be adhered to.


Other Infra considerations
--------------------------
None.


Security considerations
-----------------------
None.


Scale and Performance Impact
----------------------------
None.


Targeted Release(s)
-------------------
Fluorine.


Known Limitations
-----------------
None.


Alternatives
------------
N.A.


Usage
=====

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
No new changes to the existing REST APIs.

CLI
---
No new CLI is being added.

Implementation
==============

Assignee(s)
-----------
Primary assignee:
  Chetan Arakere Gowdru <chetan.arakere@altencalsoftlabs.com>

Other contributors:

Work Items
----------

#. Add Check to prevent re-election of Napt Switch if upgradeInProgress is set.
#. Re-check the connectivity status of earlier elected Napt-Switch after upgrade is completed.
#. Re-elect new Switch if earlier elected Napt Switch is down after upgrade.


Dependencies
============
This doesn't add any new dependencies.


Testing
=======

Unit Tests
----------
Appropriate UTs will be added for the new code coming in once framework is in place.

Integration Tests
-----------------
There won't be any Integration tests provided for this feature.

CSIT
----
TBD.

Documentation Impact
====================
This will require changes to the Developer Guide.

Developer Guide needs to capture how this feature modifies the existing Netvirt L3 forwarding
service implementation.


References
==========

* `Upgrade in Progress flag <https://git.opendaylight.org/gerrit/#/c/65299/>`_