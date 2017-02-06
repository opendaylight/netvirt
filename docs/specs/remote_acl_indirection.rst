.. contents:: Table of Contents
      :depth: 3

=======================================================
ACL Remote ACL - Indirection Table to Improve Scale
=======================================================
ACL Remote ACL Indirection patches: https://git.opendaylight.org/gerrit/#/q/topic:remote_acl_indirection

The Carbon release will enhance the initial implementation of ACL
remote ACLs filtering which was released in Boron.
The Boron release added full support for remote ACLs, however the current
implementation does not scale well in terms of flows.
The Carbon release will update the implementation to introduce a new
indirection table for ACL rules with remote ACLs, to reduce the number
of necessary flows.

Problem description
===================

Today, for each logical port, an ACL rule results in a flow in the
ACL table (ACL3).
When a remote ACL is configured on this rule, this flow is multiplied for
each VM in the remote ACL, resulting in a very large number of flows.

For example, consider we have:

- 100 computes
- 50 VMs on each compute (5000 VMs total),
- All VMs are in a SG (SG1)
- This SG has a security rule configured on it with remote SG=SG1
  (it is common to set the remote SG as itself, to set rules within the SG).

This would result in 50*5000 = 250,000 flows on each compute, and 25M flows in ODL MDSAL (!).

This blueprint proposes adding a new indirection table at the end of the ACL processing (ACL4)
in each direction, which will receive packets for rules with a remote SG configured.
Packets with no remote SG will continue to return from ACL3 to the dispatcher.

**ACL3:**
- For each security rule that ``doesn't have`` a remote SG, we keep the behavior the same: ACL3 matches on rule, and resubmits to dispatcher.
- For each security rule that ``has`` a remote SG, ACL3 matches on rule, sets metadata to remote SG, and goto_table ACL4.
**ACL4:**
- For each source IP inside the remote SG, match metadata=RemoteSG1,ip_src=IP, and resubmit to dispatcher.

Considering the example above, the new implementation would result in a much reduced number of flows:
50+5000 = 5500 flows on each compute, and 550,000 flows in ODL MDSAL.

Use Cases
---------

Neutron configuration of a security rule, configured with a remote SG.

This will be done in the ACL implementation, so any ACL configured with a remote ACL
via a different northbound or REST would also be handled.

Proposed change
===============

The proposed change is to modify the flows only for ACL rules that have a remote ACL configured.
These flows in ACL3, triggered by security rule association with a port, would now use metadata
to mark the remote ACL the rule is configured with, and goto_table ACL4 instead of resubmitting
to the dispatcher.

This will leave a single flow per ACL rule in ACL3, without matching on any source IP.

Whenever a port is associated with an ACL, we will add a flow to table ACL4,
matching the ACL ID using metadata and the src_ip using the port IP.

This metadata ACL ID would need to be unique for each security rule, we can possibly use
the ELAN ID as a part of the metadata and match on ELAN ID + ACL ID, to ensure matching
of VM IPs only from the correct ELAN (consider two VMs, with an overlapping IP, one using SG1
and one using SG2 - and SG1 has ALLOW rules for remote SG1 - itself).
An additional VM in SG1 should be allowed to access VM1, but not VM2.

We would likely require at least 12 bits for this, to support up to 4K SGs.
If the metadata bits are not available, we can use a register for this purpose (16 bits).

This is an additional problem with the current implementation that does not take this into
account.

Pipeline changes
----------------
A new ACL4 table is added to support the new remote ACL indirection table, and the rule in ACL3 is modified:

=============   =====================================  ================================================
Table           Match                                  Action
=============   =====================================  ================================================
ACL3            metadata=lport, <acl_rule>             write_metadata:(ACL_ID+ELAN_ID), goto_table:ACL4
ACL4            metadata=(ACL_ID+ELAN_ID)              ip_src/dst=VM_IP, resubmit(,DISPATCHER)
=============   =====================================  ================================================

Currently the Ingress ACLs use tables 40,41,42 and the Egress ACLs use tables 251,252,253
Considering table 43 is already proposed to be taken by SNAT, and to align with symmetric
numbering with the Egress ACLs, I propose the following change:

Ingress ACLs: 31,  32,  33,  34
Egress  ACLs: 251, 252, 253, 254

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
See example in description.
The scale of the flows will be drastically reduced when using remote ACLs.

Targeted Release
-----------------
Carbon

Alternatives
------------
None.

Usage
=====
Any configuration of ACLs rules with remote ACLs will receive this
optimization. Functionality should remain as before.

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
for managing Security Rules with Remote Security Groups.

Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a
primary assigne and other contributors.

Primary assignee:

-  Alon Kochba <alonko@hpe.com>
-  Aswin Suryanarayanan <asuryana@redhat.com>

Other contributors:

-  ?


Work Items
----------
Task list in Carbon Trello: https://trello.com/c/6WBbSSkr/145-acl-remote-acls-indirection-table-to-improve-scale-remote-acl-indirection

Dependencies
============
None.

Testing
=======

Unit Tests
----------

Integration Tests
-----------------

CSIT
----
We should add tests verifying remote SG configuration functionality.
There should be at least one positive and one negative test, for
testing security rules specifically allowing traffic between
two VMs in the same SG, and not allowing traffic between two VMs
on separate SGs.

Documentation Impact
====================
None.

References
==========

.. [#] Neutron Security Groups http://docs.openstack.org/user-guide/cli-nova-configure-access-security-for-instances.html
