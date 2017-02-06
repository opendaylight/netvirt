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
of necessary flows, in cases where the port is associated with a single
ACL. Due to the complication of supporting multiple ACLs on a single port,
the current implementation will stay the same for these cases.

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

Use Cases
---------

Neutron configuration of a security rule, configured with a remote SG.
This optimization will be relevant only when there is a single security group that
is associated with the port.

This will be done in the ACL implementation, so any ACL configured with a remote ACL
via a different northbound or REST would also be handled.

Proposed change
===============

This blueprint proposes adding a new indirection table in the ACL service in each direction,
which will attempt to match the "remote" IP address associated with the packet
(dst_ip in Ingress ACL, src_ip in Egress ACL), and set the SG value in the metadata.
This match will also include the ELAN ID to handle ports with overlapping IPs.

These flows will be added to the ACL3 table.
In addition, for each such ip->SG flow inserted in ACL3, we will insert a single SG metadata match
in ACL4 for each SG rule on the port configured with this remote SG.

If the IP is associated with multiple SGs - it is impossible to do a 1:1 matching of
the SG, so we will not set the metadata at this time and fallback to the current implementation
of matching all possible IPs in the ACL table - for this ACL3 will have a default flow passing
the unmatched packets to ACL4 with an empty metadata SG_ID write (e.g. 0x0), to prevent potential
garbage in the metadata SG ID.

This means that on transition from a single SG on the port to multiple SG (and back),
we would need to remove/add these flows from ACL3, and insert the correct rules into ACL4.

**ACL1:**

- This is the ACL that has default allow rules - it is left untouched, and usually goes to ACL3 (ACL2 is only used
  for the learn implementation, so we will ignore it in this spec as it is irrelevant).

**ACL3:**

- For each port with a single SG - we will match on the IPs and the ELAN ID (for tenant awareness) here,
  and set the SG ID in the metadata, before going to the ACL4 table.
- For any port with multiple SGs (or with no SG) - an empty value (0x0) will be set as the SG ID in the metadata,
  to avoid potential garbage in the SG ID, and goto ACL4 table.

**ACL4:**

- For each security rule that *doesn't have* a remote SG, we keep the behavior the same: ACL4 matches on rule,
  and resubmits to dispatcher if there is a match (Allow). The SG ID in the metadata will **not** be matched.
- For each security rule that *does have* a remote SG, we have two options:

  - For ports that are associated with a single SG - there will be a single flow per rule,
    matching the SG ID from the metadata (in addition to the other rule matches) and allowing.
  - For ports that are associated with multiple SGs - the existing implementation will stay the same,
    multiplying the rule with all possible IP matches from the remote security groups.

Considering the example from the problem description above, the new implementation would result
in a much reduced number of flows:

5000+50 = 5050 flows on each compute, and 505,000 flows in ODL MDSAL.

As noted above, this would require using part of the metadata for writing/matching of an ACL ID.
We would likely require at least 12 bits for this, to support up to 4K SGs,
where 16 bits to support up to 65K would be ideal.
If the metadata bits are not available, we can use a register for this purpose (16 bits).

In addition, the dispatcher will set the ELAN ID in the metadata before entering the ACL services,
to allow tenant aware IP to SG detection, supporting multi-tenants with IP collisions.

Pipeline changes
----------------
ACL4 will be added, and the flows in ACL3/ACL4 will be modified as noted above in the proposed change:

=============   =================================================  ================================================
Table           Match                                              Action
=============   =================================================  ================================================
Dispatcher      metadata=service_id:ACL                            write_metadata:(elan_id=ELAN, service_id=NEXT), goto_table:ACL1
ACL1                                                               goto_table:ACL3
ACL3            metadata=ELAN_ID, ip_src/dst=VM1_IP                write_metadata:(remote_acl=id), goto_table:ACL4
ACL3            metadata=ELAN_ID, ip_src/dst=VM2_IP                write_metadata:(remote_acl=id), goto_table:ACL4
...
ACL3                                                               goto_table:ACL4
ACL4            metadata=lport, <acl_rule>                         resubmit(,DISPATCHER)   (X)
ACL4            metadata=lport+remote_acl, <acl_rule>              resubmit(,DISPATCHER)   (XX)
ACL4            metadata=lport,ip_src/dst=VM1_IP, <acl_rule>       resubmit(,DISPATCHER)   (XXX)
ACL4            metadata=lport,ip_src/dst=VM2_IP, <acl_rule>       resubmit(,DISPATCHER)   (XXX)
...
=============   =================================================  ================================================

| (X)   These are the regular rules, not configured with any remote SG.
| (XX)  These are the proposed rules with the optimization - assuming the lport is using a single ACL.
| (XXX) These are the remote SG rules in the current implementation, which we will fall back to if the lport has multiple ACLs.

**Table Numbering:**

Currently the Ingress ACLs use tables *40,41,42* and the Egress ACLs use tables *251,252,253*.

Considering table 43 is already proposed to be taken by SNAT, and to align with symmetric
numbering with the Egress ACLs, I propose the following change:

- Ingress ACLs: 31,  32,  33,  34
- Egress  ACLs: 251, 252, 253, 254

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
For fully optimized support in all scenarios for remote SGs, meaning including
support for ports with multiple ACLs on them, we did consider implementing a similar
optimization.

However, for this to happen due to OpenFlow limitations we would need to introduce
an internal dispatcher inside the ACL services, meaning we loop the ACL service multiple
times, each time setting a different metadata SG value for the port.

For another approach we could use a bitmask, but this would limit the number of possible SGs to
be the number of bits in the mask, which is much too low for any reasonable use case.

Usage
=====
Any configuration of ACL rules with remote ACLs will receive this
optimization if the port is using a single SG.

Functionality should remain as before in any case.

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
