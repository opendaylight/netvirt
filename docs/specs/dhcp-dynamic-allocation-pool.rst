.. contents:: Table of Contents
   :depth: 3

===================================
DHCP Server Dynamic Allocation Pool
===================================

[gerrit filter: https://git.opendaylight.org/gerrit/#/q/topic:dhcp_server_pool]

Extension of the ODL based DHCP server, which add support for dynamic address allocation to end point users, that are not controlled (known) by OpenStack Neutron. Each DHCP pool can be configured with additional information such as DNS servers, lease time (not yet), static allocations based on MAC address, etc.

The feature supports IPv4 only.

Problem description
===================
In a non-neutron northbounds environment e.g. SD-WAN solution (unimgr), there is currently no dynamic DHCP service for end-points or networks that are connected to OVS.

Use Cases
---------
This feature will support the following use cases:

* Use case 1: a DHCP packet that is received by the odl, not from a Neutron port - will be handled by the DHCP dynamic allocation pool that is configured on the receiving OVS port.


Proposed change
===============
Currently, for every DHCP packet that is received by the controller, the controller finds the neutron port based on the inport of the packet, extracts the ip which was allocated by neutron for that vm, and replies using that info. If the dhcp packet is from a non neutron port, the packet won't even reach the controller. We wish to change that and send every dhcp packet to the controller (as long as it is configured to use the controller dhcp). Once the packet is recieved by the controller, the controller will check if it is from a neutron port. if so, the controller will reply using the information from the neutron port. Otherwise, the controller will find the allocation pool for the network which the packet came from and will allocate the next free ip. The operation of each allocation pool will be managed through the Genius ID Manager service.

Pipeline changes
----------------
This new rule in table 60 will be responsible for forwarding dhcp packets to the controller:

.. code-block:: bash

 cookie=0x6800000, duration=121472.576s, table=60, n_packets=1, n_bytes=342, priority=49,udp,tp_src=68,tp_dst=67 actions=CONTROLLER:65535


Yang changes
------------
New YANG model to support the configuration of the DHCP allocation pools and allocations, per network and subnet.

* Allocation-Pool: configuration of allocation pool parameters like range, gateway and dns servers.
* Allocation-Instance: records of addresses allocation, or pre-configured static allocations, per-mac.

.. code-block:: none
   :caption: dhcp_allocation_pool.yang

    container dhcp_allocation_pool {
        config true;
        description "contains internal (non neutron) DHCP allocation";

        list network {
            key "network-id";
            leaf network-id {
                description "network (elan-instance) id";
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
                leaf network-id {
                    description "network-id for inner reference";
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
    }


Configuration impact
--------------------
The feature is activated in the configuration (disabled by default).

adding **dhcp-dynamic-allocation-pool-enabled** leaf to dhcpservice-config:

.. code-block:: none
   :caption: dhcpservice-config.yang

    container dhcpservice-config {
        leaf controller-dhcp-enabled {
            description "Enable the dhcpservice on the controller";
            type boolean;
            default false;
        }

        leaf dhcp-dynamic-allocation-pool-enabled {
            description "Enable dynamic allocation pool on controller dhcpservice";
            type boolean;
            default false;
        }
    }

and netvirt-dhcpservice-config.xml:

.. code-block:: xml

    <dhcpservice-config xmlns="urn:opendaylight:params:xml:ns:yang:dhcpservice:config">
      <controller-dhcp-enabled>false</controller-dhcp-enabled>
      <dhcp-dynamic-allocation-pool-enabled>false</dhcp-dynamic-allocation-pool-enabled>
    </dhcpservice-config>


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

**URL:** /config/dhcp_allocation_pool:dhcp_allocation_pool/

**Sample JSON data**

.. code-block:: json
  {"dhcp_allocation_pool": {
    "network": [
      {
        "network-id": "d211a14b-e5e9-33af-89f3-9e43a270e0c8",
        "allocation-pool": [
          {
            "subnet": "10.1.1.0/24",
            "dns-servers": [
              {
                "dns-server": "8.8.8.8"
              }
            ],
            "gateway": "10.1.1.1",
            "network-id": "d211a14b-e5e9-33af-89f3-9e43a270e0c8",
            "allocate-from": "10.1.1.2",
            "allocate-to": "10.1.1.200"
          }
  ]}]}}

Static address allocation
^^^^^^^^^^^^^^^^^^^^^^^^^

**URL:** /config/dhcp_allocation_pool:dhcp_allocation_pool/

**Sample JSON data**

.. code-block:: json
  {"dhcp_allocation_pool": {
    "network": [
      {
        "network-id": "d211a14b-e5e9-33af-89f3-9e43a270e0c8",
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

Other contributors:
  Alex Feigin (alex.feigin@hpe.com)

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

