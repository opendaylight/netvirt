.. contents:: Table of Contents
         :depth: 3

===================================================
Chaining Routers for Subnet support in OpenDaylight
===================================================

https://git.opendaylight.org/gerrit/#/q/topic:router-chaining-for-single-subnet

In this specification we will introduce chaining multiple routers for single
subnet support in NetVirt. Routers chaining enables a single subnet
to attach to multiple routers. Traffic from the subnet will be routed to proper
router based on the destination addresses.

The router with interface fixed IP address matching the subnet's gateway IP
address will be designated as default router.
The default router handles routing between networks attaching to the router as usual.

Other routers attaching to the subnet will be designated as chained routers.
Outgoing traffic from the subnet is checked if the destination address
belong to subnets attaching to a chained router and routed to the destination
via the respective router. Otherwise the traffic will be routed via the default router.

Problem description
===================

Currently Neutron allows multiple routers for single subnet scenarios as
described above. However ``netvirt`` only supports single router per subnet.
When adding second router the subnet, the first router's ``vpn-id`` is replaced
with the second router's ``vpn-id``  in all the L3 pipeline flows installed
for the subnet's instances. The subnet's traffic now is routed
via second router only.

Workflow details as below:

* First router attaches to the subnet, traffic from the subnet are tagged with
  the ``vpn-id`` of the router. Subsequently, it goes to the l3 pipeline flows
  that matches ``vpn-id`` and routed to the destination within the subnets that
  also attach the same router.

* Now if we attach the same subnet to the another router, ``netvirt`` replaces
  the ``vpn-id`` of the newly-attached router in all above-mentioned l3 pipeline flows.
  There are two problems with this:

  * From netvirt's perspective, the subnet is now only attached to the second
    router since ``netvirt`` doesn't support multiple routers for single subnet.
    This is inconsistent with Neutron since the latter shows that the subnet
    has interfaces to both routers.

  * Traffic within the first router's domain to/from the subnet
    will be dropped since the all flow entries for the subnet now match the ``vpn-id``
    of the second router.


Use Cases
---------

Limitations:
^^^^^^^^^^^^

  * The feature only supports Flat and Vlan external provider network types.
  * The following use cases are not supported:

    * BGP-VPN based connectivity over VXLAN (EVPN) or GRE (L3VPN).
    * External provider network types that use BGP-VPN to reach
      external (internet) network traffic from the Dara Center-VMs.

The following describe supported two use cases with Flat and Vlan external
provider network types.

.. _`Cross-router internal networks routing`:

1. Use Case 1: Cross-router internal networks routing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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
     |  Subnet 1a  |       |  Subnet 1   |       |  Subnet 2   |
     +-------------+       +-------------+       +-------------+


* Subnet 1 and Subnet 1a attach to Router 1
* Subnet 1 and Subnet 2 attach to Router 2
* Subnet 1 attaches to both Router 1 and Router 2
* Traffic between Subnet 1 and Subnet 1a is routed via Router 1
* Traffic between Subnet 1 and Subnet 2 is routed via Router 2
* Subnet 1 and Subnet 1a can access each other and External Network 1 (and vice versa) via Router 1
* Subnet 2 can only access Subnet 1 (and vice versa), it does not have access to external network
  and Subnet 1a

.. _`Cross-router external networks routing`:

2. User Case 2: Cross-router external networks routing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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
                           |   Subnet 1   |
                           +--------------+

* Subnet 1 attaches to both Router 1 and Router 2
* Router 1 connects to External Network 1
* Router 2 connects to External Network 2
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

