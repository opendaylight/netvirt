.. contents:: Table of Contents
      :depth: 3

=====================
Support for QoS Alert
=====================

https://git.opendaylight.org/gerrit/#/q/topic:qos-alert

This feature adds support to monitor the per port packet drop counts when QoS rate limit rule is
applied.

Problem description
===================

If QoS bandwidth policy is applied on a neutron port, all packets exceeding the rate limit are 
dropped by the switch. This spec proposes a new service to monitor the packet drop ratio and log 
the alert message if packet drop ratio is greater than the configured threshold value.

Use Cases
---------
Periodically monitor the port statistics of neutron ports having bandwidth limit rule and log an
alert message in a log file if packet drop ratio cross the threshold value. Log file can be
analyzed offline later to check the health/diagnostics of the network.


Proposed change
===============
Proposed new service will use the RPC
``/operations/opendaylight-direct-statistics:get-node-connector-statistics`` provided by
openflowplugin to retrieve port statistics directly from switch. Polling frequency is configurable
with default value of 2 minutes.

Port packet drop ratio is calculated using delta of two port statistics counters
``rx_dropped`` and ``rx_received`` between the sample interval.

 ``packet drop ratio =  100 * (rx_dropped  / (rx_received + rx_dropped ))``

An alert message is logged if packet drop ratio is greater than the configured threshold value.
Existing karaf pax logging infrastructure shall be used to log the alert messages in the log file.
A new ``log4j`` appender ``qosalertmsg`` shall be added in ``org.ops4j.pax.logging.cfg`` to define the
different logging properties like file name, maximum file size and maximum number of backup files.

Log file name is also configurable through karaf shell CLI. Currently, log file can be created only
on local or network file system and URL to file is not supported.

Log file format
---------------
.. code-block:: bash

 2017-01-17 01:17:49 Packet drop threshold hit for qos policy qospolicy1 with qos-id qos-2dbf02f6-dcd1-4c13-90ee-6f727e21fe8d for port port-3afde68d-1103-4b8a-a38d-9cae631f7d67 on network network-563f9610-dd91-4524-ae23-8ec3c32f328e rx_received 4831 rx_dropped 4969 
 2017-01-17 01:17:49 Packet drop threshold hit for qos policy qospolicy2 with qos-id qos-cb7e5f67-2552-4d49-b534-0ce90ebc8d97 for port port-09d3a437-f4a4-43eb-8655-85df8bbe4793 on network network-389532a1-2b48-4ba9-9bcd-c1705d9e28f9 rx_received 3021 rx_dropped 4768
 2017-01-17 01:19:49 Packet drop threshold hit for qos policy qospolicy1 with qos-id qos-2dbf02f6-dcd1-4c13-90ee-6f727e21fe8d for port port-3afde68d-1103-4b8a-a38d-9cae631f7d67 on network network-563f9610-dd91-4524-ae23-8ec3c32f328e rx_received 3837 rx_dropped 3961
 2017-01-17 01:19:49 Packet drop threshold hit for qos policy qospolicy2 with qos-id qos-cb7e5f67-2552-4d49-b534-0ce90ebc8d97 for port port-09d3a437-f4a4-43eb-8655-85df8bbe4793 on network network-389532a1-2b48-4ba9-9bcd-c1705d9e28f9 rx_received 2424 rx_dropped 2766

Pipeline changes
----------------
None.

Yang changes
------------
A new yang file qos-alert-config.yang shall be created for qos-alert configuration as specified
below:

.. code-block:: none
   :caption: qos-alert-config.yang

      module qosalert-config {
          yang-version 1;
          namespace "urn:opendaylight:params:xml:ns:yang:netvirt:qosalert:config";
          prefix "qosalert";

          revision "2017-01-03" {
              description "Initial revision of qosalert model";
          }

          description "This YANG module defines QoS alert configuration.";

          container qosalert-config {

          config true;

            leaf qos-alert-enabled {
               description "QoS alert enable-disable config knob";
               type boolean;
               default false;
            }

            leaf qos-drop-packet-threshold {
            description "QoS Packet drop threshold config. Specified as % of rx packets";
               type uint8 {
                  range "1..100";
               }
               default 5;
            }

            leaf qos-alert-log-file {
               description "Path and name of log file";
               type string;
               default alerts/qos/qos-alerts.log;
            }

            leaf qos-alert-poll-interval {
              description "Polling interval in minutes";
              type uint16 {
                  range "1..3600";
              }
              default 2;
            }

          }
      }



