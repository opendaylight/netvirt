.. contents:: Table of Contents
   :depth: 3

==============================================================
ACL: Support for protocols that are not supported by conntrack
==============================================================

https://git.opendaylight.org/gerrit/#/q/topic:acl-non-conntrack

This spec addresses following issues in ACL module:
 (a) Enhance ACL to support protocols like OSPF, VRRP etc that are not supported by conntrack
     in stateful mode.
 (b) Handle overlapping IP addresses while processing remote ACLs.
 (c) Optimization with reduced flows even for ports having multiple ACLs using Remote ACL table.

Problem description
===================

 (a) Enhance ACL to support protocols like OSPF, VRRP etc that are not supported by conntrack
     in stateful mode.

     **Problem:**
     With current stateful ACL implementation, data packets of IP protocols like OSPF, VRRP, etc
     are classified as invalid and dropped in ACL tables. This is because conntrack used with
     stateful mode, does not recognize these IP protocols.

     In the current ACL implementation, all IP traffic (except DHCP and ARP) are passed through
     the conntrack framework for stateful tracking. Conntrack supports (and hence recognizes) only
     the following protocols:

     - ICMP
     - TCP
     - UDP
     - ICMPv6

     Stateful tracking is done only for the above protocols and all other traffic is classified as
     INVALID by conntrack and hence being dropped by the ACL tables in the OF pipeline.

 (b) Handle overlapping IP addresses while processing remote ACLs.

     **Problem:**
     In the current implementation if two or more ports with different ACL's have same IP address
     (configured via allowed-address-pair), ACL remote table (212/242) will have only one flow per
     ELAN for that IP address pointing to a remote ACL. Flow for other ACL will be missing. Hence,
     packet may not hit the right flow and end up dropping the packets.

 (c) Optimization of reduced flows (using Remote ACL table) even for ports having multiple ACLs.

     **Problem:**
     In the current implementation, optimization of reduced flows (Remote ACL table) is supported
     only for ports having single ACL but the same optimization is not available for ports having
     multiple ACLs.

Use Cases
---------
 (a) SDN infrastructure must be able to recognize any IP protocol and depending on the ACL rule
     configured must ALLOW or DROP the corresponding traffic.

 (b) Multiple ports can have same IP/MAC in many scenarios. Few instances include VRRP or OSPF
     cases where multicast IP/MAC (eg: 224.0.0.5/01:00:5e:00:00:05) is configured via
     allowed-address-pair.

 (c) Improves scalability and performance.

Proposed change
===============

 (a) Selectively allow only certain IP protocols to transit through the conntrack framework.
     The rest of the traffic should bypass the conntrack framework and be governed only by the
     configured ACL filter rules (except DHCP and ARP).
     So, the behavior would be as follows:

     * DHCP and ARP are not governed by ACL rules and are always allowed.
     * A selective set of IP protocols which carry point-to-point datapath information are allowed
       to pass through conntrack for stateful connection tracking.
     * The rest of the protocols are NOT passed through conntrack and are directly checked against
       the OF translated ACL filter rules.

 (b) New yang container (acl-ports-lookup) is introduced to handle overlapping IP addresses while
     processing remote ACLs. This would remove dependency on ID manager which was used to generate
     flow priorities.  With the yang approach, having a unique priority for each flow in ACL filter
     table is not required anymore.

 (c) ACL pipeline is re-designed to reduce number of flows in ACL tables.


**ACL0 (239):**

- This table is untouched.

**ACL1 (210/240):**

- Anti-spoofing filter table.
- An additional match on lport tag is added for anti-spoofing flows to support overlapping IP
  addresses where multiple VMs are configured with same IP/MAC addresses via allowed-address-pair.

  ::

  cookie=0x6900000,table=240,priority=61010,**reg6=0x200/0xfffff00**,ip,dl_dst=fa:16:3e:5c:42:d5,nw_dst=10.10.10.12 actions=goto_table:242


**ACL2 (211/241):**

- Classifies conntrack supported traffic and sends the traffic to conntrack table (ACL3).
- Traffic not supported by conntrack is directly sent to ACL filter table (ACL5).
- Metadata is written to classify conntrack (CONST_0) or non-conntrack (CONST_1) traffic.

**ACL3 (212/242):**

