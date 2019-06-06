.. contents:: Table of Contents
      :depth: 5

============================
Enhance Robustness of L3VPN
============================

https://git.opendaylight.org/gerrit/#/q/topic:l3vpn-robustness

Enhance current L3VPN to improve its robustness for specific
use-cases and also enable it to consistently work with
Neutron BGPVPN initiated orchestration lifecycles.

Problem description
===================

Witnessing issues faced in L3VPN with production
deployments of OpenDaylight, it was gradually occurring
that piecemeal fixing issues in L3VPN (and all its
related modules) unfortunately wouldn't scale.

It was ascertained during fixing such issues (ie., providing
patches for production) that there was scope for improving
robustness in L3VPN.

There were specific cases that may (or) may not succeed most of the time in L3VPN.

1. VM Migration

 - Specifically when VM being migrated is a parent of multiple IPAddresses (more than 4) where some are V4 and some V6.

2. VM Evacuation

 - Mutliple VMs on a host, each evacuated VM being a parent of multiple IPAddresses, and the host made down.

3. Speed up Router-association and Router-disassociation to a VPN lifecycle

4. Graceful ECMP handling (i.e, ExtraRoute with multiple nexthops)

 - Modify an existing extra-route with additional nexthops.
 - Modify an existing extra-route by removing a nexthop from a list of configured nexthops.
 - Boot a VM, which is a nexthop for a pre-configured extra-route
 - Delete a VM, which is a nexthop for an extra-route

5. Robust VIP/MIP Handling

 - MIP movement across VMs on different computes with VRRP configuration.
 - VM (holding MIP) migration.
 - MIP traffic impact on ODL cluster reboot.
 - MIP traffic impact on OVS disconnect/connect.
 - MIP Aliveness monitoring sessions are not persistent.

If we take a closer look, the first three points (points 1, 2 and 3) require
vpn-interface-based serialization across the cluster for consistently driven
control-plane + data-plane semantics.

Similarly the next two points above (points 4 and 5), we will notice that they require IP-driven
serialization across the cluster for consistently driven control-plane + data-plane semantics.

However, today the entire VPN Engine unfortunately is vpn-interface-driven and not IP-driven.
As a result, points 4 and 5 would never be realized with constant consistentcy.

Point 3 case deals with vpn-interface (and so IP Addresses within them) movement across two VPNs, one being
router-vpn and other being the bgpvpn-with-that-router.  And this is extremely slow today and will be unable to
catch up with orchestration of any kind (be it via ODL RPCs) or via Openstack Neutron BGPVPN APIs in a scaled
cloud.

The initiative of this specification is to improve intrinsic reliability and behavioural consistency within the
L3VPN Engine by explicitly enforcing IP Serialization wherever required.
In addition to that provide a design that can handle faster (but consistent)
movement of IPAddresses between router-vpn and bgpvpn-with-that-router.

In scope
---------

Out of scope
------------

Use Case Summary
----------------
All the below use-cases have to be considered with a (3-node Openstack Controller + 3-node ODL cluster).

UC 1: VM Migration (cold migration)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VM Cold Migrations are made robust within L3VPN Engine.
If you notice this mimics a vpninterface moving to different designated location in the cloud.
Has the following sub-cases:
UC 1.1 - Migrate a single dualstack VM residing on a vpn
UC 1.2 - Migrate multiple dualstack VMs residing on different vpns

UC 2: Evacuation of VM (voluntary / involuntary shutdown of the compute host)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VM evacuation cycles are made robust within L3VPN Engine.
This mimics a vpninterface moving to different location in the cloud, but triggered via failing
a compute node.  Has the following sub-cases:
Has the following sub-cases:
UC 2.1 -  Evacuate a single dualstack VM from a single vpn from a compute host
UC 2.2 -  Evacuate multiple dualstack VMs across multiple vpns from the same compute host

UC 3: An Extra-route serviced by one or more VMs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure ECMP handling robustness within L3VPN Engine.
This mimics an ipv4 address being reachable from multiple nexthops (or multiple vpninterfaces).
Has the following sub-cases:
UC 3.1 -  Append a nexthop to an existing IPv4 extra-route
UC 3.2 -  Remove a nexthop from an existing IPv4 extra-route with multiple nexthops
UC 3.3 - Clear away an IPv4 extra-route with multiple nexthops from a router, altogether
UC 3.4 - Delete the VM holding the nexthop of an extra-route
UC 3.5 - Delete a VM and add another new VM holding the same nexthop-ip of an existing extra-route

