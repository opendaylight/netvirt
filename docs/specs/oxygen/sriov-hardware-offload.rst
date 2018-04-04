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
As a cloud operator, I would like to leverage the OVS hardware offload feature
to gain performance improvement.

Proposed change
===============
In addition to the ``normal`` and ``direct`` ports we are introducing a
port that supports hardware offloading.
We refer to a port supporting HW offloading as a direct port with
"switchdev" capability, as the NIC functions as an embedded switch device,
routing packages according to the offloaded rules.
A port is considered a "switchdev" port if the following applies:

1. The port's vnic_type is ``direct``

2. The port contains additional information in ``binding profile:
   '{"capabilities": ["switchdev"]}'``

3. The port is bound to a host that supports vnic_type direct
This is validated by retrieving the hostconfig by the port's
host id. This is done as extra validation.
An example of A hostconfig supporting direct ports:


.. code-block:: json
   :emphasize-lines: 11

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


Note that in order to validate the hostconfig the port must be
bound to a hypervisor, this lead to workflow changes when creating
ports of type switchdev.

Workflow
--------
For SR-IOV legacy port - all workflows remain unchanged:
On Neutron port create event we call handleNeutronPortCreate and update the subnet map
to hold the port's IP in the relevant subnet id.

For Switchdev port:

* On Neutron port create event we ignore switchdev ports.
* On Neutron port update event we check if the port is bound to a host,
  if so we call handleNeutronPortCreate and update the subnet, in addition, we
  create an Openflow and Elan interfaces (just like a "normal" type port).


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
This feature does not support security groups.

Scale and Performance Impact
----------------------------
For every newly bound switchdev port, A DS read is executed to retrieve the host config.


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
The openstack port should be created as:

.. code-block:: bash

    openstack port create --network private --vnic-type=direct --binding-profile '{"capabilities": ["switchdev"]}' port1

.. end

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
Also, check the hostconfig allows binding the direct port see
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
This feature has dependency on the v2 driver and pseudoagent port binding,
And on commit: https://git.opendaylight.org/gerrit/#/c/65551/ fixing
profile attribute handling in odl-neutton.

Testing
=======
Unit Tests
----------
Add test case for creating swichdev port.

Integration Tests
-----------------

CSIT
----
Will be added in the future.

Documentation Impact
====================
Update the documentation to provide explanation on the feature dependencies
and hostconfig configuration.

References
==========
[1] http://netdevconf.org/1.2/papers/efraim-gerlitz-sriov-ovs-final.pdf
[2] https://patchwork.ozlabs.org/patch/738176/
[3] https://mail.openvswitch.org/pipermail/ovs-dev/2017-April/330606.html
[4] https://specs.openstack.org/openstack/neutron-specs/specs/api/ports_binding_extended_attributes__ports_.html
