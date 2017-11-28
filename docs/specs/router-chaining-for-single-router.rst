.. contents:: Table of Contents
         :depth: 3

===================================================
Chaining Routers for Subnet support in OpenDaylight
===================================================

https://git.opendaylight.org/gerrit/#/q/topic:router-chaining-for-single-router

In this specification we will introduce chaining multiple routers for single
subnet support in NetVirt. Chaining routers support enables a single subnet
to attach to multiple routers. Traffic from the subnet will be routed to proper
router based on the destination addresses.

One router among them will be designated as default router.
The default router is usually the first router that associates with the subnet,
it handles routing between networks attaching to the router as usual.

The rest of the routers attaching to the subnet will be secondary routers.
Outgoing traffic from the subnet will be checked if the destination address
belong the subnets that attach to a secondary router and routed via the respective
router. Otherwise the traffic will be routed via the default router.

Problem description
===================

Currently neutron allows multiple routers for single subnet scenarios as
described above. However netvirt only supports single router per subnet.
When adding second router the subnet, the first router's ``vpn-id`` is replaced
with the second router's ``vpn-id``  in all the L3 pipeline flows installed
for the subnet's instances. The subnet's traffic now is routed
via second router only.

Flow details as below:

* First router attaches to the subnet, traffic from the subnet are tagged with
  the ``vpn-id`` of the router. Subsequently, it goes to the l3 pipeline flows
  that matches ``vpn-id`` and routed to the destination within the subnets that
  also attach the same router.

* Now if we attach the same subnet to the another router, netvirt replaces
  the ``vpn-id`` of the newly-attached router in all above-mentioned l3 pipeline flows.
  There are two problems with this:

  * From netvirt's perspective, the subnet is now only attached to the second
    router since netvirt doesn't support multiple routers for single subnet.
    This is inconsistent with neutron since the latter shows that the subnet
    has interfaces to both routers.

  * Traffic within the vpn instance for the first router to/from the subnet
    will be dropped since the all flow entries for the subnet match the ``vpn-id``
    of the second vpn instances.


Use Cases
---------

This feature faciliates chaining multiple routers for single subnet support for
the following scenarios:

1. Cross-router internal networks routing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Currently traffic from one subnet can reach other subnets in the same router
  domain only.
* With the support for chaining multiple routers for a single subnet, traffic
  from that subnet can reach other subnets in multiple router domains which have
  interface to the subnet.

Consider the scenario below:

    ::

              +------------+
              | External   |
              |  Network 1 |
              +------+-----+
                     |
              +------+-----+             +------------+
              |  Router 1  |             |  Router 2  |
              +---+----+---+             +---+----+---+
                  |    |                     |    |
           +------+    +------+      +-------+    +-----+
           |                  |      |                  |
     +-----+-------+       +--+------+---+       +------+------+
     | Subnet 1    |       |  Subnet 2   |       |  Subnet 3   |
     | 10.0.2.0/24 |       | 10.0.3.0/24 |       | 10.0.4.0/24 |
     +-------------+       +-------------+       +-------------+


  * Subnet 1 and Subnet 2 attach to Router 1
  * Subnet 2 and Subnet 3 attach to Router 2
  * Subnet 2 attaches to both Router 1 and Router 2
  * Traffic between Subnet 2 and Subnet 1 is routed via Router 1
  * Traffic between Subnet 2 and Subnet 3 is routed via Router 2
  * Subnet 1 and Subnet 2 can access each other and External Network 1 (and vice versa) via Router 1
  * Subnet 3 can only access subnet 2 (and vice versa), it does not have access to external network
    and Subnet 1

2. Cross-router external networks routing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Traffic from one subnet can reach multiple external networks, each external network connects to
  different router that attaches to the subnet.
* One router (default router) is designated to handle traffic from subnet to internet and vice versa.

Consider the scenario below:

     ::

             +------------+               +------------+
             | External   |               | External   |
             |  Network 1 |               |  Network 2 |
             +------+-----+               +------+-----+
                    |                            |
                    |                            |
             +------+-----+               +------+-----+
             |  Router 1  |               |  Router 2  |
             +------+-----+               +------+-----+
                    |                            |
                    +----------+      +----------+
                               |      |
                           +---+------+---+
                           | Subnet 1     |
                           | 10.0.2.0/24  |
                           +--------------+


  * Subnet 1 attaches to both Router 1 and Router 2
  * Router 1 connects to external network 1
  * Router 2 connects to external network 2
  * Traffic (SNAT) from the subnet can reach both external networks
  * FIP and internet access to/from subnet is handled via the default router.

Proposed Changes
================

Pipeline changes
----------------

Installing new flows in FIB table to set proper ``vpn-id`` for traffic destined
for subnets that are not in the default router domain. The new ``vpn-id`` belongs to
the vpn instance of the router that destination subnet attaches to.

Use Case 1: Cross-router internal networks routing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The workflow are below:

* Attach the subnet the first router. This router will be the default router
  for the subnet. L3 flow entries are installed for the subnet’s instances with the
  first router’s ``vpn-id``.

* When second router is attached:

  * Keep L3 flow entries for first router and the subnet associations.
    These flows continue to handle the default routing cases.
  * Proposed new flow entries to handle scenarios where destinations are in second router’s subnets.
    The new flow entries are installed in table FIB_TABLE (21) to convert the ``vpn-id`` from
    the default router to second router. After the replacing of ``vpn-id``, the packets will be
    resubmitted to FIB_TABLE and continue with usual L3 work flow.
  * Other traffic (to subnets in default router domain, and to external network) goes to existing
    pipeline flow for the default router.


Traffic from Subnet 1 to Subnet 2
*********************************

Traffic from Subnet 1 (connected to both Router 1 and Router 2) to Subnet 2 (connected to Router 2 only):

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router1-id`` =>
  | GW Mac table (19) ``match: vpn-id=router1-id,dst-mac=router1-interface-mac`` =>
  | **FIB table (21) ``match: vpn-id=router1-id,dst-subnet2-ip set vpn-id=router2-id``** =>  [1]
  | **Subnet Route table (22) ``match: vpn-id=router2-id resubmit table 21``** =>            [2]
  | FIB table (21) ``match: vpn-id=router2-id,dst-subnet2-vm-ip`` => ``OF Group for subnet2's VM``

*Note: Flows go from table 21 => table 22 and resubmit to table 21 because
OVS doesn't allow resubmit after set metadata, ie the following flow syntax:*

::

  table=21,priority=43,ip,metadata=0x30d40/0xfffffe,nw_dst=10.100.6.0/24 actions=write_metadata:0x30d48/0xfffffe,resubmit(,21)

*results in error:*

::

  instruction apply_actions must be specified before write_metadata

Traffic from subnet 2 to subnet 1
*********************************

Traffic from subnet 2 (connected to router 2) to subnet 1 (connected to both router 1 and router 2):

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router2-id`` =>
  | GW Mac table (19) ``match: vpn-id=router2-id,dst-mac=router2-interface-mac`` =>
  | **FIB table (21) ``match: vpn-id=router2-id,dst-subnet1-ip set vpn-id=router1-id``** =>I  [3]
  | **Subnet Route table (22) ``match: vpn-id=router1-id resubmit table 21``** =>             [4]
  | FIB table (21) ``match: vpn-id=router1-id,dst-subnet1-vm-ip`` => ``OF Group for subnet1's VM``

[1], [2], [3], and [4] are proposed new flows.

Sample flows as below, new proposed flow entries are prefixed with ``**``:

