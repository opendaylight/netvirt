
==============================
Faster DC-GW Failure Detection
==============================

https://git.opendaylight.org/gerrit/#/q/topic:faster-dcgw-failure-detection

In L3VPN, local and external routes are exchanged using BGP protocol with
DC-Gwy (Data Center Gateway). BGP control plane failure detection is based
on hold-timer. BGP hold-timer is negotiated to be the lowest value between
two neighbors. Once BGP hold-timer expires, peer can remove the routes received
from its neighbor. Minimum value of hold-timer can be 3 seconds.

In some deployment scenarios like inter-DC (Data Center), BGP-VPN(internet
connectivity), ...  ODL will have Quagga BGP stack (qbgp). This qbgp will form
neighborship with DC-Gwy and exchange routes based on 'route descriptor', 'route
targets'. This will enable communication between "Hosts/VM's in DC" to external
connectivity.

Problem description
===================
In some existing deployment scenarios "OPNFV clustered environment", BGP
neighborship establishment with DC-Gwy takes ~24 Seconds. Expectation to form
neighborship is within 3-5 Seconds.

Use Cases
---------
Redundant DC-Gwy with ECMP enabled in DPN(Data Path Node): DC-Gwy failover
detection using existing control plane mechanism takes ~24 seconds in
OPNFV-clustered environment.

why ~24 seconds?

In a clustered setup, BGP-EoS owner node gets rebooted and the node happens
to be pacemaker seed node:

- To detect node failure and synchronize ~10 Seconds
- seed node election ~5Seconds.
- Time taken to elect the next EoS owner : ~5Seconds
- Once the cluster is formed and ODL/BgpManager opens thrift port (< 1 second)
- Configuring qthrift/bgpd ~2Seconds.

Note: above timings are based on observations, MAXIMUM time taken in clustered
OPNFV environment.
In summary : For a "ODL with qbgp in OPNFV(clustered) environment" to recover
from a node reboot takes ~24 seconds.
BGP keepalive, hold timers are negotiated and the least values between two
neighbors is selected.

HOLD timer is used to purge the routes from DC-Gwy in case of neighbor down,
it shall be configured beyond 24 seconds to avoid purging.

In a HA scenario of DC-Gwy, it is expected to switch the NEXT-HOP of a prefix
to a available DC-Gwy. But, this was not achieved due to BGP HOLD timer value.

Setup Representation
====================

    ::

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

Setup Representation
====================

    ::

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

Using publicly available BFD implementation,  BFD Daemon (BFDD) would be
integrated with QBGP. In this approach, the BFDD would make use of BFD messages
to detect the health of the connection with the DC-Gwy.

Upon connection failure, the BFDD would inform QBGP about the failure of the
DC-Gwy. QBGP will terminate the neighborship and purge the routes received
from failed DC-Gwy. Route withdrawl from QBGP results in, removing routes
from dataplane.

BFDD =(1)=> BGPD =(2)=> ODL
 (1) BFD tells neighbor DC-Gwy1 is lost
 (2) BGP sends peer-down notification to ODL
 (3) BGP withdraw prefixes learned from DC-Gwy1

The QBGP will ONLY depend on the BFDD to know the health status of DC-Gwy.
So long as the DC-Gwy is marked as down by BFDD, the QBGP will REJECT connections
from the DC-Gwy and will stop sending BGP OPEN messages to the DC-Gwy.

Similarly, when BFD Daemon detects that the DC-Gwy is back online, it informs
QBGP about the same. The QBGP would now start accepting BGP connections from
the DC-Gwy. It will also send out BGP Open messages to the DC-Gwy.

Since BFD monitoring interval can be set to 300-500ms, it would be possible
to achieve sub-second DC-Gwy failure detection with BFD based monitoring.

Since the failure detection mechanism does NOT USE HOLD TIME, the QBGP failure
recovery will be independent of DC-Gwy failure detection.

The proposal makes use of BFD in the control plane to detect the failure of
the DC-Gwy. The Control Path between QBGP and DC-Gwy BGP Daemon is monitored
using BFD. Failure of the control plane is used to purge the corresponding
routes in the data plane.

With ECMP, alternate routes are preprogrammed in the data plane. Consequently,
when the routes received from the failed DC-Gwy are purged, the flows
automatically take the alternate path to reach their destination.

Below parameters are required for configuring BFD:

- Desired Min TX Interval: The QBGP must program this value to be equal to
  1/3rd of the HOLD TIME value configured by default. By default, this value
  would be 60 seconds. The solution will provide a method to configure this
  value from the thrift interface.

- Required Min RX Interval: This would be configured to the value configured
  in bfdRxInterval

- bfdFailureDetectionThreshold: The bfdFailureDetectionThreshold will be used
  by the BFD implementation to identify the failure. When the number of lost
  packets exceed bfdFailureDetectionThreshold, the BFD protocol detects failure
  of the neighbour.

- bfdDebounceDown:  This indicates the amount of time BFDD must wait to inform
  the QBGP about DC-Gwy failure. When BFDD detects DC-Gwy failure, it starts a
  timer with the value configured in bfdDebounceDown microseconds. Upon the expiry
  of the timer, the latest BFD state is checked. If the latest BFD state still
  indicates DC-Gwy failure, then the corresponding failure is reported to QBGP.
  If the latest BFD state indicates that DC-Gwy is restored, no message is sent to QBGP.

- bfdDebounceUp :This indicates the amount of time BFDD must wait to inform
  the QBGP about DC-Gwy Restoration. When BFDD detects DC-Gwy Restoration, it
  starts a timer with the value configured in bfdDebounceUp microseconds. Upon
  the expiry of the timer, the latest BFD state is checked. If the latest BFD
  indicates DC-Gwy restoration, then the corresponding restoration is reported
  to QBGP. If the latest BFD state indicates DC-Gwy failure, no message is sent
  to QBGP.