- Sends traffic to conntrack.

**ACL4 (213/243):**

- This table was introduced by spec #acl_reflection_on_existing_traffic which supports the ACL
  changes reflecting on existing traffic.

**ACL5 (214/244):**

- ACL conntrack state table which matches only on ct_state and takes action accordingly.
- If session is already established (ct_state=+est+trk | +rel+trk), packet would get returned to
  dispatcher table from here itself. It doesn't go through next subsequent ACL tables.
- INVALID packets (ct_state=+inv+trk) are dropped.
- Other tracked packets (ct_state=+trk) are sent to ACL6 table.

**ACL6 (215/245):**

- ACL filter cum dispatcher table which is common to both conntrack supported and non-conntrack
  supported traffic.
- Flows are classified into three categories as explained below:

  (i)   Flows for ACL rules which are configured with **remote_ip_prefix**.
        This is straight forward case where packets matching these flows would be directly sent to
        table ACL9.

        ::

        e.g:
        sg1 -> ALLOW IPv4 22/tcp from 0.0.0.0/0

        cookie=0x6900000,table=245,priority=62040,tcp,reg6=0x600/0xfffff00,tp_dst=22 actions=goto_table:248

  (ii)  Ports having single or multiple SGs but all the rules with a common (single) remote SG.
        For this case, flows match on ACL rules and send packets to Remote ACL filter table (ACL8).

        ::

        sg1 -> ALLOW IPv4 icmp from sg1
        sg1 -> ALLOW IPv4 22/tcp from sg1
        sg2 -> ALLOW IPv4 100/udp from sg1

        cookie=0x6900000,table=245,priority=62030,reg6=0x200/0xfffff00,metadata=0x100/0xfffffd actions=drop
        cookie=0x6900000,table=245,priority=62010,icmp,reg6=0x200/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:247
        cookie=0x6900000,table=245,priority=62010,tcp,reg6=0x200/0xfffff00,tp_dst=22 actions=write_metadata:0x100/0xfffffd,goto_table:247
        cookie=0x6900000,table=245,priority=62010,udp,reg6=0x200/0xfffff00,tp_dst=100 actions=write_metadata:0x100/0xfffffd,goto_table:247

  (iii) Ports having single or multiple SGs with collective ACL rules having different remote SGs.
        In this case, flows **doesn't match** on ACL rules in this table but instead will match
        ACL rules in ACL7 table. To support this usecase, dispatcher kind of mechanism is performed
        to loop/iterate through all the rules having remote ACLs.

        Example-1: Port having single SG (sg1).

        ::

        sg1 -> ALLOW IPv4 from sg1
        sg1 -> ALLOW IPv4 icmp from sg2

        cookie=0x6900000,table=245,priority=62030,reg6=0x200/0xfffff00,metadata=0x200/0xfffffd actions=drop
        cookie=0x6900000,table=245,priority=62020,reg6=0x200/0xfffff00,metadata=0x100/0xfffffd actions=write_metadata:0x200/0xfffffd,goto_table:246
        cookie=0x6900000,table=245,priority=62010,reg6=0x200/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246
        cookie=0x6900000,table=245,priority=0 actions=drop

        Example-2: Port having multiple SGs (sg1, sg2 and sg3).

        ::

        sg1 -> ALLOW IPv4 from sg1
        sg1 -> ALLOW IPv4 icmp from sg2
        sg2 -> ALLOW IPv4 22/tcp from sg2
        sg3 -> ALLOW IPv4 100/udp from sg3

        cookie=0x6900000,table=245,priority=62030,reg6=0x200/0xfffff00,metadata=0x300/0xfffffd actions=drop
        cookie=0x6900000,table=245,priority=62020,reg6=0x200/0xfffff00,metadata=0x200/0xfffffd actions=write_metadata:0x300/0xfffffd,goto_table:246
        cookie=0x6900000,table=245,priority=62020,reg6=0x200/0xfffff00,metadata=0x100/0xfffffd actions=write_metadata:0x200/0xfffffd,goto_table:246
        cookie=0x6900000,table=245,priority=62010,reg6=0x200/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246
        cookie=0x6900000,table=245,priority=0 actions=drop

