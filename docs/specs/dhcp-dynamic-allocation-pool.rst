.. contents:: Table of Contents
   :depth: 3

===================================
DHCP Server Dynamic Allocation Pool
===================================

[gerrit filter: https://git.opendaylight.org/gerrit/#/q/topic:cool-topic]

Extension of the ODL based DHCP server, which add support for dynamic address allocation to end point users, that are not controlled (known) by OpenStack Neutron. Each DHCP pool can be configured with additional information such as DNS servers, lease time (not yet), static allocations based on MAC address, etc.

The feature supports IPv4 only.

Problem description
===================
Currently, there is no dynamic DHCP service to nodes that are connected to a non-neutron controlled network.

Use Cases
---------
This feature will support the following use cases:

* Use case 1: a DHCP packet that is received by the odl, not from a Neutron port - will be handled by the DHCP dynamic allocation pool that is configured on the receiving OVS port.


Proposed change
===============
Details of the proposed change.

Pipeline changes
----------------
None.

Yang changes
------------
New YANG model to support the config and operatioal data of the DHCP allocation pools, per VRF.
Configuration includes the allocation pool parameters and allocation instances that were granted, or pre-configured as static.
Operational includes mappings of subnet-per-port and IP-to-MAC address, and the alloacation status of each allocation pool.

.. code-block:: none

    container dhcpi {
        config true;
        description "contains internal (non neutron) DHCP allocation";

        list vrf {
            key "vrf-id";
            leaf vrf-id {
                description "VRF ID";
                type string;
            }
            list allocation {
                key "subnet";
                leaf subnet {
                    description "subnet for the dhcp to allocate ip addresses";
                    type inet:ip-prefix;
                }

                list allocation-instance {
                    key "mac";
                    leaf mac {
                        description "requesting mac";
                        type yang:phys-address;
                    }
                    leaf allocated-ip {
                        description "allocated ip address";
                        type inet:ip-address;
                    }
                }
            }
            list allocation-pool {
                key "subnet";
                leaf subnet {
                    description "subnet for the dhcp to allocate ip addresses";
                    type inet:ip-prefix;
                }
                leaf vrf-id {
                    description "vrf-id for inner reference";
                    type string;
                }
                leaf allocate-from {
                    description "low allocation limit";
                    type inet:ip-address;
                }
                leaf allocate-to {
                    description "high allocation limit";
                    type inet:ip-address;
                }
                leaf gateway {
                    description "default gateway for dhcp allocation";
                    type inet:ip-address;
                }
                list dns-servers {
                    description "dns server list";
                    leaf dns-server {
                        description "dns server entry";
                        type inet:ip-address;
                    }
                }
            }
        }

        list op-vrf {
            description "contains operational subnet prefix to subnet uuid mapping";
            config false;
            key "vrf-id";
            leaf vrf-id {
                description "VRF ID";
                type string;
            }
            list subnet-prefix-op-data-entry {
                key "subnet";
                leaf subnet {
                    description "subnet for the dhcp to allocate ip addresses";
                    type inet:ip-prefix;
                }

                leaf "subnet-uuid" {
                    description "subnet uuid";
                    type yang:uuid;
                }
            }
            list op-allocation-pool {
                key "subnet";
                leaf subnet {
                    description "subnet for the dhcp to allocate ip addresses";
                    type inet:ip-prefix;
                }
                leaf high-water-mark-ip {
                    description "high water mark for quick access to next ip-address for allocation";
                    type inet:ip-address;
                }
                leaf high-water-mark {
                    description "high water mark for quick access to next ip-address for allocation";
                    type int32;
                }
                leaf allocate-from {
                    description "low allocation limit";
                    type int32;
                }
                leaf allocate-to {
                    description "high allocation limit";
                    type int32;
                }
            }
            list op-allocation-instance {
                key "subnet";
                leaf subnet {
                    description "subnet for the dhcp to allocate ip addresses";
                    type inet:ip-prefix;
                }
                list op-allocation-instance-entry{
                    key "allocated-ip";
                    leaf allocated-ip {
                        description "allocated ip address";
                        type inet:ip-address;
                    }
                    leaf mac {
                        description "requesting mac";
                        type yang:phys-address;
                    }
                }
            }
        }

    }


Configuration impact
--------------------
The feature is activated in the configuration.

Clustering considerations
-------------------------
Support clustering.

Other Infra considerations
--------------------------
None.

Security considerations
-----------------------
None.

Scale and Performance Impact
----------------------------
None.

Targeted Release
----------------
Carbon.

Alternatives
------------
Implement and maintain an external DHCP server.

Usage
=====

Features to Install
-------------------
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

REST API
--------
Introducing a new REST API for the feature

Dynamic allocation pool
^^^^^^^^^^^^^^^^^^^^^^^

**URL:** /config/dhcpi:dhcpi/

**Sample JSON data**

.. code-block:: none
  {"dhcpi": {
    "vrf": [
      {
        "vrf-id": "7323895824352670344",
        "allocation-pool": [
          {
            "subnet": "10.1.1.0/24",
            "dns-servers": [
              {
                "dns-server": "8.8.8.8"
              }
            ],
            "gateway": "10.1.1.1",
            "vrf-id": "7323895824352670344",
            "allocate-from": "10.1.1.2",
            "allocate-to": "10.1.1.200"
          }
  ]}]}}

Static address allocation
^^^^^^^^^^^^^^^^^^^^^^^^^

**URL:** /config/dhcpi:dhcpi/

**Sample JSON data**

.. code-block:: none
  {"dhcpi": {
    "vrf": [
      {
        "vrf-id": "7323895824352670344",
        "allocation": [
          {
            "subnet": "10.1.1.0/24",
            "allocation-instance": [
              {
                "mac": "fa:16:3e:9d:c6:f5",
                "allocated-ip": "10.1.1.2"
              }
  ]}]}]}}

CLI
---
None.

Implementation
==============

Assignee(s)
-----------
Primary assignee:
  Shai Haim (shai.haim@hpe.com)

Other contributors: ??

Work Items
----------
Here is the link for the Trello Card:
https://trello.com/c/0mgGyJuV/153-dhcp-server-dynamic-allocation-pool

Dependencies
============
None.

Testing
=======

Unit Tests
----------
N.A.

Integration Tests
-----------------
N.A.

CSIT
----
N.A.

Documentation Impact
====================
??

References
==========

