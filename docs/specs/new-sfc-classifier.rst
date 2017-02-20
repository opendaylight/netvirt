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

- NSH - Network Service Headers, used as Service Chaining encapsulation. NSH RFC Draft [1]

- NSI - Network Service Index, a field in the NSH header used to indicate the next hop

- NSP - Network Service Path, a field in the NSH header used to indicate the service chain

- RSP - Rendered Service Path, a service chain.

- SFC - Service Function Chaining. SFC RFC [2] ODL SFC Wiki [3].

- VXGPE - VXLAN GPE (Generic Protocol Encapsulation) 
  Used as transport for NSH. VXGPE uses the same header format as traditional
  VXLAN, but adds a Next Protocol field to indicate NSH will be the next header.
  Traditional VXLAN implicitly expects the next header to be ethernet. VXGPE RFC
  Draft [4].


Problem description
===================

The classifier is an integral part of Service Function Chaining (SFC).
The classifier maps client/tenant traffic to a service chain by matching
the packets using an ACL, and once matched, the classifier encapsulates
the packets using some sort of Service Chaining encapsulation. Currently,
the only supported Service Chaining encapsulation is NSH using Vxgpe as
the transport. Very soon (in the Carbon release) Vxlan will be added as
another encapsulation/transport, in which case NSH is not used. The transport
and encapsulation information to be used for the service chain is obtained
by querying the RSP specified in the ACL action.

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

SFC works with both OVS and VPP virtual switches, and its even possible
to have a hybrid setup whereby Netvirt is hosted on OVS and SFC is hosted
on VPP switches. This blueprint addresses the case where only OVS is used.

As mentioned previously, currently SFC works with VXGPE transport and NSH
encapsulation, and soon SFC will work with VXLAN as the transport and
encapsulation. The first version of the implementation will focus on VXGPE
and NSH. In the future, when VXLAN is implemented in SFC, VXLAN can be added
to the Netvirt SFC classifier. Changes in the transport and encapsulation
used for service chains will have no affect on the Netvirt ACL model, since
the transport and encapsulation information is obtained via the RSP specified
in the RSP.

Proposed change
===============

The existing old Netvirt SFC code can be found here:
    netvirt/openstack/net-virt-sfc/{api,impl}

Once the new Netvirt SFC classifier is implemented and working, the old
Netvirt SFC classifier code will be left in place for at least one project
cycle.

The new Netvirt SFC code base will be located here:
    **TODO** *: this code location needs to be confirmed by Sam*
    netvirt/classifier/{api,impl}

The new Netvirt SFC classifier implementation will be new code. This
implementation is not to be confused with the existing Netvirt aclservice,
which is implemented for Security Groups. More details about the Genius
integration can be found in the following section, but the Netvirt SFC
classifier will be in a new Genius classifier service. The SFC
implementation is already integrated with Genius and is managed via
the Genius SFC service.


Integration with Genius
-----------------------

Genius [7], [8] is an OpenDaylight project that provides generic
infrastructure services to other OpenDaylight projects. New Netvirt makes
use of Genius and the new Netvirt classifier will also make use of Genius
services. Among these services, the interface manager, tunnel manager
and service binding services are of special relevance for the new
Netvirt classifier.

Genius interface manager handles an overlay of logical interfaces on
top of the data plane physical ports. Based on these logical interfaces,
different services/applications may be bound to them with certain
priority ensuring that there is no interference between them. Avoiding
interference between services/applications is called Application Coexistence
in Genius terminology. Typically, the effect of an application binding to
a logical interface is that downstream traffic from that interface will be
handed off to that application pipeline. Each application is then responsible
to either perform a termination action with the packet (i.e output action)
or to return the packet back to Genius so that another application
continues to handle the packet. There is a predefined set of types of
services that can bind, and ACL is one of them.

For OpenStack environments, Netvirt registers Neutron ports as logical
interfaces in the Genius interface manager. Classifying traffic for a
client/tenant ultimately relies on classifying traffic downstream from
their corresponding Neutron ports. As such, the Netvirt classifier will
bind on these interfaces as a newly defined Genius Classifier service
through the Genius interface manager. It was considered integrating the
Netvirt classifier with the existing Netvirt security groups, but the idea
was discarded due to the possible conflicts and other complications this
could cause.

Netvirt also keeps track of the physical location of these Neutron
ports in the data plane and updates the corresponding Genius logical
interface with this information. Services integrated with Genius may
consume this information to be aware of the physical location of a
logical interface in the data plane and it's changes when a VM migrates
from one location to another. New Netvirt classifier will install the
ACL rules based on the data plane location of the client/tenant Neutron
ports whose traffic is to be classified. On VM migration, the
classifier has to remove or modify the corresponding ACL rules
accounting for this location change, which can be a physical node
change or a physical port change.

