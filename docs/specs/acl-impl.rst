.. contents:: Table of Contents
      :depth: 3

===========================================================
ACL - Reflecting the ACL changes on existing traffic
===========================================================
ACL patches:
https://git.opendaylight.org/gerrit/#/q/topic:acl_enhancement

This spec describes the new implementation for applying ACL changes on existing traffic.

In current ACL implementation, once a connection had been committed to the connection tracker, the connection would
continue to be allowed, even if the policy defined in the ACL table has changed. This spec will explain the new approach
that changes this implementation, so that existing connections will affected by policy changes. This approach will
improve the pipeline behaviour in terms of reliable traffic between the VMs.

Problem description
===================

When the traffic between two VMs starts, the first packet will match the actual SG flow, which commits the packets
in connection tracker. It changes the state of the packets to established. The further traffic will match the global
conntrack flow and it will go through the connection tracker straightly. This will continue until we terminate the
established traffic.

When a rule is removed from the VM, the respective flow getting removed from the flow table. But, the already
established traffic is still working, because it does not know the policy changes and the packets are matched by the
conntrack flow.

For example, consider the below scenario which explains the problem in detail,

- Create a VM and associate the rule which allows ICMP

- Ping the DHCP server from the VM

- Dissociate the ICMP rule and check the ongoing traffic

Removal of ICMP rule is not affecting the existing traffic and it still alive even though required ICMP flow is not
exists in the flow table.

The traffic between the VMs should be reliable and it should be succeeded accordance with SG flow. When a SG rule is
removed from the VM, the packets of ongoing traffic should be dropped.

Use Cases
---------

The new ACL implementation will affect the below use cases,
   - VM Creation/Deletion
   - SG addition and removal

Proposed change
===============

This spec proposes the fix that requires a new table (210/240) in the existing pipeline.

In this approach, we will use the "ct_label" and "ct_mark" flag of connection tracker. The default value of ct_label and ct_mark is zero.

 - ct_label=0 matches the packet in new state
 - ct_label=1 matches the packet in established state
 - We will use the ct_mark flag to identify the deletion of SG rule from the VM.

For every new traffic, the ct_label and ct_mark value will be zero. When the traffic begins, the first packet of every
new traffic will be matched by the respective SG flow. The SG flow will commit the packets into the connection tracker. It will change
the ct_label value to 1 and ct_mark value to priority of the matched SG flow (say XXX). So, every packets of established
traffic will have the ct_label value as 1 and the ct_mark as the priority of matched SG flow.

In conntrack flow, we will have a ct_label=1 match condition. After first packet committed to the connection tracker, further packets of
established traffic will be matched by the conntrack flow straightly.

=================       ===============================================
Packet state              Flag value
=================       ===============================================
 New                     ct_label=0,ct_mark=0
 Established             ct_label=1,ct_mark=priority_sg_flow (XXX)
 SG_rule_deleted         ct_label=0,ct_mark=priority_sg_flow (XXX)
 SG_rule_restore         ct_label=1,ct_mark=priority_new_sg_flow (YYY)
=================       ===============================================

In every SG flow, we will have below changes,
  "table=213/243, priority=3902, **ct_state=+trk** ,icmp,reg6=0x200/0xfffff00 actions=ct(commit,zone=6001,
  **exec(set_field:0x1->ct_label), exec(set_field:3902->ct_mark)**),resubmit(,17/220)"

  - The SG flow will match the packets which are in tracked state. It will commit
    the packet into the connection tracker. It will change the ct_label value to 1 and ct_mark value to priority.

  - Every SG flow should change the ct_mark value to the unique number for identifying the particular SG flow removal.
    So, we will use the priority value to be set as ct_mark value.

  - When a VM having duplicate flows, the removal of one flow should not affect the existing traffic. To achieve this,
    We are removing the new state check from ct_state condition to match the established packets.

In conntrack flow,
  "table=213/243, priority=62020,ct_state=-new+est-rel-inv+trk, **ct_label=1** actions=resubmit(,17/220)"
  "table=213/243, priority=62020,ct_state=-new-est+rel-inv+trk, **ct_label=1** actions=resubmit(,17/220)"

  - The conntrack flow will match the packet which are in established state.

  - For every new traffic, the first packet will be matched by the SG flow, which will change the ct_label value to 1.
    So, further packets will match the conntrack flow straightly.