Pipeline changes
----------------
None

Yang changes
------------
A new yang file ebgp-bfd.yang, published to accomodate bfd parameters,
which has bfd-config container as below.

.. code-block:: none
   :caption: ebgp-bfd.yang

    container bfd-config {
        config        "true";

        leaf bfd-enabled {
            description  "is BFD enabled";
            type         boolean;
            default      false;
        }

        leaf detect-mult {
            type        uint32;
            default     3;
            description "The number of packets that have to be missed 
                         in a row to declare the session to be down.";
        }
        leaf min-rx {
            type        uint32 {
                            range "50..50000";
            }
            default     500;
            description "The shortest interval, in milli-seconds, at
                         which this BFD session offers to receive
                         BFD control messages. Defaults to 500";
        }
        leaf min-tx {
            type        uint32 {
                            range "1000 .. 60000";
            }
            default     6000;
            description "The shortest interval, in milli-seconds,
                         at which this BFD session is willing to
                         transmit BFD control messages. Defaults
                         to 6000";
        }

        leaf multihop {
            type        boolean;
            default     true;
            description "Value of True indicates suppport for BFD multihop";
            config      "true";
        }
    }

Changes will be needed in ``ebgp.yang``.

- dc-gw TEP ip will be modified as a container

.. code-block:: none
   :caption: ebgp.yang

     container dcgw-tep-list {
          list dcgw-tep {
               key          "dc-gw-ip";
               description  "mapping: DC-Gwy ip <> TEP ip";

               leaf dc-gw-ip {
                   type string;
               }
               leaf-list tep-ips {
                   type string;
               }
         }
     }

Configuration impact
---------------------
New BFD configuration parameters will be added with this feature.

* enable-bfd(default: true)
* min-rx (default: 500ms)
* monitor-window (default: 3)
* min-tx (default: 60 sec)
* failure-threshold (default: 100ms)
* success-threshold (default: 5 sec)
* AssociateTEPDCGW([tep-ip], DC-Gwy):

How will it impact existing deployments?
 There is NO impact on existing deployments.

Clustering considerations
-------------------------
There is no impact on clustering, as the bfdd/bgpd/zrpcd processes
are supposed to run on only one node.
If the bgp-controller-node goes down, it is the responsibility
of CLUSTER environment to bringup on other nodes.

Other Infra considerations
--------------------------

Security considerations
-----------------------
none

Scale and Performance Impact
----------------------------
What are the potential scale and performance impacts of this change?
 * There shall be no impact on performance.

Does it help improve scale and performance or make it worse?
 * There shall be no impact on performance.

Targeted Release
-----------------
What release is this feature targeted for?
Oxygen/Fluorine.

Alternatives
------------

Enable tunnel monitoring in Data Path using BFD (between CSS and DC-Gwy).

Setup Representation
====================

    ::

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

This was not being implemented, as most of the DC-gwy's do not
support BFD monitoring on MPLS/GRE tunnels.

Usage
=====
As described in diagram, this feature is mainly to "switchover
traffic to surviving DC-Gwy, in case of a DC-Gwy failure" and
to reduce impact on Data Path.

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
 1. Enabling bfdd to be part of ODL deployment.
 2. Configuration of bfdd from ODL via thrift interface
    (bfdRxInterval, bfdFailureThreshold, bfdTxInterval,
    bfdDebounceDown, bfdDebounceUp)
 3. BFDD shall inform session status to BGPD.
 4. BGP shall react to BFDD session notifications
    with DC-Gwy.
 5. ODL shall implement, new thrift api's for
    "(un)configuring bfdd", "peer notifications up/down".
 6. on peer down notification from bfd, ODL shall
    disable ECMP bucket for the respective tunnel towards
    the peer. Raise an alarm, indicating peer-down.
 7. on peer up notification from bfd, bgpd shall enable
    BGP communication with peer. ODL shall disable peer-down
    alaram.
 8. Configuration/debugging : new CLI (command line
    interface) for configuration and debugging. REST
    interface for configuration.

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors,
designate a primary assigne and other contributors.

Primary assignee:

- Ashvin Lakshmikantha

- Siva Kumar Perumalla

Other contributors:

- Vyshakh Krishnan C H

- Shankar M


Work Items
----------
Will be added before start of implementation.


Dependencies
============
- DC-Gwy: MUST support BFD monitoring of the BGP control plane
- genius: yang changes in aliveness monitor


Testing
=======
* Configuration: bgp, bfdd peer configuration, neighborship
  establishment and route exchange between DC-Gwy1, DC-Gwy2
  and ODL with ECMP enabled OVS.

* Data Path: Advertise prefix p1 from both DC-Gwy1, DC-Gwy2, traffic
  shall be distributed to both DC-Gwy(s).

* Reboot DC-Gwy2, peer down notification shall be observed
  in logs within 2Seconds. Traffic shall be switched to DC-Gwy1.

* When DC-Gwy2 comes back up, peer up notification shall be
  observed in logs, traffic shall be distributed between DC-Gwy1 and
  DC-Gwy2.

* Verification of bfdDebounceDown/bfdDebounceUp timers by flaping
  connection between ODL and DC-Gwy(s)

* Sanity check of existing BGP behavior, by disabling bfd.

* non-HA scenario: sanity check of existing BGP behavior,
  with single DC-Gwy (includes Graceful-Restart, admin down, ...).

Unit Tests
----------

Integration Tests
-----------------

CSIT
----

Documentation Impact
====================
Yes, Documentation will have an impact.

Contributors to documentation

- Ashvin Lakshmikantha

- Siva Kumar Perumalla

References
==========
none.