::

  table=0, priority=4,in_port=4,vlan_tci=0x0000/0x1fff actions=write_metadata:0x20000000000/0xffffff0000000001,goto_table:17
  table=0, priority=4,in_port=6,vlan_tci=0x0000/0x1fff actions=write_metadata:0x40000000000/0xffffff0000000001,goto_table:17
  table=0, priority=4,in_port=2,vlan_tci=0x0000/0x1fff actions=write_metadata:0x50000000001/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x8000020000000000/0xffffff0000000000 actions=load:0x186a0->NXM_NX_REG3[0..24],write_metadata:0x9000020000030d40/0xfffffffffffffffe,goto_table:19
  table=17, priority=10,metadata=0x8000040000000000/0xffffff0000000000 actions=load:0x186a4->NXM_NX_REG3[0..24],write_metadata:0x9000040000030d48/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d40/0xfffffe,dl_dst=fa:16:3e:b4:58:8e actions=goto_table:21
  table=19, priority=20,metadata=0x30d48/0xfffffe,dl_dst=fa:16:3e:62:fe:5e actions=goto_table:21
  table=19, priority=20,metadata=0x30d50/0xfffffe,dl_dst=fa:16:3e:8e:2c:98 actions=write_metadata:0x30d52/0xfffffe,goto_table:21
  table=21, priority=42,icmp,metadata=0x30d40/0xfffffe,nw_dst=10.100.5.1,icmp_type=8,icmp_code=0 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:fa:16:3e:b4:58:8e->eth_src,move:NXM_OF_IP_SRC[]->NXM_OF_IP_DST[],set_field:10.100.5.1->ip_src,set_field:0->icmp_type,load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=42,icmp,metadata=0x30d48/0xfffffe,nw_dst=10.100.6.1,icmp_type=8,icmp_code=0 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:fa:16:3e:62:fe:5e->eth_src,move:NXM_OF_IP_SRC[]->NXM_OF_IP_DST[],set_field:10.100.6.1->ip_src,set_field:0->icmp_type,load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=42,ip,metadata=0x30d40/0xfffffe,nw_dst=10.100.5.14 actions=group:150000
  table=21, priority=42,ip,metadata=0x30d48/0xfffffe,nw_dst=10.100.6.14 actions=group:150003
  table=21, priority=42,ip,metadata=0x30d52/0xfffffe,nw_dst=192.168.56.17 actions=write_metadata:0x30d52/0xfffffe,goto_table:44
  table=21, priority=34,ip,metadata=0x30d52/0xfffffe,nw_dst=192.168.56.0/24 actions=write_metadata:0x138b030d52/0xfffffffffe,goto_table:22
  **table=21,priority=43,ip,metadata=0x30d40/0xfffffe,nw_dst=10.100.6.0/24 actions=write_metadata:0x30d48/0xfffffe,goto_table:22   [1]
  **table=21,priority=43,ip,metadata=0x30d48/0xfffffe,nw_dst=10.100.5.0/24 actions=write_metadata:0x30d40/0xfffffe,goto_table:22   [3]
  table=21, priority=10,ip,metadata=0x30d52/0xfffffe actions=group:225000
  table=21, priority=10,ip,metadata=0x30d40/0xfffffe actions=goto_table:26
  table=22, priority=42,ip,metadata=0x30d52/0xfffffe,nw_dst=192.168.56.255 actions=drop
  **table=22,priority=42,ip,metadata=0x30d40/0xfffffe actions=resubmit(,21)**                                                      [2]
  **table=22,priority=42,ip,metadata=0x30d48/0xfffffe actions=resubmit(,21)**                                                      [4]


*Legends:*

::

  subnet1 ip    : 10.100.5.0/24
  subnet1 vm ip : 10.100.5.14
  subnet2 ip    : 10.100.6.0/24
  subnet2 vm    : 10.100.6.14
  external net  : 192.168.56.0/24


Use Case 2: Cross-router external networks routing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* When adding second router to the same subnet, the default router is:

  * the first router if the it connects to an external network
  * the second router if it connects to an external network and the first router does not.

* Traffic from the subnet to the internet always go through the default router.
* Instances from the subnet can access servers in both external networks.
* Traffic from subnet to the external network connected to default router
  goes through the L3 pipeline for default router's vpn.