In default drop flow of ACL3,
  "table=213, n_packets=0, n_bytes=0, priority=50, **ct_state=+trk** ,metadata=0x20000000000/0xfffff0000000000 actions=drop"
  "table=243, n_packets=6, n_bytes=588, priority=50, **ct_state=+trk** ,reg6=0x300/0xfffff00 actions=drop"

  - For every VM, we are having a default drop flow to measure the drop statistics of particular VM. So, we will removed
    the "+new" state check from the ct_state to measure the drop counts accurately.

Deletion of SG flow will add the below flow with configured hard time out in the new table 210/240.

   [3] "table=210/240, n_packets=73, n_bytes=7154, priority=40,icmp,reg6=0x200/0xfffff00,ct_mark=3902
   actions=ct(commit, zone=5500, **exec(set_field:0->ct_label)**),goto_table:ACL3"

   - It will match the ct_mark value with the priority of deleted SG flow.

The below tables describes the default hard time out of each protocol as configured in the conntrack.

============   ==================
Protocol        Time out (secs)
============   ==================
 ICMP            30
 TCP             431999 (5 days)
 UDP             180
============   ==================

For Egress, Dispatcher table (table 17) will forward the packets to table 211 where we are checking the destination match.
It will forward the packet to 212 to match the source of the packets. After the source of the packet verified,
The packets have resubmitted to new table 210. In the table 210/240, we will have a new flow, It will match
the ct_mark value and forward the packets to the 213 table.

Similarly for Ingress, Th packet will be forwarded through,
  Dispatcher table (220) >> 241 >> 242 >> New table (240) >> 243.

The packets of established traffic will have the ct_label value as 1 and ct_mark value as the priority of the SG flow
matched by first packet of every traffic.

Deletion of SG rule will add a new flow in the table 210/240 as mentioned above. The first packet after SG got deleted,
will match the above new flow and will change the ct_label value to zero. So this packet will not match the conntrack
flow and will check the ACL3 table whether it having any other flows to match this packet.

If the SG flow found, the packet will be matched and change the ct_mark value to the newly matched flow priority.
The change in ct_mark will make the further packets to not match the added new flow in table 210/240.

If we restore the SG rule again, we will delete the added flow [3] from the 210/240 table, so the packets of
existing traffic will match the newly added SG flow in ACL3 table, it will make the traffic to be successful.

Sample flows to be installed in each scenario,

 **SG rule addition **
    SG flow: [ADD]
       "table=213/243, n_packets=33, n_bytes=3234, priority=XXXX, **ct_state=+trk**, icmp,
       reg6=0x200/0xfffff00 actions=ct(commit,zone=6001, **exec(set_field:0x1->ct_label), exec(set_field:XXXX->ct_mark)**),resubmit(,17/220)"

    Conntrack flow: [DEFAULT]
       "table=213/243, n_packets=105, n_bytes=10290, priority=62020,ct_state=-new+est-rel-inv+trk, **ct_label=0x1** actions=resubmit(,17/220)"

 **SG Rule deletion **
    SG flow: [DELETE]
       "table=213/243, n_packets=33, n_bytes=3234, priority=XXXX, ct_state=+trk,icmp,reg6=0x200/0xfffff00
       actions=ct(commit,zone=6001,exec(set_field:0x1->ct_label),exec(set_field:XXXX->ct_mark)),resubmit(,17/220)"

    New flow: [ADD]
      "table=210/240, n_packets=73, n_bytes=7154, priority=62021, **ct_mark=XXXX**,icmp,reg6=0x200/0xfffff00
      actions=ct(commit, **exec(set_field:0->ct_label)**),goto_table:213/243"

 **Rule Restore **
    SG flow: [ADD]
       "table=213/243, n_packets=33, n_bytes=3234, priority=YYYY, ct_state=+trk, icmp,reg6=0x200/0xfffff00
       actions=ct(commit,zone=6001,exec(set_field:0x1->ct_label),exec(set_field:YYYY->ct_mark)),resubmit(,17/220)"

    New flow: [DELETE]
       "table=210/240, n_packets=73, n_bytes=7154, priority=62021,ct_mark=XXXX,icmp,reg6=0x200/0xfffff00
       actions=ct(commit,exec(set_field:0->ct_label)),goto_table:213/243"

Since we are introducing a new table, a default flow will be added in the table 210/240 with least priority to allow
all the packets to the next table.

