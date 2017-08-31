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
SR-IOV ODL should support binding the direct vnic_type.

Use Cases
---------
As a cloud operator I would like to leverage the OVS hardware offload feature
to gain performance improvement.

Proposed change
===============
This feature should be enabled by hostconfig “supported_vnic_types” direct
and when user request vnic_type direct with binding profile
'{"capabilities": ["switchdev"]}. When all the conditions are met ODL should to
the VF representor to the openflow pipeline.
This will allow us to support legacy SR-IOV and OVS hardware offload on the
same compute nodes.

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
What release is this feature targeted for?

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
Update the handleNeutronPortCreated and handleNeutronPortDelete to allow to
add/remove VF representor from the ovs pipeline in the following case:
check that neutron port is vnic_type is direct and with
binding:profile '{"capabilities": ["switchdev"]}.
also check the the hostconfig allows binding the direct port see
example below:
{“supported_vnic_types”: [{
        “vnic_type”: “normal”,
        “vif_type”: “ovs”,
        “vif_details”: “{}”},
        {“vnic_type”: “direct”,
        “vif_type”: “ovs”,
        “vif_details”: “{}”}
    }]
    “allowed_network_types”: ["local", "gre", "vlan", "vxlan"]”,
    “bridge_mappings”: {“physnet1":"br-ex”}
}"

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
Update the documentation to provide explanation on the feature dependencies
and hostconfig configuration.

References
==========
[1] http://netdevconf.org/1.2/papers/efraim-gerlitz-sriov-ovs-final.pdf
[2] https://patchwork.ozlabs.org/patch/738176/
[3] https://mail.openvswitch.org/pipermail/ovs-dev/2017-April/330606.html