* For traffic from the subnet to the external network connected to the secondary router,
  proposed new flow entries are installed in FIB table to replace default router's ``vpn-id``
  with the second router's ``vpn-id``.

Traffic from Subnet to the second router's external network (SNAT):
-------------------------------------------------------------------

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router1-id`` =>
  | GW Mac table (19) ``match: vpn-id=router1-id,dst-mac=router1-interface-mac`` =>
  | **FIB table (21) ``match: vpn-id=router1-id,dst-ext-subnet2-ip set vpn-id=router2-id``** => [1]
  | PSNAT_TABLE (26) =>
  | OUTBOUND_NAPT_TABLE (46) ``set vpn-id=router-id, punt-to-controller``
  | OUTBOUND_NAPT_TABLE (46) ``learned flow - match vpn-id=router2-id,src-ip set vpn-id=ext-subnet2-vpn-id,dst-ip=router2-gw-ip,dst-mac=router2-gw-mac``
  | NAPT_PFIB_TABLE (47) ``match: vpn-id=ext-subnet2-vpn-id``
  | FIB table (21) ``match: vpn-id=ext-subnet2-vpn-id,dst-ip`` =>  ``OF group per external subnet``

Reverse traffic from second external network to the subnet (SNAT):
------------------------------------------------------------------

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=ext-net1-vpn-id`` =>
  | GW Mac table (19) ``match: vpn-id=ext-net1-vpn-id,dst-mac=router2-ext-gw-mac`` =>
  | FIB table (21) ``match: vpn-id=router2-ext-gw-ip,dst-ext-subnet2-ip``  =>
  | INBOUND_NAPT_TABLE (44) ``learned flow - match src-ip=router2-ext-gw-ip set vpn-id=router2-id,dst-ip=subnet-vm-ip,dst-mac=subnet-vm-mac`` =>
  | **FIB table (21) ``match: vpn-id=router2-id,dst-ext-subnet1-ip set vpn-id=router1-id``** =>  [2]
  | **Subnet Route table (22) ``match: vpn-id=router1-id resubmit table 21``** =>                [3]
  | FIB table (21) ``match: vpn-id=router1-id,dst-subnet1-vm-ip`` => ``OF Group for subnet1's VM``


[1], [2], and [3] are proposed new flows.

The following are sample of pipeline flow with proposed new flows prefixed with '**':

::

  table=0, priority=4,in_port=1,vlan_tci=0x0000/0x1fff actions=write_metadata:0x60000000001/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x60000000000/0xffffff0000000000 actions=load:0x186ac->NXM_NX_REG3[0..24],write_metadata:0x9000060000030d58/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d58/0xfffffe,dl_dst=fa:16:3e:71:34:70 actions=write_metadata:0x30d5a/0xfffffe,goto_table:21
  table=21, priority=34,ip,metadata=0x30d5a/0xfffffe,nw_dst=192.168.57.0/24 actions=write_metadata:0x138c030d5a/0xfffffffffe,goto_table:22
  table=21, priority=42,ip,metadata=0x30d5a/0xfffffe,nw_dst=192.168.57.14 actions=write_metadata:0x30d5a/0xfffffe,goto_table:44
  table=21, priority=42,ip,metadata=0x30d5a/0xfffffe,nw_dst=192.168.57.1 actions=set_field:08:00:27:07:5a:1f->eth_dst,load:0x600->NXM_NX_REG6[],resubmit(,220)
  **table=21, priority=10,ip,metadata=0x30d40/0xfffffe,nw_dst=192.168.57.0/24 actions=write_metadata:0x30d48/0xffffff,goto_table:26 [1]
  **table=21,priority=43,ip,metadata=0x30d48/0xfffffe,nw_dst=10.100.5.0/24 actions=write_metadata:0x30d40/0xfffffe,goto_table:22    [2]
  table=21, priority=10,ip,metadata=0x30d5a/0xfffffe actions=group:225001
  table=22, priority=42,ip,metadata=0x30d5a/0xfffffe,nw_dst=192.168.57.255 actions=drop
  **table=22,priority=42,ip,metadata=0x30d40/0xfffffe actions=resubmit(,21)                                                         [3]
  table=26, priority=5,ip,metadata=0x30d48/0xfffffe actions=goto_table:46
  table=44, send_flow_rem priority=10,tcp,nw_dst=192.168.57.14,tp_dst=49152 actions=set_field:10.100.6.14->ip_dst,set_field:45791->tcp_dst,write_metadata:0x30d48/0xfffffe,goto_table:47
  table=46, idle_timeout=300, send_flow_rem priority=10,tcp,metadata=0x30d48/0xfffffe,nw_src=10.100.6.14,tp_src=45791 actions=set_field:192.168.57.14->ip_src,set_field:49152->tcp_src,set_field:fa:16:3e:71:34:70->eth_src,write_metadata:0x30d5a/0xffffff,goto_table:47
  table=46, priority=5,ip,metadata=0x30d48/0xfffffe actions=CONTROLLER:65535,write_metadata:0x30d48/0xfffffe
  table=47, priority=5,ip,metadata=0x30d5a/0xfffffe actions=load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=47, priority=5,ip,metadata=0x30d58/0xfffffe actions=load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=10,ip,metadata=0x30d40/0xfffffe,nw_dst=192.168.57.0/24 actions=write_metadata:0x30d48/0xffffff,goto_table:26
  group_id=225001,type=all,bucket=actions=set_field:08:00:27:07:5a:1f->eth_dst,load:0x600->NXM_NX_REG6[],resubmit(,220)