- To handle rules having remote SG, flows in this table are grouped based on remote SG. Flows for
  rules having common remote ACL are grouped together and matched based on remote SG ID.
  Example-4 gives the reason for having **looping/iteration based approach** in this table.


**ACL7 (216/246):**

- ACL filter table for ports having single or multiple SGs collectively having rules with different
  remote SGs.
- Table miss would resubmit back to ACL6 table to iterate for the next remote SG.

Below are some of the cases where ports associated to single or multiple SGs collectively having
rules with different remote SGs.

(i) Port having single SG (sg1).

 ::

    sg1 -> ALLOW IPv4 from sg1
    sg1 -> ALLOW IPv4 icmp from sg2

    cookie=0x6900000,table=246,priority=61010,ip,reg6=0x200/0xfffff00,metadata=0x100/0xfffffd actions=goto_table:247
    cookie=0x6900000,table=246,priority=61010,icmp,reg6=0x200/0xfffff00,metadata=0x200/0xfffffd actions=goto_table:247
    cookie=0x6900000,table=246,priority=0 actions=resubmit(,245)

(ii) Port having multiple SGs (sg1, sg2 and sg3).

 ::

    sg1 -> ALLOW IPv4 from sg1
    sg1 -> ALLOW IPv4 icmp from sg2
    sg2 -> ALLOW IPv4 22/tcp from sg2
    sg3 -> ALLOW IPv4 100/udp from sg3

    cookie=0x6900000,table=246,priority=61010,ip,reg6=0x200/0xfffff00,metadata=0x100/0xfffffd actions=goto_table:247
    cookie=0x6900000,table=246,priority=61010,icmp,reg6=0x200/0xfffff00,metadata=0x200/0xfffffd actions=goto_table:247
    cookie=0x6900000,table=246,priority=61010,tcp,reg6=0x200/0xfffff00,metadata=0x200/0xfffffd,tp_dst=22 actions=goto_table:247
    cookie=0x6900000,table=246,priority=61010,udp,reg6=0x200/0xfffff00,metadata=0x300/0xfffffd,tp_dst=100 actions=goto_table:247
    cookie=0x6900000,table=246,priority=0 actions=resubmit(,245)

**ACL8 (217/247):**

- Remote ACL filter table.
- Flows match on remote ACL and corresponding IP addresses.
- This table is independent of ports i.e., no match on lport tag.
- During IP delete scenarios (port delete/update), look-up to yang container (acl-ports-lookup) is
  performed, flows are deleted only when IP address is not used by any other ports within that ACL.

**ACL9 (218/248):**

- Packets reaching this table would have passed all the ACL filters. Traffic could be of both
  conntrack and non-conntrack supported.
- In case of conntrack traffic, commits the session in conntrack and resubmits to dispatcher.
- In case of non-conntrack traffic, resubmits to dispatcher.


Pipeline changes
----------------

**Current ACL pipeline:**

==============  =========================================================  ===============================================================
Table           Match                                                      Action
==============  =========================================================  ===============================================================
Dispatcher      metadata=service_id:ACL                                    write_metadata:(elan_id=ELAN|VPN_ID, service_id=NEXT), goto_table:ACL0|ACL1
.
ACL0 (239)      ct_state=+trk                                              ct(table=ACL1)
ACL0 (239)      (TABLE-MISS)                                               goto_table:ACL1
.
ACL1 (210/240)  (anti-spoofing filters)                                    goto_table:ACL2
ACL1 (210/240)  (TABLE-MISS)                                               drop
.
ACL2 (211/241)  metadata=ELAN|VPN_ID, ip_src/dst=VM1_IP                    write_metadata:(remote_acl=id), goto_table:ACL3
ACL2 (211/241)  metadata=ELAN|VPN_ID, ip_src/dst=VM2_IP                    write_metadata:(remote_acl=id), goto_table:ACL3
...
ACL2 (211/241)  (TABLE-MISS)                                               goto_table:ACL3
.
ACL3 (212/242)  reg6=lport, ip|ipv6, ct_mark=0x1                           (set_field:0x0->ct_mark), goto_table:ACL4
ACL3 (212/242)  (TABLE-MISS)                                               goto_table:ACL4
.
ACL4 (213/243)  ct_state=-new+est-rel-inv+trk, ct_mark=0x1                 resubmit(,DISPATCHER)
ACL4 (213/243)  ct_state=-new-est+rel-inv+trk, ct_mark=0x1                 resubmit(,DISPATCHER)
ACL4 (213/243)  reg6=lport, ct_state=+inv+trk                              drop
ACL4 (213/243)  reg6=lport, ct_state=+trk, <acl_rule>                      set_field:0x1>ct_mark, resubmit(,DISPATCHER)    :superscript:`(X)`
ACL4 (213/243)  reg6=lport+remote_acl, ct_state=+trk, <acl_rule>           set_field:0x1>ct_mark, resubmit(,DISPATCHER)    :superscript:`(XX)`
ACL4 (213/243)  reg6=lport, ct_state=+trk, ip_src/dst=VM1_IP, <acl_rule>   set_field:0x1>ct_mark, resubmit(,DISPATCHER)    :superscript:`(XXX)`
ACL4 (213/243)  reg6=lport, ct_state=+trk, ip_src/dst=VM2_IP, <acl_rule>   set_field:0x1>ct_mark, resubmit(,DISPATCHER)    :superscript:`(XXX)`
ACL4 (213/243)  reg6=lport, ct_state=+trk                                  drop
...
ACL4 (213/243)  (TABLE-MISS)                                               drop
==============  =========================================================  ===============================================================

