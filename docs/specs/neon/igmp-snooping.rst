.. contents:: Table of Contents
   :depth: 3

===============
IGMPv3 Snooping
===============

[gerrit filter: https://git.opendaylight.org/gerrit/#/q/topic:igmp-snooping]

IGMP Snooping is typically used to prevent flooding of multicast packets in a domain. In switches
that do not support IGMP snooping, multicast packets would typically be flooded to all ports in the
domain (VLAN, for example).

This flooding of multicast packets results in wasted bandwidth, as packets are sent on ports where there are no
interested listeners of the particular multicast group address.

The main benefit of IGMP Snooping is to eliminate the flooding of packets by listening to IGMP messages between
hosts and multicast routers, and build a database that will be used to forward multicast packets selectively 
to interested hosts and multicast routers.

This feature will implement IGMPv3 Snooping, as defined in RFC 4541.

Highlights of RFC 4541 that will be addressed by this feature:

- A snooping switch should forward IGMP Membership Reports only to those ports where multicast routers are attached. 
- The switch supporting IGMP snooping must maintain a list of multicast routers and the ports on which they are attached.
- Packets with a destination IP address outside 224.0.0.X which are not IGMP should be forwarded according to group-based port membership tables and must also be forwarded on router ports.
- Packets with a destination IP (DIP) address in the 224.0.0.X range which are not IGMP must be forwarded on all ports.


This feature will not IPv6 MLD Snooping.

A global flag will be supported to enable/disable IGMP Snooping. Initially, this value will be read with ODL starts. 
Support for dynamic changes of this flag may not be initially supported.

IGMP Packet Processing and Forwarding
=====================================
We need to keep track of where the multicast listeners and multicast routers are located.

The switch learns about interfaces by monitoring IGMP traffic. If an interface receives IGMP queries, this will indicate that a 
multicast router is attached to that interface, and the port will be added to the multicast forwarding table as a multicast-router 
interface (querier).

If an interface receives membership reports or igmp join for a multicast group, this will indicate that a multicast listener (host)
is attached to that interface, and the port will be added to the multicast forwarding table as a group-member interface. 

If an interface receives igmp leave for a multicast group, remove the port from the multicast forwarding table as a group-member interface.

Learning group members and multicast routers
============================================

IGMP Membership Report

- Add port to multicast Group Member BC group (210021)
- Add group address match criteria to multicast table (61)

IGMP Leave packet received (or timer expires):

- Remove port from multicast Group Member BC group (210021)
- Remove group address match rule from multicast table (61)

IGMP general query or group-specific query (any query)

- Add port to multicast Router BC group (220022)

Multicast Forwarding Rules
==========================
IGMP packets will be sent to ODL Controller learning, and then forwarded via sendIgmpPacketOut() as follows:

- Membership Report (leave) forwarded to all multicast router ports
- IGMP general query forwarded to all ports in domain
- IGMP group-specific query forwarded to group member ports

Multicast traffic that is not IGMP:

An unregistered packet is defined as an IPv4 multicast packet with a destination address which does not match 
any of the groups announced in earlier IGMP Membership Reports. A registered packet is defined as an IPv4 multicast 
packet with a destination address which matches one of the groups announced in earlier IGMP Membership Reports. 


- destination address of 224.0.0.0/24 is flooded to all ports in domain
- Unregistered packet forwarded to all multicast router ports
- Registered packets forwarded to group member and multicast router ports


Problem description
===================

The current behavior of IPv4 Multicast Packet Forwarding as of Oxygen Release:

IPv4 multicast

- Packets are flooded to all ports in domain when port-security is disabled.
- Packets are not forwarded when port-security is enabled, dropped by ACL rules.

IGMP

- Packets are flooded to all ports in domain when port-security is disabled.
- Packets are not forwarded when port-security is enabled, dropped by ACL rules.

As you can see above, when port security is disabled, multicast packets are flooded. When port security
is enabled, multicast packets are dropped by ACL rules.

This IGMP Snooping feature, when enabled, will learn about multicast hosts and multicast routers from the IGMP 
conversation. These learned entries will be used to build a multicast forwarding database to forward IPv4
multicast packets as described in the Multicast Forwarding Rules section above.

Configuration to enable IGMP Snooping
=====================================
From a user perspective, the following will need to be configured:

1. IGMP Snooping will need to be globally enabled in the config file. Default value is false.
2. IGMP protocol will need to be configured in security groups. Reference Security Group section above.


Use Cases
=========

UC1
Multicast listener and sender on same compute node.

UC2
Mulicast listener and sender on different compute nodes. This will
ensure IGMP Snooping works across internal tunnels.

UC3
Multicast listener on compute node and sender on vlan provider network.

UC4
Multicast listener on compute node and sender on flat provider network.

UC5 (Need to confirm if this is required)
Multicast listener and sender on different compute nodes. One
of the compute nodes is connected to L2GW. This will ensure
IGMP Snooping works across external tunnels.

UC6 
Multicast router on physical network (querier)


Proposed change
===============

IGMP Snooping feature will send IGMP Packets to the ODL Controller. The IGMP messages will be parsed, and a multicast database will be built, consisting of multicast goup member ports and multicast router ports. This database will be used to populate Multicast Broadcast Groups, that will 
then be used to forward IPv4 multicast packets per the Multicast Forwarding Rules section above.

New Multicast Broadcast Groups (BC)
===================================
There will be a total of 3 broadcast groups/network needed for IGMP Snooping.  These broadcast groups will be very similar to existing 
L2 BC groups. There would be a Local BC group per network (local ports only - packet ingress on tunnel port) and a Full BC group per 
network (local ports and tunnel ports - packet ingress on vm port).

- Multicast Router L/F - This group has the multicast router ports for the network.
- Multicast Group Member L/F - This group has the multicast group member ports for the network.
- All ports in domain L/F - This group already exists (Table 52, unknown DMACs).

Multicast Router Local (220021)
-------------------------------
sudo ovs-ofctl add-group br-int -OOpenflow13 "group_id=220021,type=all,bucket=actions=set_field:0x0b->tun_id,resubmit(,55)"

Multicast Router Full (220022)
------------------------------
sudo ovs-ofctl add-group br-int -OOpenflow13 “group_id=220022,type=all,bucket=actions=group:220021,bucket=actions=set_field:0x5dd->tun_id,load:0x600->NXM_NX_REG6[],resubmit(,220)

Multicast Group Local (210021)
------------------------------
sudo ovs-ofctl add-group br-int -OOpenflow13 "group_id=210021,type=all,bucket=actions=set_field:0x0a->tun_id,resubmit(,55)"

Multicast Group Full (210022)
-----------------------------
sudo ovs-ofctl add-group br-int -OOpenflow13 “group_id=210022,type=all,bucket=actions=group:210021,bucket=actions=set_field:0x5dc->tun_id,load:0x600->NXM_NX_REG6[],resubmit(,220)



Multicast Table (61)
====================
Create a new IPv4 Multicast Table (61). This table will have rules that:

1. punt IGMP packets to the ODL Controller (learn and forward in ODL)
2. Match 224.0.0.0/24 and send to L2 Unknown DMACs table for L2 flooding to all ports in the domain (Table 48)
3. Match  IPv4 multicast group address learned from IGMP Report/Join and send to Multicast Group BC Group and Multicast Router Group for forwarding
4. Send unmatched packets to Multicast Router Group for forwarding (unregistered multicast packet)

- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=61,priority=100,dl_type=0x0800,nw_proto=0x02 actions=CONTROLLER:65535"
- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=61,priority=100,dl_type=0x0800,nw_dst=224.0.0.0/24,actions=resubmit(,48)"
- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=61,priority=100,dl_type=0x0800,dl_type=0x0800,nw_dst=226.94.1.1,actions=goto_table:62
- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=61,actions=write_actions(group:220021)"


Multicast Group Table (62)
==========================

Need a way to send a packet to 2 BC groups. Thinking of using this table, and having something like this (better way to do this?):

- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=62,actions=write_actions(group:210021)"
- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=62,actions=write_actions(group:220021)"

Sample Flows
============

Flows to get Multicast packets to Multicast Table(61) from ARP Table (43)
-------------------------------------------------------------------------

- sudo ovs-ofctl add-flow -OOpenflow13 br-int table=43,priority=100,dl_type=0x0800,nw_proto=0x02,actions=goto_table:61
- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=43,priority=90,dl_type=0x0800,dl_dst=01:00:5e:00:00:00/ff:ff:ff:00:00:00,actions=goto_table:61"

NOTE: The 2 rules above would also have to be added to Internal Tunnel Table (36) and External Tunnel Table (38). 

Flows to get Multicast packets to Multicast Table(61) from Internal Tunnel Table (36)
-------------------------------------------------------------------------------------

- sudo ovs-ofctl add-flow -OOpenflow13 br-int table=36,priority=100,dl_type=0x0800,tun_id=0x5dc,nw_proto=0x02,actions=goto_table:61
- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=43,priority=90,dl_type=0x0800,dl_dst=01:00:5e:00:00:00/ff:ff:ff:00:00:00,tun_id=0x5dc,actions=goto_table:61"

Flow to get Multicast packets to Multicast Table(61) from External Tunnel Table (38)
------------------------------------------------------------------------------------

- sudo ovs-ofctl add-flow -OOpenflow13 br-int table=38,priority=100,dl_type=0x0800,tun_id=0x5dc,nw_proto=0x02,actions=goto_table:61
- sudo ovs-ofctl add-flow -OOpenflow13 br-int "table=36,priority=90,dl_type=0x0800,dl_dst=01:00:5e:00:00:00/ff:ff:ff:00:00:00,tun_id=0x5dc,actions=goto_table:61"


Security Groups
===============

Configure ACL to allow IGMP packet to MC Router port
----------------------------------------------------
In ODL, when an IGMP Query is received, update port config for which Query packet was received, and add allowed address pairs to multicast router port. Command line example here:

openstack port set --allowed-address ip-address=224.0.0.22,mac-address=01:00:5e:00:00:16 208b35fd-4c61-4d63-93f5-ab08e25a3560


Update port for IPv4 multicast address group, learned from IGMP Report/Join
---------------------------------------------------------------------------
When ODL receives IGMP Join/Membership Report, update the config for the port to allow the port to receive the IPv4 multicast packets as specified in the IGMP packet.

openstack port set --allowed-address ip-address=226.94.1.1,mac-address=01:00:5e:5e:01:01 74ab3b8e-1b95-4fef-a60d-295856b714b6

Configure security groups to allow IGMP packets
-----------------------------------------------
Adding support for IGMP protocol to security groups is required so that ACL tables will allow IGMP packets to egress the switch.

Here is an example of adding a rule to security group to allow igmp. This command adds rules to ACL tables to allow IGMP to egress.

- openstack security group rule create goPacketGo --ingress --ethertype IPv4 --protocol igmp
- openstack security group rule create goPacketGo --egress --ethertype IPv4  --protocol igmp

This adds a rule to table 240 that allows IGMP pkts to proceed through pipeline, going to table 241. Sample flow:

cookie=0x6900000, duration=82.942s, table=240, n_packets=8, n_bytes=432, priority=61010,ip,reg6=0xa00/0xfffff00,dl_dst=01:00:5e:00:00:16,nw_dst=224.0.0.22 actions=goto_table:241

ODL Security groups do not currently support IGMP. As such, some small code changes are required to support IGMP. For example, in 
ODL Oxygen, if you issue the command:

- openstack security group rule create goPacketGo --ingress --ethertype IPv4 --protocol igmp

an error is thrown from ODL neutron, saying protocol igmp is not supported. There is also a small change required
in ACL to add support for igmp in security groups. I have the fix for this in my sandbox, and will be pushing this
patch as part of this feature.


Pipeline changes
================

Add rules to ARP Table (43) to send IPv4 multicast packets to new IPv4 Multicast Table(61). Currently, ARP Table (43) sends packets to L2 Pipeline (48) if not ARP. We do not want IPv4 multicast packets to be processed in L2 Pipeline (and flooded to all ports in the network). 

In table 43:

- arp check -> group 5000 (existing)
- igmp check ->  table 61 (new)
- IPv4 MC check -> table 61 (new)
- goto table 48 (existing)

Add rules to Internal Table (36) to do the same as above:

In table 36:

- igmp check -> table 61 (new)
- IPv4 MC check -> table 61 (new)
- goto table 51 (existing)

Add rule to External Table (38) to do the same as above:

In table 38:

- igmp check -> table 61 (new)
- IPv4 MC check -> table 61 (new)
- goto table 51 (existing)


Yang changes
------------
Add new yang to enable/disable igmp snooping.

module igmpsnooping-config {
    yang-version 1;
    namespace "urn:opendaylight:params:xml:ns:yang:igmpsnooping:config";
    prefix "igmpsnooping-config";

    description
        "Service definition for igmpsnooping module";

    revision "2018-04-20" {
        description
                "Initial revision";

    }

    container igmpsnooping-config {
        leaf controller-igmpsnooping-enabled {
            description "Enable igmp snooping on the controller";

            type boolean;

            default false;

        }

    }

}


Configuration impact
--------------------
Adding new option to enable/disable igmp snooping for the controller.

Clustering considerations
-------------------------
TBD

Other Infra considerations
--------------------------
N/A

Security considerations
-----------------------
N/A

Scale and Performance Impact
----------------------------
Would be good to do some scale testing with large number
of IGMP listeners/senders to determine if there is any
negative impact on performance. Be sure to test with scale
where there are lots of IGMP Report/Joins/Leaves to see
if there are performance issues with IGMP punting to
ODL Controller


Targeted Release
----------------
Flourine

Alternatives
------------
N/A

Usage
=====
User would have to enable IGMP Snooping in xml/rest before starting ODL.

User would have to configure Security Group for port and add IGMP protocol
to Security Group.

Then, user should be able to spin up VMs on compute nodes, have some
listeners, some senders, and the multicast listeners should be able
to receive IPv4 Multicast packets from the senders.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------


CLI
---

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  <Victor Pickard>, <vpickard>, <vpickard@redhat.com>


Work Items
----------
- Write blueprint.
- Update Pipeline for IGMP/IPv4 MC packet processing
- Add code to:
	- Listen for IGMP Packets
	- Create, manage and populate Multicast BC Groups learned from IGMP
	- Add rules to tables 43,36,38,61,62 for IGMP/IPv4 MC pkts
	- Test using IPerf
	- Add tests to CSIT


Dependencies
============
None

Testing
=======
Setup Openstack/ODL deployment and test use cases as follows:

Unit Tests
----------

TC 1
^^^^
Start a multicast listener - sends IGMP Report/Join pkts
iperf -s -u -B 226.94.1.1 -i 1

Start a multicast source. Sends stream of UDP 1Mbps to 226.94.1.1
iperf -c 226.94.1.1 -u -t 3600

Verify multicast listener receives packets from sender for all use cases.
Verify listener ports are added to Multicast Group BC Group.
Verify IPv4 multicast traffic is only sent to registered listeners (not flooded).

TC 2
^^^^
Start (or simulate) a multicast router on Flat Provider network. We
want to see IGMP Query messages arrive from provider network port.

Verify multicast router port is added to Multicast Router BC Group.

Send IPv4 multicast traffic on the network.
Verify that registered and unregistered packets forwarded to multicast router port.

TC 3
^^^^
Start multicast listeners and multicast routers on network.

Send IPv4 Traffic with DIP 224.0.0.X/24.
Verify traffic is flooded to all ports in domain.

TC 4
^^^^
Start multicast listeners and multicast routers on the network.
Send IGMP Membership Report.
Verify packet is forwarded to all multicast router ports on the domain.

TC 5
^^^^
Start multicast listeners and multicast routers on the network.
Send IGMP general query.
Verify packet is forwarded to all ports in domain.

TC 6
^^^^
Start multicast listeners and multicast routers on the network.
Send IGMP group-specific query.
Verify packet is forwarded to group member ports.


Integration Tests
-----------------

CSIT
----

Add IGMP/IPv4 Multicast test cases to CSIT to address Unit Test cases above.

Documentation Impact
====================
Vpickard to work with Doc team to add configuration/overview/operation
of IGMP Snooping.

References
==========

[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

[3] `IGMP Snooping Overview <https://www.juniper.net/documentation/en_US/junos/topics/concept/igmp-snooping-qfx-series-overview.html>`_

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
