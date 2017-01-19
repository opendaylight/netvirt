.. contents:: Table of Contents
      :depth: 3

=====================
Support for QoS Alert
=====================

https://git.opendaylight.org/gerrit/50533

Adds support to monitor the drop counts and log alert message when
Qos rate limit rule is applied.

Problem description
===================

If QoS bandwidth policy is applied on a neutron port, all packets exceeding
the limit shall get dropped by the switch. This spec proposes a new service
to monitor the packet drop ratio and raise an alert if packet drop ratio is
greater than the configured threshold value.

Use Cases
---------
Periodically monitor port statistics for the neutron ports having bandwidth
limit rule and log an alert message if drop packet ratio cross the threshold
value. This log can be analyzed offline later to check the health/diagnostics
of network.


Proposed change
===============

If qos-alert is enabled, per port statistics (rx_dropped and rx_packets) are
queried every 5 minutes and drop packet ratio is calculated.

Packet drop ratio is the ratio of dropped packets to the total packets received
on a port during the measurement interval

(rx_dropped  / rx_dropped + rx_packets)

An alert is logged/triggered is packet drop ration is greater than the configured
threshold.

Pipeline changes
----------------
None.

Yang changes
------------
A new yang file shall be created for qos-alert configuration as specified below:

.. code-block::

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

          }
      }



Configuration impact
---------------------
Following new parameters shall be made available as configuration. Initial or default configuration
is specified in netvirt-qosalert-config.xml

**1. qos-alert-enable** – Configurable parameter to enable/disable the alerts. Default value is
false.

**2. qos-drop-packet-threshold** – Configurable parameter to set the drop percentage threshold.
Default value is 5.

**3. qos-alert-log-file** – Configurable parameter to set the log file location and name. Default
value is qosalert/qos-alert.log

Clustering considerations
-------------------------
N.A.

Other Infra considerations
--------------------------
N.A.

Security considerations
-----------------------
None.

Scale and Performance Impact
----------------------------
N.A.

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

===========================  =======  ===============   ===========================================
        Parameter              Type   Possible Values                   Comments
===========================  =======  ===============   ===========================================
"qos-alert-enabled"          Boolean  true/false         Optional (default false)

"qos-drop-packet-threshold"  Uint16   0..100             Optional (default 5)

"qos-alert-log-file"         String   path to file       Optional (default qosalert/qos-alert.log)
===========================  =======  ===============   ===========================================


**Example**:

.. code-block::

 {
   "qosalert-config": {
    "qos-alert-enabled": true,

    "qos-drop-packet-threshold": 35,

    "qos-alert-log-file": "qosalert/qos-alert.log"

   }

 }


CLI
---

Following new karaf CLIs are added

**qos:enable-qos-alert <true|false>**

**qos:drop-packet-threshold <threshold value in %>**

**qos:alert-log-file-name <file-name>**


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