Configuration impact
---------------------
Following new parameters shall be made available as configuration. Initial or default configuration
is specified in netvirt-qosalert-config.xml

======== =============================  ====================================================
  Sl No.  configuration                 Description
======== =============================  ====================================================
1.       ``qos-alert-enabled``          configuration parameter to enable/disable the alerts

2.       ``qos-drop-packet-threshold``  Drop percentage threshold configuration.

3.       ``qos-alert-log-file``         Name and location of log file.

4.       ``qos-alert-poll-interval``    Polling interval in minutes
======== =============================  ====================================================

Clustering considerations
-------------------------
In cluster setup, only one instance of qosalert service shall poll for port statistics.
Entity owner service (EOS) shall be used to determine the owner of service.

Other Infra considerations
--------------------------
N.A.

Security considerations
-----------------------
None.

Scale and Performance Impact
----------------------------
QoS Alert Service miniizes scale and performance impact by following:

- This uses direct-statistics RPC instead of OpenflowPlugin statistics-manager. This is lightweight
  beause only node-connector statistics are queries intead of all statistics
- Polling interval is specified in minutes and it's quite high. Default polling interval is **two minutes**.

Targeted Release
-----------------
Carbon.

Alternatives
------------
N.A.

Usage
=====

Features to Install
-------------------
This feature can be used by installing ``odl-netvirt-openstack``.
This feature doesn't add any new karaf feature.

REST API
--------
Put Qos Alert Config
^^^^^^^^^^^^^^^^^^^^
Following API puts Qos Alert Config.

**Method**: POST

**URI**:  /config/qosalert-config:qosalert-config

**Parameters**:

=============================  =======  ============  ===============================================
        Parameter              Type     Value range                   Comments
=============================  =======  ============  ===============================================
``qos-alert-enabled``          Boolean  true/false    Optional (default false)

``qos-drop-packet-threshold``  Uint16   1..100        Optional (default 5)

``qos-alert-log-file``         String   path to file  Optional (default alerts/qos/qos-alerts.log)

``qos-alert-poll-interval``    Uint16   1..65535      Optional time interval in minute(s) (default 2)
=============================  =======  ============  ===============================================


**Example**:

.. code-block:: json

 {
    "input":
    {
        "qos-alert-enabled": true,

        "qos-drop-packet-threshold": 35,

        "qos-alert-log-file": "alerts/qos/qos-alerts.log",

        "qos-alert-poll-interval": 5

   }

 }


CLI
---

Following new karaf CLIs are added


.. code-block:: bash


 qos:enable-qos-alert <true|false>

 qos:drop-packet-threshold <threshold value in %>

 qos:alert-log-file-name <file-name>

 qos:alert-poll-interval <polling interval in minutes>

Implementation
==============

Assignee(s)
-----------

Primary assignee:
 - Arun Sharma (arun.e.sharma@ericsson.com)

Other contributors:
 - Ravi Sundareswaran (ravi.sundareswaran@ericsson.com)
 - Mukta Rani (mukta.rani@tcs.com)

Work Items
----------
Trello Link <https://trello.com/c/780v28Yw/148-netvirt-qos-alert>

#. Adding new yang file and then listener.
#. Adding new ``log4j appender`` in odlparent ``org.ops4j.pax.logging.cfg`` file.
#. Retrieval of port statistics data using the openflowplugin RPC.
#. Logging alert message into the log file.
#. UT and CSIT

Dependencies
============
This doesn't add any new dependencies.


Testing
=======
Capture details of testing that will need to be added.

Unit Tests
----------
Standard UTs will be added.

Integration Tests
-----------------
N.A.

CSIT
----
Following new CSIT tests shall be added 

1. Verify that alerts are generated if drop packets percentage is more than the configured threshold
   value.
2. Verify that alerts are not generated if drop packets percentage is less than threshold value.
3. Verify that alerrs are not generated when ``qos-alert-enabled`` if false irrespective of drop
   packet percentage.

Documentation Impact
====================
This will require changes to User Guide.

User Guide will need to add information on how qosalert service can
be used.

References
==========

[1] `Neutron QoS <http://docs.openstack.org/developer/neutron/devref/quality_of_service.html>`__

[2] `Spec for NetVirt QoS <http://docs.opendaylight.org/en/latest/submodules/netvirt/docs/specs/qos.html>`__ 

[3] `Openflowplugin port statistics
<https://github.com/opendaylight/openflowplugin/blob/master/model/model-flow-statistics/src/main/yang/opendaylight-direct-statistics.yang>`__
