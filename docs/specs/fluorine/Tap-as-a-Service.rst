.. contents:: Table of Contents
   :depth: 3

================
Tap-as-a-Service
================

[gerrit filter: https://git.opendaylight.org/gerrit/#/q/topic:TaaS]

Tap-as-a-Service provides port mirroring for tenant virtual networks.

Problem description
===================
Debugging a complex virtual network can be a difficult task for a cloud administrator.
The administrator does not have any visibility into the network. In order to analyze the network 
behavior we need to mirror the traffic and do a packet inspection.

Use Cases
---------
TaaS will provide visibility into the network, by monitoring the network traffic generated or received by the VM.
Port mirroring involves sending a copy of packets entering or leaving one port to another designated port.
TaaS can also provide data for network analytics and security applications.

The first phase of the feature will support the following use cases.

* Use case 1: Local port mirroring where the Tap Service Port(TSP) and Tap Flow Port(TFP) are on the same switch.
  Both the TFP and TSP are untagged Neutron Ports.
* Use case 2: Local Port mirroring where TFP is untagged and TSP is tagged sub-port.
* Use case 3: Local Port mirroring where TSP and TFP are both transparent.
* Use case 4: Local Port mirroring where TFP is untagged and TSP is transparent.
* Use case 5: Local Port mirroring where TFP is tagged sub-port and TSP is transparent.
  Remote port mirroring where the Tap Service Port and Tap Flow Port are on different switches.
* Use case 6: Remote port mirroring where the Tap Service Port and Tap Flow Port are on different switches.
  Both the TSP and TFP are untagged Neutron Ports
* Use case 7: Remote Port mirroring where TFP is untagged and TSP is tagged sub-port.
* Use case 8: Remote Port mirroring where TSP and TFP are both transparent.
* Use case 9: Remote Port mirroring where TFP is untagged and TSP is transparent.
* Use case 10: Remote Port mirroring where TFP is tagged sub-port and TSP is transparent.

Proposed change
===============
To realize tap services in OpenDaylight, a new tapservice module will be implemented. This module listens to neutron-tapaas configuration DS and fetches tap service and flow port data. This modules also listens to Interface State operational DS and retrieves the service and flow port related operational information. Based on the data, pipeline to realize the tap service is programmed. The tap service details are persisted so that if the VM that hosts the flow or service port migrates, the old flows can be detected and removed.


Pipeline changes
----------------
This features introduce the following pipeline:

Two new tables will be added as a part of this feature.
  OUTBOUND_TAP_CLASSIFIER_TABLE  = 150;
  INBOUND_TAP_CLASSIFIER_TABLE = 151;

Service priority for this feature is as follows :-
* For traffic on tap flow port bound to outbound traffic (TaaS API with direction == OUT) ,
  then TAP MUST be highest  priority ingress service (w.r.t switch) for that lport
  INGRESS_TAP_SERVICE_INDEX = 1
* For a tap flow port bound to inbound traffic (TaaS API with direction == IN ) ,
  then TAP MUST be lowest priority egress service (w.r.t to switch) for that port
  EGRESS_TAP_SERVICE_INDEX = 9

A block of IDs from Id Manager will be allocated to get the tunnel Id corresponding to the Tap Service
  TAP_SERVICE_LOW_ID = 350000L
  TAP_SERVICE_HIGH_ID = 375000L;

Tap Flow Port Egress Traffic
^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Mirroring traffic egressing from Tap Flow port will be of highest priority. So the ingress traffic
(w.r.t switch) will match the ``INGRESS_TAP_SERVICE_INDEX`` at the ``LPORT_DISPATCHER_TABLE`` and go to the ``OUTBOUND_TAP_CLASSIFIER_TABLE`` where the packet matching the ``Lport Tag`` of the Flow Tap port will be
* copied and sent based on the Egress Actions for the Tap Service Port either
    * to ``EGRESS_LPORT_DISPATCHER_TABLE`` to the VM port if the Tap Service port is in the same switch as Tap Flow port or
    * embed the label corresponding to Tap Service Id in the VNI field and sent onto the tunnel.
* original packet is ReSubmitted to the ``LPORT_DISPATCHER_TABLE``.

Tap Flow Port Ingress Traffic
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Mirroring traffic ingressing into the Tap Flow Port will be of the lowest priority. So the egress traffic (w.r.t switch) will match the ``EGRESS_TAP_SERVICE_INDEX`` at the
``EGRESS_LPORT_DISPATCHER_TABLE`` and go to ``Inbound_Tap_CLASSIFIER_TABLE`` where the packet matching the ``Lport Tag`` of the Flow Tap port will be
* copied and sent based on the Egress Actions for the Tap Service Port either
    * to ``EGRESS_LPORT_DISPATCHER_TABLE`` to the VM port if the Tap Service port is in the same switch as Tap Flow port or
    * embed the label corresponding to Tap Service Id in the VNI field and sent onto the tunnel.
* ReSubmit to ``EGRESS_LPORT_DISPATCHER_TABLE`

Tap Service Port Ingress Traffic
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
If the Tap Service port and Tap Flow port are on different switches then,
the copied packet will egress from the tunnel and will match on the tunnel id corresponding to the
Tap Service Id in the ``INTERNAL_TUNNEL_TABLE`` and go to ``EGRESS_LPORT_DISPATCHER_TABLE`` and from there it will be output onto the VM of the Service Tap port.


+-------------------------+---------------------------+----------------------------------+
| TABLE                   | MATCH                     |            ACTION                |
+=========================+===========================+==================================+
| LPORT_DISPATCHER_TABLE  | metadata=service priority |  goto OUTBOUND_TAP_CLASSIFIER    |
|                         | && lport-tag              |  _TABLE                          |
+-------------------------+---------------------------+----------------------------------+
| OUTBOUND_TAP_CLASSIFIER |  lport-tag=tap flow       |Action 1: GoTo EGRESS_LPORT_      |
|      _TABLE             |            port           |  DISPATCHER_TABLE, if same switch|
|                         |                           |  ONTO Tunnel port, if different  |
|                         |                           |Action 2:                         |
|                         |                           |  ReSubmit to LPORT_DISPATCHER    |
|                         |                           |  _TABLE                          |
+-------------------------+---------------------------+----------------------------------+
| EGRESS_LPORT_DISPATCHER |  Reg6==service Priority   | Go to INBOUND_TAP_CLASSIFIER     |
|  _TABLE                 |  && lport-tag             |             _TABLE               |
|                         |                           |                                  |
+-------------------------+---------------------------+----------------------------------+
| INBOUND_TAP_CLASSIFIER |  lport-tag=tap flow port   | Action 1: Output on the VM       |
|  _TABLE                 |                           | Service Port if same switch      |
|                         |                           |  ONTO Tunnel port, if different  |
|                         |                           | Action 2:                        |
|                         |                           |  ReSubmit to EGRESS_LPORT_       |
|                         |                           |  DISPATCHER_TABLE                |
+-------------------------+---------------------------+----------------------------------+
|  INTERNAL_TUNNEL_TABLE  | tunnel_id=tap service id  |  go to EGRESS_LPORT_DISPATCHER   |
|                         |                           |  TABLE                           |
+-------------------------+---------------------------+----------------------------------+

Tap Service with VLAN Tags
^^^^^^^^^^^^^^^^^^^^^^^^^^
+-------------------------+---------------------------+----------------------------------+
| TFP TYPE        | TSP TYPE           |  Packet entering TSP   |  Pipeline              |
+=========================+===========================+==================================+
| UnTagged        |  UnTagged          |   UnTagged             |   Normal               |
+----------------------------------------------------------------------------------------+
| UnTagged        |  Subport -Tag Y    |   Tagged with Tag Y    | Match on Lport Tag of   |
|                 |                    |                        |     subport            |
+----------------------------------------------------------------------------------------+
| Transparent     |   Transparent      |  Tag retained          |   Normal               |
+----------------------------------------------------------------------------------------+
| UnTagged        |  Transparent       |  UnTagged              |   Normal               |
+----------------------------------------------------------------------------------------+
| Subport- Tag X  |  Transparent       |   Tagged with Tag X    |  Lport Tag of the      |
|                 |                    |                        |  Subport               |
+----------------------------------------------------------------------------------------+

Yang changes
------------
New YANG model to support the tap service realization in opendaylight.

.. code-block:: none
   :caption: tapservice.yang

   grouping tap-port-attributes {
       description
           "Attributes for the service and flow port";
       leaf dpid {
           type uint64;
       }
       leaf port-number {
           type uint32;
       }
       leaf if-index {
           type int32;
       }
   }
   container tap-services-lookup {
       description "Container to store the list of tap services configured from openstack along
       with its service and flow port attributes. This is used to program the openflow rules
       on the switches corresponding to tap service and flow ports";

       list tap-services {
           key "tap-service-id";
           leaf tap-service-id {
               type yang:uuid;
               description "UUID of the Tap Service Instance";
           }
           leaf port-id {
               type yang:uuid;
               description "Destination port for traffic";
           }
           uses tap-port-attributes;
           list tap-flows {
               key "tap-flow-id";
               description "List of tap flows associated with the tap Service";
               leaf tap-flow-id {
                   type yang:uuid;
                   description "Tap flow Instance";
               }
               uses neutron-taas:tap-flow-attributes;
               uses tap-port-attributes;
           }
       }
   }


Configuration impact
--------------------
There is no change to any existing configuration.

Clustering considerations
-------------------------
Clustering support is already taken care in the infrastructure. There is no new requirement
for this feature.

Other Infra considerations
--------------------------
None.

Security considerations
-----------------------
Tap Service Port should be configured with the Openstack "port_security_enabled" set to "false" to enable tap traffic to ingress it.

Scale and Performance Impact
----------------------------
The performance impact of mirroring on the switches needs to be tested and documented

Targeted Release
----------------
Fluorine.

Alternatives
------------
None.

Usage
=====

Features to Install
-------------------
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

Workflow
--------

Following are the steps to be followed for mirroring a Neutron port.

1. Neutron port with "port_security_enabled" must be set to "false".
2. Launch a VM for receiving mirrored data. Associate the Neutron port in step 1
   while creating the VM.
3. Create a Tap Service Port and associate with the port created in Step 1 either using
   Neutron Client command for TaaS, "neutron tap-service-create" or using REST API.
4. Create a Tap Flow Port using Neutron Client command for TaaS, "neutron tap-flow-create" or
   through REST API and associate with the Tap Service instance create in Step 3 and the target
   Neutron port whose traffic needs to be mirrored. Mirroring can be done for both incoming
   and/or outgoing traffic from the target Neutron port.

REST API
--------
Tap Service and Flow port can also be created using the following REST API.

Create TapService
^^^^^^^^^^^^^^^^^

**URL:** /POST /v2.0/taas/tap_services

**Sample JSON data**

.. code-block:: json

  {
    "tap_service": {
        "description": "Test_Tap",
        "name": "Test",
        "port_id": "c9beb5a1-21f5-4225-9eaf-02ddccdd50a9",
        "tenant_id": "97e1586d580745d7b311406697aaf097"
    }
  }

Create TapFlow
^^^^^^^^^^^^^^

**URL:** POST /v2.0/taas/tap_flows

**Sample JSON data**

.. code-block:: json
   {
    "tap_flow": {
        "description": "Test_flow1",
        "direction": "BOTH",
        "name": "flow1",
        "source_port": "775a58bb-e2c6-4529-a918-2f019169b5b1",
        "tap_service_id": "69bd12b2-0e13-45ec-9045-b674fd9f0468",
        "tenant_id": "97e1586d580745d7b311406697aaf097"
    }
   }

Delete TapService
^^^^^^^^^^^^^^^^^
**URL:** DELETE /v2.0/taas/tap_services/{tap_service_uuid}

Delete TapFlow
^^^^^^^^^^^^^^
**URL:** DELETE /v2.0/taas/tap_flows/{tap_flow_uuid}

CLI
---
None.

Implementation
==============

Assignee(s)
-----------
Primary assignee:
  <Hema Gopalakrishnan> (hema.gopalkrishnan@ericsson.com)

Work Items
----------
1. Add a new bundle
2. Define a new Yang
3. Add listener to neutron-tapaas configuration DS and do the processing.
4. Add listener to Interface State Operational DS.
5. Support Tap Service add for each of the use case.
6. Support Tap Service delete scenario.
7. Support VM migration
8. Add UTs.
9. Add ITs.
10. Add CSIT.
11. Add Documentation

Dependencies
============
Taap driver in networking-odl needs to be implemented.

Testing
=======

Unit Tests
----------
Relevant Unit Test cases will be added.

Integration Tests
-----------------
1. Configure Tap Service and Flow ports in the same switch and verify the traffic from the flow ports
   are mirrored to the tap service port.
2. Configure Tap Service and Flow ports in different switches and verify that traffic flows through
   tunnel to reach the tap service port.
3. Configure the Tap Service and flow ports with VLAN tags as untagged, tagged and transparent and
   verify each use case.
4. Configure the Tap Flow port with different mirroring direction and verify the appropriate behavior.

CSIT
----
Relevant CSIT will be added.

Documentation Impact
====================
This will require changes to User Guide and Developer Guide.

User Guide needs to be updated with information on how to configure Tap Services.

References
==========
[1] Netvirt Florine Release Plan -
    https://docs.google.com/spreadsheets/d/1bDygyIwNOGFEEFDTQJN2LqoTyfmaxwtka-AlwkPcvzE/edit#gid=1799274276

[2] Netvirt Trello Card

[3] Opensstack API Reference - https://github.com/openstack/tap-as-a-service/blob/master/API_REFERENCE.rst