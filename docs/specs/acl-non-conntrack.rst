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
 (c) Optimization of reduced flows (using Remote ACL table) even for ports having multiple ACLs.

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
	   
 (b) New yang container (acl-ports-lookup) used to handle overlapping IP addresses while processing
     remote ACLs.

 (c) ACL pipeline is re-designed to reduce number of flows in ACL tables.


**ACL0 (239):**

- This table is untouched.

**ACL1 (211/241):**

- Anti-spoofing filter table. 

**ACL2 (212/242):**

- Classifies conntrack supported traffic and sends the traffic to conntrack table (ACL3).
- For traffic not supported by conntrack, traffic is sent directly to ACL filter table (ACL5).
- Metadata is written to classify conntrack or non-conntrack traffic.

**ACL3 (213/243):**

- Sends traffic to conntrack. 

**ACL4 (214/244):**

- ACL conntrack state table which matches only on ct_state and takes action accordingly.
- If session is already established (ct_state=+est+trk | +rel+trk), packet would get returned to
  dispatcher table from here itself. It doesn't go thru next subsequent ACL tables.
- Packets are dropped for INVALID packets (ct_state=+inv+trk).

**ACL5 (215/245):**

- ACL filter table which is common to both conntrack supported and non-conntrack supported traffic.
- Flows are classified as below:

  (i)  Flows for ACL rules which are configured with remote_ip_prefix.
       This is straight forward case where packets matching these flows would be directly sent to
	   table ACL7.

	   .. code-block:: bash

		e.g: cookie=0x6900000,table=245,priority=62010,tcp,reg6=0x600/0xfffff00 actions=goto_table:246

  (ii) Flows for ACL rules which are configured with remote_group_id.

- To handle rules having remote SG, ACL5 flows are grouped based on remote SG. Flows for rules 
  having common remote ACL are grouped together and matched based on remote SG ID.
  Example-4 gives the reason for having looping/iteration based approach in ACL5 table.

Flows related to remote SG are explained with examples below:

1) Port having single SG (sg1) and common remote SG.

 .. code-block:: bash

	sg1 -> ALLOW IPv4 tcp from sg1
	sg1 -> ALLOW IPv4 icmp from sg1

	cookie=0x6900000,table=245,priority=62030,reg6=0x600/0xfffff00,metadata=0x100/0xfffffd actions=drop
	cookie=0x6900000,table=245,priority=62010,tcp,reg6=0x600/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246  
	cookie=0x6900000,table=245,priority=62010,icmp,reg6=0x600/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246  

2) Port having single SG which has two rules with different remote SG.

 .. code-block:: bash

	sg1 -> ALLOW IPv4 tcp from sg1
	sg1 -> ALLOW IPv4 icmp from sg2

	cookie=0x6900000,table=245,priority=62030,reg6=0x600/0xfffff00,metadata=0x200/0xfffffd actions=drop
	cookie=0x6900000,table=245,priority=62020,icmp,reg6=0x600/0xfffff00,metadata=0x100/0xfffffd actions=write_metadata:0x200/0xfffffd,goto_table:246
	cookie=0x6900000,table=245,priority=62010,tcp,reg6=0x600/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246 
  
3) Port having two SG's and different remote SG's.

 .. code-block:: bash

	sg1 -> ALLOW IPv4 tcp from sg1
	sg2 -> ALLOW IPv4 icmp from sg2

	cookie=0x6900000,table=245,priority=62030,reg6=0x600/0xfffff00,metadata=0x200/0xfffffd actions=drop
	cookie=0x6900000,table=245,priority=62020,icmp,reg6=0x600/0xfffff00,metadata=0x100/0xfffffd actions=write_metadata:0x200/0xfffffd,goto_table:246
	cookie=0x6900000,table=245,priority=62010,tcp,reg6=0x600/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246 
  
4) Packets matching multiple rules having different remote SGs.
   This is a case where packets matching both rules but it might match src/dst IP in the second iteration with remote SG (sg2).
   This usecase is the reason for having looping/iteration based approach in ACL5 table.

 .. code-block:: bash

	sg1 -> ALLOW IPv4 from sg1
	sg1 -> ALLOW IPv4 icmp from sg2

	cookie=0x6900000,table=245,priority=62030,reg6=0x600/0xfffff00,metadata=0x200/0xfffffd actions=drop
	cookie=0x6900000,table=245,priority=62020,icmp,reg6=0x600/0xfffff00,metadata=0x100/0xfffffd actions=write_metadata:0x200/0xfffffd,goto_table:246
	cookie=0x6900000,table=245,priority=62010,ip,reg6=0x600/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246 

**ACL6 (216/246):**

- Remote ACL filter table.
- Even if multiple ports have same IP within an ACL, a single flow is created in this table.
- During delete IP scenarios (port delete/update), look-up to yang container (acl-ports-lookup) is
  done. Flow is deleted only when IP address is not used by any other ports within that ACL.

**ACL7 (217/247):**

- Packets reaching this table would have passed all the ACL filters. Traffic could be of both
  conntrack and non-conntrack supported.
- In case of conntrack traffic, commits the session in conntrack and resubmits to dispatcher.
- In case of non-conntrack traffic, resubmits to dispatcher.


Pipeline changes
----------------

Re-designed ACL pipeline as below:

==============  =================================================  ===============================================================
Table           Match                                              Action
==============  =================================================  ===============================================================
Dispatcher      metadata=service_id:ACL                            write_metadata:(service_id=NEXT), goto_table:ACL0|ACL1

ACL0 (239)      CT_STATE=TRK                                       ct(table=ACL1)
ACL0 (239)      (TABLE-MISS)                                       goto_table:ACL1

ACL1 (211/241)  (anti-spoofing filters)                            goto_table:ACL2
ACL1 (211/241)  (TABLE-MISS)                                       drop

ACL2 (212/242)  UDP                                                write_metadata:CONST_0, goto_table:ACL3           :superscript:`(X)`           
ACL2 (212/242)  TCP                                                write_metadata:CONST_0, goto_table:ACL3           :superscript:`(X)`
ACL2 (212/242)  ICMP                                               write_metadata:CONST_0, goto_table:ACL3           :superscript:`(X)`
ACL2 (212/242)  ICMPv6                                             write_metadata:CONST_0, goto_table:ACL3           :superscript:`(X)`
ACL2 (212/242)  (TABLE-MISS)                                       write_metadata:CONST_1, goto_table:ACL5           :superscript:`(XX)`

ACL3 (213/243)  metadata=lport1                                    ct(table=ACL4,zone=ELAN_ID)
ACL3 (213/243)  metadata=lport2                                    ct(table=ACL4,zone=ELAN_ID)
...
ACL3 (213/243)  (TABLE-MISS)                                       drop

ACL4 (214/244)  reg6=lport, ct_state=+est+trk | +rel+trk           resubmit(,DISPATCHER) 
ACL4 (214/244)  reg6=lport, ct_state=+new+trk                      goto_table:ACL5
ACL4 (214/244)  reg6=lport, ct_state=+inv+trk                      drop 
...
ACL4 (214/244)  (TABLE-MISS)                                       drop

ACL5 (215/245)  reg6=lport, priority=30, <acl_rule>                goto_table:ACL7                                   :superscript:`(XXX)`
ACL5 (215/245)  reg6=lport, priority=10, <acl_rule>                write_metadata:(remote_acl1), goto_table:ACL6     :superscript:`(XXXX)`
ACL5 (215/245)  reg6=lport, priority=10, <acl_rule>                write_metadata:(remote_acl1), goto_table:ACL6     :superscript:`(XXXXX)`
ACL5 (215/245)  reg6=lport,pri=20,metadata=remote_acl1,<acl_rule1> write_metadata:(remote_acl2), goto_table:ACL6     :superscript:`(XXXXX)`
ACL5 (215/245)  reg6=lport,pri=20,metadata=remote_acl1,<acl_rule2> write_metadata:(remote_acl2), goto_table:ACL6     :superscript:`(XXXXX)`
ACL5 (215/245)  reg6=lport,pri=30,metadata=remote_acl2             drop                                              :superscript:`(XXXXX)`
ACL5 (215/245)  reg6=lport                                         drop
...
ACL5 (215/245)  (TABLE-MISS)                                       drop

ACL6 (216/246)  metadata=remote_acl1, ip_src/dst=VM1_IP            goto_table:ACL7
ACL6 (216/246)  metadata=remote_acl1, ip_src/dst=VM2_IP            goto_table:ACL7
ACL6 (216/246)  metadata=remote_acl2, ip_src/dst=VM3_IP            goto_table:ACL7
ACL6 (216/246)  metadata=remote_acl2, ip_src/dst=VM4_IP            goto_table:ACL7
...
ACL6 (216/246)  (TABLE-MISS)                                       resubmit(,ACL5)

ACL7 (217/247)  reg6=lport, metadata=CONST_0                       ct(commit,zone=ELAN_ID), resubmit(,DISPATCHER)    :superscript:`(X)` 
ACL7 (217/247)  reg6=lport, metadata=CONST_1                       resubmit(,DISPATCHER)                             :superscript:`(XX)`
...
ACL7 (217/247)  (TABLE-MISS)                                       drop

==============  =================================================  ===============================================================

|  CONST_0  Constant referring to conntrack supported traffic. eg: 0x0/0x2
|  CONST_1  Constant referring to non-conntrack supported traffic. eg: 0x2/0x2

| (X)     These are conntrack supported traffic.
| (XX)    These are non-conntrack supported traffic.
| (XXX)   These are the regular rules, not configured with any remote SG.
| (XXXX)  These are the rules having remote SG (normal case without overlapping scenarios).
| (XXXXX) These are rules having different remote SG. 

**Note:**
Note the sample priorities in table ACL5. 
For XXX, priority=30 and for XXXX, priority=10
In case of XXXXX, priority=10 for the first remote SG (which doesn't match on remote ACL ID) and
for subsequent remote SG's, flows have priority=20.


**Sample flows:**

.. code-block:: bash

	cookie=0x6900000,table=241,priority=61010,reg6=0x600/0xfffff00,ip,dl_dst=fa:16:3e:40:04:bb,nw_dst=10.10.10.11 actions=goto_table:242

	cookie=0x6900000,table=242,priority=61010,tcp6 actions=write_metadata:0x0/0x2,goto_table:243
	cookie=0x6900000,table=242,priority=61010,udp6 actions=write_metadata:0x0/0x2,goto_table:243
	cookie=0x6900000,table=242,priority=61010,tcp actions=write_metadata:0x0/0x2,goto_table:243
	cookie=0x6900000,table=242,priority=61010,udp actions=write_metadata:0x0/0x2,goto_table:243
	cookie=0x6900000,table=242,priority=61010,icmp6 actions=write_metadata:0x0/0x2,goto_table:243
	cookie=0x6900000,table=242,priority=61010,icmp actions=write_metadata:0x0/0x2,goto_table:243
	cookie=0x6900000,table=242,priority=0 actions=write_metadata:0x2/0x2,goto_table:245

	cookie=0x6900000,table=243,priority=61010,ip,reg6=0x600/0xfffff00 actions=ct(table=244,zone=5002)
	cookie=0x6900000,table=243,priority=0 actions=drop

	cookie=0x6900000,table=244,priority=62020,ct_state=-new+est-rel-inv+trk actions=resubmit(,220)
	cookie=0x6900000,table=244,priority=62020,ct_state=-new-est+rel-inv+trk actions=resubmit(,220)
	cookie=0x6900000,table=244,priority=62015,reg6=0x600/0xfffff00,ct_state=+inv+trk actions=drop
	cookie=0x6900000,table=244,priority=61010,reg6=0x600/0xfffff00,ct_state=+new+trk actions=goto_table:245
	cookie=0x6900000,table=244,priority=0 actions=drop

	cookie=0x6900000,table=245,priority=62030,tcp,reg6=0x600/0xfffff00 actions=goto_table:246
	cookie=0x6900000,table=245,priority=62030,reg6=0x600/0xfffff00,metadata=0x200/0xfffffd actions=drop
	cookie=0x6900000,table=245,priority=62020,icmp,reg6=0x600/0xfffff00,metadata=0x100/0xfffffd actions=write_metadata:0x200/0xfffffd,goto_table:246
	cookie=0x6900000,table=245,priority=62010,icmp,reg6=0x600/0xfffff00 actions=write_metadata:0x100/0xfffffd,goto_table:246
	cookie=0x6900000,table=245,priority=0 actions=drop

	cookie=0x6900000,table=246,priority=61010,ip,metadata=0x100/0xfffffd,nw_src=10.10.10.6 actions=goto_table:247
	cookie=0x6900000,table=246,priority=61010,ip,metadata=0x100/0xfffffd,nw_src=10.10.10.11 actions=goto_table:247
	cookie=0x6900000,table=246,priority=61010,ip,metadata=0x200/0xfffffd,nw_src=10.10.10.5 actions=goto_table:247
	cookie=0x6900000,table=246,priority=0 actions=resubmit(,245)

	cookie=0x6900000,table=247,priority=61010,ip,reg6=0x600/0xfffff00,metadata=0x0/0x2 actions=ct(commit,zone=5002),resubmit(,220)
	cookie=0x6900000,table=247,priority=61010,ipv6,reg6=0x600/0xfffff00,metadata=0x0/0x2 actions=ct(commit,zone=5002),resubmit(,220)
	cookie=0x6900000,table=247,priority=61010,reg6=0x600/0xfffff00,metadata=0x2/0x2 actions=resubmit(,220)
	cookie=0x6900000,table=247,priority=0 actions=drop

Yang changes
------------

Below yang container is used to support overlapping IP addresses while processing remote ACLs.

.. code-block:: bash

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
No configuration parameters being added/deprecated for this feature

Clustering considerations
-------------------------
New feature planned should work in cluster environment seamlessly as it's with the current ACL
features.

Other Infra considerations
--------------------------
N.A.

Security considerations
-----------------------
N.A.

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
 - The support has to be provided in conntrack module.
 - List of protocols to be supported in conntrack might need continuous updates or it has to be
   handled in generic way.

Usage
=====
Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
No new REST API is being added for this feature.

CLI
---
No CLI being added for this feature

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


Dependencies
============
This doesn't add any new dependencies.

Also, there is no dependency on other features.

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


References
==========

