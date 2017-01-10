.. contents:: Table of Contents
            :depth: 3

=====================
Netvirt Statistics
=====================

https://git.opendaylight.org/gerrit/#/q/topic:netvirt-counters

The feature enables getting statistics on ports and switches.


Problem description
===================

Being able to ask for statistics, given as input Netvirt identifiers.
It will enable filtering the results and having aggregated result.
In a later stage, it will be also used to get element to element counters.
Examples for possible filters: RX only, TX only, port + VLAN counters...

Use Cases
---------

* Getting port counters, given its interface id (ietf interface name).
* Getting node counters, given its node id.

Port counters can be useful also to get statistics on traffic going into tunnels
when requesting it from the tunnel endpoint port.
In addition, there will also be support in aggregated results. For example:
Getting the total number of transmitted packets from a given switch.

Proposed change
===============

Adding a new bundle named "statistics-plugin" to Netvirt.
This bundle will be responsible for converting the Netvirt unique identifiers into OpenFlow ones,
and will get the relevant statistics by using OpenFlowPlugin capabilities.
It will also be responsible of validating and filtering the results.
It will be able to provide a wide range of aggregated results in the future.

Work flow description: Once a port statistics request is received, it is translated to a port statistics request from openflow plugin. Once the transaction is received, the data is validated and translated to a user friendly data. The user will be notified if a timeout occurs.
In case of a request for aggregated counters, the user will receive a single counter result divided to groups (such as "bits", "packets"...). The counters in each group will be the sum of all of the matching counters for all ports.
Neither one of the counter request nor the counter response will not be stored in the configuration database. Moreover, requests are not periodic and they are on demand only.

Pipeline changes
----------------
None

Yang changes
------------
The new plugin introduced will have the following models:
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

    rpc getNodeConnectorCounters {
        input {
            leaf portId {
                type string;
            }
            uses filters;
        }
        output {
            uses result;
        }
    }

    rpc getNodeCounters {
        input {
            leaf nodeId {
                type uint64;
            }
        }
        output {
            uses result;
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


Configuration impact
---------------------
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
Carbon

Alternatives
------------
Getting the statistics from OpenFlow flows: it would be possible to target the appropriate rules in ingress/egress tables, and count the hits on these flows. The reason we decided to work with ports instead is because we don't want to be dependent on flow structure changes.

Usage
=====
* Create router, network, VMS, VXLAN tunnel.
* Connect to one of the VMs, send ping ping to the other VM.
* Use REST to get the statistics.

Port statistics:

::

    http://10.0.77.135:8181/restconf/operational/ietf-interfaces:interfaces-state/

Choose a port id and use the following REST in order to get the statistics:

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:getNodeConnectorCounters, input={"input":{"portId":"b99a7352-1847-4185-ba24-9ecb4c1793d9"}}, headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]


Node statistics:

::

    http://10.0.77.135:8181/restconf/config/odl-interface-meta:bridge-interface-info/

Choose a node dpId and use the following REST in order to get the statistics:

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:getNodeCounters, input=
           {"input": { "portId": "b99a7352-1847-4185-ba24-9ecb4c1793d9","groups": [{ "name": "byte*",
                                "counters": [{
                                                                "name": "rec*",
                                                        }, {
                                                                "name": "transmitted*",
                                                        }]
                                        }]
            }},
    headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:getNodeAggregatedCounters, input=
           {"input": { "portId": "b99a7352-1847-4185-ba24-9ecb4c1793d9","groups": [{ "name": "byte*",
                                "counters": [{
                                                                "name": "rec*",
                                                        }, {
                                                                "name": "transmitted*",
                                                        }]
                                        }]
            }},
    headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]

Example for a filtered request:

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:getPortCounters, input={"input": {"portId":"b99a7352-1847-4185-ba24-9ecb4c1793d9"} }, headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]

An example for node connector counters result:

::

    {
  "output": {
    "counterResult": [
      {
        "id": "openflow:194097926788804:5",
        "groups": [
          {
            "name": "Duration",
            "counters": [
              {
                "name": "durationNanoSecondCount",
                "value": 471000000
              },
              {
                "name": "durationSecondCount",
                "value": 693554
              }
            ]
          },
          {
            "name": "Bytes",
            "counters": [
              {
                "name": "bytesReceivedCount",
                "value": 1455
              },
              {
                "name": "bytesTransmittedCount",
                "value": 14151299
              }
            ]
          },
          {
            "name": "Packets",
            "counters": [
              {
                "name": "packetsReceivedCount",
                "value": 9
              },
              {
                "name": "packetsTransmittedCount",
                "value": 9
              }
            ]
          }
        ]
      }
    ]
  }
 }

