.. contents:: Table of Contents
            :depth: 3

==============================================================
Element Counters
==============================================================

https://git.opendaylight.org/gerrit/#/c/50771/

Netvirt Statistics Plugin: https://git.opendaylight.org/gerrit/#/c/50164/8

This feature enables collecting statistics on filtered traffic passed from/to a network element. For example: traffic outgoing/incoming from a specific IP, tcp traffic, udp traffic, incoming/outgoing traffic only.

Problem description
===================

Collecting statistics on filtered traffic sent to/from a VM is currently not possible.

Use Cases
---------

- Tracking East/West communication between local VMs.
- Tracking East/West communication between VMs that are located in different compute nodes.
- Tracking communication between a local VM and an IP located in an external network. 
- Tracking TCP/UDP traffic sent from/to a VM.
- Tracking dropped packets between 2 VMs.

Proposed change
===============

The Netvirt Statistics Plugin will receive requests regarding element filtered counters.
A new service will be implemented ("CounterService"), and will be associated with the relevant interfaces (either ingress side, egress sides or both of them).

* Ingress traffic: The service will be the first one in the pipeline, closest to the traffic received from the port.
* Egress traffic: The service will be the last one in the pipeline, just before traffic is being output to the port. 
* The input for counters request regarding VM A, and incoming and outgoing traffic from VM B, will be VM A interface uuid and VM B IP.
* The input can also include other filters like TCP only traffic, UDP only traffic, incoming/outgoing traffic.
* In order to track dropped traffic between VM A and VM B, the feature should be activated on both VMS (either in the same compute node or in different compute nodes). service binding will be done on both VMs relevant interfaces.
* If the counters request involves an external IP, service binding will be done only on the VM interface.
* Adding/Removing the "CounterService" should be dynamic and triggered by requesting element counters.

Two new tables will be used: table 39 for ingress traffic from the VM, 
and table 254 for egress traffic from the VM. 
A new rule will be installed in each table for each VM, as described below in the 
"Pipline changes" section. 
The default rule will resubmit traffic to the appropriate dispatcher table. 

The Statictics Plugin will use OpenFlow flow statistic requests for these new rules, 
allowing it to gather statistics regarding the traffic between the 2 elements. 
It will be responsible to validate and filter the counters results.

Pipeline changes
----------------
Assuming we want statistics on VM A traffic, received or sent from VM B.

VM A Ingress traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
In table 39 traffic will be matched against src-ip and lport tag.

  | Ingress dispatcher table (17): ``match: lport-tag=vmA-interface, actions: go to table 39`` =>
  | Ingress counters table  (39): ``match: src-ip=vmB-ip, lport-tag=vmA-interface, actions: go to table 17`` =>

VM A Egress traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Egress dispatcher table (220): ``match: lport-tag=vmB-interface, actions: go to table 254`` =>
  | Egress counters table (254): ``match: lport-tag=vmB-interface, tcp, actions: go to table 220`` =>

Assuming we want statistics on VM A outgoing TCP traffic.

VM A Egress traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Egress dispatcher table (220): ``match: lport-tag=vmB-interface, actions: go to table 254`` =>
  | Egress counters table (254): ``match: lport-tag=vmB-interface, dst-ip=vmB-ip, actions: go to table 220`` =>

Assuming we want statistics on VM A incoming UDP traffic.

VM A Egress traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

   | Ingress dispatcher table (17): ``match: lport-tag=vmA-interface, actions: go to table 39`` =>
   | Ingress counters table  (39): ``match: lport-tag=vmA-interface, udp, actions: go to table 17`` =>

Yang changes
---------------
Netvirt Statistics module will be enhanced with the following RPC:
::

    grouping result {
        list counterResult {
            key id;
            leaf id {
                type string;
            }
            list groups {
                key name;
                leaf name {
                    type string;
                }
                list counters {
                    key name;
                    leaf name {
                        type string;
                    }
                    leaf value {
                        type uint64;
                    }
                }
            }
        }
    }

    grouping filters {
        leaf-list groupFilters {
            type string;
        }
        leaf-list counterFilter {
            type string;
        }
    }

    grouping elementRequestData {
        container filters {

            container trafficTypeFilter {
                leaf tcp {
                    type string;
                    default "";
                }
                leaf udp {
                    type string;
                    default "";
                }
                leaf portId {
                    type string;
                    default "";
                }
            }

            container ipFilter {
                leaf ip {
                    type string;
                    default "";
                }
            }
        }
    }

    rpc getNodeAggregatedCounters {
        input {
            leaf nodeId {
                type uint64;
            }
            uses filters;
        }
        output {
            uses result;
        }
    }

    rpc getElementCounters {
        input {
            leaf portId {
                type string;
            }
            container incomingTraffic {
                uses elementRequestData;
            }
            container outgoingTraffic {
                uses elementRequestData;
            }
            uses filters;
        }
        output {
            uses result;
        }
    }

    rpc startElementCountersService {
        input {
            leaf portId {
                type string;
            }
            leaf incomingTraffic {
                type boolean;
                default false;
            }
            leaf outgoingTraffic {
                type boolean;
                default false;
            }
        }
        output {
        }
    }

    rpc stopElementCountersService {
        input {
            leaf portId {
                type string;
            }
        }
        output {
        }
    }

Configuration impact
---------------------
The described above YANG model will be saved in the data store.

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
Since adding the new service is done by a request (as well as removing it), not all packets will be sent to the new tables described above.

Targeted Release
-----------------
Carbon

Alternatives
------------
None

Usage
=====

* Create router, network, 2 VMS, VXLAN tunnel.
* Connect to each one of the VMs and send ping to the other VM.
* Use REST to get the statistics.

Run the following to get interface ids:

::

    http://10.0.77.135:8181/restconf/operational/ietf-interfaces:interfaces-state/

Choose VM B interface and use the following REST in order to get the statistics:
Assuming VM A IP = 1.1.1.1, VM B IP = 2.2.2.2

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:getElementCounters, input={"input": {"portId":"b99a7352-1847-4185-ba24-9ecb4c1793d9", "incomingTraffic": ["ipFilter": ["ip":"1.1.1.1"]]}}, headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]

Stop service:

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:stopElementCounters, input={"input":     {"portId":"b99a7352-1847-4185-ba24-9ecb4c1793d9"}}, headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]

Features to Install
-------------------
odl-netvirt-genius-openstack

REST API
--------

CLI
---


Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Guy Regev <guy.regev@hpe.com>

Other contributors:
  TBD


Work Items
----------
https://trello.com/c/88MnwGwb/129-element-to-element-counters

* Add new service in Genius.
* Implement new rules installation.
* Update Netvirt Statistics module to support the new counters request.

Dependencies
============

None

Testing
=======

Unit Tests
----------

Integration Tests
-----------------

CSIT
----

Documentation Impact
====================

References
==========

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode

