.. contents:: Table of Contents
      :depth: 3

==================
New SFC Classifier
==================

https://git.opendaylight.org/gerrit/#/q/topic:new-sfc-classifier

The current SFC Netvirt classifier only exists in the old Netvirt.
This blueprint explains how to migrate the old Netvirt classifier
to a new Netvirt classifier.


Terminology
===========

NSH - Network Service Headers, used as Service Chaining encapsulation. NSH RFC Draft [1]
NSI - Network Service Index, a field in the NSH header used to indicate the next hop
NSP - Network Service Path, a field in the NSH header used to indicate the service chain
RSP - Rendered Service Path, a service chain.
SFC - Service Function Chaining. SFC RFC [2] ODL SFC Wiki [3].
VXGPE - VXLAN GPE (Generic Protocol Encapsulation) Used as transport for NSH. VXGPE uses
        the same header format as traditional VXLAN, but adds a Next Protocol field to
        indicate NSH will be the next header. Traditional VXLAN implicitly expects the
        next header to be ethernet.
        VXGPE RFC Draft [4]


Problem description
===================

Detailed description of the problem being solved by this feature

The classifier is an integral part of Service Function Chaining (SFC).
The classifier maps client/tenant traffic to a service chain by matching
the packets using an ACL, and once matched, the classifier encapsulates
the packets using some sort of Service Chaining encapsulation. Currently,
the only supported Service Chaining encapsulation is NSH using Vxgpe as
the transport. Very soon (in the Carbon release) Vxlan will be added as
another encapsulation/transport, in which case NSH is not used.

In the Boron release, an SFC classifier was implemented, but in the
old Netvirt. This blueprint intends to explain how to migrate the
old Netvirt classifier to a new Netvirt classifier.

The following image details the packet headers used for Service Chaining
encapsulation with VXGPE+NSH.

.. figure:: ./images/vxgpe-nsh-pkt-headers.jpg
   :alt: VXGPE+NSH packet headers

Diagram source [5].

Use Cases
---------

The main use case addressed by adding an SFC classifier to Netvirt
is to integrate the 2 features, thus allowing for Service Chaining
to be used in an OpenStack virtual deployment, such as the OPNFV
SFC project [6].

Proposed change
===============

Details of the proposed change.

The existing old Netvirt SFC code can be found here:
    netvirt/openstack/net-virt-sfc/{api,impl}

The new Netvirt SFC code base will be located here:
    netvirt/aclservice/{api,impl}

Once the new Netvirt SFC classifier is implemented and working,
the old Netvirt SFC classifier code will be removed.

Integration with Genius
-----------------------


Pipeline changes
----------------
Any changes to pipeline must be captured explicitly in this section.

Yang changes
------------

The api yangs used for the classifier build on the ietf acl models in
mdsal.model. No new Yang changes will be introduced.


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
This change is targeted for the ODL Carbon release.

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

Identify existing karaf feature to which this change applies and/or new karaf
features being introduced. These can be user facing features which are added
to integration/distribution or internal features to be used by other projects.

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

[1] https://datatracker.ietf.org/doc/draft-ietf-sfc-nsh/

[2] https://datatracker.ietf.org/doc/rfc7665/

[3] https://wiki.opendaylight.org/view/Service_Function_Chaining:Main

[4] https://datatracker.ietf.org/doc/draft-ietf-nvo3-vxlan-gpe/

[5] https://docs.google.com/presentation/d/1kBY5PKPETEtRA4KRQ-GvVUSLbJoojPsmJlvpKyfZ5dU/edit?usp=sharing

[6] https://wiki.opnfv.org/display/sfc/Service+Function+Chaining+Home