The classifier has to forward packets to the first service function in the
chain. This service function, depending on how it was configured, may also
be located through a Neutron port registered in Genius as a logical
interface. Its location may be the same node as that of the Neutron
port whose traffic is being classified or a different node. In the
former case, the classifier will output the encapsulated packet to the
physical port corresponding to the logical interface. The specific
actions to achieve this are provided by Genius.

In scenarios where the service function node is on a different compute host
than the client node, the encapsulated packet needs to be forwarded to that
node through a tunnel port. Tunnels are handled by the Genius tunnel
manager (ITM) with an entity called transport zone: all nodes in a transport
zone will be connected through a tunnel mesh. Thus the netvirt classifier
needs to ensure that the client node and the service function node are
included in a transport zone. The transport type is also specified at the
transport zone level and for NSH it needs to be VXGPE. The classifier
needs to make sure that this transport zone is handled in relation with
client and service function location changes in the case that either of
the VMs migrate.

The afore-mentioned Genius ITM is different than the tunnels currently used
by Netvirt.  SFC uses VXGPE tunnels, and requests they be created via the
Genius ITM. 


Pipeline changes
----------------
The Genius pipelines can be found here [10].

Yang changes
------------
The api yangs used for the classifier build on the ietf acl models in mdsal
model.

Multiple routes can be taken, depending on the desired functionality. Depending
on it, yang changes *might be* required.

Assuming no yang changes, SFC classification can be done to all VMs within the
same neutron-network - this attribute is already present in the yang model.
**This is the proposed route**, since it hits a sweet-spot in the trade-off
between functionality and risk.

If classifying the traffic from specific interfaces was desired, then the yang
model would need to be updated, possibly by adding it a list of interfaces to
classify.

Configuration impact
--------------------
None

Clustering considerations
-------------------------
None

Other Infra considerations
--------------------------
Since SFC uses NSH, and the new Netvirt Classifier will need to add NSH
encapsulation, a version of OVS that supports NSH must be used. NSH has not
been officially accepted into the OVS project, so a branched version of OVS is
used. Details about the branched version of OVS can be found here [9].

Security considerations
-----------------------
None

Scale and Performance Impact
----------------------------
None

Targeted Release
-----------------
This change is targeted for the ODL Carbon release.

Alternatives
------------
None

Usage
=====
The new Netvirt Classifier will be configured via the REST JSON configuration
mentioned in the REST API section below.

Features to Install
-------------------

The existing old Netvirt SFC classifier is implemented in the following Karaf
feature:

odl-ovsdb-openstack

When the new Netvirt SFC classifier is implemented, the previous Karaf feature
will no longer be needed, and the following will be used:

odl-netvirt-classifier
(Feature name to be confirmed by Sam)

REST API
--------

The classifier REST API wont change from the old to the new Netvirt. The
following example is how the old Netvirt classifier is configured.

Defined in netvirt/openstack/net-virt-sfc/api/src/main/yang/netvirt-acl.yang

The first step is to create an ACL which specifies the matching criteria
and the action, which is to send the packets to an SFC RSP. Notice the
"network-uuid" is set. This is for binding the Netvirt classifier service
to a logical port. The procedure will be to query Genius for all the logical
ports in that network uuid, and bind the Netvirt classifier service to each
of them.

.. code-block:: none

   URL: /restconf/config/ietf-access-control-list:access-lists/

   {
     "access-lists": {
       "acl": [
         {
           "acl-name": "ACL1",
           "acl-type": "ietf-access-control-list:ipv4-acl",
           "access-list-entries": {
             "ace": [
               {
                 "rule-name": "ACE1",
                 "actions": {
                   "netvirt-sfc-acl:rsp-name": "RSP1"
                 },
                 "matches": {
                   "network-uuid" : "eccb57ae-5a2e-467f-823e-45d7bb2a6a9a",
                   "source-ipv4-network": "192.168.2.0/24",
                   "protocol": "6",
                   "source-port-range": {
                       "lower-port": 0
                   },
                   "destination-port-range": {
                       "lower-port": 80
                   }
                 }
               }
             ]
           }
         }]}}

Once the ACL is created, then a classifier is created as follows:

