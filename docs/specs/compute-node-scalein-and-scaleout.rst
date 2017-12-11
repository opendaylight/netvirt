.. contents:: Table of Contents
   :depth: 3

===============================================
Support for compute node scale in and scale out
===============================================

https://git.opendaylight.org/gerrit/#/q/topic:compute-scalein-scaleout

Add support for adding a new compute node into the existing topology
and for removing or decomissioning existing compute node from topology.

Problem description
===================
Support for adding a new compute node is already available.
But when we scale in a compute node, we have to cleanup its relevant flows
from openflow tables and cleanup the vxlan tunnel endpoints from other compute nodes.
Also if the scaled in compute node is the designated compute for a particular service
like nat or subnetroute etc , then those services have to choose a new compute node.

Use Cases
---------
* Scale out of compute nodes.
* Scale in of single compute node
* Scale in of a bunch of compute nodes


Proposed change
===============

The following steps will be taken to achieve compute node scale in.

* The Nova Compute(s) shall be set into maintenance mode (nova service-disable <hostname> nova-compute).

This to avoid VM's to be scheduled to these Compute Hosts.

* Call a new rpc scalein-computes-start <list of scaledin compute node names> to mark them as tombstoned.

* VMs still residing on the Compute Host(s), shall be migrated from the Compute Host(s) or deleted.

* Delete tep related information from ITM (Internal tunnel mechanism)

* Disconnect the compute node from opendaylight controller node.

To signal the end of scale in process the following rpc will be invoked

Call a new rpc scalein-computes-end <list of scaledin compute node names>.

Incase vm migration or deletion from some of these compute nodes fails

The following recovery rpc will be invoked

scalein-compute-recover <list of not scaled in computes which were passed as arg in scalein-computes-start>

Typically When a single compute node gets scaled in as it gets disconnected from controller
all the services who designated this compute as their designated compute would re-elect another
compute node.

But when multiple compute nodes are getting scaled in during that window some of these computes
should not be elected as designated compute.

To achieve that these scaled in computes are marked as tombstoned and they should be avoided when
doing designated switch election or programming new services.

When we receive scalein-compute-end rpc call then corresponding computes config inventory and topology
database also can be deleted.

Pipeline changes
----------------

None.

Yang changes
------------

The following rpcs will be added.

.. code-block:: none
   :caption: scalein-api.yang

        rpc scalein-computes-start {
            description "To trigger start of scale in the given dpns";
            input {
                leaf-list scalein-node-ids {
                    type string;
                }
            }
        }

        rpc scalein-computes-end {
            description "To end the scale in of the given dpns";
            input {
                leaf-list scalein-node-ids {
                    type string;
                }
            }
        }

        rpc scalein-computes-recover {
            description "To recover the dpns which are marked for scale in";
            input {
                leaf-list recover-node-ids {
                    type string;
                }
            }
        }


Topology node bridge-external-ids will be updated with additional key called "tombstoned".


Configuration impact
---------------------
None.

Clustering considerations
-------------------------
None.

Other Infra considerations
--------------------------
None.

Security considerations
-----------------------
None.

Scale and Performance Impact
----------------------------
None

Targeted Release
-----------------
Oxygen

Alternatives
------------
None.

Usage
=====
N/A.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
N/A.

CLI
---
N/A.

Implementation
==============

Assignee(s)
-----------
Primary assignee:

* suneelu varma (k.v.suneelu.verma@ericsson.com)

Other contributors:

* Hanmanth (hanamantagoud.v.kandagal@ericsson.com)
* Chetan (chetan.arakere@altencalsoftlabs.com)

Work Items
----------
TODO

Dependencies
============
No new dependencies.

Testing
=======
* Verify that scaled out compute vms should be able to communicate with inter and intra compute vms.
* Verify that scale in compute flows be removed and existing service continue work.
* Verify that scale in compute nodes config inventory and topology datastores are cleaned.
* Identify a compute node which is designated for NAT/subnetroute functionality , scale in that compute,
  verify that NAT/subnetroute functionality continues to work. Verify that its relevant flows are reprogrammed.
* While the scale in work flow is going on for few computes, create a new NAT/subnetroute resource,
  make sure that one of these compute nodes are not chosen.
* Verify the recovery procedure of scale in workflow, make sure that the recovered compute gets
  its relevant flows.
* Scale in a compute which is designated and no other compute has presence of that service (vpn)
  to be designated, make sure that all its flows and datastores are deleted.
* Start scale in for a compute which is designated and no other compute has presence of that service (vpn)
  to be designated, recover the compute and make sure that all its flows and datastores are recovered.

Unit Tests
----------
N/A.

Integration Tests
-----------------
N/A.

CSIT
----
* Verify that scale out compute vms should be able to communicate with inter and intra compute vms.
* Verify that scale in compute flows be removed and existing service continue work.
* Identify a compute node which is designated for NAT/subnetroute functionality , scale in that compute,
  verify that NAT/subnetroute functionality continues to work. Verify that its relevant flows are reprogrammed.
* Verify the recovery procedure of scale in workflow, make sure that the recovered compute gets
  its relevant flows.

Documentation Impact
====================
N/A

References
==========
N/A
