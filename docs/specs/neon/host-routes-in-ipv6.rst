.. contents:: Table of Contents
   :depth: 3

===================
Host Routes in IPv6
===================

[gerrit filter: https://git.opendaylight.org/gerrit/#/q/topic:host-routes-in-ipv6]

   This document as stated in RFC4191 describes an optional extension RIO (Route Information Option) to
   Router Advertisement messages for communicating default router preferences and more-specific routes from
   routers to hosts.This improves the ability of hosts to pick an appropriate router, especially when the host is
   multi-homed and the routers are on different links.

Problem description
===================
   Multi-homed hosts are an increasingly important scenario, especially with IPv6. Today most of the VM's
   are getting deployed with multiple vNIC's whereas each vNIC's is connects to a different routing domain.
   And, traffic to destinations that are not on attached subnets would be forwarded to the default GW, but this
   will be problem in multi homed host, where each vNIC is connected to different routed domains with different
   reachability.
In scope
--------
Reachability to subnets connected to a router.

Out of scope
------------
Reachability to ``special destinations” hosted by VMs.

Use Cases
---------

UC 1: Reachability to subnets connected to a router
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
In current scenarios, almost all VNF's are multi vNIC and today these multi vNIC needs to be manually configured
with routing table entries.In IPv4, this is managed via DHCPv4 host-routes, which is not available in DHCPv6
So this use case deals where it must be possible for the VM to unambiguously decide which router can forward
packets and to what subnet.

Proposed change
===============
As per RFC 4191 to handle this issue we are going to adds a new option route-information option in existing RA.
Using this option, it is possible for a router to advertise host-route prefixes that are reachable via that router.
As Router Advertisements is an existing standard and a stable protocol for router-to-host communication.
Piggybacking this additional information on existing message traffic from routers to hosts will reduces network
overhead as well.

Route Information Option as described in RFC 4191 is in the below diagram :
.. image:: images/route-information-option.PNG

Route Information Option description
Type          : Must contain value 24
Length        : Length of the option in units of 8 bytes
Prefix length : Length of the prefix
Preference    : Set to value 00* which indicates medium priority
Route lifetime: Set to 0xffffffff
Prefix        : The actual prefix

For each subnet attached to a router, a RIO must be constructed and sent along with the RA.
In case of multiple subnet attached to a router, their can be two approaches as its possible the approach 1 explained
below will not work as expected, after testing we can finalize whether approach 1 is working of not, in case approach 1
is not working we can go to approach 2.
Let us take a example where a router is connected with 3 different subnets and every subnet is having one VM.
Approach 1:
In RA add RIO for all three subnets, and send all three subnet details along with RA to all three VM's.
Approach 2:
In RA add RIO for two subnets excluding the RIO where this vm is attached to.


Pipeline changes
----------------
No pipeline change required.

Yang changes
------------
Changes will be needed in router-advertisement-packet container of ``ipv6-neighbor-discovery.yang`` to support
the route information option.

 .. code-block:: none

     :caption: ipv6-neighbor-discovery.yang
     container router-advertisement-packet {
            uses ethernet-header;
            uses ipv6-header;
            uses icmp6-header;
            leaf cur-hop-limit {
                type uint8;
            }
            leaf flags {
                type uint8;
            }
            leaf router-lifetime {
                type uint16;
            }
            leaf reachable-time {
                type uint32;
            }
            leaf retrans-time {
                type uint32;
            }

            leaf option-source-addr {
                type uint8;
            }
            leaf source-addr-length {
                type uint8;
            }
            leaf source-ll-address {
                type yang:mac-address;
            }

            leaf option-mtu {
                type uint8;
            }
            leaf option-mtu-length {
                type uint8;
            }
            leaf mtu {
                type uint32;
            }
            list prefix-list {
                key "prefix";
                leaf option-type {
                    type uint8;
                }
                leaf option-length {
                    type uint8;
                }
                leaf prefix-length {
                    type uint8;
                }
                leaf flags {
                    type uint8;
                }
                leaf valid-lifetime {
                    type uint32;
                }
                leaf preferred-lifetime {
                    type uint32;
                }
                leaf reserved {
                    type uint32;
                }
                leaf prefix {
                    type inet:ipv6-prefix;
                }
            }
            list route-information-option-list {
                key "prefix";
                leaf option-type {
                    type uint8;
                }
                leaf option-length {
                    type uint8;
                }
                leaf prefix-length {
                    type uint8;
                }
                leaf flags {
                    type uint8;
                }
                leaf route-lifetime {
                    type uint32;
                }
                leaf prefix {
                    type inet:ipv6-prefix;
                }
            }
        }

Configuration impact
--------------------
There is no change to any existing configuration.

Clustering considerations
-------------------------
The feature should operate in ODL Clustered (3-node cluster) environment reliably.

HA considerations
------------------
Cluster Restart
^^^^^^^^^^^^^^^
Upon cluster reboot the RIOs must be reconstructed for all the subnets attached to a router.

Single Node Restart
^^^^^^^^^^^^^^^^^^^
When a single controller instance restarts (or becomes unavailable), the RIO generation mechanism must
gracefully move to another instance.If a single node that is processing a subnet add/delete operation restarts,
then one of the other instances must gracefully take over the RIO realization.

Switch & QBGP Restart
^^^^^^^^^^^^^^^^^^^^^
* There should not be any impact related to RIO generation when switch restarts.
* There should not be any impact related to RIO generation during QBGP restart.


Other Infra considerations
--------------------------
N.A.

Security considerations
-----------------------
N.A.

Scale and Performance Impact
----------------------------
Not covered by this Design Document.

Targeted Release
----------------
Neon.

Alternatives
------------
None.

Usage
=====

Features to Install
-------------------
* This feature can be used by installing odl-netvirt-openstack.
* This feature doesn't add any new karaf feature.

Workflow
--------

Subnet Attach to a Router
^^^^^^^^^^^^^^^^^^^^^^^^^
* When an IPv6 subnet is attached to a router, a new subnet range becomes reachable via that router.
* The IPv6 module must create a new RIO option that includes the CIDR of the IPv6 subnet as a prefix.
* All subsequent router advertisements (unsolicited & solicited) must carry the new RIO as one of the options


Subnet detach from a Router
^^^^^^^^^^^^^^^^^^^^^^^^^
* When a Subnet is detached from the router, the corresponding subnet is no longer reachable via that router
* The RIO that corresponds to this subnet’s CIDR must be deleted.
* All subsequent RAs (Unsolicited & Solicited) must no longer carry the RIO corresponding to the deleted Subnet.

REST API
--------
None.

CLI
---
None.

Implementation
==============

Assignee(s)
-----------
Primary assignee:
  <Nishchya Gupta> (nishchyag@altencalsoftlabs.com)

Work Items
----------
1. Modify router-advertisement-packet container of ipv6-neighbor-discovery.yang
   of ipv6util module of genius project.
2. Fill RIO for each subnet in RA response and send across.
3. Add UTs.
4. Add CSIT.


Dependencies
============

Testing
=======

Unit Tests
----------
Relevant Unit Test cases will be added.

Integration Tests
-----------------
N/A

CSIT
----
Relevant test cases will be added to Netvirt CSIT.


Documentation Impact
====================

References
==========
