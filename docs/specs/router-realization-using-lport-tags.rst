==================================
Router Realization using LPortTags
==================================

https://git.opendaylight.org/gerrit/#/q/topic:router-lport-tag

**Important: All gerrit links raised for this feature will have topic name as "router-lport-tag"**

This feature realizes L3 router forwarding using LPortTags for intra-DC usecases instead of making use of MPLS labels
 as is done currently. In doing so, it is a step forward towards decoupling completely the intra-DC L3/BGPVPN
 forwarding making use of LPort tags from its inter-DC counterpart using MPLS labels.


Problem description
===================

As of today, intra-DC routing across router interfaces involves matching on MPLS labels. This is not required since
label based matching should only be required when a packet is sent out of the DC-GW. For intra-DC usecases, just using
the LPort tags should suffice.

Use Cases
---------
This feature involves amendments/testing pertaining to the following use cases (for intra DC L3 based forwarding):

* Use case 1: Router realization for subnet added as router-interface holding a pre-created VM.
* Use case 2: Router realization for subnet added as router-interface when a new VM is booted on the subnet.
* Use case 3: Router updated with an extra route to an existing VM.
* Use case 4: Router updated to remove previously added one/more extra routes.
* Use case 7: Keeping SNAT functionality intact for internet based forwarding (when no internet VPN is created)
* Use case 8: Keeping DNAT functionality intact for internet based forwarding (when no internet VPN is created)
* Use case 9: Keeping SNAT functionality intact over MPLSoGRE for internet based forwarding (when internet VPN
*             workflow is used)
* Use case 10: Keeping DNAT functionality intact for internet based forwarding (when internet VPN workflow is used)


Following use case will not be supported:

* Using LPort tags for L3VPN based intra-DC forwarding (currently uses MPLS).


Proposed change
===============

The following components within OpenDaylight Controller needs to be enhanced:
a. VPN Engine (VPN Manager and VPN Interface Manager)
b. FIB Manager
c. NAT Service


Pipeline changes
----------------

**Local DPN:**  VMs on the same DPN

TABLE 0 => DISPATCHER TABLE => MY-MAC-TABLE => FIB TABLE => Output to destination VM port


**Remote DPN:**  VMs on two different DPNs

a.    VM sourcing the traffic (Ingress DPN)
TABLE 0 => DISPATCHER TABLE => MY MAC TABLE => FIB TABLE (Set Tunnel ID with LPORT TAG) => Output to Tunnel port
b.    VM receiving the traffic (Egress DPN)
TABLE 0 => TERMINATING SERVICE TABLE (match on LPORT TAG) => Output to destination VM port


YANG changes
------------
No yang changes.


Configuration impact
--------------------
This change doesn't add or modify any configuration parameters.


Clustering considerations
-------------------------
No specific additional clustering considerations to be adhered to.


Other Infra considerations
--------------------------
None.


Security considerations
-----------------------
None.


Scale and Performance Impact
----------------------------
None.


Targeted Release(s)
-------------------
Carbon.

Known Limitations
-----------------
As indicated in the Use Cases, this feature will not cover LPort based intra-DC forwarding for BGPVPNs.


Alternatives
------------
N.A.


Usage
=====

Features to Install
-------------------
This feature doesn't add any new karaf feature. Installing odl-netvirt-openstack as earlier should suffice.

REST API
--------
No new changes to the existing REST APIs.

CLI
---
No new CLI is being added.


Implementation
==============

Assignee(s)
-----------
Primary assignee:
  <Abhinav Gupta>

Other contributors:
  <Vacancies available>


Work Items
----------
#. Code changes to alter the pipeline and e2e testing of the use-cases mentioned.
#. Add Documentation


Dependencies
============
This doesn't add any new dependencies.


Testing
=======

Unit Tests
----------

Integration Tests
-----------------

CSIT
----
Datapath testcases need to be added/tweaked to account for the changes in pipeline.


Documentation Impact
====================
This will require changes to Developer Guide.

Developer Guide will need to capture how this feature modifies the existing Netvirt VPNService implementation.


References
==========

* https://wiki.opendaylight.org/view/Genius:Carbon_Release_Plan
