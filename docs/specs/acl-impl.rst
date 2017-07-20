.. contents:: Table of Contents
      :depth: 3

===========================================================
ACL - Reflecting the ACL changes on existing traffic
===========================================================
ACL patches:
https://git.opendaylight.org/gerrit/#/q/topic:acl_reflection_on_existing_traffic

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

When a rule is removed from the VM, the respective flow getting removed from the flow the respective table. But, the already
established traffic is still working, because it does not know the policy changes and the packets are matched by the
conntrack flow always.

For example, consider the below scenario which explains the problem in detail,

- Create a VM and associate the rule which allows ICMP

- Ping the DHCP server from the VM

- Remove the ICMP rule and check the ongoing traffic

When we remove the ICMP rule, the respective ICMP flow getting removed from the respective
table (For egress, table 213 and For Ingress, table 243). But, Since the conntrack flow having high priority than
the SG flow, the packets are matched by the conntrack flow and the live traffic is unaware of the flow removal.

The traffic between the VMs should be reliable and it should be succeeded accordance with SG flow. When a SG rule is
removed from the VM, the packets of ongoing traffic should be dropped.

Use Cases
---------

The new ACL implementation will affect the below use cases,
   - VM Creation/Deletion
   - SG Rule addition and removal from existing SG associated to ports

Proposed change
===============

This spec proposes the fix that requires a new table (210/240) in the existing pipeline.

In this approach, we will use the "ct_mark" flag of connection tracker. The default value of ct_mark is zero.

 - ct_mark=0 matches the packet in new state
 - ct_mark=1 matches the packet in established state

For every new traffic, the ct_mark value will be zero. When the traffic begins, the first packet of every
new traffic will be matched by the respective SG flow. The SG flow will commit the packets into the connection tracker. It will change
the ct_mark value to 1. So, every packets of established traffic will have the ct_mark value as 1.

In conntrack flow, we will have a ct_mark=1 match condition. After first packet committed
to the connection tracker, further packets of established traffic will be matched by the conntrack flow straightly.

In every SG flow, we will have below changes,
  "table=213/243, priority=3902, **ct_state=+trk** ,icmp,reg6=0x200/0xfffff00 actions=ct(commit,zone=6001,
  **exec(set_field:0x1->ct_mark)**),resubmit(,17/220)"

  - The SG flow will match the packets which are in tracked state. It will commit
    the packet into the connection tracker. It will change the ct_mark value to 1.

  - When a VM having duplicate flows, the removal of one flow should not affect the existing traffic. To achieve this,
    We are removing the new state check from ct_state condition to match the established packets.

In conntrack flow,
  "table=213/243, priority=62020,ct_state=-new+est-rel-inv+trk, **ct_mark=1** actions=resubmit(,17/220)"

  "table=213/243, priority=62020,ct_state=-new-est+rel-inv+trk, **ct_mark=1** actions=resubmit(,17/220)"

  - The conntrack flow will match the packet which are in established state.

  - For every new traffic, the first packet will be matched by the SG flow, which will change the ct_mark value to 1.
    So, further packets will match the conntrack flow straightly.

In default drop flow of ACL4,
  "table=213, n_packets=0, n_bytes=0, priority=50, **ct_state=+trk** ,metadata=0x20000000000/0xfffff0000000000 actions=drop"
  "table=243, n_packets=6, n_bytes=588, priority=50, **ct_state=+trk** ,reg6=0x300/0xfffff00 actions=drop"

  - For every VM, we are having a default drop flow to measure the drop statistics of particular VM. So, we will removed
    the "+new" state check from the ct_state to measure the drop counts accurately.

Deletion of SG flow will add the below flow with configured hard time out in the new table 212/242.

   [3] "table=212/242, n_packets=73, n_bytes=7154, priority=40,icmp,reg6=0x200/0xfffff00,ct_mark=1
   actions=ct(commit, zone=5500, **exec(set_field:0->ct_mark)**),goto_table:ACL4"

   - It will match the ct_mark value with the one and change the ct_mark to zero.

The below tables describes the default hard time out of each protocol as configured in the conntrack.

============   ==================
Protocol        Time out (secs)
============   ==================
 ICMP            30
 TCP             18000
 UDP             180
============   ==================

For Egress, Dispatcher table (table 17) will forward the packets to the new table 210 where we will check the destination match.
It will forward the packet to 211 to match the source of the packets. After the source of the packet verified,
The packets will forwarded to the table 212. In the table 212, we will have a new flow, It will match
the ct_mark value and forward the packets to the 213 table.

Similarly for Ingress, Th packet will be forwarded through,
  Dispatcher table (220) >> New table (240) >> 241 >>  242 >> 243.

Now, For egress, We are using the the table 211 for destination match and 212 for source match. To maintain the table order,
We will use the table 210 for destination match and 211 for source match. Table 212 will be used to install the new flows,
to flip the ct_mark flag value, when we delete the SG flow.

