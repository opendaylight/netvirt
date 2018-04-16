.. contents:: Table of Contents
   :depth: 3

===================================
DHCP Server Dynamic Allocation Pool
===================================

[gerrit filter: https://git.opendaylight.org/gerrit/#/q/topic:dhcp_server_pool]

Extension of the ODL based DHCP server, which add support for dynamic address allocation to end
point users, that are not controlled (known) by OpenStack Neutron. Each DHCP pool can be configured
with additional information such as DNS servers, lease time (not yet), static allocations based on
MAC address, etc.

The feature supports IPv4 only.

Problem description
===================
In a non-neutron northbounds environment e.g. SD-WAN solution (unimgr), there is currently no
dynamic DHCP service for end-points or networks that are connected to OVS. Every DHCP packet that is
received by the controller, the controller finds the neutron port based on the inport of the packet,
extracts the ip which was allocated by neutron for that vm, and replies using that info. If the dhcp
packet is from a non-neutron port, the packet won't even reach the controller.

Use Cases
---------
a DHCP packet that is received by the odl, from a port that is managed by Netvirt and was configured
using the netvirt API, rather then the neutron API, in a way that there is no pre-allocated IP for
network interfaces behind that port - will be handled by the DHCP dynamic allocation pool that is
configured on the network associated with the receiving OVS port.

Proposed change
===============
We wish to forward to the controller, every dhcp packet coming from a non-neutron port as well (as
long as it is configured to use the controller dhcp). Once a DHCP packet is recieved by the
controller, the controller will check if there is already a pre-allocated address by checking if
packet came from a neutron port. if so, the controller will reply using the information from the
neutron port. Otherwise, the controller will find the allocation pool for the network which the
packet came from and will allocate the next free ip. The operation of each allocation pool will
be managed through the Genius ID Manager service that will support the allocation and release of IP
addresses (ids), persistent mapping across controller restarts and more. Neutron IP allocations will
be added to the relevant pools to avoid allocation of the same addresses.

The allocation pool DHCP server will support:

* DHCP methods: Discover, Request, Release, Decline and Inform (future)
* Allocation of a dynamic or specific (future) available IP address from the pool
* (future) Static IP address allocations
* (future) IP Address Lease Time + Rebinding and Renewal Time
* Classless Static Routes for each pool
* Domain names (future) and DNS for each pool
* (future) Probe an address before allocation
* (future) Relay agents

Pipeline changes
----------------
This new rule in table 60 will be responsible for forwarding dhcp packets to the controller:

.. code-block:: bash

 cookie=0x6800000, duration=121472.576s, table=60, n_packets=1, n_bytes=342, priority=49,udp,tp_src=68,tp_dst=67 actions=CONTROLLER:65535


Yang changes
------------
New YANG model to support the configuration of the DHCP allocation pools and allocations, per
network and subnet.

* Allocation-Pool: configuration of allocation pool parameters like range, gateway and dns servers.
* Allocation-Instance: configuration of static IP address allocation and Neutron pre-allocated addresses, per MAC address.

.. code-block:: none
   :caption: dhcp_allocation_pool.yang

    container dhcp_allocation_pool {
        config true;
        description "contains DHCP Server dynamic allocations";

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
                leaf-list dns-servers {
                    description "dns server list";
                    type inet:ip-address;
                }
                list static-routes {
                    description "static routes list for dhcp allocation";
                    key "destination";
                    leaf destination {
                        description "destination in CIDR format";
                        type inet:ip-prefix;
                    }
                    leaf nexthop {
                        description "router ip address";
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

    {
        "dhcp_allocation_pool": {
            "network": [
            {
                "network-id": "d211a14b-e5e9-33af-89f3-9e43a270e0c8",
                "allocation-pool": [
                {
                    "subnet": "10.1.1.0/24",
                    "dns-servers": [
                        "8.8.8.8"
                    ],
                    "gateway": "10.1.1.1",
                    "allocate-from": "10.1.1.2",
                    "allocate-to": "10.1.1.200"
                    "static-routes": [
                    {
                        "destination": "5.8.19.24/16",
                        "nexthop": "10.1.1.254"
                    }]
                }]
            }]
        }
    }

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

