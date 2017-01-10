..
   Key points to consider:
  * Use RST format. For help with syntax refer http://sphinx-doc.org/rest.html
  * Use http://rst.ninjs.org/ a web based WYSIWYG RST editor.
  * For diagrams, you can use http://asciiflow.com to make ascii diagrams.
  * MUST READ http://docs.opendaylight.org/en/latest/documentation.html and follow guidelines.
  * Use same topic branch name for all patches related to this feature.
  * All sections should be retained, but can be marked None or N.A.
  * Set depth in ToC as per your doc requirements. Should be at least 2.

.. contents:: Table of Contents
         :depth: 3

=====================
Netvirt Statistics
=====================

https://git.opendaylight.org/gerrit/#/c/50164/

The feature enables getting statistics on ports, tunnels and switches.


Problem description
===================

Being able to ask for statistics, given as input Netvirt identifiers. It will enable filtering the results and having aggregated result. In a later stage, it will be also used to get element to element counters.
Examples for possible filters: RX only, TX only, port + VLAN counters...

Use Cases
---------

* Getting port counters, given its dpid.
* Getting tunnel counters, given its dpid.
* Getting host counters, given its dpid.

Proposed change
===============

Adding a new bundle named "statistics-plugin" to Netvirt. This bundle will be responsible for converting the Netvirt unique identifiers into OpenFlow ones, and will get the relevant statistics by using OpenFlowPlugin capabilities. It will also be responsible of validating and filtering the results. It will be able to provide a wide range of aggregated results in the future.

Pipeline changes
----------------
None

Yang changes
------------
The new plugin introduced will have the following models:
::

    grouping result {
            list groups {
                key name;
                leaf name {
                    type string;
                }
                list counters {
                    key name;
                    leaf dpId {
                        type int64;
                    }
                    leaf value {
                        type uint64;
                    }
                }
            }
    }

    grouping filters{
            leaf-list groupFilters {
                type string;
            }
            leaf-list counterFilter {
                type string;
            }
    }

    rpc getTunnelCounters {
        input {
            leaf dpId {
                type int64;
            }
            uses filters;          
        }
        output {
                uses result;
        }
    }

    rpc getPortCounters {
        input {
            leaf dpId {
                type int64;
            }
            uses filters;
        }
        output {
                uses result;
        }
    }

    rpc getHostCounters {
        input {
            leaf dpId {
                type int64;
                }
            }
        output {
                uses filters;
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
None

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

    10.0.77.135:8181/restconf/operations/statistics-plugin:getPortCounters, input={"input": {"dpId":"233637712977072"}}, headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]

Tunnel statistics:

::

    http://10.0.77.135:8181/restconf/operational/ietf-interfaces:interfaces-state/

Choose a tunnel id and use the following REST in order to get the statistics:

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:getTunnelCounters, input={"input": {"dpId":"233637712977076"}}, headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]

Host statistics:

::

    http://10.0.77.135:8181/restconf/config/odl-interface-meta:bridge-interface-info/

Choose a bridge id and use the following REST in order to get the statistics:

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:getHostCounters, input={"input": {"dpId":"233637712977071"}}, headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]


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

* Adding support for port counters.
* Adding support for tunnel counters.
* Adding support for host counters.

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

[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode

