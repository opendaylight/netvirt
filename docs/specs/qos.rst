..
==============================
Quality of Service for NetVirt
==============================

[link to gerrit patch]

This feature provides fuller QoS support for NetVirt - including broader support
for the Neutron QoS API.  In addition, it may provide a more generic QoS interface
for NetVirt for use with other northbound applications.



Problem description
===================

It is important to be able to configure QoS attributes of workloads on
virtual networks.  The Neutron QoS API provides a method for definining
QoS rules which can be applied to Neutron Port and Networks.  These rules
include:
  ingress rate limiting
  DSCP marking
  and more ...

As a Neutron provider for ODL, NetVirt will provide the ability to report
back to Neutron its QoS rule capabiltiies and provide the ability to
configure and manage the supported QoS rules on supported backends
(e.g. OVS, ...).

Other considerations:  NetVirt is used by other application such as Unimgr
which may also need QoS capabilities.  So, some thought may be required to
ensure the QoS support is not bound too closely to a single Northbound
use case.

Use Cases
---------

Neutron QoS API support, including:
- Ingress rate limiting
- DSCP Marking
- Reporting of QoS capabilities

toto: check for latest Neutron QoS API updates and support.

Proposed change
===============

TBD.

A couple items are known:

1. Cleanup existing rate limiting code
2. Provide capabilitity to report QoS capabilities back to Neutron

A proposal for a QoS model should be developed.  The Neutron QoS
model is the current defacto model, but it may be desirable to 
provide a more generic model.

A proposal for how to implement QoS support should be developed.
There was some discussion about implementing QoS via the ACL service, but
maybe QoS should be handled independently.

Pipeline changes
----------------
Any changes to pipeline must be captured explicitly in this section.

Yang changes
------------
QoS model described here.

Configuration impact
---------------------
Any configuration parameters being added/deprecated for this feature?
What will be defaults for these? How will it impact existing deployments?

Note that outright deletion/modification of existing configuration
is not allowed due to backward compatibility. They can only be deprecated
and deleted in later release(s).

Clustering considerations
-------------------------
This should capture how clustering will be supported. This can include but
not limited to use of CDTCL, EOS, Cluster Singleton etc.

Other Infra considerations
--------------------------
This should capture impact from/to different infra components like
MDSAL Datastore, karaf, AAA etc.

Security considerations
-----------------------
Document any security related issues impacted by this feature.

Scale and Performance Impact
----------------------------
What are the potential scale and performance impacts of this change?
Does it help improve scale and performance or make it worse?

Targeted Release
-----------------
Carbon

Alternatives
------------
Alternatives considered and why they were not selected.

Usage
=====
How will end user use this feature? Primary focus here is how this feature
will be used in an actual deployment.

e.g. For most netvirt features this will include OpenStack APIs.

This section will be primary input for Test and Documentation teams.
Along with above this should also capture REST API and CLI.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
Sample JSONS/URIs. These will be an offshoot of yang changes. Capture
these for User Guide, CSIT, etc.

CLI
---
Any CLI if being added.


Implementation
==============

Assignee(s)
-----------
Who is implementing this feature? In case of multiple authors, designate a
primary assigne and other contributors.

Primary assignee:
  <developer-a>

Other contributors:
  <developer-b>
  <developer-c>


Work Items
----------
Break up work into individual items. This should be a checklist on
Trello card for this feature. Give link to trello card or duplicate it.


Dependencies
============
Any dependencies being added/removed? Dependencies here refers to internal
[other ODL projects] as well as external [OVS, karaf, JDK etc.] This should
also capture specific versions if any of these dependencies.
e.g. OVS version, Linux kernel version, JDK etc.

This should also capture impacts on existing project that depend on Netvirt.

Following projects currently depend on Netvirt:
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
What is impact on documentation for this change? If documentation
change is needed call out one of the <contributors> who will work with
Project Documentation Lead to get the changes done.

Don't repeat details already discussed but do reference and call them out.

References
==========
Add any useful references. Some examples:

* Links to Summit presentation, discussion etc.
* Links to mail list discussions
* Links to patches in other projects
* Links to external documentation

[1] http://docs.opendaylight.org/en/latest/documentation.html

[2] https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html

.. note::

  This template was derived from [2], and has been modified to support our project.

  This work is licensed under a Creative Commons Attribution 3.0 Unported License.
  http://creativecommons.org/licenses/by/3.0/legalcode

