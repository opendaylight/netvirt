.. contents:: Table of Contents
         :depth: 3

========================================
Subnet Routing for hidden IPv6 addresses
========================================

https://git.opendaylight.org/gerrit/#/q/topic:subnet-routing-for-hidden-ipv6

This spec addresses Subnet Routing feature for hidden IPv6 traffic wherein ODL would learn the
hidden/invisible IPv6 addresses and set up forwarding rules for both intra-DC and inter-DC
communication.


Problem description
===================

Many Virtual network functions (VNFs) use IP addresses that are configured out of band
(i.e., outside of Neutron) within neutron subnet (i.e., it cannot be a non-neutron subnet). To
discover such IPs and provide L3 routing and L3VPN based forwarding for these hidden IPs, Subnet
Route feature is required. Currently, ODL supports subnet routing feature for hidden IPv4 only.
This feature extends the solution for hidden IPv6 addresses. Only the IP Addresses that fall in
neutron subnet CIDR range will be discovered.

Use Cases
---------

a. A hidden IPv6 address in a valid Neutron IPv6 subnet must be discoverable when traffic is
   initiated to that address from a source external to the DC (i.e, from an IPv6 address behind the
   DC-GW). The traffic must succeed to that discovered IPv6 address from the same external source
   after initial loss of few packets (~10).

b. A hidden IPv6 address in a valid Neutron IPv6 subnet must be discoverable when traffic is
   initiated to that address from a source internal to the DC (i.e., some other VM on a subnet
   routable towards this IPv6 subnet). The traffic must succeed to that discovered IPv6 address
   from the same internal routable source after initial loss of few packets (~10).

c. If the hidden IPv6 address is a globally unique IPv6 address and its in a valid Neutron IPv6
   subnet which is on an external network, then such address must be discoverable when traffic is
   initiated to that address from internet to the DC (from internet IPv6 address towards this IPv6
   subnet). The traffic must succeed to that discovered IPv6 address from the internet after
   initial loss of few packets (~10).

Proposed change
===============

Subnet routing for hidden IPv6 addresses
----------------------------------------

Background: Subnet routing for hidden IPv4 addresses
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Hidden v4 IPs were learnt via two ways:

* The IPv4 addresses were learnt when the VMs sent out GARP messages.
* When a user within or outside the subnet or DC-GW attempts to communicate with a Hidden IP,
    * The message was received by the ODL.
    * The ODL would identify the host hosting the hidden IP.
    * Forwarding rules were setup to establish communication between the user and hidden IP.

Proposal: Subnet routing for hidden IPv6 addresses
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

In general, all the use cases like L2, L3 routing, L3VPN forwarding applicable to IPv4 is also
applicable to IPv6 (except NAT) and hence needs to be supported. This spec highlights only the
difference which will be handled for IPv6 specifically.

Similar to IPv4, even IPv6 addresses will be learnt via two ways:

* The IPv6 addresses will be learnt when the VMs sends out unsolicited Neighbor Advertisement (NA)
  messages.
* When a user within or outside the subnet or DC-GW attempts to communicate with a Hidden IP,
    * The message will be received by the ODL.
    * The ODL would identify the host hosting the hidden IP by initiating Neighbor Solicitation (NS)
      message. Learn IPv6 addresses via NA response.
    * Forwarding rules (FIB entries) will be configured to establish communication between the user
      and hidden IP.

Currently, subnet routing for hidden IPv4 addresses works only when router is associated with
BGPVPN. As part of this spec, it will be enhanced (for both IPv4 and IPv6) to support even plain
router use cases (i.e., with just subnets associated to router) without BGPVPN configurations.

It will be implemented in two phases:

**Phase-I:** Adhering to the current design of IPv4, subnet routing for hidden IPv6 addresses will
be supported only when router is associated to BGPVPN.

**Phase-II:** Subnet routing for both IPv4 and IPv6 will be supported even for plain router use
cases (i.e., with just subnets associated to router) without BGPVPN configurations.

Details:
^^^^^^^^

It is possible that some hidden IPs may not be discovered by Neighbor Advertisements.

Example:

* VM may not send unsolicited neighbor advertisements.
* There may not be intra-Subnet traffic to the hidden IP.
* The very first packet arriving to the hidden IP might be from.
    * A different Subnet attached to the Same router OR.
    * A user outside the DC.

The ODL will not know where to direct the packet since it has not learnt the IP.

For each Router and for each subnet attached to the Router, the L3VPN service

* Will identify a **Designated DPN** to attract traffic for that Subnet. A DPN is eligible for
  becoming a **Designated DPN** only if there is atleast one active VM IPv6 port on the subnet on
  that DPN (wherein this subnet also hosts the hidden IPv6 address).
* The subnet route will be matched only when there are no /128 routes matching the dest IP of the
  packet.
* Traffic matching the **subnet route** entry will be punted to the controller.
* Controller sends out a NS message to the DestIP in the corresponding Subnet.
* The NS message is broadcasted in the ELAN and eventually reaches the VM hosting the IP.
* The VM responds back with a neighbor advertisement message, which is punted to the controller.
* Controller learns location of the IPv6 address by reading the metadata which contains lport tag
  of neutron port. Forms FIB entry with the NH corresponding to neutron port, programs it in the
  FIB table.
* FIB programming triggers DC-GW advertisement wherein it advertises routes to BGP neighbor.
* FIB programming also results in programming the remote flow rules in all the other DPNs that have
  the same VPN footprint.

Criteria for learning Hidden IPv6 addresses
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* The hidden IPs will be learnt by ODL ONLY when the subnet is attached to a router. It MUST NOT be
  learning hidden IPs belonging to a subnet that is NOT associated with any router.
* Hidden IPs will be in the same subnet as the Neutron Subnet configured by OpenStack.
* It must be possible to learn the hidden IPs from both IPv4 and IPv6 subnets to which the same
  port can be associated (dual stack VMs).
* It must be possible to communicate to the Hidden IP from the same subnet and from another subnet
  attached to the same router.
* A single VM could have multiple vNICs configured and each vNIC could be associated with a
  different router (or VPN). The Hidden IPs will be configured on the interface of the VM and
  the VM can have multiple Hidden IPs in each subnet. It must be possible for ODL to learn the
  hidden IPs such that Hidden IPs are learnt in the corresponding subnet. There are no leaks from
  one VPN into another.
* It must be possible for the Hidden IPs to move across VNF instances within the same subnet. ODL
  must be capable of determining the correct owner of the hidden IP and forward the frames
  accordingly.

High Availability of Subnet Routing Service
-------------------------------------------

OVS Failure: DPN DISCONNECT
^^^^^^^^^^^^^^^^^^^^^^^^^^^

When the DPN (OVS) disconnects, the VPN service

* Must identify whether there are any Subnet routes with the TEP IP of the OVS as the next hop.
* For each such subnet route
    * The VPN service will withdraw the route from the DC-GW.
    * The VPN service will find an alternate designated DPN for the Subnet
        * If no such OVS exists, then the action is DEFERRED until such an OVS becomes available.
    * The subnet route is re-advertised with the Next-Hop (NH) set to the TEP IP of the alternate OVS.
    * The subnet route is reprogrammed on ALL OVSes (with the VPN footprint) to direct the traffic
      to the alternate OVS.
    * The discovered hidden IP routes are NOT withdrawn.

OVS Failure: DPN CONNECT
^^^^^^^^^^^^^^^^^^^^^^^^

When the DPN (OVS) connects, the VPN service

* Must identify whether there are any DEFERRED Subnet routes.
* For each such subnet route
    * The VPN service will check if the connected OVS can become the designated DPN.
    * This is possible if the connected OVS is a OVS that has at least one VM in the Subnet.
    * If the OVS could be a designated DPN for the subnet, then a Subnet route is advertised to the
      DC-GW with the NH set to the TEP IP of the connected OVS.
    * The OVS is programmed with a Flow rule matching the Subnet with an action to punt the packets
      to the controller.

TEP (Tunnel End-Point) Failures: TEP DELETE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Whenever a TEP is deleted,

* The VPN service will identify
    * The set of subnets for which the OVS was a designated DPN.
    * The set of Hidden IPs hosted in the VMs connected to the affected OVS.
    * The set of Neutron Port IPs attached to the affected OVS.
* The VPN service will immediately withdraw the Neutron Port IPs and Hidden IPs identified.
* For each subnet identified, the actions in `OVS Failure: DPN DISCONNECT`_ are triggered.

TEP Failures: TEP ADD
^^^^^^^^^^^^^^^^^^^^^

Whenever a TEP is added,

* The VPN service will identify
    * The set of deferred Subnet Routes.
    * The set of Neutron Port IPs attached to the affected OVS.
* The VPN service will immediately advertise the Neutron Port IPs to the DC-GW.
* For each subnet identified, the actions described in `OVS Failure: DPN CONNECT`_ are triggered.

Movement of Hidden IP
---------------------

Learning IPv6 addresses
^^^^^^^^^^^^^^^^^^^^^^^

When the IPv6 hidden IP moves between hosts, the information with the ODL becomes invalid.
To recover from this error, the ODL makes use of Unsolicited NA (UNA) message

* When the Hidden IP moves, it is possible that the VM sends out UNA message.
* Punting the NA message to the controller, the ODL will identify that the location of the hidden
  IP.
* IPv6 address has changed and ODL can inform the DC-GW accordingly.


Last VM on a VPN removed from the designated DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When the last VM on a VPN removed from the designated DPN, a new DPN having VM presence for the subnet
needs to be elected as the designated DPN which will anchor the subnet routing.

Limiting Flow Cache
^^^^^^^^^^^^^^^^^^^

For every Hidden IP discovered, the VPN Service will maintain a FLOW VALID timer

* The timer value will be global.
* The timer value is configurable via configuration files.
* The default value of the timer should be 2 minutes.

When the timer expires, the VPN Service

* Sends out a Unicast NS message to the VM that is hosting the Hidden IP.
* Starts a ND_MESSAGE_SENT timer.
* The ND_MESSAGE_SENT timer value will be global and configurable via configuration files.
* The default value of the timer should be 30 sec.

If the VPN Service receives a NA message as response before ND_MESSAGE_SENT expires

* The VPN Service restarts the FLOW_VALID timer.

If the ND_MESSAGE_SENT timer expires

* The NS Message is sent again.

If the response is NOT received for the second message as well,

* The VPN Service withdraws the affected Hidden IP from the DC-GW.
* The VPN Service removes the affected Hidden IP from the FIB.
* The VPN Service removes the flow entries that correspond to the affected Hidden IP from all OVSes.

Pipeline changes
----------------

* When a user outside the subnet or DC-GW attempts to communicate with a Hidden IP. If there is no
  match in FIB for this hidden IP (i.e., the hidden IP is unknown so far), then the packets needs
  to be punted to the controller. So that the controller could identify the host hosting the hidden
  IP by initiating Neighbor Solicitation (NS) message then learn IPv6 addresses via NA response.

  Currently, VPN service programs FIB entries in L3 FIB table (21) for both IPv4 and IPv6 subnets
  (e.g., match on nw_dst=10.0.0.0/24 or ipv6_dst=1001:db8:0:2::/64) only when router is associated
  with BGPVPN. But actually it needs be programmed even when just subnet is associated with a
  router to support intra-DC traffic for hidden IPs across subnets. These flows matching on subnet
  forward packets from FIB table (21) to subnet route table (22).

  e.g.:

  .. code-block:: bash

     cookie=0x8000003, duration=350.898s, table=21, n_packets=0, n_bytes=0, priority=74,ipv6,metadata=0x30d70/0xfffffe,ipv6_dst=1001:db8:0:2::/64 actions=write_metadata:0x138c030d70/0xfffffffffe,goto_table:22
     cookie=0x8000003, duration=350.898s, table=21, n_packets=0, n_bytes=0, priority=74,ipv6,metadata=0x30d70/0xfffffe,ipv6_dst=2001:db8:0:2::/64 actions=write_metadata:0x138d030d70/0xfffffffffe,goto_table:22

  In order to punt packets to the controller, there is no need of additional flow as it already
  exists in L3 subnet route table (22) as below.

  .. code-block:: bash

     cookie=0x8000004, duration=12731.641s, table=22, n_packets=0, n_bytes=0, priority=0 actions=CONTROLLER:65535

* Flow needs to be programmed in IPv6 table (45) for punting Neighbor Advertisements (NA) to the
  controller and forward the packet further in the pipeline as well. These NA packets are used
  for learning the hidden IPs.

  Only NAs from Global Unicast Address (GUA) IPv6 addresses excluding from neutron port Fixed IPs
  and Link Local Address (LLA)'s will be punted to controller.

  In order to exclude NAs from neutron port Fixed IPs being punted to controller, one flow per
  fixed GUA IPv6 address will be programmed in IPv6 table (45) which resubmits to dispatcher
  table (17).
  e.g.:

  .. code-block:: bash

     cookie=0x4000000, duration=382.556s, table=45, n_packets=1, n_bytes=70, priority=50,icmp6,metadata=0x138b000000/0xffff000000,icmp_type=136,icmp_code=0,ipv6_src=1001:db8:0:2:f816:3eff:feb4:aaaa actions=resubmit(,17)
     cookie=0x4000000, duration=382.556s, table=45, n_packets=1, n_bytes=70, priority=50,icmp6,metadata=0x138b000000/0xffff000000,icmp_type=136,icmp_code=0,ipv6_src=1001:db8:0:2:f816:3eff:feb4:bbbb actions=resubmit(,17)

  Lower priority flows (e.g., priority=40) matching on subnet CIDR will be programmed to punt NA
  packets to controller.
  e.g.:

  .. code-block:: bash

     cookie=0x4000000, duration=382.556s, table=45, n_packets=1, n_bytes=70, priority=40,icmp6,metadata=0x138a000000/0xffff000000,icmp_type=136,icmp_code=0,ipv6_src=1001:db8:0:2::/64 actions=CONTROLLER:65535,resubmit(,17)
     cookie=0x4000000, duration=382.556s, table=45, n_packets=1, n_bytes=70, priority=40,icmp6,metadata=0x138b000000/0xffff000000,icmp_type=136,icmp_code=0,ipv6_src=2001:db8:0:2::/64 actions=CONTROLLER:65535,resubmit(,17)

* The learnt hidden IPv6 addresses will be programmed in FIB table.
  e.g.:

  .. code-block:: bash

     cookie=0x8000003, duration=20.092s, table=21, n_packets=0, n_bytes=0, priority=138,ipv6,metadata=0x30d52/0xfffffe,ipv6_dst=1001:db8:0:2:f816:3eff:feb4:deff actions=group:150003
     cookie=0x8000003, duration=5.313s, table=21, n_packets=0, n_bytes=0, priority=138,ipv6,metadata=0x30d52/0xfffffe,ipv6_dst=2001:db8:0:2:f816:3eff:fe13:d202 actions=group:150005


Out-of-scope
------------

Subnet Routing feature was made to work for FLAT/VLAN external networks for IPv4 addresses via the
PNF (Physical Network Functions) feature. This spec doesn't claim to support IPv6 Subnet Routing
feature for FLAT/VLAN external networks and is out-of-scope.

Yang changes
------------

