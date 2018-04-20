..
 Key points to consider:
  * Use RST format. For help with syntax refer http://sphinx-doc.org/rest.html
  * Use http://rst.ninjs.org/ a web based WYSIWYG RST editor.
  * For diagrams, you can use http://asciiflow.com to make ascii diagrams.
  * MUST READ http://docs.opendaylight.org/en/latest/documentation.html and follow guidelines.
  * Use same topic branch name for all patches related to this feature.
  * All sections should be retained, but can be marked None or N.A.
  * Set depth in ToC as per your doc requirements. Should be at least 2.

.. contents:: Table of Contents
   :depth: 3

=====================
IGMPv3 Snooping
=====================

[gerrit filter: https://git.opendaylight.org/gerrit/#/q/topic:igmp-snooping]

This feature will implement IGMPv3 Snooping, as defined in RFC 4541.

Highlights of RFC 4541 that will be addressed by this feature:

- A snooping switch should forward IGMP Membership Reports only to those ports where multicast routers are attached. 
- The switch supporting IGMP snooping must maintain a list of multicast routers and the ports on which they are attached.
- Packets with a destination IP address outside 224.0.0.X which are not IGMP should be forwarded according to group-based port membership tables and must also be forwarded on router ports.
- Packets with a destination IP (DIP) address in the 224.0.0.X range which are not IGMP must be forwarded on all ports.

This feature will not IPv6 MLD Snooping.

A global flag will be supported to enable/disable IGMP Snooping. Initially, this value will be read with ODL starts. 
Support for dynamic changes of this flag may not be initially supported.


Problem description
===================

IGMP and IPv4 Multicast Packet Forwarding

As of Oxygen release: 

IPv4 multicast
- Packets are flooded when port-security is disabled.
- Packets are not forwarded when port-security is enabled, dropped by ACL rules.

IGMP
- Packets are flooded when port-security is disabled.
- Packets are not forwarded when port-security is enabled, dropped by ACL rules.

When port-security is enabled, ODL will snoop IGMP packets, build multicast database
of IPv4 multicast listeners and routers, and forward packets as described in RFC 4541.

When port-security is disabled, ODL will still snoop IGMP packets and build the 
multicast database, but will not install any associated rules, and IPv4 multicast
and IGMP packets will be flooded to all ports in the network, as is done today
when port security is disabled.

Use Cases
---------

UC1
Multicast listener and sender on same compute node.

UC2
Mulicast listener and sender on different compute nodes. This will
ensure IGMP Snooping works across internal tunnels.

UC3 (Need to confirm if this is required)
Multicast listener and sender on different compute nodes. One
of the compute nodes is connected to L2GW. This will ensure
IGMP Snooping works across external tunnels.



Proposed change
===============

Create a new IGMP Table(61). This table will punt the IGMP packet to the ODL Controller, and then send the packet to new MC BC Group table for forwarding. 
There is an entry in this table that sends IPv4 multicast packets to the MC BC Group for forwarding.

IGMP Packets will be punted to the Controller,and processed as follows:

- If the Packet is IGMP Query
	- add port to MC BC Group
	- update ACL for port and add allowed address pair 224.0.0.2, MAC 01:00:5e:00:00:16.
	- send packet to MC BC Group (pipeline processing) to be forwarded to all mc listeners and mc routers

- If the Packet is IGMP Join/Report
	- add port to MC BC Group
	- update ACL for port and add allowed address pair based on address in Join/Report
	- send packet to MC BC Group (pipeline processing) to be forwarded to all mc listeners and mc routers 


Create a new Broadcast group per network for IGMP and IPv4 multicast packets. This broadcast group will be very similar to existing L2 BC groups. There would be a Local BC group per network (local ports only - packet ingress on tunnel port) and a Full BC group per network (local ports and tunnel ports - packet ingress on vm port).

This BC group would be populated based on IGMP messages, as described above in IGMP Processing summary above, meaning this BC Group contains multicast router ports and multicast listener ports.

Security Groups
===============

ODL Security groups do not currently support IGMP. As such, some small code changes are required to support IGMP. For example, in 
ODL Oxygen, if you issue the command:

openstack security group rule create goPacketGo --ingress --ethertype IPv4 --dst-port 1:65535 --protocol igmp

an error is thrown from ODL neutron, saying protocol igmp is not supported. There is also a small change required
in ACL to add support for igmp in security groups.

Adding support for IGMP protocol to security groups is required so that ACL tables will allow IGMP packets to egress the switch.



Pipeline changes
----------------

Add rules to ARP Table (43) to send IGMP packets to new IGMP Table(61). Currently, ARP Table (43) sends packets to L2 Pipeline (48) if not ARP. We do
not want IGMP to be processed in L2 Pipeline (and flooded to all ports in the network). 

In table 43:

- arp check -> group 5000 (existing)
- igmp check ->  table 61 (new)
- special IPv4 MC check (224.0.0.0/24) -> table 48 (new). 
- IPv4 MC check -> table 61 (new)
- goto table 48 (existing)

Add rules to Internal Table (36) to do the same as above:

In table 36:

- igmp check -> table 61 (new)
- special IPv4 MC check (224.0.0.0/24)-> table 51  (new)
- IPv4 MC check -> table 61 (new)
- goto table 51 (existing)

Add rule to External Table (38) to do the same as above:

In table 38:

- igmp check -> table 61 (new)
- special IPv4 MC check (224.0.0.0/24)-> table 51  (new)
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
	- Create, manage and populate MC BC Group from IGMP
	- Add rules to tables 43, 36, 38 for IGMP/IPv4 MC pkts
	- Test using IPerf
	- Add tests to CSIT 


Dependencies
============
None

Testing
=======
Setup Openstack/ODL deployment and test use cases 1-3 as follows:

Start a multicast listener - sends IGMP Report/Join pkts

iperf -s -u -B 226.94.1.1 -i 1


Start a multicast source. Sends stream of UDP 1Mbps to 226.94.1.1

iperf -c 226.94.1.1 -u -t 3600

Verify multicast listener receives packets from sender for all use cases.

Unit Tests
----------

Integration Tests
-----------------

CSIT
----

Add IGMP/IPv4 Multicast test cases to CSIT to cover use cases 1-3.

Documentation Impact
====================
Vpickard to work with Doc team to add configuration/overview/operation
of IGMP Snooping.

References
==========


[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
