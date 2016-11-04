# Copyright? We're using OpenStack one as reference, do we need to retain
their copyright?
=====================
Title of the feature
=====================

[link to gerrit patch]

Brief introduction of the feature.

Key points when using this template:


Problem description
===================

Detailed description of the problem being solved by this feature

Use Cases
---------

Use cases addressed by this feature.

Proposed change
===============

Details of the proposed change.

Pipeline changes
----------------
Any changes to pipeline must be captured explicitly in this section.

# Should we have a separate document that tracks current pipeline?

Targeted Release
-----------------
What release is this feature targetted for? Will this be backported to a
previous release? If yes, which ones?

Alternatives
------------
Alternatives considered and why they were not selected.

Yang changes
------------
This should detail any changes to yang models.

Configuration impact
---------------------
Any configuration parameters being added/deleted/modified for this feature?
What will be defaults for these? How will it impact existing deployments?

Clustering considerations
-------------------------

This should capture how clustering will be supported. This can include but
not limited to use of CDTCL, EOS, Cluster Singleton etc.

Security considerations
-----------------------
Document any security related issues impacted by this feature.

Scale and Performance Impact
----------------------------
What are the potential scale and performance impact of this change?
Does it help improve scale and performance or make it worse?

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
Sample JSONS/URIs. These will be an offshoot of yang changes.

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

Break up work into individual items. This would be like checklist on
Trello card for this feature.



Dependencies
============
Any dependencies being added/removed? Dependencies here refers to internal
[other ODL projects] as well as external [OVS, karaf, JDK etc.]
This should capture any implications for dependent projects.

# Template should have a list of projects that currently depend on us.

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

* https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html