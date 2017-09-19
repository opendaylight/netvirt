.. contents:: Table of Contents
      :depth: 3

===========================================================
ACL - Reflecting the ACL changes on existing traffic
===========================================================
ACL patches:
https://git.opendaylight.org/gerrit/#/q/topic:acl-reflection-on-existing-traffic

This spec describes the new implementation for applying ACL changes on existing traffic.

In current ACL implementation, once a connection had been committed to the connection tracker, the connection would
continue to be allowed, even if the policy defined in the ACL table has changed. This spec will explain the new approach
that ensures ACL policy changes will affect existing connections as well. This approach will
improve the pipeline behaviour in terms of reliable traffic between the VMs.

Problem description
===================

When the traffic between two VMs starts, the first packet will match the actual SG flow, which commits the packets
in connection tracker. It changes the state of the packets to established. Further traffic will match
the global conntrack flow and go through the connection tracker straightly. This will continue until we terminate the
established traffic.

When a rule is removed from the VM, the ACL flow getting removed from the respective tables. But, the already
established traffic is still working, because the connection still exists as 'committed' in the conntrack tracker.

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
   - VM Creation/Deletion with SG
   - SG Rule addition and removal to/from existing SG associated to ports

Proposed change
===============

This spec proposes the fix that requires a new table (210/240) in the existing pipeline.

In this approach, we will use the "ct_mark" flag of connection tracker. The default value of ct_mark is zero.

 - ct_mark=0 matches the packet in new state
 - ct_mark=1 matches the packet in established state

For every new traffic, the ct_mark value will be zero. When the traffic begins, the first packet of every
new traffic will be matched by the respective SG flow which commits the packets into the connection tracker and
changes the ct_mark value to 1. So, every packets of established traffic will have the ct_mark value as 1.

In conntrack flow, we will have a ct_mark=1 match condition. After first packet committed
to the connection tracker, further packets of established traffic will be matched by the conntrack flow straightly.

In every SG flow, we will have below changes,
  "table=213/243, priority=3902, **ct_state=+trk** ,icmp,reg6=0x200/0xfffff00 actions=ct(commit,zone=6001,
  **exec(set_field:0x1->ct_mark)**),resubmit(,17/220)"

  - The SG flow will match the packets which are in tracked state. It will commit
    the packet into the connection tracker. It will change the ct_mark value to 1.

  - When a VM having duplicate flows, the removal of one flow should not affect the
    existing traffic.

    For example, consider a VM having ingress ICMP and Other protocol (ANY) rule. Ping the VM from the DHCP server. Removal of ingress ICMP rule
    from the VM should not affect the existing traffic. Because the Other protocol ANY flow will match
    the established packets of existing ICMP traffic and should make the communication possible.
    To make the communication possible in above specific scenarios, we should match the established
    packets in every SG flow. So, We will remove the "+new" check from the ct_state condition of every ACL flow to
    recommit the established packets again into the conntrack.

In conntrack flow,
  "table=213/243, priority=62020,ct_state=-new+est-rel-inv+trk, **ct_mark=0x1** actions=resubmit(,17/220)"
  "table=213/243, priority=62020,ct_state=-new-est+rel-inv+trk, **ct_mark=0x1** actions=resubmit(,17/220)"

  - The conntrack flow will match the packet which are in established state.

  - For every new traffic, the first packet will be matched by the SG flow, which will change the ct_mark value to 1.
    So, further packets will match the conntrack flow straightly.

In default drop flow of table 213/243,
  "table=213, n_packets=0, n_bytes=0, priority=50, **ct_state=+trk** ,metadata=0x20000000000/0xfffff0000000000 actions=drop"
  "table=243, n_packets=6, n_bytes=588, priority=50, **ct_state=+trk** ,reg6=0x300/0xfffff00 actions=drop"

  - For every VM, we are having a default drop flow to measure the drop statistics of particular VM. So, we will remove
    the "+new" state check from the ct_state to measure the drop counts accurately.

Deletion of SG flow will add the below flow with configured hard time out in the table 212/242.

   [1] "table=212/242, n_packets=73, n_bytes=7154, priority=40,icmp,reg6=0x200/0xfffff00,ct_mark=1
   actions=ct(commit, zone=5500, **exec(set_field:0x0->ct_mark)**),goto_table:ACL4"

   - It will match the ct_mark value with the one and change the ct_mark to zero.

The below tables describes the default hard time out of each protocol as configured in the conntrack.