UC 4: Robust MIP Handling
^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure VIP/MIP handling robustness within L3VPN Engine.
Has the following sub-cases:
UC 4.1 - Move a MIP from one VM port to another VM port, wherein both the VMs are on the same subnet.
UC 4.2 - When a MIP is shared by two VM ports (active / standby), delete the VM holding the MIP.
UC 4.3 - Migrate a VM which is holding MIP.
UC 4.4 - MIP traffic impact on ODL cluster reboot.
UC 4.5 - MIP traffic impact on OVS disconnect/connect.
UC 4.6 - MIP Aliveness monitoring sessions are not persistent.

UC 5: IP visibility across related VPNs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is to ensure that ip reachability across two related vpns is made robust within L3VPN Engine.
Has the following sub-cases:
UC 5.1 - Peering VPNs being configured and initiating migration of VMs on one of the peering VPNs
UC 5.2 - Delete peering VPNs simultaneously

In general the above tests sufficiently trigger IP Serialization enforcement and this will enable us
to remove the 2 seconds sleep() from within the ArpProcessingEngine (ArpNotificationHandler).

UC 6: Speed up Router-Association and Router-Disassociation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This use-case is about attempting to speed up the swap of Router into L3VPN and vice-versa.
The idea is to eliminate the 2 seconds sleep present within swap logic, thereby increasing
the rate of servicing vpn-interfaces in the router for the swap cases.


Proposed change
===============
We brainstormed many proposals (or ways) to enforce IP Serialization (within the scope of router / vpn)
and ended up with agreeing with the following proposal.

The proposal chosen was about enforcing IP Serialization by processing all
the VPNs (and then all VPNInterfaces and IPAddresses within them)
in the ODL system through a single node.

Please note that the L3VPN Service consists of the following engines:
* VPNEngine (everything under VpnManager-Api and VpnManager-Impl)
* FIBEngine (everything under FibManager-Api and FibManager-Impl)
* VPNTunnelEngine (TunnelInterfacexxxListener and TEPListener)
* SubnetRouteEngine (VpnSubnetRouteHandler)
* ARPLearningEngine (ArpNotificationHandler and AlivenessMonitor)
All the above engines will be effected/affected as part of implementing following proposals.

Section 1
---------
This section talks about enforcing IP Serialization for extra-routes.
The main goal of the proposed implementation in this section is two-fold:

* To enforce IP Serialization for Extra-Route (or Static-Route) IP Addresses in a plain-router domain
  (or) a BGPVPN routing domain
* To eliminate the Thread.sleep enforced for extra-route hanlding in NeutronRouterChangeListener and
  also to remove the clusterLocks introduced in NextHopManager.
* The implementation enforces IP Serialization in the following way:

 1. It introduces a new yang container, 'extra-route-adjacency' to hold all the configured extra-routes.
    The container model is defined in the Yang Changes section.
 2. All the extra-routes configured on a router will now be written to 'extra-route-adjacency' by
    Neutronvpn. This will eliminate adding the extra-routes as adjacencies to their respective nexthop
    interfaces, i.e, Extra-Routes will never be represented as vpn-interface adjacencies going forward.
    All information about the configured extra-routes will only be present in 'extra-route-adjacency'.
 3. A new 'extra-route-adjacency' listener will be responsible for creating FIB, and updating other ECMP
    related datastores for the extra-routes. This listener will be a clustered one, that will use EOS to
    execute only on the owner node for L3VPN Entity. Translation of FIB to flows remains unchanged.
 4. This section will also handle configuring/unconfiguring pre-created extra-routes on VMs that are
    booted at a later point in time. This is done by a new service for extra-route to port binding
    service, that will be executed on every VM addition/deletion.

Section 2
---------
This section talks about enforcing IP Serialization for MIPs.

* The implementation enforces IP Serialization in the following way:

 1. A new MIP-Engine will be implemented which will listen on 'mip-route-adjacency' config container.
    The container model is defined in the Yang Changes section.
 2. When GARP/ARP Responses messages are punted to ODL controller, ArpNotificationHandler will write MIP info to
    'mip-route-adjacency'.This listener will be a clustered one, that will use EOS to execute only on the
    owner node for L3VPN Entity.
 3. MIP-Engine will be responsible for creating/deleting the FIB entry. It will also trigger the Aliveness Manager
    to start/stop the Monitoring session for each learnt IPs.
 4. Upon ODL cluster reboot , MIP-Engine will trigger the Aliveness Manager to start the monitoring as Aliveness
    Manager does not persist the Monitoring sessions.

This section talks about making VPNEngine robust enough to handle following MIP related scenario:

 1. Currently when OVS disconnects from ODL controller, AlivenessManager does not detect it. It tries to send
    Arp Request message to OVS hosting MIP and fails. Eventually AlivenessManager thinks MIP is dead and forces
    VPNEngine to withdraw the MIP route from DC-Gateway. It leads to MIP traffic loss eventhough MIP is active on OVS.
 2. VPNEngine listens on 'Nodes' container to detect OVS disconnect/connect.VPNEngine will pause the Monitoring
    session for MIP when OVS disconnects and will resume the Monitoring session for MIP when OVS connects.