Deletion of SG rule will add a new flow in the table 212/242 as mentioned above. The first packet after SG got deleted,
will match the above new flow and will change the ct_mark value to zero. So this packet will not match the conntrack
flow and will check the ACL4 table whether it having any other flows to match this packet. If the SG flow found, the packet
will be matched and change the ct_mark value 1.

If we restore the SG rule again, we will delete the added flow [3] from the 212/242 table, so the packets of
existing traffic will match the newly added SG flow in ACL4 table, it will make the traffic to be successful.

Sample flows to be installed in each scenario,

 **SG rule addition **
    SG flow: [ADD]
       "table=213/243, n_packets=33, n_bytes=3234, priority=62021, **ct_state=+trk**, icmp,
       reg6=0x200/0xfffff00 actions=ct(commit,zone=6001, **exec(set_field:0x1->ct_mark)**),resubmit(,17/220)"

    Conntrack flow: [DEFAULT]
       "table=213/243, n_packets=105, n_bytes=10290, priority=62020,ct_state=-new+est-rel-inv+trk, **ct_mark=0x1** actions=resubmit(,17/220)"

 **SG Rule deletion **
    SG flow: [DELETE]
       "table=213/243, n_packets=33, n_bytes=3234, priority=62021, ct_state=+trk,icmp,reg6=0x200/0xfffff00
       actions=ct(commit,zone=6001,exec(set_field:0x1->ct_mark)),resubmit(,17/220)"

    New flow: [ADD]
      "table=212/242, n_packets=73, n_bytes=7154, priority=62021, **ct_mark=1**,icmp,reg6=0x200/0xfffff00
      actions=ct(commit, **exec(set_field:0->ct_mark)**),goto_table:213/243"

 **Rule Restore **
    SG flow: [ADD]
       "table=213/243, n_packets=33, n_bytes=3234, priority=62021, ct_state=+trk, icmp,reg6=0x200/0xfffff00
       actions=ct(commit,zone=6001,exec(set_field:0x1->ct_mark)),resubmit(,17/220)"

    New flow: [DELETE]
       "table=212/242, n_packets=73, n_bytes=7154, priority=62021,ct_mark=1,icmp,reg6=0x200/0xfffff00
       actions=ct(commit,exec(set_field:0->ct_mark)),goto_table:213/243"

Since we are introducing a new table, a default flow will be added in the table 210/240 with least priority to allow
all the packets to the next table.

"table=210/240, n_packets=1, n_bytes=98, priority=0 actions=goto_table:211/241"

Pipeline changes
----------------

Two new tables (210 and 240) will be introduced in the pipeline.

For Egress, The traffic will use the tables in following order,
   17 >> 210 >> 211 >> 212 >> 213.

For Ingress, The traffic will use the tables in following order,
   220 >> 240 >> 241 >> 242 >> 243


flow will be added in table 212/242, and the match condition of ACL4 flows will be modified as noted above in the proposed change:

==============  =======================================================   ============================================================================
Table             Match                                                    Action
==============  =======================================================   ============================================================================
Dispatcher         metadata=service_id:ACL                                  write_metadata:(elan_id=ELAN, service_id=NEXT), goto_table:210/240 (ACL1)
ACL1 (210/240)                                                              goto_table:ACL2
...
ACL2 (211/241)                                                              resubmit:ACL3
ACL3 (212/242)     ip,ct_mark=1,reg6=0x200/0xfffff00                       (set_field:0->ct_mark), goto_table:ACL4
ACL3 (212/242)                                                              goto_table:ACL4
ACL4 (213/243)     ct_state=-new+est-rel-inv+trk,ct_mark=0x1                resubmit(,DISPATCHER)
ACL4 (213/243)     ct_state=+trk,priority=3902,ip,reg6=0x200/0xfffff00      set_field:0x1>ct_mark, resubmit(,DISPATCHER)
ACL4 (213/243)     ct_state=+trk, reg6=0x200/0xfffff00                      drop
...
==============  =======================================================   ============================================================================

**Table Numbering:**

Currently the Ingress ACl uses the tables **211, 212, 213, 214** and the Egress ACLs uses the tables **241, 242, 243, 244**.
To align Ingress/Egress with symmetric numbering, I propose the following change for new flow addition:

   - Ingress ACLs: 210
   - Egress  ACLs: 240

Yang changes
------------
The nicira-action.yang and the openflowplugin-extension-nicira-action.yang needs to be updated
with ct_mark action. The action structure shall be

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
with the ct_mark match.

::

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
While deleting a SG flow from the flow table, we will add a DROP flow with the highest priority in the ACL4 table.
This DROP flow will drop the packets and it will stop the existing traffic. Similarly, when we restore the
same rule again, we will delete the DROP flow from the ACL4 table which will enable the existing traffic.

But this approach will be effective only if the VM do not have any duplicate flows. With the current ACL
implementation, if we associate two SGs which having similar set of SG rule, netvirt will install the two set of
flows with different priority for the same VM.

As per above approach, if we dissociate any one of SG from the VM, It will add the DROP flow in ACL4 table which
will stops the existing traffic irrespective of there is still another flow available in ACL4, to make the
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