============   ==================
Protocol        Time out (secs)
============   ==================
 ICMP            30
 TCP             18000
 UDP             180
============   ==================

Please refer the Pipeline Changes for table information.

For Egress, Dispatcher table (table 17) will forward the packets to the new table 210 where we will check the source match.
It will forward the packet to 211 to match the destination of the packets. After the destination of the packet verified,
The packets will forward to the table 212. New flow in the table, will match the ct_mark value and forward
the packets to the 213 table.

Similarly, for Ingress, the packets will be forwarded through,
  Dispatcher table (220) >> New table (240) >> 241 >>  242 >> 243.

In dispatcher flows, we will have the below changes which will change the table 211/241 from the goto_table action to
the new table 210/240.

   "table=17, priority=10,metadata=0x20000000000/0xffffff0000000000 actions=write_metadata:0x900002157f000000/0xfffffffffffffffe, **goto_table:210**"

   "table=220, priority=6,reg6=0x200 actions=load:0x90000200->NXM_NX_REG6[],write_metadata:0x157f000000/0xfffffffffe, **goto_table:240**"

Deletion of SG rule will add a new flow in the table 212/242 as mentioned above. The first packet after SG got deleted,
will match the above new flow and will change the ct_mark value to zero. So this packet will not match the conntrack
flow and will check the ACL4 table whether it having any other flows to match this packet. If the SG flow found, the packet
will be matched and change the ct_mark value 1.

If we restore the SG rule again, we will delete the added flow [1] from the 212/242 table, so the packets of
existing traffic will match the newly added SG flow in ACL4 table and proceed successfully.

Sample flows to be installed in each scenario,

 **SG rule addition**
    SG flow: [ADD]
       "table=213/243, n_packets=33, n_bytes=3234, priority=62021, **ct_state=+trk**, icmp,
       reg6=0x200/0xfffff00 actions=ct(commit,zone=6001, **exec(set_field:0x1->ct_mark)**),resubmit(,17/220)"

    Conntrack flow: [DEFAULT]
       "table=213/243, n_packets=105, n_bytes=10290, priority=62020,ct_state=-new+est-rel-inv+trk, **ct_mark=0x1**
       actions=resubmit(,17/220)"

 **SG Rule deletion**
    SG flow: [DELETE]
       "table=213/243, n_packets=33, n_bytes=3234, priority=62021, ct_state=+trk,icmp,reg6=0x200/0xfffff00
       actions=ct(commit,zone=6001,exec(set_field:0x1->ct_mark)),resubmit(,17/220)"

    New flow: [ADD]
       "table=212/242, n_packets=73, n_bytes=7154, priority=62021, **ct_mark=0x1**,icmp,reg6=0x200/0xfffff00
       actions=ct(commit, **exec(set_field:0x0->ct_mark)**),goto_table:213/243"

 **Rule Restore**
    SG flow: [ADD]
       "table=213/243, n_packets=33, n_bytes=3234, priority=62021, ct_state=+trk, icmp,reg6=0x200/0xfffff00
       actions=ct(commit,zone=6001,exec(set_field:0x1->ct_mark)),resubmit(,17/220)"

    New flow: [DELETE]
       "table=212/242, n_packets=73, n_bytes=7154, priority=62021,ct_mark=0x1,icmp,reg6=0x200/0xfffff00
       actions=ct(commit,exec(set_field:0x0->ct_mark)),goto_table:213/243"

The new tables (210/240) will matches the source and the destination of the packets respectively. So, a default flow will be added in
the table 210/240 with least priority to drop the packets.