* Attach the subnet to the first router. This router will be the default router
  for the subnet (assuming the router interface IP matches the subnet's gateway IP).
  L3 flow entries are installed for the subnet’s instances with the
  first router’s ``vpn-id``.

* When the same subnet attached to the second router:

  * Keep L3 flow entries for first router and the subnet associations.
    These flows continue to handle the default routing scenarios.
  * Proposed new flow entries to handle scenarios where destinations are in second router’s subnets.
    The new flow entries are installed in table FIB_TABLE (21) to convert the ``vpn-id`` from
    the default router to that of the second router. After the replacing of ``vpn-id``,
    the packets will be resubmitted to FIB_TABLE and continue with existing L3 pipeline flows.
  * Other traffic (to subnets in default router domain, and to external network) goes to existing L3
    pipeline flows for the default router.


*Traffic from Subnet 1 (connected to both Router 1 and Router 2) to Subnet 2 (connected to Router 2 only):*

.. code-block:: none
  :emphasize-lines: 4,5

  Classifier table (0) =>
  Dispatcher table (17) l3vpn service: set vpn-id=router1-id =>
  GW Mac table (19) match: vpn-id=router1-id,dst-mac=router1-interface-mac =>
  FIB table (21) match: vpn-id=router1-id,src-ip=src-subnet1-cidr,dst-ip=dst-subnet2-cidr set vpn-id=router2-id =>
  Subnet Route table (22) match: vpn-id=router2-id resubmit table 21 =>
  FIB table (21) match: vpn-id=router2-id,dst-ip=dst-subnet2-vm-ip => OF Group for subnet2's VM

.. note::

  Flows go from table 21 => table 22 and resubmit to table 21 because
  OVS doesn't allow resubmit after set metadata, ie the following flow syntax:

  .. code-block:: bash

     table=21,priority=43,ip,metadata=0x30d40/0xfffffe,nw_dst=10.100.2.0/24 actions=write_metadata:0x30d48/0xfffffe,resubmit(,21)

  results in error:

  .. code-block:: bash

      "instruction apply_actions must be specified before write_metadata"

The modifications in flows are illustrated below:

.. code-block:: bash
  :emphasize-lines: 5,6

  table=0, priority=4,in_port=6,vlan_tci=0x0000/0x1fff actions=write_metadata:0x40000000000/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x8000020000000000/0xffffff0000000000 actions=load:0x186a0->NXM_NX_REG3[0..24],write_metadata:0x9000020000030d40/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d40/0xfffffe,dl_dst=fa:16:3e:b4:58:8e actions=goto_table:21
  table=21, priority=42,icmp,metadata=0x30d40/0xfffffe,nw_dst=10.100.1.1,icmp_type=8,icmp_code=0 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:fa:16:3e:b4:58:8e->eth_src,move:NXM_OF_IP_SRC[]->NXM_OF_IP_DST[],set_field:10.100.1.1->ip_src,set_field:0->icmp_type,load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=43,ip,metadata=0x30d40/0xfffffe,nw_src=10.100.1.0/24,nw_dst=10.100.2.0/24 actions=write_metadata:0x30d48/0xfffffe,goto_table:22
  table=22, priority=42,ip,metadata=0x30d48/0xfffffe actions=resubmit(,21)
  table=21, priority=42,ip,metadata=0x30d48/0xfffffe,nw_dst=10.100.2.14 actions=group:150003

*Reverse Traffic from Subnet 2 (connected to router 2) to Subnet 1 (connected to both router 1 and router 2):*

.. code-block:: none
  :emphasize-lines: 4,5

  Classifier table (0) =>
  Dispatcher table (17) l3vpn service: set vpn-id=router2-id =>
  GW Mac table (19) match: vpn-id=router2-id,dst-mac=router2-interface-mac =>
  FIB table (21) match: vpn-id=router2-id,dts-ip=dst-subnet1-cidr set vpn-id=router1-id =>
  Subnet Route table (22) match: vpn-id=router1-id resubmit table 21 =>
  FIB table (21) match: vpn-id=router1-id,dst-ip=dst-subnet1-vm-ip => OF Group for subnet1's VM

The modifications in flows are illustrated below:

.. code-block:: bash
  :emphasize-lines: 5,6

  table=0, priority=4,in_port=6,vlan_tci=0x0000/0x1fff actions=write_metadata:0x40000000000/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x8000040000000000/0xffffff0000000000 actions=load:0x186a4->NXM_NX_REG3[0..24],write_metadata:0x9000040000030d48/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d48/0xfffffe,dl_dst=fa:16:3e:62:fe:5e actions=goto_table:21
  table=21, priority=42,icmp,metadata=0x30d48/0xfffffe,nw_dst=10.100.2.1,icmp_type=8,icmp_code=0 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:fa:16:3e:62:fe:5e->eth_src,move:NXM_OF_IP_SRC[]->NXM_OF_IP_DST[],set_field:10.100.2.1->ip_src,set_field:0->icmp_type,load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21,priority=43,ip,metadata=0x30d48/0xfffffe,nw_dst=10.100.1.0/24 actions=write_metadata:0x30d40/0xfffffe,goto_table:22
  table=22,priority=42,ip,metadata=0x30d48/0xfffffe actions=resubmit(,21)
  table=21, priority=42,ip,metadata=0x30d40/0xfffffe,nw_dst=10.100.1.14 actions=group:150000

Traffic within router1 domain follows existing pipeline

*Outbound from subnet1's VM to external network attached to router1*

.. code-block:: bash

  table=0, priority=4,in_port=4,vlan_tci=0x0000/0x1fff actions=write_metadata:0x20000000000/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x8000020000000000/0xffffff0000000000 actions=load:0x186a0->NXM_NX_REG3[0..24],write_metadata:0x9000020000030d40/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d40/0xfffffe,dl_dst=fa:16:3e:b4:58:8e actions=goto_table:21
  table=21, priority=42,icmp,metadata=0x30d40/0xfffffe,nw_dst=10.100.1.1,icmp_type=8,icmp_code=0 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:fa:16:3e:b4:58:8e->eth_src,move:NXM_OF_IP_SRC[]->NXM_OF_IP_DST[],set_field:10.100.1.1->ip_src,set_field:0->icmp_type,load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=10,ip,metadata=0x30d40/0xfffffe actions=goto_table:26
  table=26, priority=5,ip,metadata=0x30d40/0xfffffe actions=goto_table:46
  table=46, priority=5,ip,metadata=0x30d40/0xfffffe actions=CONTROLLER:65535,write_metadata:0x30d40/0xfffffe
  table=46, idle_timeout=300, send_flow_rem priority=10,tcp,metadata=0x30d40/0xfffffe,nw_src=10.100.1.14,tp_src=45791
      actions=set_field:192.168.51.14->ip_src,set_field:49152->tcp_src,set_field:fa:16:3e:71:34:70->eth_src,
        write_metadata:0x30d52/0xffffff,goto_table:47
  table=47, priority=5,ip,metadata=0x30d52/0xfffffe actions=load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=10,ip,metadata=0x30d52/0xfffffe actions=group:225000
  group_id=225000,type=all,bucket=actions=set_field:08:00:27:07:5a:1f->eth_dst,load:0x500->NXM_NX_REG6[],resubmit(,220)

*Inbound from external network 1 to subnet1's VM*

.. code-block:: bash

  table=0, priority=4,in_port=2,vlan_tci=0x0000/0x1fff actions=write_metadata:0x50000000001/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x60000000000/0xffffff0000000000 actions=load:0x186ab->NXM_NX_REG3[0..24],write_metadata:0x9000060000030d50/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d50/0xfffffe,dl_dst=fa:16:3e:8e:2c:98 actions=write_metadata:0x30d52/0xfffffe,goto_table:21
  table=21, priority=42,ip,metadata=0x30d52/0xfffffe,nw_dst=192.168.51.17 actions=write_metadata:0x30d52/0xfffffe,goto_table:44
  table=44, send_flow_rem priority=10,tcp,nw_dst=192.168.51.14,tp_dst=49152 actions=set_field:10.100.1.14->ip_dst,set_field:55152->tcp_dst,write_metadata:0x30d40/0xfffffe,goto_table:47
  table=47, priority=5,ip,metadata=0x30d40/0xfffffe actions=load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, n_packets=0, n_bytes=0, priority=42,ip,metadata=0x30d40/0xfffffe,nw_dst=10.100.1.14 actions=group:150001

.. note::

   | 0x30d40: vpn id of first router
   | 0x30d50: vpn id of first router's external net
   | 0x30d52: vpn id of first router's external subnet
   | 0x30d48: vpn id of second router
   | subnet1 ip    : 10.100.1.0/24
   | subnet1 vm ip : 10.100.1.14
   | subnet2 ip    : 10.100.2.0/24
   | subnet2 vm    : 10.100.2.14
   | external net  : 192.168.51.0/24


Use Case 2: Cross-router external networks routing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Traffic from the subnet to the internet always go through the default router.
* Instances from the subnet can access servers with specific IP addresses in both external networks.
* Traffic from subnet to the external network connected to default router
  goes through the L3 pipeline for default router's vpn.
* Traffic from the subnet to the external networks connected to the chained routers will
  goes through the proposed new flows which are installed in FIB table to replace
  default router's ``vpn-id`` with the chained router's ``vpn-id``.

Controller-base SNAT
____________________

*Traffic from Subnet to the second router's external network (SNAT):*

.. code-block:: bash
  :emphasize-lines: 4

  Classifier table (0) =>
  Dispatcher table (17) l3vpn service: set vpn-id=router1-id =>
  GW Mac table (19) match: vpn-id=router1-id,dst-mac=router1-interface-mac =>
  FIB table (21) match: vpn-id=router1-id,src-ip=src-subnet1-cidr,dst-ip=dst-ext-subnet2-cidr set vpn-id=router2-id =>
  PSNAT_TABLE (26) =>
  OUTBOUND_NAPT_TABLE (46) vpn-id=router2-id, punt-to-controller
  OUTBOUND_NAPT_TABLE (46) learned flow - match vpn-id=router2-id,src-ip set vpn-id=ext-subnet2-vpn-id,dst-ip=router2-gw-ip,dst-mac=router2-gw-mac
  NAPT_PFIB_TABLE (47) match: vpn-id=ext-subnet2-vpn-id
  FIB table (21) match: vpn-id=ext-subnet2-vpn-id,dst-ip=specific-external-ip-address =>  OF group per external subnet

The modifications in flows are illustrated below:

.. code-block:: bash
  :emphasize-lines: 4

  table=0, priority=4,in_port=4,vlan_tci=0x0000/0x1fff actions=write_metadata:0x20000000000/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x8000020000000000/0xffffff0000000000 actions=load:0x186a0->NXM_NX_REG3[0..24],write_metadata:0x9000020000030d40/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d40/0xfffffe,dl_dst=fa:16:3e:2b:94:fe actions=goto_table:21
  table=21, priority=10,ip,metadata=0x30d40/0xfffffe,nw_src=10.100.1.0/24,nw_dst=192.168.52.0/24 actions=write_metadata:0x30d48/0xffffff,goto_table:26
  table=26, priority=5,ip,metadata=0x30d48/0xfffffe actions=goto_table:46
  table=46, priority=5,ip,metadata=0x30d48/0xfffffe actions=CONTROLLER:65535,write_metadata:0x30d48/0xfffffe
  table=46, idle_timeout=300, send_flow_rem priority=10,tcp,metadata=0x30d48/0xfffffe,nw_src=10.100.1.15,tp_src=45791
      actions=set_field:192.168.52.14->ip_src,set_field:49152->tcp_src,set_field:fa:16:3e:71:34:70->eth_src,
        write_metadata:0x30d5a/0xffffff,goto_table:47
  table=46, priority=5,ip,metadata=0x30d48/0xfffffe actions=CONTROLLER:65535,write_metadata:0x30d48/0xfffffe
  table=47, priority=5,ip,metadata=0x30d5a/0xfffffe actions=load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=10,ip,metadata=0x30d5a/0xfffffe actions=group:225000
  group_id=225000,type=all,bucket=actions=set_field:08:00:27:07:5a:1f->eth_dst,load:0x600->NXM_NX_REG6[],resubmit(,220)

*Reverse traffic from second external network to the subnet (SNAT):*

.. code-block:: none
  :emphasize-lines: 7,8

  Classifier table (0) =>
  Dispatcher table (17) l3vpn service: set vpn-id=ext-net1-vpn-id =>
  GW Mac table (19) match: vpn-id=ext-net1-vpn-id,dst-mac=router2-ext-gw-mac =>
  FIB table (21) match: vpn-id=router2-ext-gw-ip,dst-ip=dst-ext-subnet2-cidr  =>
  INBOUND_NAPT_TABLE (44) learned flow - match src-ip=router2-ext-gw-ip set vpn-id=router2-id,dst-ip=subnet-vm-ip,dst-mac=subnet-vm-mac =>
  FIB table (21) match: vpn-id=router2-id,dst-ip=dst-ext-subnet2-cidr set vpn-id=router1-id =>
  Subnet Route table (22) match: vpn-id=router1-id resubmit table 21 =>
  FIB table (21) match: vpn-id=router1-id,dst-subnet1-vm-ip => OF Group for subnet1's VM

.. code-block:: bash
  :emphasize-lines: 7,8

  table=0, priority=4,in_port=1,vlan_tci=0x0000/0x1fff actions=write_metadata:0x60000000001/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x60000000000/0xffffff0000000000 actions=load:0x186ab->NXM_NX_REG3[0..24],write_metadata:0x9000060000030d56/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d56/0xfffffe,dl_dst=fa:16:3e:cb:a7:37 actions=write_metadata:0x30d58/0xfffffe,goto_table:21
  table=21, priority=42,ip,metadata=0x30d58/0xfffffe,nw_dst=192.168.52.14 actions=write_metadata:0x30d58/0xfffffe,goto_table:44
  table=44, send_flow_rem priority=10,tcp,nw_dst=192.168.51.14,tp_dst=49152 actions=set_field:10.100.1.15->ip_dst,set_field:55152->tcp_dst,write_metadata:0x30d48/0xfffffe,goto_table:47
  table=47, priority=5,ip,metadata=0x30d48/0xfffffe actions=load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=43,ip,metadata=0x30d48/0xfffffe,nw_dst=10.100.1.0/24 actions=write_metadata:0x30d40/0xfffffe,goto_table:22
  table=22, priority=42,ip,metadata=0x30d40/0xfffffe actions=resubmit(,21)
  table=21, n_packets=0, n_bytes=0, priority=42,ip,metadata=0x30d40/0xfffffe,nw_dst=10.100.1.14 actions=group:150001

Conntrack-based SNAT
____________________

*Traffic from Subnet to the second router's external network (SNAT):*

.. code-block:: none
  :emphasize-lines: 4

  Classifier table (0) =>
  Dispatcher table (17) l3vpn service: set vpn-id=extnet--id =>
  GW Mac table (19) match: vpn-id=router1-id,dst-mac=router1-interface-mac =>
  FIB table (21) match: vpn-id=router1-id,src-ip=src-subnet1-cidr,dst-ip=dst-ext-subnet2-cidr set vpn-id=router2-id =>
  PSNAT_TABLE (26) => submit to netfilter to check wheather it is an established connection
  OUTBOUND_NAPT_TABLE (46) match: ct_state=+snat,vpn-id=router2-id set src-mac=router2-gw-mac => table 47
  OUTBOUND_NAPT_TABLE (46) match: ct_state=+new+trk,vpn-id=router2-id set src-mac=router2-gw-mac,commit connection to netfilter => table 47
  NAPT_PFIB_TABLE (47) match: ct_state=+snat set vpn-id=ext-subnet2-vpn-id =>
  FIB table (21) match: vpn-id=ext-subnet2-vpn-id,dst-ip =>  OF group per external subnet

.. code-block:: bash
  :emphasize-lines: 4

  table=0, priority=4,in_port=4,vlan_tci=0x0000/0x1fff actions=write_metadata:0x20000000000/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x8000020000000000/0xffffff0000000000 actions=load:0x186a0->NXM_NX_REG3[0..24],write_metadata:0x9000020000030d40/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d40/0xfffffe,dl_dst=fa:16:3e:2b:94:fe actions=goto_table:21
  table=21, priority=10,ip,metadata=0x30d40/0xfffffe,nw_src=10.100.1.0/24,nw_dst=192.168.52.0/24 actions=write_metadata:0x30d48/0xffffff,goto_table:26
  table=26, priority=5,ip,metadata=0x30d48/0xfffffe actions=goto_table:46
  table=46, priority=6,ct_state=+snat,ip,metadata=0x30d48/0xfffffe actions=set_field:fa:16:3e:71:34:70->eth_src,resubmit(,47)
  table=46, priority=5,ct_state=+new+trk,ip,metadata=0x30d48/0xfffffe actions=set_field:fa:16:3e:71:34:70->eth_src,ct(commit,table=47,zone=5005,nat(src=192.168.52.14))
  table=47, priority=6,ct_state=+snat,ip,metadata=0x30d48/0xfffffe actions=load:0x30d5a->OXM_OF_METADATA[0..23],load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=10,ip,metadata=0x30d5a/0xfffffe actions=group:225000
  group_id=225000,type=all,bucket=actions=set_field:08:00:27:07:5a:1f->eth_dst,load:0x600->NXM_NX_REG6[],resubmit(,220)

*Reverse traffic from second external network to the subnet (SNAT):*

.. code-block:: none
  :emphasize-lines: 7,8

  Classifier table (0) =>
  Dispatcher table (17) l3vpn service: set vpn-id=ext-net2-vpn-id =>
  GW Mac table (19) match: vpn-id=ext-net2-vpn-id,dst-mac=router2-ext-gw-mac set vpn-id=ext-subnet2-vpn-id =>
  FIB table (21) match: vpn-id=ext-subnet2-vpn-id,dst-ip=router2-ext-gw-ip
  INBOUND_NAPT_TABLE (44) match: src-ip=router2-ext-gw-ip set vpn-id=router2-id, submit to netfilter to check for an existing connection =>
  NAPT_PFIB_TABLE (47) match: ct_state=+dnat,vpn-id=router2-id =>
  FIB table (21) match: vpn-id=router2-id,dst-ip=dst-ext-subnet2-cidr set vpn-id=router1-id =>
  Subnet Route table (22) match: vpn-id=router1-id resubmit table 21 =>
  FIB table (21) match: vpn-id=router1-id,dst-subnet1-vm-ip => OF Group for subnet1's VM

The modifications in flows are illustrated below:

.. code-block:: bash
  :emphasize-lines: 7,8

  table=0, priority=4,in_port=1,vlan_tci=0x0000/0x1fff actions=write_metadata:0x60000000001/0xffffff0000000001,goto_table:17
  table=17, priority=10,metadata=0x60000000000/0xffffff0000000000 actions=load:0x186ab->NXM_NX_REG3[0..24],write_metadata:0x9000060000030d56/0xfffffffffffffffe,goto_table:19
  table=19, priority=20,metadata=0x30d56/0xfffffe,dl_dst=fa:16:3e:cb:a7:37 actions=write_metadata:0x30d58/0xfffffe,goto_table:21
  table=21, priority=42,ip,metadata=0x30d58/0xfffffe,nw_dst=192.168.52.14 actions=write_metadata:0x30d58/0xfffffe,goto_table:44
  table=44, priority=10,ip,metadata=0x30d58/0xfffffe,nw_dst=192.168.52.14 actions=load:0x30d48->OXM_OF_METADATA[0..23],ct(table=47,zone=5005,nat)
  table=47, n_packets=0, n_bytes=0, priority=5,ct_state=+dnat,ip,metadata=0x30d48/0xfffffe actions=load:0->NXM_OF_IN_PORT[],resubmit(,21)
  table=21, priority=43,ip,metadata=0x30d48/0xfffffe,nw_dst=10.100.1.0/24 actions=write_metadata:0x30d40/0xfffffe,goto_table:22
  table=22, priority=42,ip,metadata=0x30d40/0xfffffe actions=resubmit(,21)
  table=21, n_packets=0, n_bytes=0, priority=42,ip,metadata=0x30d40/0xfffffe,nw_dst=10.100.1.14 actions=group:150001

.. note::

   | 0x30d40: vpn id of default router
   | 0x30d48: vpn id of second router
   | 0x30d58: vpn id of second router's external net
   | 0x30d5a: vpn id of second router's external subnet
   | 10.100.1.0 : subnet ip
   | 192.168.52.0: IP address of external subnet attached to second router
   | 192.168.52.14: IP address of external gateway to second router

Floating IPs
^^^^^^^^^^^^

Floating IPs for instances in the subnet can only be generated for the external network
associating with default router. The reason is floating ip and the VM ip are one-to-one,
once the FIP is generated for a Neutron port, no new FIP can be generated for the same
port.

Updating Router's Roles in Router Chain
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The default router for a subnet will change when the subnet's gateway IP address
is updated to new value that matches the IP address of one of the chained router's
interface, eg. using the CLI commands:

.. code-block:: none

   openstack router remove subnet/port <router-id> <subnet-id>/<default-router-ifc-port-id>
   openstack subnet set --gateway=<promoting-router-ifc-ip> <subnet-id>

When a chained router becomes the default router for a subnet, all L3 pipeline flows for
the subnet will be changed with the ``vpn-id`` of the newly promoted default router.

After new default router to a subnet is promoted, for new VM instances, the default
route in the routing table will match the new subnet's gateway IP address and hence
also match the new default router. All L3 traffic will be forwarding via the new default
router.

For the existing VMs that created before the default router changes, the default route
entry in the routing table still points to the former default router. As a result,
L3 traffic will fail since all L3 pipeline flows are now switched to new default router.
To enable L3 traffic via the new default router, we have to manually change the default
route to match the new gateway's IP address, ie:

.. code-block:: none

   sudo ip route delete default
   sudo ip router add default via <new gateways' IP>


YANG changes
------------

*Subnetmap* structure must be changed to support a list with chained router IDs.

Proposed changes:

* New ``grouping subnet-vpn-info`` is refactored out of ``subnetmap``.
  The grouping contains the attributes related to router-subnet association.
* ``subnetmap`` contains attribute for the association between default router and the subnet.
* ``subnetmap`` also contains a list of associations for chained routers and the subnet.

.. code-block:: bash
  :caption: neutronvpn.yang
  :emphasize-lines: 3-29,43-76

  module neutronvpn {
  ..
  +    grouping subnet-vpn-info {
  +
  +        leaf router-id {
  +            type    yang:uuid;
  +            description "router to which this subnet belongs";
  +        }
  +
  +        leaf router-interface-port-id {
  +            type    yang:uuid;
  +            description "port corresponding to router interface on this subnet";
  +        }
  +
  +        leaf router-intf-mac-address {
  +            type    string;
  +            description "router interface mac address on this subnet";
  +        }
  +
  +        leaf router-interface-fixed-ip {
  +            type    string;
  +            description "fixed ip of the router interface port on this subnet";
  +        }
  +
  +        leaf vpn-id {
  +            type    yang:uuid;
  +            description "VPN to which this subnet belongs";
  +        }
  +    }

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
  +         leaf gateway-ip {
  +             type    string;
  +             description "The IP of subnet's gateway";
  +         }
  -         leaf router-id {
  -             type    yang:uuid;
  -             description "router to which this subnet belongs";
  -         }
  -
  -         leaf router-interface-port-id {
  -             type    yang:uuid;
  -             description "port corresponding to router interface on this subnet";
  -         }
  -
  -         leaf router-intf-mac-address {
  -             type    string;
  -             description "router interface mac address on this subnet";
  -         }
  -
  -         leaf router-interface-fixed-ip {
  -             type    string;
  -             description "fixed ip of the router interface port on this subnet";
  -         }
  -
  -         leaf vpn-id {
  -             type    yang:uuid;
  -             description "VPN to which this subnet belongs";
  -         }
  +
  +         uses subnet-vpn-info;
  +
  +         list chained-router {
  +             uses subnet-vpn-info;
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

  * There will be no impact for OVS switches.
  * There is significant data plan impact for OF switches that forward packets
    by taking them table by table.

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

Use Case 1:
-----------

The following are the CLI used to set up `Cross-router internal networks routing`_

1. Create NET1 and associated SUBNET1

.. code-block:: bash

   openstack network create NET1 --provider-network-type vxlan --provider-segment 1501
   openstack subnet create --network NET1 --subnet-range 10.100.1.0/24 --dns-nameserver 8.8.8.8 --allocation-pool start=10.100.1.5,end=10.100.1.100 SUBNET1

3. Launch VM1 on NET1

.. code-block:: bash

   openstack keypair create --public-key ~/.ssh/id_rsa.pub admin_key
   nova boot --poll --flavor m1.nano --image $(glance image-list | grep 'cirros' | awk '{print $2}' | tail -1) --nic net-id=$(openstack network list | grep -w NET1 | awk '{print $2}') VM1 --availability-zone=nova:control --key-name admin_key

3. Create NET1a and associated SUBNET1a

.. code-block:: bash

   openstack network create NET1a --provider-network-type vxlan --provider-segment 1502
   openstack subnet create --network NET1a --subnet-range 10.100.7.0/24 --dns-nameserver 8.8.8.8 --allocation-pool start=10.100.7.5,end=10.100.7.100 SUBNET1a

4. Launch VM1a on NET1a

.. code-block:: bash

   nova boot --poll --flavor m1.nano --image $(glance image-list | grep 'cirros' | awk '{print $2}' | tail -1) --nic net-id=$(openstack network list | grep -w NET1a | awk '{print $2}') VM1a --availability-zone=nova:control --key-name admin_key

5. Create ROUTER1 and attach SUBNET1 and SUBNET1a to ROUTER1

.. code-block:: bash

   openstack router create ROUTER1
   openstack router add subnet ROUTER1 SUBNET1
   openstack router add subnet ROUTER1 SUBNET1a

6. Create NET2 and associated SUBNET2

.. code-block:: bash

   openstack network create NET2 --provider-network-type vxlan --provider-segment 1502
   openstack subnet create --network NET2 --subnet-range 10.100.2.0/24 --dns-nameserver 8.8.8.8 --allocation-pool start=10.100.2.5,end=10.100.2.100 SUBNET2

7. Launch VM2 on NET2

.. code-block:: bash

   nova boot --poll --flavor m1.nano --image $(glance image-list | grep 'cirros' | awk '{print $2}' | tail -1) --nic net-id=$(openstack network list | grep -w NET2 | awk '{print $2}') VM2 --availability-zone=nova:control --key-name admin_key

8. Create ROUTER2 and attach SUBNET2 to ROUTER2

.. code-block:: bash

   openstack router create ROUTER2
   openstack router add subnet ROUTER2 SUBNET2

9. Create external network EXTNET1 and associated EXTSUBNET1

.. code-block:: bash

   openstack network create EXTNET1 --external --provider-physical-network physnet1 --provider-network-type flat
   openstack subnet create --network EXTNET1 --allocation-pool start=192.168.51.9,end=192.168.51.20 --no-dhcp --gateway 192.168.51.1 --subnet-range 192.168.51.0/24 EXTSUBNET1

10. Set EXTNET1 as gateway for ROUTER1

.. code-block:: bash

   openstack router set --external-gateway EXTNET1 ROUTER1

11. Attach SUBNET1 to ROUTER2. First we create a Neutron port PORT_SUB1_RT2 on SUBNET1 and then
    attach SUBNET1 to ROUTER2 via PORT_SUB1_RT2

.. code-block:: bash
  :emphasize-lines: 1-2

   openstack port create --fixed-ip subnet=SUBNET1,ip-address=10.100.1.3 --network NET1 PORT_SUB1_RT2
   openstack router add port ROUTER2 $(openstack port list | grep -w PORT_SUB1_RT2 | awk '{print $2}')

Use Case 2:
-----------

The following are the CLI used to set up `Cross-router external networks routing`_

1. Create NET1 and associated SUBNET1

.. code-block:: bash

   openstack network create NET1 --provider-network-type vxlan --provider-segment 1501
   openstack subnet create --network NET1 --subnet-range 10.100.1.0/24 --dns-nameserver 8.8.8.8 --allocation-pool start=10.100.1.5,end=10.100.1.100 SUBNET1

2. Launch VM1 on NET1

.. code-block:: bash

   openstack keypair create --public-key ~/.ssh/id_rsa.pub admin_key
   nova boot --poll --flavor m1.nano --image $(glance image-list | grep 'cirros' | awk '{print $2}' | tail -1) --nic net-id=$(openstack network list | grep -w NET1 | awk '{print $2}') VM1 --availability-zone=nova:control --key-name admin_key


3. Create external network EXTNET1 and associated EXTSUBNET1

.. code-block:: bash

   openstack network create EXTNET1 --external --provider-physical-network physnet1 --provider-network-type flat
   openstack subnet create --network EXTNET1 --allocation-pool start=192.168.51.9,end=192.168.51.20 --no-dhcp --gateway 192.168.51.1 --subnet-range 192.168.51.0/24 EXTSUBNET1

4. Create external network EXTNET2 and associated EXTSUBNET2

.. code-block:: bash

   openstack network create EXTNET2 --external --provider-physical-network physnet2 --provider-network-type flat
   openstack subnet create --network EXTNET2 --allocation-pool start=192.168.52.9,end=192.168.52.20 --no-dhcp --gateway 192.168.52.1 --subnet-range 192.168.52.0/24 EXTSUBNET2

6. Create ROUTER1 and attach SUBNET1 to ROUTER1

.. code-block:: bash

   openstack router create ROUTER1
   openstack router add subnet ROUTER1 SUBNET1

7. Set EXTNET1 as gateway for ROUTER1

.. code-block:: bash

   openstack router set --external-gateway EXTNET1 ROUTER1

8. Create ROUTER2 and set EXTNET2 as gateway for ROUTER2

.. code-block:: bash

   openstack router create ROUTER2
   openstack router set --external-gateway EXTNET1 ROUTER1

9. Attach SUBNET1 to ROUTER2. First we create a Neutron port PORT_SUB1_RT2 on SUBNET1 and then
   attach SUBNET1 to ROUTER2 via PORT_SUB1_RT2

.. code-block:: bash
  :emphasize-lines: 1-2

   openstack port create --fixed-ip subnet=SUBNET1,ip-address=10.100.1.3 --network NET1 PORT_SUB1_RT2
   openstack router add port ROUTER2 $(openstack port list | grep -w PORT_SUB1_RT2 | awk '{print $2}')

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

Unit tests related to chaining routers for subnet as above.

Integration Tests
-----------------
TBD

CSIT
----

CSIT specific testing will be done to check VMs connectivity with
chaining routers for single subnet solution:

Use Case 1
^^^^^^^^^^

1. Create network NET1
2. Create subnetwork SUBNET1 on NET1
3. Launch instance VM1 on NET1
4. Create network NET1a
5. Create subnetwork SUBNET1a
6. Launch instance VM1a on NET1a
7. Create router ROUTER1
8. Attach SUBNET1 and SUBNET1a to ROUTER1
   5.1 Verify VM1 and VM1a connectivity
   5.1 Verify VM1 and VM1a can communicate with each other
9. Create external network EXTNET1
10. Create external subnetwork EXTSUBNET1
11. Set EXTNET1 as gateway for ROUTER1
12. Create network NET2
13. Create subnetwork SUBNET2 on NET2
14. Launch VM2 on NET2
15. Create router Router2
16. Attach SUBNET2 on ROUTER2
17. Create Neutron port PORT_SUB1_RT2 on SUBNET1
18. Attach Neutron port PORT_SUB1_RT2 as interface to ROUTER2
    18.1 Verify VM1 and VM2 can communicate with each other
    18.2 Verify VM1 and VM1a still can communicate with each other
    18.3 Verify VM2 and VM1a can not communicate
    18.4 Verify VM1 and VM1a can access external network EXTNET1 and vice versa
    18.5 Verify VM2 can not access external network EXTNET1 and vice versa
19. Repeat steps 12-18 for chaining more routers to SUBNET2 and verify results
    similarly to step 18.1-18.5
20. Remove routers in reserse steps and verify the setup works with the
    remaining routers in the chain.
21. Clean up

Use Case 2
^^^^^^^^^^

1. Create network NET1
2. Create subnetwork SUBNET1 on NET1
3. Launch instance VM1 on NET1
4. Create router ROUTER1
5. Create external network EXTNET1
6. Create external subnetwork EXTSUBNET1 on EXTNET1
7. Set EXTNET1 as gateway for ROUTER1
8. Attach SUBNET1 to ROUTER1
    8.1 Verify SNAT from SUBNET1 to EXTNET1
    8.2 Add FIP for VM1, verify FIP communication from SUBNET1 to internet
9. Create Router ROUTER2
10. Create external network EXTNET2
11. Create external subnetwork EXTSUBNET2 on EXTNET2
12. Set EXTNET2 as gateway for ROUTER2
13. Create Neutron Port PORT_SUB1_RT2 on SUBNET1
14. Attach Neutron Port PORT_SUB1_RT2 as interface to Router ROUTER2
    14.1 Verify SNAT from SUBNET1 to EXTNET2
15. Repeat steps 9-14 for chaining more routers to SUBNET1 and verify results
    similarly to step 14.1
16. Unset EXTNET1 as gateway to ROUTER1
    16.1 Verify EXTNET2 becomes default router for SUBNET1, ie SNAT/FIP from
    SUBNET1 is possible via ROUTER2 and EXTNET2.
17. Remove routers in reserse steps and verify the setup works with the
    remaining routers in the chain.
18. Clean up

Documentation Impact
====================

Necessary documentation would be added if needed.

References
==========