| (X)   These are the regular rules, not configured with any remote SG.
| (XX)  These are the rules with the optimization - assuming the lport is using a single ACL.
| (XXX) These are the remote SG rules in the current implementation, which we will fall back to if the lport has multiple ACLs.


**Proposed ACL pipeline:**

==============  ==================================================  ===============================================================
Table           Match                                               Action
==============  ==================================================  ===============================================================
Dispatcher      metadata=service_id:ACL                             write_metadata:(service_id=NEXT), goto_table:ACL0|ACL1
.
ACL0 (239)      ct_state=+trk                                       ct(table=ACL1)
ACL0 (239)      (TABLE-MISS)                                        goto_table:ACL1
.
ACL1 (210/240)  (anti-spoofing filters)                             goto_table:ACL2
ACL1 (210/240)  (TABLE-MISS)                                        drop
.
ACL2 (211/241)  UDP                                                 write_metadata:CONST_0, goto_table:ACL3           :superscript:`(X)`
ACL2 (211/241)  TCP                                                 write_metadata:CONST_0, goto_table:ACL3           :superscript:`(X)`
ACL2 (211/241)  ICMP                                                write_metadata:CONST_0, goto_table:ACL3           :superscript:`(X)`
ACL2 (211/241)  ICMPv6                                              write_metadata:CONST_0, goto_table:ACL3           :superscript:`(X)`
ACL2 (211/241)  (TABLE-MISS)                                        write_metadata:CONST_1, goto_table:ACL6           :superscript:`(XX)`
.
ACL3 (212/242)  metadata=lport1, ip|ipv6                            ct(table=ACL4,zone=ELAN_ID)
ACL3 (212/242)  metadata=lport2, ip|ipv6                            ct(table=ACL4,zone=ELAN_ID)
...
ACL3 (212/242)  (TABLE-MISS)                                        drop
.
ACL4 (213/243)  reg6=lport, ip|ipv6, ct_mark=0x1                    (set_field:0x0->ct_mark), goto_table:ACL5
ACL4 (213/243)  (TABLE-MISS)                                        goto_table:ACL5
.
ACL5 (214/244)  ct_state=-new+est-rel-inv+trk, ct_mark=0x1          resubmit(,DISPATCHER)
ACL5 (214/244)  ct_state=-new-est+rel-inv+trk, ct_mark=0x1          resubmit(,DISPATCHER)
ACL5 (214/244)  reg6=lport, ct_state=+inv+trk                       drop
ACL5 (214/244)  reg6=lport, ct_state=+trk                           goto_table:ACL6
...
ACL5 (214/244)  (TABLE-MISS)                                        drop
.
ACL6 (215/245)  reg6=lport1, pri=40, <acl_rule>                     goto_table:ACL9                                   :superscript:`(XXX)`
ACL6 (215/245)  reg6=lport1, pri=30, metadata=remote_acl1           drop                                              :superscript:`(XXXX)`
ACL6 (215/245)  reg6=lport1, pri=10, <acl_rule1>                    write_metadata:(remote_acl1), goto_table:ACL8     :superscript:`(XXXX)`
ACL6 (215/245)  reg6=lport1, pri=10, <acl_rule2>                    write_metadata:(remote_acl1), goto_table:ACL8     :superscript:`(XXXX)`
ACL6 (215/245)  reg6=lport2, pri=30, metadata=remote_acl3           drop                                              :superscript:`(XXXXX)`
ACL6 (215/245)  reg6=lport2, pri=20, metadata=remote_acl2           write_metadata:(remote_acl3), goto_table:ACL7     :superscript:`(XXXXX)`
ACL6 (215/245)  reg6=lport2, pri=20, metadata=remote_acl1           write_metadata:(remote_acl2), goto_table:ACL7     :superscript:`(XXXXX)`
ACL6 (215/245)  reg6=lport2, pri=10                                 write_metadata:(remote_acl1), goto_table:ACL7     :superscript:`(XXXXX)`
ACL6 (215/245)  reg6=lport1, pri=5                                  drop
ACL6 (215/245)  reg6=lport2, pri=5                                  drop
...
ACL6 (215/245)  (TABLE-MISS)                                        drop
.
ACL7 (216/246)  reg6=lport2, pri=20, metadata=remote_acl1, <rule1>  goto_table:ACL8
ACL7 (216/246)  reg6=lport2, pri=20, metadata=remote_acl1, <rule2>  goto_table:ACL8
ACL7 (216/246)  reg6=lport2, pri=20, metadata=remote_acl2, <rule1>  goto_table:ACL8
ACL7 (216/246)  reg6=lport2, pri=20, metadata=remote_acl3, <rule1>  goto_table:ACL8
...
ACL7 (216/246)  (TABLE-MISS)                                        resubmit(,ACL6)
.
ACL8 (217/247)  metadata=remote_acl1, ip_src/dst=VM1_IP             goto_table:ACL9
ACL8 (217/247)  metadata=remote_acl1, ip_src/dst=VM2_IP             goto_table:ACL9
ACL8 (217/247)  metadata=remote_acl2, ip_src/dst=VM3_IP             goto_table:ACL9
ACL8 (217/247)  metadata=remote_acl3, ip_src/dst=VM4_IP             goto_table:ACL9
...
ACL8 (217/247)  (TABLE-MISS)                                        resubmit(,ACL6)
.
ACL9 (218/248)  reg6=lport, metadata=CONST_0                        ct(commit,zone=ELAN_ID,exec(set_field:0x1->ct_mark)), resubmit(,DISPATCHER)  :superscript:`(X)`
ACL9 (218/248)  reg6=lport, metadata=CONST_1                        resubmit(,DISPATCHER)                                                        :superscript:`(XX)`
...
ACL9 (218/248)  (TABLE-MISS)                                        drop

==============  ==================================================  ===============================================================

|  CONST_0  Constant referring to conntrack supported traffic. eg: 0x0/0x2
|  CONST_1  Constant referring to non-conntrack supported traffic. eg: 0x2/0x2

| (X)     These are conntrack supported traffic.
| (XX)    These are non-conntrack supported traffic.
| (XXX)   These are the rules not configured with any remote SG.
| (XXXX)  These are the cases where all the rules have common (single) remote SG.
| (XXXXX) These are rules having different remote SG.

**Note: ** Observe the priorities in ACL6 table for different cases.


**Sample flows:**

::

    cookie=0x6900000,table=239,priority=62020,ct_state=+trk,ip actions=ct(table=240)
    cookie=0x6900000,table=239,priority=62020,ct_state=+trk,ipv6 actions=ct(table=240)
    cookie=0x6900000,table=239,priority=61010 actions=goto_table:240

    cookie=0x6900000,table=240,priority=63010,arp,reg6=0x200/0xfffff00 actions=resubmit(,220)
    cookie=0x6900000,table=240,priority=61010,reg6=0x200/0xfffff00,ip,dl_dst=fa:16:3e:5c:42:d5,nw_dst=10.10.10.12 actions=goto_table:242

    cookie=0x6900000,table=241,priority=61010,tcp6 actions=write_metadata:0x0/0x2,goto_table:242
    cookie=0x6900000,table=241,priority=61010,udp6 actions=write_metadata:0x0/0x2,goto_table:242
    cookie=0x6900000,table=241,priority=61010,tcp actions=write_metadata:0x0/0x2,goto_table:242
    cookie=0x6900000,table=241,priority=61010,udp actions=write_metadata:0x0/0x2,goto_table:242
    cookie=0x6900000,table=241,priority=61010,icmp6 actions=write_metadata:0x0/0x2,goto_table:242
    cookie=0x6900000,table=241,priority=61010,icmp actions=write_metadata:0x0/0x2,goto_table:242
    cookie=0x6900000,table=241,priority=0 actions=write_metadata:0x2/0x2,goto_table:245

    cookie=0x6900000,table=242,priority=61010,ip,reg6=0x200/0xfffff00 actions=ct(table=243,zone=5002)
    cookie=0x6900000,table=242,priority=0 actions=drop

    cookie=0x6900000,table=243,priority=0 actions=goto_table:244

    cookie=0x6900000,table=244,priority=62030,ct_state=-new+est-rel-inv+trk,ct_mark=0x1 actions=resubmit(,220)
    cookie=0x6900000,table=244,priority=62030,ct_state=-new-est+rel-inv+trk,ct_mark=0x1 actions=resubmit(,220)
    cookie=0x6900000,table=244,priority=62020,reg6=0x200/0xfffff00,ct_state=+inv+trk actions=drop
    cookie=0x6900000,table=244,priority=62010,reg6=0x200/0xfffff00,ct_state=+trk actions=goto_table:245
    cookie=0x6900000,table=244,priority=0 actions=drop

    cookie=0x6900000,table=245,priority=62040,tcp,reg6=0x200/0xfffff00 actions=goto_table:248
    cookie=0x6900000,table=245,priority=62030,reg6=0x200/0xfffff00,metadata=0x100/0xfffffd actions=drop
    cookie=0x6900000,table=245,priority=62010,icmp,reg6=0x200/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:247
    cookie=0x6900000,table=245,priority=62010,tcp,reg6=0x200/0xfffff00,tp_dst=22 actions=write_metadata:0x100/0xfffffd,goto_table:247
    cookie=0x6900000,table=245,priority=62010,udp,reg6=0x200/0xfffff00,tp_dst=100 actions=write_metadata:0x100/0xfffffd,goto_table:247
    cookie=0x6900000,table=245,priority=62030,reg6=0x300/0xfffff00,metadata=0x300/0xfffffd actions=drop
    cookie=0x6900000,table=245,priority=62020,reg6=0x300/0xfffff00,metadata=0x200/0xfffffd actions=write_metadata:0x300/0xfffffd,goto_table:246
    cookie=0x6900000,table=245,priority=62020,reg6=0x300/0xfffff00,metadata=0x100/0xfffffd actions=write_metadata:0x200/0xfffffd,goto_table:246
    cookie=0x6900000,table=245,priority=62010,reg6=0x300/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246
    cookie=0x6900000,table=245,priority=0 actions=drop

    cookie=0x6900000,table=246,priority=61010,tcp,reg6=0x300/0xfffff00,metadata=0x100/0xfffffd actions=goto_table:247
    cookie=0x6900000,table=246,priority=61010,udp,reg6=0x300/0xfffff00,metadata=0x100/0xfffffd actions=goto_table:247
    cookie=0x6900000,table=246,priority=61010,icmp,reg6=0x300/0xfffff00,metadata=0x200/0xfffffd actions=goto_table:247
    cookie=0x6900000,table=246,priority=0 actions=resubmit(,245)

    cookie=0x6900000,table=247,priority=61010,ip,metadata=0x100/0xfffffd,nw_src=10.10.10.6 actions=goto_table:248
    cookie=0x6900000,table=247,priority=61010,ip,metadata=0x100/0xfffffd,nw_src=10.10.10.12 actions=goto_table:248
    cookie=0x6900000,table=247,priority=61010,ip,metadata=0x200/0xfffffd,nw_src=10.10.10.4 actions=goto_table:248
    cookie=0x6900000,table=247,priority=61010,ip,metadata=0x300/0xfffffd,nw_src=10.10.10.8 actions=goto_table:248
    cookie=0x6900000,table=247,priority=0 actions=resubmit(,245)

    cookie=0x6900000,table=248,priority=61010,ip,reg6=0x200/0xfffff00,metadata=0x0/0x2 actions=ct(commit,zone=5002,exec(set_field:0x1->ct_mark)),resubmit(,220)
    cookie=0x6900000,table=248,priority=61010,ipv6,reg6=0x200/0xfffff00,metadata=0x0/0x2 actions=ct(commit,zone=5002,exec(set_field:0x1->ct_mark)),resubmit(,220)
    cookie=0x6900000,table=248,priority=61010,reg6=0x200/0xfffff00,metadata=0x2/0x2 actions=resubmit(,220)
    cookie=0x6900000,table=248,priority=0 actions=drop