"table=210/240, n_packets=1, n_bytes=98, priority=0 actions=drop"

 **Flow Sample:**
    Egress flows before the changes,

      cookie=0x6900000, duration=30.590s, table=17, n_packets=108, n_bytes=10624, priority=10,metadata=0x20000000000/0xffffff0000000000 actions=write_metadata:0x9000021389000000/0xfffffffffffffffe,goto_table:211
      cookie=0x6900000, duration=30.247s, table=211, n_packets=0, n_bytes=0, priority=61010,ipv6,dl_src=fa:16:3e:93:dc:92,ipv6_src=fe80::f816:3eff:fe93:dc92 actions=ct(table=212,zone=5001)
      cookie=0x6900000, duration=30.236s, table=211, n_packets=96, n_bytes=9312, priority=61010,ip,dl_src=fa:16:3e:93:dc:92,nw_src=10.100.5.3 actions=ct(table=212,zone=5001)
      cookie=0x6900000, duration=486.527s, table=211, n_packets=2, n_bytes=180, priority=0 actions=drop
      cookie=0x6900000, duration=30.157s, table=212, n_packets=0, n_bytes=0, priority=50,ipv6,metadata=0x1389000000/0xffff000000,ipv6_dst=fe80::f816:3eff:fe93:dc92 actions=write_metadata:0x2/0xfffffe,goto_table:212
      cookie=0x6900000, duration=30.152s, table=212, n_packets=0, n_bytes=0, priority=50,ip,metadata=0x1389000000/0xffff000000,nw_dst=10.100.5.3 actions=write_metadata:0x2/0xfffffe,goto_table:212
      cookie=0x6900000, duration=486.527s, table=212, n_packets=96, n_bytes=9312, priority=0 actions=goto_table:212
      cookie=0x6900000, duration=486.056s, table=213, n_packets=80, n_bytes=8128, priority=62020,ct_state=-new+est-rel-inv+trk actions=resubmit(,17)
      cookie=0x6900000, duration=485.948s, table=213, n_packets=0, n_bytes=0, priority=62020,ct_state=-new-est+rel-inv+trk actions=resubmit(,17)
      cookie=0x6900001, duration=30.184s, table=213, n_packets=0, n_bytes=0, priority=62015,ct_state=+inv+trk,metadata=0x20000000000/0xfffff0000000000 actions=drop
      cookie=0x6900000, duration=30.177s, table=213, n_packets=16, n_bytes=1184, priority=1000,ct_state=+new+trk,ip,metadata=0x20000000000/0xfffff0000000000 actions=ct(commit,zone=5001),resubmit(,17)
      cookie=0x6900000, duration=30.168s, table=213, n_packets=0, n_bytes=0, priority=1001,ct_state=+new+trk,ipv6,metadata=0x20000000000/0xfffff0000000000 actions=ct(commit,zone=5001),resubmit(,17)
      cookie=0x6900001, duration=30.207s, table=213, n_packets=0, n_bytes=0, priority=50,ct_state=+new+trk,metadata=0x20000000000/0xfffff0000000000 actions=dro

   After the changes, flows will be,

      cookie=0x6900000, duration=30.590s, table=17, n_packets=108, n_bytes=10624, priority=10,metadata=0x20000000000/0xffffff0000000000 actions=write_metadata:0x9000021389000000/0xfffffffffffffffe,goto_table:210
      cookie=0x6900000, duration=30.247s, table=210, n_packets=0, n_bytes=0, priority=61010,ipv6,dl_src=fa:16:3e:93:dc:92,ipv6_src=fe80::f816:3eff:fe93:dc92 actions=ct(table=211,zone=5001)
      cookie=0x6900000, duration=30.236s, table=210, n_packets=96, n_bytes=9312, priority=61010,ip,dl_src=fa:16:3e:93:dc:92,nw_src=10.100.5.3 actions=ct(table=211,zone=5001)
      cookie=0x6900000, duration=486.527s, table=210, n_packets=2, n_bytes=180, priority=0 actions=drop
      cookie=0x6900000, duration=30.157s, table=211, n_packets=0, n_bytes=0, priority=50,ipv6,metadata=0x1389000000/0xffff000000,ipv6_dst=fe80::f816:3eff:fe93:dc92 actions=write_metadata:0x2/0xfffffe,goto_table:212
      cookie=0x6900000, duration=30.152s, table=211, n_packets=0, n_bytes=0, priority=50,ip,metadata=0x1389000000/0xffff000000,nw_dst=10.100.5.3 actions=write_metadata:0x2/0xfffffe,goto_table:212
      cookie=0x6900000, duration=486.527s, table=211, n_packets=96, n_bytes=9312, priority=0 actions=goto_table:212
      cookie=0x6900000, duration=486.527s, table=212, n_packets=96, n_bytes=9312, priority=0 actions=goto_table:213
      cookie=0x6900000, duration=486.056s, table=213, n_packets=80, n_bytes=8128, priority=62020,ct_state=-new+est-rel-inv+trk,ct_mark=0x1 actions=resubmit(,17)
      cookie=0x6900000, duration=485.948s, table=213, n_packets=0, n_bytes=0, priority=62020,ct_state=-new-est+rel-inv+trk,ct_mark=0x1 actions=resubmit(,17)
      cookie=0x6900001, duration=30.184s, table=213, n_packets=0, n_bytes=0, priority=62015,ct_state=+inv+trk,metadata=0x20000000000/0xfffff0000000000 actions=drop
      cookie=0x6900000, duration=30.177s, table=213, n_packets=16, n_bytes=1184, priority=1000,ct_state=+trk,ip,metadata=0x20000000000/0xfffff0000000000 actions=ct(commit,zone=5001,exec(set_field:0x1->ct_mark)),resubmit(,17)
      cookie=0x6900000, duration=30.168s, table=213, n_packets=0, n_bytes=0, priority=1001,ct_state=+new+trk,ipv6,metadata=0x20000000000/0xfffff0000000000 actions=ct(commit,zone=5001),resubmit(,17)
      cookie=0x6900001, duration=30.207s, table=213, n_packets=0, n_bytes=0, priority=50,ct_state=+trk,metadata=0x20000000000/0xfffff0000000000 actions=drop

   New flow will be installed in table 212 when we delete SG rule,
      "cookie=0x6900000, duration=30.177s, table=212, n_packets=16, n_bytes=1184, priority=1000,ct_state=+trk,ip,metadata=0x20000000000/0xfffff0000000000,ct_mark=1,idle_timeout=1800 actions=ct(commit,zone=5001,exec(set_field:0x0->ct_mark)),goto_table:213"

   Similarly, the ingress related flows will have the same changes as mentioned above.


