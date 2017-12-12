==============================
Faster DC-GW Failure Detection
==============================

[link to gerrit patch]

In L3VPN, local and external routes are exchanged using BGP protocol with DC-Gwy. BGP control plane failure
detection is based on keepalive and hold-down timers.

Problem description
===================
External network data path disconnection observed even with redundant DC-Gwy.

Use Cases
---------
Redundant DC-Gwy with ECMP enabled CSS: DC-Gwy failover detection using existing control plane
mechanism takes ~24 seconds in CEE environment.

why ~24 seconds?

In a clustered CEE, EoS owner node gets rebooted and it happens to be pacemaker seed node:

- corosync: to detect node failure ~10 Seconds
- Pacemaker seed node election ~5Seconds.
- Time taken to elect the next EoS owner : ~5Seconds
- Once the cluster is formed and ODL/BgpManager opens thrift port ( less than 1 second)
- Configuring qthrift/bgpd ~2Seconds.

Note: above timings are based on observations, MAXIMUM time taken in CEE environment.
In summary : For a CSC to recover from a node reboot takes ~24 seconds.

BGP keep alive, hold down timers are negotiated and select the least values between two neighbors.

Hold Down Timer shall be configured as 24+ seconds in order to avoid purging routes (assuming TCP FIN message is not sent while cluster node goes down). HOLD-Down timer is used to purge the routes from DC-Gwy in case of fail-over.

In a HA scenario, it is expected to switch the NEXT-HOP of a prefix to a available DC-Gwy. But, this was not achieved due to BGP HOLD-Down timer value.


+--------------------------+                +----------------+
|   +------------------+   |     BGP        |   DC-Gwy 1     |
|   |                  |   +----------------+                |
|   |                  |   |                +-------+------+-+  +------------------+
|   |   Qthrift/bgpd   |   |      BGP               |      |    |    DC-Gwy 2      |
|   |                  |   +------------------------------------+                  |
|   |                  |   |                        |      |    +------+-------+---+
|   +--------+---------+   |                        |      |           |       |
|            |             |                    MPLS over GRE          |       |
|            |             |                        |      |           |       |
|            |             |                        |    +-+------+----+--+    |MPLS over GRE
|            |             |                        |    |    CSS - 1     |    |
|   +--------+---------+   +-----------------------------+                |    |
|   |       ODL        |   |                        |    +----------------+    |
|   +-----------+------+   |                        |                          |
|   |node| node | node |   |                     +--+----------+---------------+---+
|   |  1 |   2  |   3  |   |                     |         CSS - 2                 |
|   +----+------+------+   +---------------------+                                 |
|                          |                     +---------------------------------+
+--------------------------+
         CSC


Proposed change
===============

Proposal
--------
Enable tunnel monitoring in Control Path using BFD (between CSC and DC-Gwy).

+--------------------------+                +----------------+
|   +------------------+   |   BGP, BFD     |   DC-Gwy 1     |
|   |                  |   +----------------+                |
|   |   Qthrift        |   |                +-------+------+-+  +------------------+
|   |   bgpd           |   |    BGP, BFD            |      |    |    DC-Gwy 2      |
|   |   **bfd**        |   +------------------------------------+                  |
|   |                  |   |                        |      |    +------+-------+---+
|   +--------+---------+   |                        |      |           |       |
|            |             |                    MPLS over GRE          |       |
|            |             |                        |      |           |       |
|            |             |                        |    +-+------+----+--+    |MPLS over GRE
|            |             |                        |    |    CSS - 1     |    |
|   +--------+---------+   +-----------------------------+    (ECMP)      |    |
|   |       ODL        |   |                        |    +----------------+    |
|   +-----------+------+   |                        |                          |
|   |node| node | node |   |                     +--+----------+---------------+---+
|   |  1 |   2  |   3  |   |                     |            CSS - 2              |
|   +----+------+------+   +---------------------+            (ECMP)               |
|                          |                     +---------------------------------+
+--------------------------+
         CSC

Using publicly available BFD implementation,  BFD Daemon (BFDD) would be integrated with QBGP. In this approach, the BFDD it would make use of BFD messages to detect the health of the connection with the DC-GW.
Upon connection failure, the BFDD would inform QBGP about the failure of the DC-GW. The QBGP will terminate the neighborship and purge the routes from the failed DC-GW. The QBGP will depend ONLY on the BFDD to know the health status of DC-GW. So long as the DC-GW is marked as down by BFDD, the QBGP will REJECT connections from the DC-GW and will stop sending BGP OPEN messages to the DC-GW.

Similarly, when BFD Daemon detects that the DC-GW is back online, it informs QBGP about the same. The QBGP would now start accepting BGP connections from the DC-GW. It will also send out BGP Open messages to the DC-GW.

Since BFD monitoring interval can be set to 300-500ms, it would be possible to achieve sub-second DC-GW failure detection with BFD based monitoring.

Since the failure detection mechanism does NOT USE HOLD TIME, the QBGP failure recovery will be independent of DC-GW failure detection.

The proposal makes use of BFD in the control plane to detect the failure of the DC-GW. The Control Path between QBGP and DC-GW BGP Daemon is monitored using BFD. Failure of the control plane is used to purge the corresponding routes in the data plane.

With ECMP, alternate routes are preprogrammed in the data plane. Consequently, when the routes received from the failed DC-GW are purged, the flows automatically take the alternate path to reach their destination.

Below parameters are required for configuring BFD:

- Desired Min TX Interval: The QBGP must program this value to be equal to 1/3rd of the HOLD TIME value configured by default. By default, this value would be 60 seconds. The solution will provide a method to configure this value from the thrift interface.
- Required Min RX Interval: This would be configured to the value configured in bfdRxInterval
- bfdFailureDetectionThreshold: The bfdFailureDetectionThreshold will be used by the BFD implementation to identify the failure. When the number of lost packets exceed bfdFailureDetectionThreshold, the BFD protocol detects failure of the neighbour.
- bfdDebounceDown:  This indicates the amount of time BFDD must wait to inform the QBGP about DC-GW failure. When BFDD detects DC-GW failure, it starts a timer with the value configured in bfdDebounceDown microseconds. Upon the expiry of the timer, the latest BFD state is checked. If the latest BFD state still indicates DC-GW failure, then the corresponding failure is reported to QBGP. If the latest BFD state indicates that DC-GW is restored, no message is sent to QBGP.
- bfdDebounceUp :This indicates the amount of time BFDD must wait to inform the QBGP about DC-GW Restoration. When BFDD detects DC-GW Restoration, it starts a timer with the value configured in bfdDebounceUp microseconds. Upon the expiry of the timer, the latest BFD state is checked. If the latest BFD indicates DC-GW restoration, then the corresponding restoration is reported to QBGP. If the latest BFD state indicates DC-GW failure, no message is sent to QBGP.


Pipeline changes
----------------
None

Yang changes
------------
Changes will be needed in ``aliveness-monitor.yang``.

A new parameter ``success-threshold`` will be added to ``monitor-profile-params`` in aliveness-monitor.yang

.. code-block:: none
   :caption: aliveness-monitor.yang
   leaf success-threshold { type uint32; } //Number N of missing messages in window to detect failure ("N out of M")


Configuration impact
---------------------
New BFD configuration parameters will be added with this feature.

enable-bfd(default: true)
min-rx (default: 500ms)
monitor-window (default: 3)
min-tx (default: 60 sec)
failure-threshold (default: 100ms)
success-threshold (default: 5 sec)
AssociateTEPDCGW([tep-ip], DC-GW):

How will it impact existing deployments?
There is NO impact on existing deployments.

Note that outright deletion/modification of existing configuration
is not allowed due to backward compatibility. They can only be deprecated
and deleted in later release(s).

Clustering considerations
-------------------------
There is no impact on clustering, as the bfdd process supposed to run on only one node.

Other Infra considerations
--------------------------

Security considerations
-----------------------
Document any security related issues impacted by this feature.

Scale and Performance Impact
----------------------------
What are the potential scale and performance impacts of this change?
- There shall be no impact on performance.
Does it help improve scale and performance or make it worse?
- There shall be no impact on performance.

Targeted Release
-----------------
What release is this feature targeted for?
Oxygen/Fluorine.

Alternatives
------------

Enable tunnel monitoring in Data Path using BFD (between CSS and DC-Gwy).
+--------------------------+                +----------------+
|   +------------------+   |   BGP          |   DC-Gwy 1     |
|   |                  |   +----------------+                |
|   |   Qthrift        |   |                +-------+------+-+  +------------------+
|   |   bgpd           |   |    BGP                 |      |    |    DC-Gwy 2      |
|   |                  |   +------------------------------------+                  |
|   |                  |   |                        |      |    +------+-------+---+
|   +--------+---------+   |                        |      |           |       |
|            |             |                    MPLS over GRE          |       |
|            |             |                    BFD |      |           |       |
|            |             |                        |    +-+------+----+--+    |MPLS over GRE
|            |             |                        |    |    CSS - 1     |    |BFD
|   +--------+---------+   +-----------------------------+   BFD          |    |
|   |       ODL        |   |                        |    +----------------+    |
|   +-----------+------+   |                        |                          |
|   |node| node | node |   |                     +--+----------+---------------+---+
|   |  1 |   2  |   3  |   |                     |         CSS - 2                 |
|   +----+------+------+   +---------------------+                BFD              |
|                          |                     +---------------------------------+
+--------------------------+
         CSC

This was not being implemented, as most of the DC-gwy's do not support BFD monitoring on MPLS/GRE tunnels.

Usage
=====
As described in diagram, this feature is mainly to "Fast DC-Gwy failure" and to reduce impact on Data Path.

Features to Install
-------------------
odl-netvirt-openstack
additional process : bfdd


REST API
--------
will be added, when we start with implementation.

CLI
---
Yes, new CLI to configure bfdd (along with REST).


Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a
primary assigne and other contributors.

Primary assignee:
  Ashvin Lakshmikantha
  Siva Kumar Perumalla

Other contributors:
  Siva Kumar Perumalla
  Shankar M


Work Items
----------
Will be added before start of implementation.


Dependencies
============
- MUST support BFD monitoring of the BGP control plane

Any dependencies being added/removed? Dependencies here refers to internal
[other ODL projects] as well as external [OVS, karaf, JDK etc.] This should
also capture specific versions if any of these dependencies.
e.g. OVS version, Linux kernel version, JDK etc.

This should also capture impacts on existing project that depend on Netvirt.

Following projects currently depend on Netvirt:
 Unimgr

Testing
=======
Capture details of testing that will need to be added.

Unit Tests
----------

Integration Testsbgp
-----------------

CSIT
----

Documentation Impact
====================
Yes, Documentation impact is there. Contributors to documentation <Ashvin Lakshmikantha, Siva Kumar Perumalla>

References
==========
none.

