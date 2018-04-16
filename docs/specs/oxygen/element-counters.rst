.. contents:: Table of Contents
   :depth: 3

==============================================================
Element Counters
==============================================================

https://git.opendaylight.org/gerrit/#/q/element-counters

This feature depends on the Netvirt statistics feature.

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

* Ingress traffic: The service will be the first one in the pipeline after the Ingress ACL service.
* Egress traffic: The service will be the last one after the Egress ACL service.
* The input for counters request regarding VM A, and incoming and outgoing traffic from VM B, will be VM A interface uuid and VM B IP.
* The input can also include other filters like TCP only traffic, UDP only traffic, incoming/outgoing traffic.
* In order to track dropped traffic between VM A and VM B, the feature should be activated on both VMS (either in the same compute node or in different compute nodes). service binding will be done on both VMs relevant interfaces.
* If the counters request involves an external IP, service binding will be done only on the VM interface.
* Adding/Removing the "CounterService" should be dynamic and triggered by requesting element counters.


The Statistics Plugin will use OpenFlow flow statistic requests for these new rules,
allowing it to gather statistics regarding the traffic between the 2 elements.
It will be responsible to validate and filter the counters results.

Pipeline changes
----------------

Two new tables will be used: table 219 for outgoing traffic from the VM, and table 249 for incoming traffic from the VM.
In both ingress and egress pipelines, the counter service will be just after the appropriate ACL service.
The default rule will resubmit traffic to the appropriate dispatcher table.

Assuming we want statistics on VM A traffic, received or sent from VM B.

VM A Outgoing Traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
In table 219 traffic will be matched against dst-ip and lport tag.

  | Ingress dispatcher table (17): ``match: lport-tag=vmA-interface, actions: go to table 219`` =>
  | Ingress counters table  (219): ``match: dst-ip=vmB-ip, lport-tag=vmA-interface, actions: resubmit to table 17`` =>

VM A Incoming Traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
In table 249 traffic will be matched against src-ip and lport tag.

  | Egress dispatcher table (220): ``match: lport-tag=vmA-interface, actions: go to table 249`` =>
  | Egress counters table (249): ``match: lport-tag=vmA-interface, src-ip=vmB-ip, actions: resubmit to table 220`` =>

Assuming we want statistics on VM A incoming TCP traffic.

VM A Outgoing Traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Egress dispatcher table (220): ``match: lport-tag=vmA-interface, actions: go to table 249`` =>
  | Egress counters table (249): ``match: lport-tag=vmA-interface, tcp, actions: resubmit to table 220`` =>

Assuming we want statistics on VM A outgoing UDP traffic.

VM A Incoming traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

   | Ingress dispatcher table (17): ``match: lport-tag=vmA-interface, actions: go to table 219`` =>
   | Ingress counters table  (219): ``match: lport-tag=vmA-interface, udp, actions: resubmit to table 17`` =>

Assuming we want statistics on all traffic sent to VM A port.

VM A Incoming traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

   | Ingress dispatcher table (17): ``match: lport-tag=vmA-interface, actions: go to table 219`` =>
   | Ingress counters table  (219): ``match: lport-tag=vmA-interface, actions: resubmit to table 17`` =>

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
                container tcpFilter {
                leaf on {
                    type boolean;
                }
                leaf srcPort {
                    type int32;
                    default -1;
                }
                leaf dstPort {
                    type int32;
                    default -1;
                }
            }

            container udpFilter {
                leaf on {
                    type boolean;
                }
                leaf dstPort {
                    type int32;
                    default -1;
                }
                leaf srcPort {
                    type int32;
                    default -1;
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

    container elementCountersRequestConfig {
        list counterRequests {
            key "requestId";
            leaf requestId {
                type string;
            }
            leaf lportTag {
                type int32;
            }
            leaf dpn {
                type uint64;
            }
            leaf portId {
                type string;
            }
            leaf trafficDirection {
                type string;
            }
            uses elementRequestData;
        }
    }

    rpc acquireElementCountersRequestHandler {
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
            leaf incomingTrafficHandler {
                type string;
            }
            leaf outcoingTrafficHandler {
                type string;
            }
        }
    }

    rpc releaseElementCountersRequestHandler {
        input {
            leaf handler {
                type string;
            }
        }
        output {
        }
    }

    rpc getElementCountersByHandler {
        input {
            leaf handler {
                type string;
            }
        }
        output {
            uses result;
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

.. code-block:: none

    http://10.0.77.135:8181/restconf/operational/ietf-interfaces:interfaces-state/

Choose VM B interface and use the following REST in order to get the statistics:
Assuming VM A IP = 1.1.1.1, VM B IP = 2.2.2.2

Acquire counter request handler:

.. code-block:: json
   :caption: 10.0.77.135:8181/restconf/operations/statistics-plugin:acquireElementCountersRequestHandler

    {
        "input": {
            "portId":"4073b4fe-a3d5-47c0-b37d-4fb9db4be9b1",
            "incomingTraffic": {
                "filters": {
                    "ipFilter": {
                        "ip":"1.1.3.9"
                    }
                }
            }
        }
    }

Release handler:

.. code-block:: json
   :caption: 10.0.77.135:8181/restconf/operations/statistics-plugin:releaseElementCountersRequestHandler

    {
        "input": {
            "handler":"1"
        }
    }

Get counters:

.. code-block:: json
   :caption: 10.0.77.135:8181/restconf/operations/statistics-plugin:getElementCountersByHandler

    {
        "input": {
            "handler":"1"
        }
    }

Example counters output:

.. code-block:: json

    {
        "output": {
            "counterResult": [
                {
                    "id": "SOME UNIQUE ID",
                    "groups": [
                        {
                            "name": "Duration",
                            "counters": [
                                {
                                    "name": "durationNanoSecondCount",
                                    "value": 298000000
                                },
                                {
                                "name": "durationSecondCount",
                                "value": 10369
                                }
                            ]
                        },
                        {
                            "name": "Bytes",
                            "counters": [
                                {
                                    "name": "bytesTransmittedCount",
                                    "value": 648
                                },
                                {
                                    "name": "bytesReceivedCount",
                                    "value": 0
                                }
                            ]
                        },
                        {
                            "name": "Packets",
                            "counters": [
                                {
                                    "name": "packetsTransmittedCount",
                                    "value": 8
                                },
                                {
                                    "name": "packetsReceivedCount",
                                    "value": 0
                                }
                            ]
                        }
                    ]
                }
            ]
        }
    }

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

Netvirt statistics feature: https://git.opendaylight.org/gerrit/#/c/50164/8



