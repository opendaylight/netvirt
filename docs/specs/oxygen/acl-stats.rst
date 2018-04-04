.. contents:: Table of Contents
   :depth: 3

==============
ACL Statistics
==============

https://git.opendaylight.org/gerrit/#/q/topic:acl-stats

This feature is to provide additional operational support for ACL through statistical counters.
ACL rules provide security to VMs by filtering packets in either directions (ingress/egress).
Using OpenFlow statistical counters, ODL will provide additional information on the number of
packets dropped by the ACL rules. This information is made available to the operator “on demand”.

Drop statistics will be provided for below cases:

* Packets dropped due to ACL rules
* Packets dropped due to INVALID state. The INVALID state means that the packet can't be identified
  or that it does not have any state. This may be due to several reasons, such as the system
  running out of memory or ICMP error messages that do not respond to any known connections.

The packet drop information provided through the statistical counters enable operators to
trouble shoot any misbehavior and take appropriate actions through automated or manual
intervention.

Collection and retrieval of information on the number of packets dropped by the SG rules

* Done for all (VM) ports in which SG is configured
* Flow statistical counters (in OpenFlow) are used for this purpose
* The information in these counters are made available to the operator, on demand, through an API

This feature will only be supported with Stateful ACL mode.

Problem description
===================
With only ACL support, operators would not be able to tell how many packets dropped by ACL rules.
This enhancement planned is about ACL module supporting aforementioned limitation.

Use Cases
---------
Collection and retrieval of information on the number of packets dropped by the ACL rules

* Done for all (VM) ports in which ACL is configured
* The information in these counters are made available to the operator, on demand, through an API
* Service Orchestrator/operator can also specify ports selectively where ACL rules are configured

Proposed change
===============

ACL Changes
-----------
Current Stateful ACL implementation has drop flows for all ports combined for a device. This needs
to be modified to have drop flows for each of the OF ports connected to VMs (Neutron Ports).

With the current implementation, drop flows are as below:

.. code-block:: bash

   cookie=0x6900000, duration=938.964s, table=252, n_packets=0, n_bytes=0, priority=62020,
           ct_state=+inv+trk actions=drop

   cookie=0x6900000, duration=938.969s, table=252, n_packets=0, n_bytes=0, priority=50,
           ct_state=+new+trk actions=drop

Now, for supporting Drop packets statistics per port, ACL will be updated to replace above
flows with new DROP flows with lport tag as metadata for each of the VM (Neutron port) being
added to OVS as specified below:

.. code-block:: bash

   cookie=0x6900001, duration=938.964s, table=252, n_packets=0, n_bytes=0, priority=62015,
           metadata=0x10000000000/0xffffff0000000000, ct_state=+inv+trk actions=drop

   cookie=0x6900001, duration=938.969s, table=252, n_packets=0, n_bytes=0, priority=50,
           metadata=0x10000000000/0xffffff0000000000, ct_state=+new+trk actions=drop

Drop flows details explained above are for pipeline egress direction. For ingress side,
similar drop flows would be added with ``table=41``.

Also, new cookie value ``0x6900001`` would be added with drop flows to identify it uniquely and
priority ``62015`` would be used with +inv+trk flows to give higher priority for +est and +rel
flows.

Drop packets statistics support
-------------------------------
ODL Controller will be updated to provide a new RPC/NB REST API ``<get-acl-port-statistics>`` in
ACL module with ``ACL Flow Stats Request`` and ``ACL Flow Stats Response`` messages. This RPC/API
will retrieve details of dropped packets by Security Group rules for all the neutron ports
specified as part of ``ACL Flow Stats Request``. The retrieved information (instantaneous) received
in the OF reply message is formatted as ``ACL Flow Stats Response`` message before sending it as a
response towards the NB.

``<get-acl-port-statistics>`` RPC/API implementation would be triggering
``opendaylight-direct-statistics:get-flow-statistics`` request of OFPlugin towards OVS to get the
flow statistics of ACL tables (ingress / egress) for the required ports.

ACL Flow Stats Request/Response messages are explained in subsequent sections.

