.. contents:: Table of Contents
      :depth: 3

=======================================================
ACL - Reflecting the ACL changes on existing communication
=======================================================
ACL patches:
None

This spec is to reflecting the ACL changes on the existing communication. The current ACL implementation 
does not affect the existing communication when the ACL flows are changed. The dissociation of ACL
rule from the VM, should affect the existing communication. The existing communication is still succeeded even
though there is no respective ACL flow available in the flow table. This spec will explain the new behaviour of ACL
which will solve the existing communication problem. This fix will improve the pipeline behaviour in terms of
reliable communication between the VMS.

Problem description
===================

When the communication begin, the first packet will hit the actual SG flow, which commit the packets in conntrack.
Further packets will hit the conntrack flow and directly will go to the conntrack pipeline without hitting the SG
flow again. It will continue until we terminate the communication. When we dissociating the rule from the VM, the
respective flow will be removed from the flow table and further new communication will not be succeeded. But already
established communication is still working even the SG flow not available in the flow table.

For example, consider the below scenario which explains the problem in detail,

- Created a VM with ALL ICMP egress rule
- Pinged the DHCP server from the VM
- Dissociated the ALL ICMP egress rule without affecting the ongoing communication
- Removal of ICMP rule is not affecting the existing communication and it still succeeded even though required
ICMP flow is not exists in the flow table.

The communication between the VMs should be reliable and it should be succeed only when the respective SG flow
exists. When a SG rule is removed from the VM, the packets of respective ongoing communication should be dropped and
communication should fail.

Use Cases
---------

This fix will affect all the SG flows and conttrack flows in the pipeline. When a VMs are created with the SG,
The respective communication should happen between the VMs. Dissociating a rule from the VM, should stops
the existing communication as well, if any. Associating the same rule again to the VM, should resumes the existing
communication.

A new flow with default hard time out, will be added in the ACL2 table and It should remove after the hard time_out
reaches. The time out for each protocol is described in proposed change topic.

ACL changes reflection on existing communication is applicable for all the protocol rules (ICMP, TCP, UDP..). So all
the communication should be work only if the VM associated with the corresponding rules.

Proposed change
===============

This spec proposes the fix that requires some additional flows to be installed in ACL2 table and also changes in
ACL3 table flows.

In this fix, we are going to use the conntrack metadata flag "ct_label". For all the packets of new communication,
The default value of this ct_label is 0. We can change the ct_label value using set_field method.

Currently we have a common conntrack flow (+est) for all the VM, which allows the committed packets through
conntrack connection. In this conntrack flow we will add ct_label flag condition to match the ct_label value of
incoming packets. Sample of conntrack flow will be,

   [1] "n_packets=627, n_bytes=61446, priority=62020,ct_state=-new+est-rel-inv+trk, ct_label=1 actions=resubmit(,220)"

Similarly, We will add ct_label flag condition in all SG flows, installing in the ACL3 tables. This flag will help
the packets of existing communication, to identify the ACL changes occurred on the specific VM. Sample of SG flow,

   [2] "n_packets=33, n_bytes=3234, priority=3902, ct_state=+trk, ct_label=0,icmp,reg6=0x200/0xfffff00 actions=ct(commit,zone=6001,exec(set_field:0x1->ct_label)),resubmit(,220)"

When a communication begin, the packets will have ct_label=0, It will hit the above SG flow first, This SG flow will
execute the set_field method to change the ct_label value as 1, before resubmitting to dispatcher table. Hence the
ct_label value changes committed to the conntrack, further packets will have the ct_label value as 1, will hit the
conntrack flow straightly.

When we delete a SG flow, This fix will add a new flow in the ACL2 table.

   [3] "n_packets=73, n_bytes=7154, priority=62021, ct_label=0x1,icmp,reg6=0x200/0xfffff00 actions=ct(commit,exec(set_field:0->ct_label)),goto_table:ACL3"

This flow will have the hard time out value based on the protocol. This flow will automatically getting removed
from the table after reaches the configured time out.

===========================
Protocol    -   Time out (secs)
===========================
ICMP        -   60
TCP         -   300
UDP         -   300

===========================

The new flow added in ACL2 table, will change the ct_label value to default value (i.e 0) and it will commit this
value change to the conntrack. Since the value of ct_label value changed to 0, the packet will not hit
the conntrack flow, and the packet will check whether the ACL3 table having any other flows to handle. If any flow
is available, the packet will be recommitted again to the conntrack and make the communication possible, else it
will be dropped by the default DROP flow.

If we restore the SG rule again to the VM, we will delete the added flow [3] in the ACL2 table, so the packets of
existing communication will hit the SG flow, which will set the ct_label value to 1. It resumes the existing
communication to be succeed.

These changes will affect the already established communication only and the newly established communication will
work properly without any problem since every new packets will have default ct_label value.

**ACL1 (211/241):**

- This is the ACL that has default allow rules - it is left untouched, and usually goes to ACL2.

**ACL2 (212/242):**

- For each ACL flow deletion, we will add a flow which will match on ct_label, and set the ct_label value
to default. before going to the ACL3 table.

**ACL3 (213/243):**

- For each ACL flow, we will add a match on ct_label value, and changed the ct_label value from the default value,
and resubmits to dispatcher.
- In conntrack flow, we will add a match on ct_label value.

the new implementation would result in reliable communication between VMs.

Pipeline changes
----------------
flow will be added in ACL2, and the match condition of ACL3 flows will be modified as noted above in the proposed change:

==============  ==========================================================
==========================================================================
Table           Match                                              Action
==============  ===========================================================
============================================================================
Dispatcher       metadata=service_id:ACL                           write_metadata:(elan_id=ELAN, service_id=NEXT), goto_table:ACL1
ACL1 (211/241)   goto_table:ACL2
ACL2 (212/242)   ct_label=0x1, ip,reg6=0x200/0xfffff00             (set_field:0->ct_label), goto_table:ACL3
...
ACL2 (212/242)                                                      goto_table:ACL3
ACL3 (213/243)   ct_state=-new+est-rel-inv+trk,ct_label=0x1         resubmit(,DISPATCHER)
ACL3 (213/243)   ct_state=+trk, ct_label=0,ip,reg6=0x200/0xfffff00  set_field:01>ct_label), resubmit(,DISPATCHER)
...
==============  =============================================================  =============================================================================

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
None

Targeted Release
-----------------
Carbon

Alternatives
------------
While deleting a SG flow from the flow table, we will add a DROP flow with higher priority in the ACL3 table.
This DROP flow will drop the packets and it will stops the existing communication. Similarly, When we restore the
same rule again, we will delete the DROP flow from the ACL3 table which will enable the existing communication.

But this approach will be effective only if the VM don't have any duplicate flows. With the current ACL
implementation, if we associate two SGs which having similar set of SG rule, netvirt will install the two set of
flows with different priority. Mean, There is two set of flows will be available in flow table for the same VM.

As per above approach, If we dissociate any one of SG from the VM, It will add the DROP flow in ACL3 table which
will stops the existing communication irrespective of there is still another flow available in ACL3, to make the
communication possible.

Usage
=====
Communication between the VMs should work only if the respective flows are available in the pipeline.

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
Refer to the Neutron CLI Reference [#]_ for the Neutron CLI command syntax for managing Security
Rules.

Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a primary assignee and other
contributors.

Primary assignee:

-  VinothB <vinothb@hcl.com>
-  Aswin Suryanarayanan <asuryana@redhat.com>

Other contributors:

-  ?


Work Items
----------
None

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
We should add tests verifying ACL change reflection on existing communication.
There should be at least:

* One security rule allowing ICMP traffic between VMs in the same SG.
* One positive test, checking ICMP connectivity works between two VMs using the same SG. Delete all the rules 
from the SG without disturbing the already established communication. It should stops the communication.
* One negative test, checking ICMP connectivity does not work between two VMs, one using the SG
configured with the ICMP and TCP rules above, and delete the TCP rule. This should not affect the ICMP
communication.

Documentation Impact
====================
None.

References
==========

.. [#] Neutron Security Groups http://docs.openstack.org/user-guide/cli-nova-configure-access-security-for-instances.html
