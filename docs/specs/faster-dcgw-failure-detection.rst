==============================
Faster DC-GW Failure Detection
==============================

https://git.opendaylight.org/gerrit/#/q/topic:faster-dcgw-failure-detection

In L3VPN, local and external routes are exchanged using BGP protocol with DC-Gwy. BGP control plane failure
detection is based on hold-timer and graceful-restart timer (which are based on underlying TCP).

Problem description
===================
DC-Gwy failure detection depends on underlay TCP settings.

Use Cases
---------
Redundant DC-Gwy(Data Center - Gateway) with ECMP enabled in DPN(Data Path Node): DC-Gwy failover detection using existing control plane mechanism takes ~24 seconds in OPNFV-clustered environment.

why ~24 seconds?

In a clustered setup, BGP-EoS owner node gets rebooted and the node happens to be pacemaker seed node:

- To detect node failure and synchronize ~10 Seconds
- seed node election ~5Seconds.
- Time taken to elect the next EoS owner : ~5Seconds
- Once the cluster is formed and ODL/BgpManager opens thrift port ( less than 1 second)
- Configuring qthrift/bgpd ~2Seconds.

Note: above timings are based on observations, MAXIMUM time taken in clustered OPNFV environment.
In summary : For a "ODL with qbgp in OPNFV(clustered) environment" to recover from a node reboot takes ~24 seconds.

BGP keepalive, hold timers are negotiated and select the least values between two neighbors is selected.

HOLD timer is used to purge the routes from DC-Gwy in case of neighbor down, it shall be configured beyond 24 seconds to avoid purging.

In a HA scenario, it is expected to switch the NEXT-HOP of a prefix to a available DC-Gwy. But, this was not achieved due to BGP HOLD timer value.

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
         ODL


Proposed change
===============

Proposal
--------
Enable Control Path monitoring using BFD (between ODL and DC-Gwy).

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
         ODL

Using publicly available BFD implementation,  BFD Daemon (BFDD) would be integrated with QBGP. In this approach, the BFDD it would make use of BFD messages to detect the health of the connection with the DC-Gwy.
Upon connection failure, the BFDD would inform QBGP about the failure of the DC-Gwy. The QBGP will terminate the neighborship and purge the routes which are received from the failed DC-Gwy. Route withdrawl from QBGP results in, removing routes from dataplane.

The QBGP will ONLY depend on the BFDD to know the health status of DC-Gwy. So long as the DC-Gwy is marked as down by BFDD, the QBGP will REJECT connections from the DC-Gwy and will stop sending BGP OPEN messages to the DC-Gwy.

Similarly, when BFD Daemon detects that the DC-Gwy is back online, it informs QBGP about the same. The QBGP would now start accepting BGP connections from the DC-Gwy. It will also send out BGP Open messages to the DC-Gwy.

Since BFD monitoring interval can be set to 300-500ms, it would be possible to achieve sub-second DC-Gwy failure detection with BFD based monitoring.

Since the failure detection mechanism does NOT USE HOLD TIME, the QBGP failure recovery will be independent of DC-Gwy failure detection.

The proposal makes use of BFD in the control plane to detect the failure of the DC-Gwy. The Control Path between QBGP and DC-Gwy BGP Daemon is monitored using BFD. Failure of the control plane is used to purge the corresponding routes in the data plane.

With ECMP, alternate routes are preprogrammed in the data plane. Consequently, when the routes received from the failed DC-Gwy are purged, the flows automatically take the alternate path to reach their destination.

Below parameters are required for configuring BFD:

- Desired Min TX Interval: The QBGP must program this value to be equal to 1/3rd of the HOLD TIME value configured by default. By default, this value would be 60 seconds. The solution will provide a method to configure this value from the thrift interface.
- Required Min RX Interval: This would be configured to the value configured in bfdRxInterval
- bfdFailureDetectionThreshold: The bfdFailureDetectionThreshold will be used by the BFD implementation to identify the failure. When the number of lost packets exceed bfdFailureDetectionThreshold, the BFD protocol detects failure of the neighbour.
- bfdDebounceDown:  This indicates the amount of time BFDD must wait to inform the QBGP about DC-Gwy failure. When BFDD detects DC-Gwy failure, it starts a timer with the value configured in bfdDebounceDown microseconds. Upon the expiry of the timer, the latest BFD state is checked. If the latest BFD state still indicates DC-Gwy failure, then the corresponding failure is reported to QBGP. If the latest BFD state indicates that DC-Gwy is restored, no message is sent to QBGP.
- bfdDebounceUp :This indicates the amount of time BFDD must wait to inform the QBGP about DC-Gwy Restoration. When BFDD detects DC-Gwy Restoration, it starts a timer with the value configured in bfdDebounceUp microseconds. Upon the expiry of the timer, the latest BFD state is checked. If the latest BFD indicates DC-Gwy restoration, then the corresponding restoration is reported to QBGP. If the latest BFD state indicates DC-Gwy failure, no message is sent to QBGP.


Pipeline changes
----------------
None

Yang changes
------------
Changes will be needed in ``aliveness-monitor.yang``.

A new parameter ``success-threshold`` will be added to ``monitor-profile-params`` in aliveness-monitor.yang

.. code-block:: none
   :caption: aliveness-monitor.yang
   (optional) : leaf success-threshold { type uint32; } //Number N of missing messages in window to detect failure.

   container bfd-monitor-config {
        config true;
        uses monitor-profile-params;
   }


Configuration impact
---------------------
New BFD configuration parameters will be added with this feature.

enable-bfd(default: true)
min-rx (default: 500ms)
monitor-window (default: 3)
min-tx (default: 60 sec)
failure-threshold (default: 100ms)
success-threshold (default: 5 sec)
AssociateTEPDCGW([tep-ip], DC-Gwy):

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
         ODL

This was not being implemented, as most of the DC-gwy's do not support BFD monitoring on MPLS/GRE tunnels.

Usage
=====
As described in diagram, this feature is mainly to "switchover traffic to surviving DC-Gwy, in case of a DC-Gwy failure" and to reduce impact on Data Path.

Features to Install
-------------------
odl-netvirt-openstack
package : qthrift (with bfdd, bgpd)


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
- DC-Gwy: MUST support BFD monitoring of the BGP control plane
- genius: yang changes in aliveness monitor

Any dependencies being added/removed? Dependencies here refers to internal
[other ODL projects] as well as external [OVS, karaf, JDK etc.] This should
also capture specific versions if any of these dependencies.
e.g. OVS version, Linux kernel version, JDK etc.

This should also capture impacts on existing project that depend on Netvirt.


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