An example for node counters result:

::

    {
  "output": {
    "counterResult": [
      {
        "id": "openflow:194097926788804:3",
        "groups": [
          {
            "name": "Duration",
            "counters": [
              {
                "name": "durationNanoSecondCount",
                "value": 43000000
              },
              {
                "name": "durationSecondCount",
                "value": 694674
              }
            ]
          },
          {
            "name": "Bytes",
            "counters": [
              {
                "name": "bytesReceivedCount",
                "value": 0
              },
              {
                "name": "bytesTransmittedCount",
                "value": 648
              }
            ]
          },
          {
            "name": "Packets",
            "counters": [
              {
                "name": "packetsReceivedCount",
                "value": 0
              },
              {
                "name": "packetsTransmittedCount",
                "value": 0
              }
            ]
          }
        ]
      },
      {
        "id": "openflow:194097926788804:2",
        "groups": [
          {
            "name": "Duration",
            "counters": [
              {
                "name": "durationNanoSecondCount",
                "value": 882000000
              },
              {
                "name": "durationSecondCount",
                "value": 698578
              }
            ]
          },
          {
            "name": "Bytes",
            "counters": [
              {
                "name": "bytesReceivedCount",
                "value": 0
              },
              {
                "name": "bytesTransmittedCount",
                "value": 648
              }
            ]
          },
          {
            "name": "Packets",
            "counters": [
              {
                "name": "packetsReceivedCount",
                "value": 0
              },
              {
                "name": "packetsTransmittedCount",
                "value": 0
              }
            ]
          }
        ]
      },
      {
        "id": "openflow:194097926788804:1",
        "groups": [
          {
            "name": "Duration",
            "counters": [
              {
                "name": "durationNanoSecondCount",
                "value": 978000000
              },
              {
                "name": "durationSecondCount",
                "value": 698627
              }
            ]
          },
          {
            "name": "Bytes",
            "counters": [
              {
                "name": "bytesReceivedCount",
                "value": 6896336558
              },
              {
                "name": "bytesTransmittedCount",
                "value": 161078765
              }
            ]
          },
          {
            "name": "Packets",
            "counters": [
              {
                "name": "packetsReceivedCount",
                "value": 35644913
              },
              {
                "name": "packetsTransmittedCount",
                "value": 35644913
              }
            ]
          }
        ]
      },
      {
        "id": "openflow:194097926788804:LOCAL",
        "groups": [
          {
            "name": "Duration",
            "counters": [
              {
                "name": "durationNanoSecondCount",
                "value": 339000000
              },
              {
                "name": "durationSecondCount",
                "value": 698628
              }
            ]
          },
          {
            "name": "Bytes",
            "counters": [
              {
                "name": "bytesReceivedCount",
                "value": 0
              },
              {
                "name": "bytesTransmittedCount",
                "value": 0
              }
            ]
          },
          {
            "name": "Packets",
            "counters": [
              {
                "name": "packetsReceivedCount",
                "value": 0
              },
              {
                "name": "packetsTransmittedCount",
                "value": 0
              }
            ]
          }
        ]
      },
      {
        "id": "openflow:194097926788804:5",
        "groups": [
          {
            "name": "Duration",
            "counters": [
              {
                "name": "durationNanoSecondCount",
                "value": 787000000
              },
              {
                "name": "durationSecondCount",
                "value": 693545
              }
            ]
          },
          {
            "name": "Bytes",
            "counters": [
              {
                "name": "bytesReceivedCount",
                "value": 1455
              },
              {
                "name": "bytesTransmittedCount",
                "value": 14151073
              }
            ]
          },
          {
            "name": "Packets",
            "counters": [
              {
                "name": "packetsReceivedCount",
                "value": 9
              },
              {
                "name": "packetsTransmittedCount",
                "value": 9
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
odl-netvirt-openflowplugin-genius-openstack


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
https://trello.com/c/ZdoLQWoV/126-netvirt-statistics

* Support port counters.
* Support node counters.
* Support aggregated results.
* Support filters on results.

Dependencies
============
* Genius
* OpenFlow Plugin
* Infrautils


Testing
=======
Capture details of testing that will need to be added.

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