ipv6-ndutil.yang needs to be updated with new RPC to support sending Neighbor Solicitation packet
to an OpenFlow group.

::

    rpc send-neighbor-solicitation-to-of-group {
        input {
            leaf source-ipv6 {
                type inet:ipv6-address;
                mandatory "true";
            }
            leaf target-ip-address {
                type inet:ipv6-address;
                mandatory "true";
            }
            leaf source-ll-address {
                type yang:mac-address;
                mandatory "true";
            }
            leaf dp-id {
                type uint64;
                mandatory "true";
            }
            leaf of-group-id {
                type uint32;
                mandatory "true";
                description "NS will be sent to the specified OpenFlow group ID.";
            }
        }
    }

Limitations
-----------

Since the Hidden IPs and Neutron IPs are from the same subnet, there would be coordination required
to ensure that the IP spaces do not clash.

    * This coordination is assumed to be manual and is out of scope of this spec.
    * Specifically, ODL will not build/deploy any intelligence to identify IP address clash or
      recover from it.

Configuration impact
--------------------
None

Clustering considerations
-------------------------
None

Other Infra considerations
--------------------------
None

Security considerations
-----------------------
None

Scale and Performance Impact
----------------------------
None

Targeted Release
-----------------
Fluorine

Alternatives
------------
The solution is about auto-discovery of hidden v6 IPs and provide L3 routing and L3VPN based
forwarding for hidden v6 IPs. Alternatively, L3 routing and L3VPN based forwarding for hidden IPs
can be achieved by manual configuration of extra/static routes.

Usage
=====

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
No new REST API being added.

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
  Karthikeyan K <karthikeyan.k@altencalsoftlabs.com>

  Nithi Thomas <nithi.t@altencalsoftlabs.com>

Work Items
----------
#. Program IPv6 table (45) with flows to punt NA packets to controller.
#. Handle punting IPv6 traffic to controller for unknown hidden IPv6 addresses if subnet is associated to router.
#. Learning hidden IPv6 addresses, program FIB and advertise routes if external VPN is configured.
#. Subnet routing un-programming for hidden IPv6 addresses.
#. Handle OVS disconnect/connect impact on subnet routing for hidden IPv6 addresses.
#. Handle TEP add/delete impact on subnet routing for hidden IPv6 addresses.
#. Discover movement of hidden IPv6 addresses.
#. Limiting flow cache with a flow valid timer.

Dependencies
============


Testing
=======

This feature builds on the Subnet routing feature for IPv4. It must be ensured that the feature
will not break any IPv4 subnet features. The test cases for this feature must cover dual-stack and
single-stack VMs and test the subnet route feature for both IPv4 and IPv6 subnets.

* Some VMs must have only IPv4 addresses
* Some VMs must have only IPv6 addresses
* Some VMs must have both IPv4 and IPv6 addresses

Test cases below:

#. Verify traffic between DC-GW and hidden IPv6 address in a L3VPN.
#. Verify traffic to ensure there is no leak between DC-GW and hidden IPv6 address across L3VPNs.
#. Verify traffic between hidden IPv6 address on network1 and VM2 on network2.
#. Verify traffic between hidden IPv6 address to hidden IPv6 address across different networks.
#. Verify traffic between hidden IPv6 address and VM1 on the same network.
#. Verify traffic between hidden IPv6 address to hidden IPv6 address in same network.
#. Verify traffic when hidden IPv6 address is moved between VMs on same DPN.
#. Verify traffic when hidden IPv6 address is moved between VMs on different DPN.
#. Verify traffic when designated DPN for a subnet is disconnected/connected.
#. Verify traffic when TEPs in designated DPN for a subnet is deleted/added.

Unit Tests
----------

Integration Tests
-----------------

CSIT
----

A subset (which can be done using CSIT framework) of test cases mentioned in `Testing`_ needs to be
added.

Documentation Impact
====================

References
==========