"table=210/240, n_packets=1, n_bytes=98, priority=0 actions=goto_table:213/243"

Pipeline changes
----------------
flow will be added in new table 210/240, and the match condition of ACL3 flows will be modified as noted above in the proposed change:

==============  =======================================================   ========================================================================
Table             Match                                                    Action
==============  =======================================================   ========================================================================
Dispatcher         metadata=service_id:ACL                                  write_metadata:(elan_id=ELAN, service_id=NEXT), goto_table:211/241
ACL1 (211/241)                                                              goto_table:ACL2
...
ACL2 (212/242)                                                              resubmit:210/240
210/240            ip,ct_mark=3902,reg6=0x200/0xfffff00                     (set_field:0->ct_label), goto_table:213/243
210/240                                                                     goto_table:213/243
ACL3 (213/243)     ct_state=-new+est-rel-inv+trk,ct_label=0x1               resubmit(,DISPATCHER)
ACL3 (213/243)     ct_state=+trk,priority=3902,ip,reg6=0x200/0xfffff00      set_field:01>ct_label,set_field:3902>ct_mark, resubmit(,DISPATCHER)
ACL3 (213/243)     ct_state=+trk, reg6=0x200/0xfffff00                      drop
...
==============  =======================================================   ========================================================================

**Table Numbering:**

Currently the Ingress ACl uses the tables **211, 212, 213, 214** and the Egress ACLs uses the tables **241, 242, 243, 244**.
To align Ingress/Egress with symmetric numbering, I propose the following change for new flow addition:

   - Ingress ACLs: 210
   - Egress  ACLs: 240

Yang changes
------------
The nicira-action.yang and the openflowplugin-extension-nicira-action.yang needs to be updated
with ct_label and ct_mark action. The action structure shall be

::

  grouping ofj-nx-action-conntrack-grouping {
      container nx-action-conntrack {
          leaf flags {
              type uint16;
          }
          leaf zone-src {
              type uint32;
          }
          leaf conntrack-zone {
              type uint16;
          }
          leaf ct-label{
              type uint32[4];
          }
          leaf ct-mark{
              type uint32;
          }
          leaf recirc-table {
              type uint128;
          }
          leaf experimenter-id {
              type oft:experimenter-id;
          }
          list ct-actions{
              uses ofpact-actions;
          }
      }
   }

The nicira-match.yang and the openflowplugin-extension-nicira-match.yang needs to be updated
with ct_label and ct_mark match.

::

  grouping ofj-nxm-nx-match-ct-label-grouping{
         container ct-label-values {
            leaf ct-label {
               type uint32[4];
            }
             leaf mask {
               type uint32[4];
            }
        }
    }
  grouping ofj-nxm-nx-match-ct-mark-grouping{
         container ct-mark-values {
            leaf ct-mark {
               type uint32;
            }
             leaf mask {
               type uint32;
            }
        }
    }

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
While deleting a SG flow from the flow table, we will add a DROP flow with the highest priority in the ACL3 table.
This DROP flow will drop the packets and it will stop the existing traffic. Similarly, when we restore the
same rule again, we will delete the DROP flow from the ACL3 table which will enable the existing traffic.

But this approach will be effective only if the VM do not have any duplicate flows. With the current ACL
implementation, if we associate two SGs which having similar set of SG rule, netvirt will install the two set of
flows with different priority for the same VM.

As per above approach, if we dissociate any one of SG from the VM, It will add the DROP flow in ACL3 table which
will stops the existing traffic irrespective of there is still another flow available in ACL3, to make the
traffic possible.

Usage
=====
Traffic between VMs will work accordance with the SG flow existence in the flow table.

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
-  Balakrishnan Karuppasamy <balakrishnan.ka@hcl.com>

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
We should add tests verifying ACL change reflection on existing traffic.
There should be at least:

* One security rule allowing ICMP traffic between VMs in the same SG.
* One positive test, checking ICMP connectivity works between two VMs using the same SG. Delete all the rules from
  the SG without disturbing the already established traffic. It should stop the traffic.
* One negative test, checking ICMP connectivity between two VMs, one using the SG,
  configured with the ICMP and TCP rules above, and delete the TCP rule. This should not affect the ICMP traffic.

Documentation Impact
====================
None.

References
==========

.. [#] Neutron Security Groups http://docs.openstack.org/user-guide/cli-nova-configure-access-security-for-instances.html