Yang changes
------------

Below yang container is used to support overlapping IP addresses while processing remote ACLs.
This would remove dependency on ID manager which was used to generate flow priorities. With the
yang approach, having a unique priority for each flow in ACL filter table is not required anymore.

::

    container acl-ports-lookup {
        config false;
        description "Container used to manage list of ports per ACL based on
            port's IP address/prefix (including IP address/prefix specified in
            allowed-address-pair)";

        list acl-ports-by-ip {
            key "acl-name";
            description "Refers to an ACL which are associated with list of
                ports filtered based on IP address/prefix.";

            leaf acl-name {
                type string;
                description "ACL name.";
            }
            list acl-ip-prefixes {
                key "ip-prefix";
                description "IP Prefixes and Allowed-Address-Pairs owned by
                    ports where all such ports enforce the same ACL identified
                    by acl-name";

                leaf ip-prefix {
                    type ip-prefix-or-address;
                    description "IP address/prefix";
                }
                list port-ids {
                    key "port-id";
                    description "Contains a list of ports that are enforcing
                        the same ACL identified by acl-name.";
                    leaf port-id {
                        type string;
                        description "Port UUID string";
                    }
                }
            }
        }
    }


Configuration impact
---------------------
None

Clustering considerations
-------------------------
With the proposed changes, ACL should work in cluster environment seamlessly as it's with the
current ACL feature.