Pipeline changes
----------------

The propose changes includes:
   - New tables 210 and 240
   - Re-purposed tables 211, 212, 241, 242

The propose will re-purpose the table 211 and 212 of egress, table 241 and 242 of ingress.

Currently, for egress, we are using the table 211 for source match and 212 for destination match.
In new propose, we will use the new table 210 for source match, table 211 for destination match and table 212 for new
flow installation when we delete the SG flow.

For Egress, the traffic will use the tables in following order,
   17 >> 210 >> 211 >> 212 >> 213.

Similarly, for ingress, currently we are using the table 241 for destination match and 242 for source match.
In new propose, we will use the new table 240 for destination match, table 241 for source match and table 242 for new
flow installation when we delete the SG flow.

For Ingress, the traffic will use the tables in following order,
   220 >> 240 >> 241 >> 242 >> 243


flow will be added in table 212/242, and the match condition of ACL4 flows will be modified as noted above in the proposed change:

==============  =======================================================   ============================================================================
Table             Match                                                    Action
==============  =======================================================   ============================================================================
Dispatcher         metadata=service_id:ACL                                  write_metadata:(elan_id=ELAN, service_id=NEXT), goto_table:210/240 (ACL1)
ACL1 (210/240)                                                              goto_table:ACL2
...
ACL2 (211/241)                                                              goto_table:ACL3
ACL3 (212/242)     ip,ct_mark=0x1,reg6=0x200/0xfffff00                       (set_field:0x0->ct_mark), goto_table:ACL4
ACL3 (212/242)                                                              goto_table:ACL4
ACL4 (213/243)     ct_state=-new+est-rel-inv+trk,ct_mark=0x1                resubmit(,DISPATCHER)
ACL4 (213/243)     ct_state=+trk,priority=3902,ip,reg6=0x200/0xfffff00      set_field:0x1>ct_mark, resubmit(,DISPATCHER)
ACL4 (213/243)     ct_state=+trk, reg6=0x200/0xfffff00                      drop
...
==============  =======================================================   ============================================================================

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
When we delete the SG rule from the VM, A new flow will be added in the flow table 212 to flip
the value of ct_mark of ongoing traffics. This flow will have a time out based on the protocol as mentioned in the
proposed changes section. The packets of ongoing traffic will be recommitted and will do the set filed of ct_mark until
the flow reaches the time out.

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
* One positive test, checking ICMP connectivity works between two VMs,one using the SG,
  configured with the ICMP rule, Delete and restore the ICMP rule immediately. This should stop and resume the ICMP traffic after
  restoring the ICMP rule.
* One positive test, checking ICMP connectivity between VMs, using the SG,
  configured with ICMP ALL and Other protocol ANY rule. Delete the ICMP rule from the SG, It should not stop the ICMP traffic.
* One negative test, checking ICMP connectivity between two VMs, one using the SG,
  configured with the ICMP and TCP rules above, and delete the TCP rule. This should not affect the ICMP traffic.

Documentation Impact
====================
None.

References
==========

.. [#] Neutron Security Groups http://docs.openstack.org/user-guide/cli-nova-configure-access-security-for-instances.html
