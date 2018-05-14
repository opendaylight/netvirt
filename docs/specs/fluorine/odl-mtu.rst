.. contents:: Table of Contents
:depth: 3

=================================================
Neutron MTU support with OpenDaylight deployments
=================================================
`odl-mtu gerrit topic <https://git.opendaylight.org/gerrit/#/q/topic:odl-mtu>`_

The purpose of this feature is to provide OpenDaylight the capability to
set the MTU value on the integration bridge, br-int, as configured by Neutron.

Problem description
===================
There is currently no support in ODL for honoring the global_physnet_mtu
configured by Neutron. The parameter is used to set the MTU of the underlying
physical network and is applied to br-int.

Use Cases
---------
Configuring the MTU of the underlying physical network, such as increasing
the value to support Jumbo frames at 9000 bytes.

Proposed change
===============
- A new yang model will be introduced in Neutron Northbound to store the global
  configuration from neutron. The model will be a yang list of key:value pairs
  corresponding to neutron.conf parameters.
- networking-odl will be updated to send the new REST api when configuration
  changes are detected in ``neutron.conf``.
- NetVirt will listen for Neutron Northbound neutron global configuration
  changes. NetVirt will use the configuration in OVSDB requests to change the
  MTU
- OVSDB will set the ``mtu_request`` on ``br-int``. The equivalent ``ovs-vsctl``
  command is ``ovs-vsctl set int br0 mtu_request=1450``.

Pipeline changes
----------------
None.

Yang changes
------------
The yang change below is expected in the Neutron Northbound project. The neutron-extensions.yang
will be updated to include a neutron-configuration grouping with key:value pairs.

.. code-block:: none
   :caption: neutron-configuration.yang

       grouping neutron-configuration {
           description "Key:value pairs for neutron configuration";
           list configuration {
               config false;
               key "key";
               leaf key {
                   type string;
                   mandatory:true;
               }
               leaf value {
                   type string;
                   mandatory:true;
               }
           }
       }

Configuration impact
--------------------
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
None.

Targeted Release
----------------
Fluorine.

Alternatives
------------
None.

Usage
=====
OpenStack neutron configures the parameter ``global_physnet_mtu`` in the
``neutron.conf`` file. The value will be passed in the new neutron northbound REST
api shown below. OVSDB will set the ``mtu_request`` on ``br-int``.

Features to Install
-------------------
Use the existing feature, ``odl-netvirt-openstack``.

REST API
--------
.. code-block:: json
   :caption: neutron-configuration.json

       {
           "neutron-configuration": {
               "configuration": [
                   {
                       "key": "global_physnet_mtu",
                       "value: "9000"
                   },
                   {
                       "key": "some_other_config",
                       "value: "some_other_value"
                   }
               ]
           }
       }

   CLI
   ---
   None.

Implementation
==============

Assignee(s)
-----------
Sam Hague, shague, shague@redhat.com

Work Items
----------
- update networking-odl to send neutron-configuration REST
- update Neutron Northbound to receive and write configuration to MDSAL
- add neutron-configuration listener to neutronvpn
- update OVSDB to send mtu value to the OVSDB nodes.

Dependencies
============
- networking-odl updated to use new REST api
- Neutron Northbound updated to use new REST api and update MDSAL
- OVSDB updated to set the MTU on br-int as requested by NetVirt

Testing
=======

Unit Tests
----------
Existing neutronvpn unit tests will be updated to include the new mtu
parameter.

Integration Tests
-----------------
None.

CSIT
----
An additional test will be included to the existing suites to set the
global_physnet_mtu value in the neutron.conf to a certain value. Traffic
tests will then be executed to verify if the configuration was honored.

The existing deployment and tests will be updated to do the following:

- Add the ``global_physnet_mtu`` to the ``[[post-config|\$NEUTRON_CONF]]`` section
  of the ``local.conf`` for the OpenStack control node. This will
  apply the parameter in the corresponding ``neutron.conf``.
- Ensure vm instances have been created
- Execute ``netcat`` commands from one vm instance to another that use different
  packet sizes to verify traffic passing or failing with the configured MTU.

Documentation Impact
====================
None.

References
==========
[1] `OpenStack Docs: MTU Considerations <https://docs.openstack.org/mitaka/networking-guide/config-mtu.html>`__
