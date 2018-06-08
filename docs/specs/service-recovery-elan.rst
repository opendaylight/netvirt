
.. contents:: Table of Contents
   :depth: 3

===============================
ELAN Service Recovery Test Plan
===============================

Test plan for testing service recovery manager functionalities.

Test Setup
==========
Test setup consists of ODL with `odl-netvirt-openstack` feature installed and
minimum two DPNs connected to ODL over OVSDB and OpenflowPlugin.

Hardware Requirements
---------------------
N.A

Software Requirements
--------------------------
Openstack queens + OVS 2.8

Test Suite Requirements
=======================

Test Suite Bringup
------------------
Following steps are followed at the beginning of test suite:

* Bring up controller with `odl-netvirt-openstack` feature installed
* Bring up minimum two DPNs with tunnel between them
* Create network
* Create subnet
* Create at least two VMs in each DPN
* Verify table 50/51 flows in both DPNs

Test Suite Cleanup
------------------
Following steps are followed at the end of test suite:

* Delete VMs
* Delete subnet
* Delete network

Debugging
---------
Capture any debugging information that is captured at start of suite and end of suite.

Test Cases
==========

ELAN Service Recovery
---------------------
Verify SRM by recovering ELAN Service.

Test Steps and Pass Criteria
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

#. Delete table 50/51 flow(s) corresponding to MAC address(es) of VM(s) (try deleting multiple flows)
   in any of the DPNs manually or via REST
#. Verify if table 50/51 flow(s) is/are deleted in both controller and OVS.
#. Login to karaf and use elan service recovery CLI
#. Verify if corresponding table flow(s) is/are recovered in on both controller and ovs

ELAN Interface Recovery
-----------------------
Verify SRM by recovering ELAN Interface.

Test Steps and Pass Criteria
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

#. Delete table 50/51 flow corresponding to MAC address of any of the VMs.
#. Verify if table 50/51 flow is deleted in both controller and OVS.
#. Login to karaf and use elan interface recovery CLI
#. Verify if corresponding table flow is recovered in both controller and OVS.

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Swati Niture (swati.udhavrao.niture@ericsson.com)


Other contributors:
  N.A.

Work Items
----------
N.A.

References
==========

http://docs.opendaylight.org/en/latest/submodules/genius/docs/specs/service-recovery.html#srm-operations



