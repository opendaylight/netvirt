.. contents:: Table of Contents
            :depth: 3

==============================================================
Element To Element Counters
==============================================================

https://git.opendaylight.org/gerrit/#/c/50771/

Netvirt Statistics Plugin: https://git.opendaylight.org/gerrit/#/c/50164/8

This feature enables getting statistics regarding traffic passed between two network elements.

Problem description
===================

Collecting statistics on traffic between two specified VMs is currently not possible.

Use Cases
---------

- East/West communication between local VMs.
- East/West communication between VMs that are located in different compute nodes.
- Communication between a local VM and an IP located in an external network. 

Proposed change
===============

The Netvirt Statistics Plugin will receive requests regarding element to element counters. A new service will be implemented ("CounterService"), and will be associated with the relevant interfaces (both ingress and egress sides).

* Ingress traffic: The service will be the first one in the pipeline, closest to the traffic received from the port.
* Egress traffic: The service will be the last one in the pipeline, just before traffic is being output to the port. 
* The input for counters request regarding VM A, and incoming and outgoing traffic from VM B, will be VM A interface uuid and VM B IP.
* If the counters request involves 2 VMS (either in the same compute node or in different compute nodes), service binding will be done on both VMs relevant interfaces.
* If the counters request involves an external IP, service binding will be done only on the VM interface.

Two new tables will be used: table 39 for ingress traffic from the VM, and table 254 for egress traffic from the VM. A new rule will be installed in each table for each VM, as described below in the "Pipline changes" section. The default rule will resubmit traffic to the appropriate dispatcher table. 

The Statictics Plugin will use OpenFlow flow statistic requests for these new rules, allowing it to gather statistics regarding the traffic between the 2 elements. It will be responsible to validate and filter the counters results.

Pipeline changes
----------------
Assuming we want statistics on traffic on both sides between VM A and VM B.

VM A Ingress traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
In table 39 traffic will be matched against src-ip and lport tag.

  | Ingress dispatcher table (17): ``match: lport-tag=vmA-interface, actions: go to table 39`` =>
  | Ingress counters table  (39): ``match: src-ip=vmB-ip, lport-tag=vmA-interface, actions: go to table 17`` =>

VM A Egress traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Egress dispatcher table (220): ``match: lport-tag=vmB-interface, actions: go to table 254`` =>
  | Egress counters table (254): ``match: lport-tag=vmB-interface, dst-ip=vmB-ip, actions: go to table 220`` =>

VM B Ingress traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
In table 39 traffic will be matched against src-ip and lport tag.

  | Ingress dispatcher table (17): ``match: lport-tag=vmB-interface, actions: go to table 39`` =>
  | Ingress counters table  (39): ``match: src-ip=vmA-ip, lport-tag=vmB-interface, actions: go to table 17`` =>

VM B Egress traffic (vm interface)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Egress dispatcher table (220): ``match: lport-tag=vmA-interface, actions: go to table 254`` =>
  | Egress counters table (254): ``match: lport-tag=vmA-interface, dst-ip=vmA-ip, actions: go to table 220`` =>

Yang changes
---------------
Netvirt Statistics module will be enhanced with the following RPC:
::

    rpc getElementToElementCounters {
        input {
            leaf portId {
                type string;
            }
            leaf filterIp {
                type string;
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
None

Usage
=====

* Create router, network, 2 VMS, VXLAN tunnel.
* Connect to each one of the VMs and send ping to the other VM.
* Use REST to get the statistics.

Run the following to get interface ids:

::

    http://10.0.77.135:8181/restconf/operational/ietf-interfaces:interfaces-state/

Choose VM B interface and use the following REST in order to get the statistics:
Assuming VM A IP = 1.1.1.1, VM B IP = 2.2.2.2

::

    10.0.77.135:8181/restconf/operations/statistics-plugin:getElementToElementCounters, input={"input": {"portId":"b99a7352-1847-4185-ba24-9ecb4c1793d9", "filterIp":"1.1.1.1"}}, headers={Authorization=Basic YWRtaW46YWRtaW4=, Cache-Control=no-cache, Content-Type=application/json}]

Features to Install
-------------------
odl-netvirt-genius-openstack

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
https://trello.com/c/88MnwGwb/129-element-to-element-counters

* Add new service in Genius.
* Implement new rules installation.
* Update Netvirt Statistics module to support the new counters request.

Dependencies
============

None

Testing
=======

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

