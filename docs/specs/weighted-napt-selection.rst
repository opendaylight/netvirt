.. contents:: Table of Contents
   :depth: 3

=======================
Weighted NAPT Selection
=======================

https://git.opendaylight.org/gerrit/#/q/topic:weighted-napt-selection

Brief introduction of the feature.

Problem description
===================
NATService needs to select a Designated NAPT switch amongst different switches
hosting VMs in a given VRF. Currently this selection uses a round robin algorithm
which treats all switches as equals. A switch that is selected as Designated
NAPT gets all external traffic from VMs in that VRF, thus putting extra load
on that switch. Since selection of NAPT is runtime decision it is not possible
to scale-up such switches.

To solve this problem we need a mechanism to make NAPT selection logic pick
particular switches which can handle extra traffic, more over ones that can't.
Administrator should be able to mark such switches at deployment.

Use Cases
---------
Main use case is to allow admin to specify configuration for specific computes
so they are more likely to be selected as Designated NAPT Switches. Following use
cases will be supported as part of initial feature.

* Configuration of switch with weight to pin more VRFs to it
* Add a switch with more weight than existing switches in VRF
* Remove a switch with higher weight and add it back in

.. note:: No changes will be made to triggers for NAPT Selection for initial release
   This means following use cases will not be supported yet.

   - Addition of switch with higher weight will not result in re-distribution
     of VRFs.
   - Decreasing/Increasing weight of a switch will not trigger r-distribution of VRFs.


Proposed change
===============
We'll introduce a new configuration parameter called ``odl_base_weight`` which
will be configured in ``external_ids`` parameter of ``Open_vSwitch`` in specific
switches. This will be part of 0-day orchestration. Value for this will be a
number. If nothing is configured bas weight will be considered to be ``0``.

Higher the ``odl_base_weight``, greater the number of VRFs designated on a
given switch.

``NAPTSwitchSelector`` will be modified to factor in this parameter when selecting
a designate NAPT switch. Currently weight of a given switch is only number of VRFs
hosted on it with base weight of 0. Weight of switch is incremented by *1* for each
VRF hosted on it. Switch with least weight at time of selection ends up being selected
as designated Switch. ``odl_base_weight`` of *X* will translate to weight *-X* in
``NAPTSwitchSelector``.

Pipeline changes
----------------
None.

Yang changes
------------
None.

Configuration impact
--------------------
A new configuration parameter called ``odl_base_weight`` which can be configured in
``external_ids`` parameter of ``Open_vSwitch`` for specific switches.

Refer section [Proposed Change] for more details.

Clustering considerations
-------------------------
N.A.

Other Infra considerations
--------------------------
Requires new API in Genius.

Security considerations
-----------------------
N.A.

Scale and Performance Impact
----------------------------
Selecting switches that can handle extra traffic that SNAT brings in will help
with dataplane performance

Targeted Release
----------------
Oxygen

Alternatives
------------
None.

Usage
=====
How will end user use this feature? Primary focus here is how this feature
will be used in an actual deployment.

e.g. For most netvirt features this will include OpenStack APIs.

This section will be primary input for Test and Documentation teams.
Along with above this should also capture REST API and CLI.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
None.

CLI
---
None.

Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a
primary assignee and other contributors.

Primary assignee:
  Vishal Thapar, vthapar, <vishal.thapar@ericsson.com>

Other contributors:
  <developer-b>, <irc nick>, <email>

Work Items
----------
Break up work into individual items. This should be a checklist on a
Trello card for this feature. Provide the link to the trello card or duplicate it.

Dependencies
============
None.

Testing
=======
TBD.

Unit Tests
----------
Any existing UTs will be enhanced accordingly.

Integration Tests
-----------------
Any existing UTs will be enhanced accordingly.

CSIT
----
Following test cases will be added to NAT suite:

* Test NAPT base weight 0
  * This should work like existing logic
* Test NAPT base weight 2
  * Create 4 VMs in 2 VRFs on both computes
  * Compute with base weight 2 is designated for both VRFs


Documentation Impact
====================
Refer section [Configuration Changes]

References
==========
None.