Pipeline changes
----------------
No changes needed in OF pipeline. But, new flows as specified in above section would be added for
each of the Neutron ports being added.

Yang changes
------------
New yang file will be created with RPC as specified below:

.. code-block:: none
   :caption: acl-live-statistics.yang

    module acl-live-statistics {
        namespace "urn:opendaylight:netvirt:acl:live:statistics";

        prefix "acl-stats";

        import ietf-interfaces {prefix if;}
        import aclservice {prefix aclservice; revision-date "2016-06-08";}

        description "YANG model describes RPC to retrieve ACL live statistics.";

        revision "2016-11-29" {
            description "Initial revision of ACL live statistics";
        }

        typedef direction {
            type enumeration {
                enum ingress;
                enum egress;
                enum both;
            }
        }

        grouping acl-drop-counts {
            leaf drop-count {
                description "Packets/Bytes dropped by ACL rules";
                type uint64;
            }
            leaf invalid-drop-count {
                description "Packets/Bytes identified as invalid";
                type uint64;
            }
        }

        grouping acl-stats-output {
            description "Output for ACL port statistics";
            list acl-interface-stats {
                key "interface-name";
                leaf interface-name {
                    type leafref {
                        path "/if:interfaces/if:interface/if:name";
                    }
                }
                list acl-drop-stats {
                    max-elements "2";
                    min-elements "0";
                    leaf direction {
                        type identityref {
                            base "aclservice:direction-base";
                        }
                    }
                    container packets {
                        uses acl-drop-counts;
                    }
                    container bytes {
                        uses acl-drop-counts;
                    }
                }
                container error {
                    leaf error-message {
                        type string;
                    }
                }
            }
        }

        grouping acl-stats-input {
            description "Input parameters for ACL port statistics";

            leaf direction {
                type identityref {
                    base "aclservice:direction-base";
                }
                mandatory "true";
            }
            leaf-list interface-names {
                type leafref {
                    path "/if:interfaces/if:interface/if:name";
                }
                max-elements "unbounded";
                min-elements "1";
            }
        }

        rpc get-acl-port-statistics {
            description "Get ACL statistics for given list of ports";

            input {
                uses acl-stats-input;
            }
            output {
                uses acl-stats-output;
            }
        }
    }

Configuration impact
---------------------
No configuration parameters being added/deprecated for this feature

Clustering considerations
-------------------------
No additional changes required to be done as only one RPC is being supported as part of
this feature.

Other Infra considerations
--------------------------
N.A.

Security considerations
-----------------------
N.A.

Scale and Performance Impact
----------------------------
N.A.

Targeted Release
-----------------
Carbon

Alternatives
------------
Dispatcher table (table 17 and table 220) based approach of querying drop packets count was
considered. ie., arriving drop packets count by below rule:

**<total packets entered ACL tables> - <total packets entered subsequent service>**

This approach was not selected as this only provides total packets dropped count per port by ACL
services and does not provide details of whether it’s dropped by ACL rules or for some other
reasons.

Usage
=====
Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
Get ACL statistics
^^^^^^^^^^^^^^^^^^
Following API gets ACL statistics for given list of ports.

**Method**: POST

**URI**: /operations/acl-live-statistics:get-acl-port-statistics

**Parameters**:

=================     ===================     =================================     ==============
Parameter             Type                    Possible Values                       Comments
=================     ===================     =================================     ==============
"direction"           Enum                    ingress/egress/both                   Required

"interface-names"     Array [UUID String]     [<UUID String>,<UUID String>,.. ]     Required (1,N)
=================     ===================     =================================     ==============

**Example**:

.. code-block:: json

    {
        "input":
        {
             "direction": "both",
             "interface-names": [
                 "4ae8cd92-48ca-49b5-94e1-b2921a2661c5",
                 "6c53df3a-3456-11e5-a151-feff819cdc9f"
             ]
        }
    }

**Possible Responses**:

**RPC Success**:

.. code-block:: json

    {
        "output": {
        "acl-port-stats": [
        {
            "interface-name": "4ae8cd92-48ca-49b5-94e1-b2921a2661c5",
            "acl-drop-stats": [
            {
                "direction": "ingress",
                "bytes": {
                    "invalid-drop-count": "0",
                    "drop-count": "300"
                },
                "packets": {
                    "invalid-drop-count": "0",
                    "drop-count": "4"
                }
            },
            {
                "direction": "egress",
                "bytes": {
                    "invalid-drop-count": "168",
                    "drop-count": "378"
                },
                "packets": {
                    "invalid-drop-count": "2",
                    "drop-count": "9"
                }
            }]
        },
        {
            "interface-name": "6c53df3a-3456-11e5-a151-feff819cdc9f",
            "acl-drop-stats": [
            {
                "direction": "ingress",
                "bytes": {
                    "invalid-drop-count": "1064",
                    "drop-count": "1992"
                },
                "packets": {
                    "invalid-drop-count": "18",
                    "drop-count": "23"
                 }
            },
            {
                "direction": "egress",
                "bytes": {
                    "invalid-drop-count": "462",
                    "drop-count": "476"
                 },
                "packets": {
                    "invalid-drop-count": "11",
                    "drop-count": "6"
                }
            }]
        }]
    }

**RPC Success (with error for one of the interface)**:

.. code-block:: json

    {
        "output":
        {
            "acl-port-stats": [
            {
                "interface-name": "4ae8cd92-48ca-49b5-94e1-b2921a2661c5",
                "acl-drop-stats": [
                {
                    "direction": "ingress",
                    "bytes": {
                        "invalid-drop-count": "0",
                        "drop-count": "300"
                    },
                    "packets": {
                        "invalid-drop-count": "0",
                        "drop-count": "4"
                    }
                },
                {
                    "direction": "egress",
                    "bytes": {
                        "invalid-drop-count": "168",
                        "drop-count": "378"
                    },
                    "packets": {
                        "invalid-drop-count": "2",
                        "drop-count": "9"
                    }
                },
                {
                    "interface-name": "6c53df3a-3456-11e5-a151-feff819cdc9f",
                    "error": {
                        "error-message": "Interface not found in datastore."
                    }
                }]
            }]
        }
    }

.. Note::
   Below are error messages for the interface:

   (a) "Interface not found in datastore."
   (b) "Failed to find device for the interface."
   (c) "Unable to retrieve drop counts due to error: <<error message>>”
   (d) "Unable to retrieve drop counts as interface is not configured for statistics collection."
   (e) "Operation not supported for ACL <<Stateless/Transparent/Learn>> mode"

CLI
---
No CLI being added for this feature

Implementation
==============
Assignee(s)
-----------
Primary assignee:
  <Somashekar Byrappa>

Other contributors:
  <Shashidhar R>

Work Items
----------
#. Adding new drop rules per port (in table 41 and 252)
#. Yang changes
#. Supporting new RPC

Dependencies
============
This doesn't add any new dependencies.

This feature has dependency on below bug reported in OF Plugin:

`Bug 7232 - Problem observed with "get-flow-statistics" RPC call <https://bugs.opendaylight.org/show_bug.cgi?id=7232>`__

Testing
=======
Unit Tests
----------
Following test cases will need to be added/expanded

#. Verify ACL STAT RPC with single Neutron port
#. Verify ACL STAT RPC with multiple Neutron ports
#. Verify ACL STAT RPC with invalid Neutron port
#. Verify ACL STAT RPC with mode set to "transparent/learn/stateless"

Also, existing unit tests will be updated to include new drop flows.

Integration Tests
-----------------
Integration tests will be added, once IT framework is ready

CSIT
----
Following test cases will need to be added/expanded

#. Verify ACL STAT RPC with single Neutron port with different directions (ingress, egress, both)
#. Verify ACL STAT RPC with multiple Neutron ports with different
   directions (ingress, egress, both)
#. Verify ACL STAT RPC with invalid Neutron port
#. Verify ACL STAT RPC with combination of valid and invalid Neutron ports
#. Verify ACL STAT RPC with combination of Neutron ports with few having port-security-enabled as
   true and others having false

Documentation Impact
====================
This will require changes to User Guide. User Guide needs to be updated with details about new RPC
being supported and also about its REST usage.

References
==========
N.A.

.. note::

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode
