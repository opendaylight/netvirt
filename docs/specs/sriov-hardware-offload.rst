.. contents:: Table of Contents
   :depth: 3

===============================
SR-IOV Hardware Offload for OVS
===============================

https://git.opendaylight.org/gerrit/#/q/topic:sriov-hardware-offload

This feature aims to support OVS hardware offload using SR-IOV technology.
In Kernel 4.12 we introduced Traffic Control (TC see [1]) hardware offloads
framework for SR-IOV VFs which allows us to configure the NIC [2].
Subsequent OVS patches [3] allows us to use the TC framework
to offload OVS datapath rules. This feature is supported in OVS 2.8.0.

Problem description
===================
Current ODL implementation supports bind vnic_type normal and keep track on
the other neutron port types for DHCP usage. To support the OVS offload using
SR-IOV ODL should support binding the direct vnic_type with hardware offloading
("switchdev") support.

Use Cases
---------
As a cloud operator I would like to leverage the OVS hardware offload feature
to gain performance improvement.

Proposed change
===============
In addition to the "normal" and "direct" ports we are introducing a
port that supports hardware offloading.
This port is represented by vnic_type direct and with additional information
in binding profile::

'{"capabilities": ["switchdev"]}'

The "switchdev" capability indicates hardware offloading support, as the
NIC functions as a switch device, routing packages according to the offloaded rules.
When all the conditions are met ODL should connect
the VF representor port to the openflow pipeline.
A hostconfig example with the mentioned changes:

.. code-block:: json

    {"allowed_network_types": ["local", "flat", "vlan", "vxlan", "gre"],
     "bridge_mappings": {},
     "datapath_type": "system",
     "supported_vnic_types": [{"vif_type": "ovs",
                               "vnic_type": "normal",
                               "vif_details": {"support_vhost_user": false,
                                               "has_datapath_type_netdev": false,
                                               "uuid": "d8190c22-f6df-4236-964c-9aa3544d1e4c",
                                               "host_addresses": ["my_host_name"]}},
                               {"vif_type": "ovs",
                                "vnic_type": "direct",
                                "vif_details": {"support_vhost_user": false,
                                                "has_datapath_type_netdev": false,
                                                "uuid": "d8190c22-f6df-4236-964c-9aa3544d1e4c",
                                                "host_addresses": ["my_host_name"]}}]
    }

.. end

Pipeline changes
----------------
None

Yang changes
------------
None

Configuration impact
--------------------
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
----------------
Oxygen.

Alternatives
------------
None

Usage
=====

Features to Install
-------------------
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

REST API
--------
None

CLI
---
None

Implementation
==============

Assignee(s)
-----------

Primary assignee:
 - Edan David (edand@mellanox.com)
 - Moshe Levi (moshele@mellanox.com)

Work Items
----------
Update ODL's NeutronPortChangeListener methods:
handleNeutronPortCreated and handleNeutronPortDelete to allow
adding/removing VF representor from the ovs pipeline in the following case:
check that neutron port is vnic_type is direct and with
binding:profile '{"capabilities": ["switchdev"]}'.
Also check the hostconfig allows binding the direct port see
example:

.. code-block:: json

   {"vif_type": "ovs",
    "vnic_type": "direct",
    "vif_details": {"support_vhost_user": false,
                    "has_datapath_type_netdev": false,
                    "uuid": "d8190c22-f6df-4236-964c-9aa3544d1e4c",
                    "host_addresses": ["my_host_name"]}}

.. end


Dependencies
============
This doesn't add any new dependencies.

This feature has dependency on the v2 driver and pseudoagent port binding.

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
Update the documentation to provide explanation on the feature dependencies
and hostconfig configuration.

References
==========
[1] http://netdevconf.org/1.2/papers/efraim-gerlitz-sriov-ovs-final.pdf
[2] https://patchwork.ozlabs.org/patch/738176/
[3] https://mail.openvswitch.org/pipermail/ovs-dev/2017-April/330606.html