Other Infra considerations
--------------------------
None

Security considerations
-----------------------
None

Scale and Performance Impact
----------------------------
There will be improvements in scale and performance as there will be lesser number of flows in
ACL tables.

Targeted Release
-----------------
Oxygen

Alternatives
------------
Currently, conntrack supports or recognizes only those IP protocols which carry point-to-point
datapath information. Conntrack should support all the other IP protocols (VRRP, OSPF, etc) as well
so that they are NOT classified as INVALID.

This approach was not selected as
 - The support has to be provided in conntrack module. Or until it is supported in conntrack, the
   proposed change is required in ACL module.
 - List of protocols to be supported in conntrack might need continuous updates or it has to be
   handled in generic way.

Usage
=====
Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
No new REST API is being added.

CLI
---
No new CLI being added.

Implementation
==============
Assignee(s)
-----------
Primary assignee:
  Somashekar Byrappa <somashekar.b@altencalsoftlabs.com>

Other contributors:
  Shashidhar R <shashidharr@altencalsoftlabs.com>

Work Items
----------
* Support protocols like OSPF, VRRP etc in ACL that are not supported by conntrack in stateful mode.
* Handle overlapping IP addresses while processing remote ACLs by making use of new yang container
  (acl-ports-lookup).

Dependencies
============
No new dependencies.

Testing
=======
Unit Tests
----------
Following test cases will need to be added/expanded

#. Verify ACL functionality with VRRP, OSPF protcols
#. Verify ACL functionality with other IP protocols not supported by conntrack
#. Verify ACL with ports having overlapping IP addresses.
#. Verify ACL with ports having single SG.
#. Verify ACL with ports having multiple SGs.

Also, existing unit tests have to be updated to include new pipeline/flow changes.

Integration Tests
-----------------
Integration tests will be added, once IT framework is ready

CSIT
----
Following test cases will need to be added/expanded

#. Verify ACL functionality with VRRP, OSPF protcols
#. Verify ACL functionality with other IP protocols not supported by conntrack
#. Verify ACL with ports having overlapping IP addresses.
#. Verify ACL with ports having single SG.
#. Verify ACL with ports having multiple SGs.

Documentation Impact
====================
None

References
==========

