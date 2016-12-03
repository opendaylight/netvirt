.. contents:: Table of Contents
      :depth: 3

=======================================================
Neutron Quality of Service API Enhancements for NetVirt
=======================================================

QoS patches: https://git.opendaylight.org/gerrit/#/q/topic:qos

The Carbon release will enhance the initial implementation of Neutron
QoS API [#]_ support for NetVirt which was released in Boron.  The
Boron released added support for Neutron QoS policies and the
Egress bandwidth rate limiting rule.  The Carbon release will update the
QoS feature set of NetVirt by providing support for the DSCP Marking
rule and QoS Rule capability reporting.

Problem description
===================

It is important to be able to configure QoS attributes of workloads on
virtual networks.  The Neutron QoS API provides a method for defining
QoS policies and associated rules which can be applied to Neutron Ports
and Networks.  These rules include:

- Egress Bandwidth Rate Limiting
- DSCP Marking

(Note that for the Neutron API, the direction of traffic flow (ingress, egress)
is from the perspective of the OpenStack instance.)

As a Neutron provider for ODL, NetVirt will provide the ability to report
back to Neutron its QoS rule capabilties and provide the ability to
configure and manage the supported QoS rules on supported backends
(e.g. OVS, ...).  The key changes in the Carbon release will be the
addition of support for the DSCP Marking rule.

Use Cases
---------

Neutron QoS API support, including:

- Egress rate limiting -
  Drop traffic that exceeeds the specified rate parameters for a
  Neutron Port or Network.

- DSCP Marking -
  Set the DSCP field for IP packets arriving from Neutron Ports
  or Networks.

- Reporting of QoS capabilities -
  Report to Neutron which QoS Rules are supported.

Proposed change
===============

To handle DSCP marking, listener support will be added to the
*neutronvpn* service to respond to changes in DSCP Marking
Rules in QoS Policies in the Neutron Northbound QoS models [#]_ [#]_ .

To implement DSCP marking support, a new ingress (from vswitch
perspective) QoS Service is defined in Genius.  When DSCP Marking rule
changes are detected, a rule in a new OpenFlow table for
QoS DSCP marking rules will be updated.

The QoS service will be bound to an interface when a DSCP Marking
rule is added and removed when the DSCP Marking rule is deleted.
The QoS service follows the DHCP service and precedes the IPV6
service in the sequence of Genius ingress services.

Some use cases for DSCP marking require that the DSCP mark set on the inner packet
be replicated to the DSCP marking in the outer packet.  Therefore, for packets egressing out
of OVS through vxlan/gre tunnels the option to copy the DSCP bits from the inner IP header
to the outer IP header is needed.
Marking of the inner header is done via OpenFlow rules configured on the corresponding Neutron port
as described above. For cases where the outer tunnel header should have a copy of the inner
header DSCP marking, the ``tos`` option on the tunnel interface in OVSDB must be configured
to the value ``inherit``.
The setting of the ``tos`` option is done with a configurable parameter defined in the ITM module.
By default the ``tos`` option is set to *0* as specified in the OVSDB specification [#]_ .

On the creation of new tunnels, the ``tos`` field will be set to either the user provided value
or to the default value, which may be controlled via configuration.  This will result in
the tunnel-options field in the IFM (Interface Manager) to be set which will in turn cause
the ``options`` field for the tunnel interface on the OVSDB node to be configured.

To implement QoS rule capability reporting back towards Neutron, code will
be added to the *neutronvpn* service to populate the operational qos-rule-types
list in the Neutron Northbound Qos model [3]_ with a list of the supported
QoS rules - which will be the bandwidth limit rule and DSCP marking rule for
the Carbon release.

Pipeline changes
----------------
A new QoS DSCP table is added to support the new QoS Service:

=============   =====================================  ===========================
Table           Match                                  Action
=============   =====================================  ===========================
QoS DSCP [90]   Ethtype == IPv4 or IPv6 AND LPort tag  Mark packet with DSCP value
=============   =====================================  ===========================

Yang changes
------------
A new leaf ``option-tunnel-tos`` is added to ``tunnel-end-points`` in *itm-state.yang* and to
``vteps`` in *itm.yang*.


.. code-block:: none
   :caption: itm-state.yang
   :emphasize-lines: 39-46

   list tunnel-end-points {
       ordered-by user;
       key "portname VLAN-ID ip-address tunnel-type";

       leaf portname {
           type string;
       }
       leaf VLAN-ID {
           type uint16;
       }
       leaf ip-address {
           type inet:ip-address;
       }
       leaf subnet-mask {
           type inet:ip-prefix;
       }
       leaf gw-ip-address {
           type inet:ip-address;
       }
       list tz-membership {
           key "zone-name";
           leaf zone-name {
               type string;
           }
       }
       leaf interface-name {
           type string;
       }
       leaf tunnel-type {
           type identityref {
               base odlif:tunnel-type-base;
           }
       }
       leaf option-of-tunnel {
           description "Use flow based tunnels for remote-ip";
           type boolean;
           default false;
       }
       leaf option-tunnel-tos {
           description "Value of ToS bits to be set on the encapsulating
               packet.  The value of 'inherit' will copy the DSCP value
               from inner IPv4 or IPv6 packets.  When ToS is given as
               and numberic value, the least significant two bits will
               be ignored. ";
           type string;
       }
   }


.. code-block:: none
   :caption: itm.yang
   :emphasize-lines: 17-24

   list vteps {
       key "dpn-id portname";
       leaf dpn-id {
           type uint64;
       }
       leaf portname {
           type string;
       }
       leaf ip-address {
           type inet:ip-address;
       }
       leaf option-of-tunnel {
           description "Use flow based tunnels for remote-ip";
           type boolean;
           default false;
       }
       leaf option-tunnel-tos {
           description "Value of ToS bits to be set on the encapsulating
               packet.  The value of 'inherit' will copy the DSCP value
               from inner IPv4 or IPv6 packets.  When ToS is given as
               and numberic value, the least significant two bits will
               be ignored. ";
           type string;
       }
   }


A configurable parameter ``default-tunnel-tos`` is added to *itm-config.yang* which
defines the default ToS value to be applied to tunnel ports.

.. code-block:: none
   :caption: itm-config.yang
   :emphasize-lines: 1-13

   container itm-config {
       config true;

       leaf default-tunnel-tos {
           description "Default value of ToS bits to be set on the encapsulating
               packet.  The value of 'inherit' will copy the DSCP value
               from inner IPv4 or IPv6 packets.  When ToS is given as
               and numberic value, the least significant two bits will
               be ignored. ";
           type string;
           default 0;
       }
   }

Configuration impact
---------------------
A configurable parameter ``default-tunnel-tos`` is added to
*genius-itm-config.xml* which specifies the default ToS to
use on a tunnel if it is not specified by the user when a
tunnel is created.  This value may be set to ``inherit`` for
some DSCP Marking use cases.

.. code-block:: none
   :caption: genius-itm-config.xml

   <itm-config xmlns="urn:opendaylight:genius:itm:config">
       <default-tunnel-tos>0</default-tunnel-tos>
   </itm-config>



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
Additional OpenFlow packets will be generated to configure DSCP marking rules in response
to QoS Policy changes coming from Neutron.

Targeted Release
-----------------
Carbon

Alternatives
------------
Use of OpenFlow meters was desired, but the OpenvSwitch datapath implementation
does not support meters (although the OpenvSwitch OpenFlow protocol implementation
does support meters).

Usage
=====
The user will use the QoS support by enabling and configuring the
QoS extension driver for networking-odl.  This will allow QoS Policies and
Rules to be configured for Neuetron Ports and Networks using Neutron.

Perform the following configuration steps:

-  In *neutron.conf* enable the QoS service by appending ``qos`` to
   the ``service_plugins`` configuration:

   .. code-block:: none
      :caption: /etc/neutron/neutron.conf

      service_plugins = odl-router, qos

-  Add the QoS notification driver to the *neutron.conf* file as follows:

   .. code-block:: none
      :caption: /etc/neutron/neutron.conf

      [qos]
      notification_drivers = odl-qos

-  Enable the QoS extension driver for the core ML2 plugin.
   In file *ml2.conf.ini* append ``qos`` to ``extension_drivers``

   .. code-block:: none
      :caption: /etc/neutron/plugins/ml2/ml2.conf.ini

      [ml2]
      extensions_drivers = port_security,qos

Features to Install
-------------------
Install the ODL Karaf feature for NetVirt (no change):

- odl-netvirt-openstack

REST API
--------
None.

CLI
---
Refer to the Neutron CLI Reference [#]_ for the Neutron CLI command syntax
for managing QoS policies and rules for Neutron networks and ports.

Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a
primary assigne and other contributors.

Primary assignee:

-  Poovizhi Pugazh <poovizhi.p@ericsson.com>

Other contributors:

-  Ravindra Nath Thakur <ravindra.nath.thakur@ericsson.com>
-  Eric Multanen <eric.w.multanen@intel.com>
-  Praveen Mala <praveen.mala@intel.com> (including CSIT)


Work Items
----------
Task list in Carbon Trello: https://trello.com/c/bLE2n2B1/14-qos

Dependencies
============
Genius project - Code [#]_ to support QoS Service needs to be added.

Neutron Northbound - provides the Neutron QoS models for policies and rules (already done).


Following projects currently depend on NetVirt:
 Unimgr

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
Documentation to describe use of Neutron QoS support with NetVirt
will be added.

OpenFlow pipeline documentation updated to show QoS service table.

References
==========

http://specs.openstack.org/openstack/neutron-specs/specs/newton/ml2-qos-with-dscp.html

ODL gerrit adding QoS models to Neutron Northbound: https://git.opendaylight.org/gerrit/#/c/37165/

.. [#] Neutron QoS http://docs.openstack.org/developer/neutron/devref/quality_of_service.html
.. [#] Neutron Northbound QoS Model Extensions https://github.com/opendaylight/neutron/blob/master/model/src/main/yang/neutron-qos-ext.yang
.. [#] Neutron Northbound QoS Model https://github.com/opendaylight/neutron/blob/master/model/src/main/yang/neutron-qos.yang
.. [#] OVSDB Schema http://openvswitch.org/ovs-vswitchd.conf.db.5.pdf
.. [#] Neutron CLI Reference http://docs.openstack.org/cli-reference/neutron.html#neutron-qos-available-rule-types
.. [#] Genius code supporting QoS service https://git.opendaylight.org/gerrit/#/c/49084/