*Legends*:

::

  0x30d58: vpn id of second router's external net
  0x30d5a: vpn id of second router's external subnet
  0x30d48: vpn id of second router
  0x30d40: vpn id of default router
  10.100.5.0 : subnet ip
  192.168.57.0: IP address of external subnet attached to second router
  192.168.57.14: IP address of external gateway to second router

Floating IPs
^^^^^^^^^^^^

Floating IPs for instances in the subnet can only be generated for the external network
associating with default router. The reason is floating ip and the VM ip are one-to-one,
once the FIP is generated for an neutron port, no new FIP can be generated for the same
port.

Updating Routers in Router Chain
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

A router in the secondary router list can be promoted to become the default router if:

* The default router is dissociated from the subnet
* The default router does not connect to an external network and one of secondary router becomes
  connected to an external network.

When a secondary router becomes the default router for a subnet, all L3 flows for the subnet
will be changed to the ``vpn-id`` of the newly promoted default router.

YANG changes
------------

*Subnetmap* structure must be changed to support a list with secondary router IDs.
The attributes related to router-subnet association such as *router-id*, *router-interface-port-id*,
*router-intf-mac-address*, *router-interface-fixed-ip*, and *vpn-id* hold information for
the subnet-default router association.

::

  neutronvpn.yang

  module neutronvpn {
    ..
    container subnetmaps{
        list subnetmap {
            key id;
            leaf id {
                type    yang:uuid;
                description "UUID representing the subnet ";
            }
            ..
            leaf subnet-ip {
                type    string;
                description "Specifies the subnet IP in CIDR format";
            }
            leaf router-id {
                type    yang:uuid;
  -             description "router to which this subnet belongs";
  +             description "default router to which this subnet belongs";
            }

            leaf router-interface-port-id {
                type    yang:uuid;
                description "port corresponding to router interface on this subnet";
            }

            leaf router-intf-mac-address {
                type    string;
                description "router interface mac address on this subnet";
            }

            leaf router-interface-fixed-ip {
                type    string;
                description "fixed ip of the router interface port on this subnet";
            }

            leaf vpn-id {
                type    yang:uuid;
                description "VPN to which this subnet belongs";
            }
  +
  +         leaf-list secondary-router-list{
  +             type yang:uuid;
  +             description "List of secondary routers asscociate with subnet";
  +         }

            leaf-list port-list {
                type yang:uuid;
            }

Clustering considerations
=========================
None

Other Infra considerations
==========================
None

Security considerations
=======================
None

Scale and Performance Impact
============================
None

Targeted Release
================
Oxygen

Alternatives
============
None

Usage
=====

Features to Install
===================

odl-netvirt-openstack

REST API
========

CLI
===

None

Implementation
==============

Assignee(s)
-----------
Primary assignee:
  Vinh Nguyen  <vinh.nguyen@hcl.com>

Other contributors:
  - TBD


Work Items
----------

* NeutronVpn changes
* VPNManager changes
* FibManager changes

Dependencies
============

None

Testing
=======

Unit Tests
----------

Unit tests related to chaining routers for subnet as above
as above.

Integration Tests
-----------------
TBD

CSIT
----

CSIT specific testing will be done to check VMs connectivity with
chaining routers for single subnet solution:

Use Case 1
^^^^^^^^^^

1. Create Network NET1
2. Create Subnetwork SUBNET1 on NET1
3. Launch VM1 on NET1
4. Create Router ROUTER1
5. Attach SUBNET1 to ROUTER1
6. Create Network NET2
7. Create Subnetwork SUBNET2 on NET2
8. Launch VM2 on NET2
9. Attach SUBNET2 on ROUTER1
   9.1 Verify VM1 and VM2 connectivity
   9.1 Verify VM1 and VM2 can communicate with each other
10. Create external network EXTNET
11. Create external subnetwork EXTSUBNET
12. Set EXTNET as gateway for ROUTER1
13. Create Network NET3
14. Create Subnetwork SUBNET3
15. Launch VM3 on NET3
16. Create Router Router2
17. Attach SUBNET3 on ROUTER2
18. Create Neutron Port PORT_SUB2_RT2 on SUBNET2
19. Attach Neutron Port PORT_SUB2_RT2 as interface to Router ROUTER2
    18.1 Verify VM2 and VM3 can communicate with each other
    18.2 Verify VM2 and VM1 still can communicate with each other
    18.3 Verify VM3 and VM1 can not communicate
    18.4 Verify VM1 and VM2 can access external network EXTNET and vice versa
20. Repeat steps 12-18 for chaining more routers to SUBNET2 and verify results
    similarly to step 18.1-18.4
21. Remove routers in reserse steps and verify the setup works with the
    the remaining routers in the chain.
22. Clean up

Use Case 2
^^^^^^^^^^

1. Create Network NET1
2. Create Subnetwork SUBNET1 on NET1
3. Launch VM1 on NET1
4. Create Router ROUTER1
5. Create external network EXTNET1
6. Create external subnetwork EXTSUBNET1 on EXTNET1
7. Set EXTNET1 as gateway for ROUTER1
8. Attach SUBNET1 to ROUTER1
    8.1 Verify SNAT from Subnet SUBNET1 to external net EXTNET1
    8.2 Add FIP for VM1, verify FIP communication from SUBNET1 to internet
9. Create Router ROUTER2
10. Create external network EXTNET2
11. Create external subnetwork EXTSUBNET2 on EXTNET2
12. Set EXTNET2 as gateway for ROUTER2
13. Create Neutron Port PORT_SUB1_RT2 on SUBNET1
14. Attach Neutron Port PORT_SUB1_RT2 as interface to Router ROUTER2
    14.1 Verify SNAT from Subnet SUBNET1 to external net EXTNET2
15. Repeat steps 9-14 for chaining more routers to SUBNET1 and verify results
    similarly to step 14.1
16. Unset EXTNET1 as gateway to router ROUTER1
    16.1 Verify EXTNET2 becomes default router for SUBNET1, ie SNAT/FIP from
    SUBNET1 is possible via ROUTER2 and EXTNET2.
17. Remove routers in reserse steps and verify the setup works with the
    the remaining routers in the chain.
18. Clean up

Documentation Impact
====================

Necessary documentation would be added if needed.

References
==========

* `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`

