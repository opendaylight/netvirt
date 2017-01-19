.. contents:: Table of Contents
      :depth: 3

=====================
Support for QoS Alert
=====================

https://git.opendaylight.org/gerrit/50533

This feature adds support to monitor the per port packet drop counts when
QoS rate limit rule is applied.

Problem description
===================

If QoS bandwidth policy is applied on a neutron port, all packets exceeding
the rate limit are dropped by the switch. This spec proposes a new service
to monitor the packet drop ratio and log the alert message if packet drop
ratio is greater than the configured threshold value.

Use Cases
---------
Periodically monitor the port statistics of neutron ports having bandwidth
limit rule and log an alert message if packet drop ratio cross the threshold
value. Log file can be analyzed offline later to check the health/diagnostics
of the network.


Proposed change
===============
Proposed new service will use the RPC ``/operations/opendaylight-direct-statistics:get-node-connector-statistics``
provided by lithium openflowplugin to retrieve port statistics directly from switch. Polling
frequency is configurable with default value of 2 minutes.

Port packet drop ratio is calculated using delta of two port statistics counters
``rx_dropped`` and ``rx_dropped`` between the sample interval.

 ``packet drop ratio =  100 * (packets dropped  / packets received)``

An alert message is logged if packet drop ratio is greater than the configured threshold value.
Apart from karaf log (debug level), alert messages are written into a plain text file so that it does not get wrapped
up and available for offline analysis. Name and location of log file is configurable.

Pipeline changes
----------------
None.

Yang changes
------------
A new yang file qos-alert-config.yang shall be created for qos-alert configuration as specified below:

::

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
               type boolean;
               default false;
            }

            leaf qos-drop-packet-threshold {
               type uint16;
               default 5;
            }

            leaf qos-alert-log-file {
               type string;
               default qosalert/qos-alert.log;
            }

            leaf qos-alert-poll-interval {
               type uint16;
               default 2;
            }

          }
      }



Configuration impact
---------------------
Following new parameters shall be made available as configuration. Initial or default configuration
is specified in netvirt-qosalert-config.xml

=========  ===========================  ====================================================
  Sl No.   configuration                Description
=========  ===========================  ====================================================
"1."       "qos-alert-enabled"          configuration parameter to enable/disable the alerts

"2."       "qos-drop-packet-threshold"  Drop percentage threshold configuration.

"3."       "qos-alert-log-file"         Name and location of log file.

"4."       "qos-alert-poll-interval"    Polling interval in minutes
=========  ===========================  ====================================================

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
QoS alert service does not have performance impact because -

-  New service does not use statistics data maintained by Openflowplugin statistics-manager;
   uses direct-statistics RPC instead. This is lightweight because only node-connector statistics are queried.
- Polling interval is specified in minutes and it's quite high. Default polling interval is two minutes.

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
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

REST API
--------
Put Qos Alert Config
^^^^^^^^^^^^^^^^^^^^
Following API puts Qos Alert Config.

**Method**: POST

**URI**:  /config/qosalert-config:qosalert-config

**Parameters**:

===========================  =======  ===============   ================================================
        Parameter              Type   Possible Values                   Comments
===========================  =======  ===============   ================================================
"qos-alert-enabled"          Boolean  true/false         Optional (default false)

"qos-drop-packet-threshold"  Uint16   1..100             Optional (default 5)

"qos-alert-log-file"         String   path to file       Optional (default qosalert/qos-alert.log)

"qos-alert-poll-interval"    Uint16   1..65535           Optional time interval in minute(s) (default 2)
===========================  =======  ===============   ================================================


**Example**:

::

 {
   "qosalert-config": {
    "qos-alert-enabled": true,

    "qos-drop-packet-threshold": 35,

    "qos-alert-log-file": "qosalert/qos-alert.log",

    "qos-alert-poll-interval": 5

   }

 }


CLI
---

Following new karaf CLIs are added

::

 qos:enable-qos-alert <true|false>

 qos:drop-packet-threshold <threshold value in %>

 qos:alert-log-file-name <file-name>

 qos:qos-alert-poll-interval <polling interval in minutes>

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Arun Sharma (arun.e.sharma@ericsson.com)

Other contributors:
  Ravi Sundareswaran (ravi.sundareswaran@ericsson.com)

Work Items
----------
N.A.

Dependencies
============
This doesn't add any new dependencies.


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
This will require changes to User Guide.

User Guide will need to add information on how qosalert service can
be used.

References
==========

[1] `Spec for NetVirt QoS <https://git.opendaylight.org/gerrit/48949>`__

[2] `Openflowplugin port statistics
<https://github.com/opendaylight/openflowplugin/blob/master/model/model-flow-statistics/src/main/yang/opendaylight-port-statistics.yang>`__