Section 3
---------
Details about the proposal is given below to facilitate implementors.

1. The service name used will be 'L3VPN' and for that service name the VPN-Feature will
   choose a leader node from the 3 nodes in the ODL cluster.  If there is only one node,
   then that node will be considered the leader.

2. How and which node is chosen as a leader will be decoupled from the VPN Engine.
   All the engines within the L3VPN Service will only be consumers of interface exposed by
   a new entity 'VpnLeadershipTracker' ,and this new entity will be responsible to tag
   the leader node by using 'L3VPN' service name as the key.
   While initially we will use cluster-singleton to choose a node as the leader for 'L3VPN'
   service, the tracker will also be expanded as a later review to choose that node which is
   holding the Default Operational Shard as the leader, as #ofreads and #ofwrites to the
   Default Operational Shard is higher by all the engines within the L3VPN Service.

3. A new `VPNEvent` POJO will be made available to store immutable information pertaining to an event of interest
   to the L3VPN service.  This pojo is not a datastore entity and will be constructed dynamically only on the node
   which is the current leader of service name 'L3VPN'.  Also the VPNEvent POJO will contain all the information
   required to process an event by all the engines within the L3VPN service.

4. All along in today's L3VPN Service, the FIBEngine is responsible for processing a VRFEntry creation/deletion/updation.
   Fundamentally, converting an action on a VRFEntry to flow additon/ group addition/ flow removal / group removal is
   driven by FIBEngine (aka VrfEntryListener).
   Going foward, while the FIBEngine will continue to hold this responsibility, the implementation will change the
   lifecycle for VRFEntry handling being triggered synchronously by the VPNEngine instead of asynchronously driven by the FIBEngine.
   This will provide the benefit of passing all the required information synchronously to the methods in the FIBEngine for
   VRFEntry handling by the VPNEngine.  The VRFEntry would become an artifact instead of a trigger source.
   This type of design also enables use of DJC to enforce IP Serialization within a given VPNInstance.
   This change additionally provides elimination of backpulls from the FIBEngine towards VPNEngine and other
   modules for non-BGP-imported-rows

5. Other than imported BGP routes, all other types of route processing (VM ports, exported VM ports, extra-routes, MIPs), will
   be done in the way quoted in point 4 above.  The BGP imported routes from other neighbours will continue to be
   driven from within the VRFEntryListener.

6. Re-use as much existing handlers within the engines of L3VPN Service in order to contain the robustness effort

7. All the VPN Datastores will continued to be made available the same way for other external interfaces and consumers of
   external interfaces to remain unaffected.  Most specifically implementing this area will not effect NATEngine,
   InterVPNLinksEngine and BGPEngine.

8. All BGP Advertisements and Withdrawals (for all routes managed by ODL itself - i.e., non-BGP-imported routes), will be
   done within the VRFEntryListener rather than by the VPN Engine.

9. The VPNInstance creation/deletion/updation handling will be driven on whichever node is the leader
   for 'L3VPN' service as per VpnLeadershipTracker.  The handler for VPNInstance will continue to
   use JobCoordinator keyed on 'VPN-<vpn-name>' to process creation/updation/deletion.

10. The VpnInterface creation/deletion/updation handling will also be driven on whichever node is the leader
    for 'L3VPN' service as per VpnLeadershipTracker.  The following jobKeys for JobCoordinator will be applied
    for VpnInterfaceEvents:

    a. The jobKey of 'vpnid-dpnid' will be used for populateFibOnNewDpn and cleanupFibOnNewDpn (and its related methods)
       Here vpnid is dataplane representation of the VPN (and not the control plane vpnuuid).

    b. b1. The jobKey of 'VPN-<vpn-interface-name>' will continued to be used to serialize all events for a specific vpn-interface.

       b2. Within the 'VPN-<vpn-interface-name>' run, a nested job will be fired with key of 'VPN-rd-prefix' to serialize handler
           run for all IPAddresses within a given vpn (identified by rd). The 'prefix' here can be primary-ip, extra-route-prefix,
           or a discovered mip-prefix.

There are `Thread.sleep` in mulitiple places inside ODL Netvirt projects.  This spec attempts to eliminate the sleep invocations in
the following files:
VpnInterfaceManager  - sleep introduced to allow batch movement of vpn-interfaces from router-to-bgpvpn and vice-versa
ArpNotificationHandler (arpcachetimeout) - The timeout here was actually added to facilitate vpn-lifecycle for a MIP.
NexthopManager - ClusterLock with sleep used to safe management of l3nexthop (createLocalNextHop).

There are still sleeps in other services like Elan, L2Gateway etc and those sleep removals need to be pursued by respective
components.

Pipeline changes
----------------
None

Yang changes
------------
Changes will be needed in ``l3vpn.yang`` , ``odl-l3vpn.yang`` , ``odl-fib.yang`` and
``neutronvpn.yang`` to support the robustness improvements.

ODL-FIB YANG CHANGES
^^^^^^^^^^^^^^^^^^^^
.. code-block:: none
   :caption: odl-fib.yang


    --- a/fibmanager/api/src/main/yang/odl-fib.yang
    +++ b/fibmanager/api/src/main/yang/odl-fib.yang
    @@ -100,15 +100,19 @@ module odl-fib {

     container fibEntries {
         config true;
    -        list vrfTables{
    -            key "routeDistinguisher";
    -            leaf routeDistinguisher {type string;}
    -            uses vrfEntries;
    -            uses macVrfEntries;
    -        }
    +        list vpnNames {
    +            key vpnName;
    +            leaf vpnName { type string; }
    +            list vrfTables{
    +                key "routeDistinguisher";
    +                leaf routeDistinguisher {type string;}
    +                uses vrfEntries;
    +                uses macVrfEntries;
    +            }

    -        container ipv4Table{
    -            uses ipv4Entries;
    +            container ipv4Table{
    +                uses ipv4Entries;
    +            }
         }
     }

ODL-L3VPN YANG CHANGES
^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: none
   :caption: odl-l3vpn.yang


   --- a/vpnmanager/api/src/main/yang/odl-l3vpn.yang
   +++ b/vpnmanager/api/src/main/yang/odl-l3vpn.yang
   @@ -184,10 +184,13 @@ module odl-l3vpn {
                }
         }

    -    container evpn-rd-to-networks {
    +    container evpn-to-networks {
             description "Holds the networks to which given evpn is attached";
    -        list evpn-rd-to-network {
    +        list evpn-to-network {
    -           key rd;
    +           key vpn-name;
    +           leaf vpn-name {
    +             type string;
    +           }
                leaf rd {
                  type string;
                }
    @@ -261,7 +264,7 @@ module odl-l3vpn {
         container vpn-instance-op-data {
             config false;
             list vpn-instance-op-data-entry {
    -           key vrf-id;
    +           key vpn-instance-name;
                leaf vpn-id { type uint32;}
                leaf vrf-id {
                  description


    @@ -699,4 +699,16 @@ module odl-l3vpn {
                 leaf-list nexthop-key { type string; }
             }
         }
    +
    +    container extra-route-adjacency {
    +        config false;
    +        list vpn {
    +            key "vpn-name";
    +            leaf vpn-name { type string; }
    +            list destination {
    +                key "destination-ip";
    +                leaf destination-ip { type string; }
    +                list next-hop {
    +                    key "next-hop-ip";
    +                    leaf next-hop-ip { type string; }
    +                    leaf interface-name { type string; }
    +                }
    +            }
    +        }
    +    }
    +
    +    container mip-route-adjacency {
    +        config true;
    +        list mip-ip {
    +            key "vpn-name mip-ip";
    +            leaf mip-ip { type string; }
    +            leaf vpn-name { type string; }
    +            leaf port-name { type string; }
    +            leaf mac-address { type string; }
    +            leaf creation-time { type string; }
    +        }
    +    }

Solution considerations
-----------------------

Configuration impact
--------------------

Clustering considerations
-------------------------
The feature should operate in ODL Clustered (3-node cluster) environment reliably.

Other Infra considerations
--------------------------
N.A.

Security considerations
-----------------------
N.A.

Scale and Performance Impact
----------------------------
Not covered by this Design Document.

Targeted Release
----------------
Fluorine.

Alternatives
------------
Alternatives considered and why they were not selected.

Usage
=====

Features to Install
-------------------
This feature can be used by installing odl-netvirt-openstack.
This feature doesn't add any new karaf feature.

REST API
--------

Implementation
==============

Assignee(s)
-----------
Primary assignee:

  Vivekanandan Narasimhan (n.vivekanandan@ericsson.com)

Work Items
----------
The Trello cards have already been raised for this feature
under the l3vpn-robustness.

#Here is the link for the Trello Card:
#https://trello.com/c/Tfpr3ezF/33-evpn-evpn-rt5

Dependencies
============

Testing
=======
Capture details of testing that will need to be added.

Unit Tests
----------
Appropriate UTs will be added for the new code coming in once framework is in place.

Integration Tests
-----------------
There won't be any Integration tests provided for this feature.

CSIT
----
New CSIT testcases will be added for this feature, as this starts to use Neutron BGPVPN APIs and
makes it official for ODL platform.

Documentation Impact
====================

References
==========