.. code-block:: none

   URL: /restconf/config/netvirt-sfc-classifier:classifiers/

   {
     "classifiers": {
       "classifier": [
         {
           "name": "Classifier1",
           "acl": "ACL1",
           "sffs": {
             "sff" : [
               {
                 "name": "sff1"
               }
             ]
           },
           "bridges" : {
             "bridge" : [
               {
                 "name": "br-int",
                 "direction" : "ingress"
               }
             ]
           }
         }]}}


CLI
---
None.

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  <brady.allen.johnson@ericsson.com>

Other contributors:
  <brady.allen.johnson@ericsson.com>
  <david.suarez.fuentes@ericsson.com
  <jaime.camaano.ruiz@ericsson.com>
  <miguel.duarte.de.mora.barroso@ericsson.com>


Work Items
----------
**Simple scenario:**

- Augment the provisioned ACL with the 'neutron-network' augmentation - [11]

- From the neutron-network, get a list of neutron-ports - the interfaces
  connecting the VMs to that particular neutron-network. For each interface, do
  as follows:

   - Extract the DPN-ID of the node hosting the VM having that neutron-port

   - Extract the DPN-ID of the node hosting the first SF of the RSP

   - The forwarding logic to implement depends on the co-location of the client's
     VM with the first SF in the chain.

      - When the VMs are co-located (i.e. located in the same host), the output
        actions are to forward the packet to the first table of the SFC pipeline.
      - When the VMs are **not** co-located (i.e. hosted on different nodes) it
        is necessary to:

        - Use genius RPCs to get the interface connecting 2 DPN-IDs. This will
          return the tunnel endpoint connecting the compute nodes.
        - Use genius RPCs to get the list of actions to reach the tunnel
          endpoint.

**Enabling VM mobility:**

1. Handle first SF mobility

   Listen to RSP updates, where the only relevant
   migration is when the first SF moves to another node (different DPN-IDs).
   In this scenario, we delete the flows from the *old* node, and install the
   newly calculated flows in the new one. This happens for **each** node having
   an interface to classify attached to the provisioned neutron-network.

2. Handle client VM mobility

   Listen to client's InterfaceState changes,
   re-evaluating the Forwarding logic, since the tunnel interface used to reach
   the target DPN-ID is different. This means the action list to implement it,
   will also be different. The interfaces to listen to will be ones attached to
   the provisioned neutron-network.

3. **Must** keep all the nodes having interfaces to classify (i.e. nodes
   having neutron-ports attached to the neutron-network) and the first SF host
   node within the same transport zone. By listening to InterfaceState changes
   of clients within the neutron-network & the first SF neutron ports, the
   transport zone rendering can be redone.

   **TODO:** *is there a better way to identify when the transport zone
   needs to be updated?*

Dependencies
============
No dependency changes will be introduced by this change.

Testing
=======

Unit Tests
----------
Unit tests for the new Netvirt classifier will be modeled on the existing
old Netvirt classifier unit tests, and tests will be removed and/or added
appropriately.

Integration Tests
-----------------
The existing old Netvirt Classifier Integration tests will need to be
migrated to use the new Netvirt classifier.

CSIT
----
The existing Netvirt CSIT tests for the old classifier will need to be
migrated to use the new Netvirt classifier.

Documentation Impact
====================
User Guide documentation will be added by one of the following contributors:
  <brady.allen.johnson@ericsson.com>
  <david.suarez.fuentes@ericsson.com
  <jaime.camaano.ruiz@ericsson.com>
  <miguel.duarte.de.mora.barroso@ericsson.com>

References
==========

[1] https://datatracker.ietf.org/doc/draft-ietf-sfc-nsh/

[2] https://datatracker.ietf.org/doc/rfc7665/

[3] https://wiki.opendaylight.org/view/Service_Function_Chaining:Main

[4] https://datatracker.ietf.org/doc/draft-ietf-nvo3-vxlan-gpe/

[5] https://docs.google.com/presentation/d/1kBY5PKPETEtRA4KRQ-GvVUSLbJoojPsmJlvpKyfZ5dU/edit?usp=sharing

[6] https://wiki.opnfv.org/display/sfc/Service+Function+Chaining+Home

[7] http://docs.opendaylight.org/en/stable-boron/user-guide/genius-user-guide.html

[8] https://wiki.opendaylight.org/view/Genius:Design_doc

[9] https://wiki.opendaylight.org/view/Service_Function_Chaining:Main#Building_Open_vSwitch_with_VxLAN-GPE_and_NSH_support

[10] http://docs.opendaylight.org/en/latest/submodules/genius/docs/pipeline.html

[11] https://github.com/opendaylight/netvirt/blob/master/openstack/net-virt-sfc/api/src/main/yang/netvirt-acl.yang

