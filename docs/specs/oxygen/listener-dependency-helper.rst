==========================
Listener Dependency Helper
==========================

https://git.opendaylight.org/gerrit/#/q/topic:ListenerDepedencyHelper

Listener Dependency Helper makes "Data Store Listeners" independent from dependency
resolution.

Problem description
===================
When a DataStore-Listener is fired with config add/update/delete event, as
part of listener processing it may try to read the other data store objects,
at times those datastore objects are not yet populated. In this scenario,
listener event processing has to be delayed (or) discarded, as the required
information is NOT entirely available. Later when the dependant data objects
are available, this listener event will not be triggered again by DataStore.

This results in some events not getting processed resulting in possible
data-path, bgp control and data plane failures.

**Example**: VpnInterface add() callback triggered by MD-SAL on vpnInterface
add. While processing add() callback, the corresponding vpnInstance is
expected to be present in MD-SAL operational DS; which means that vpnInstance
creation is complete (updating the vpn-targets in Operational DS and BGP).


Information: vpnInstance Config-DS listener thread has to process vpnInstance
creation and update vpnInstance in operational DS. vpnInstance creation
listener callback is handled by different listener thread.

Use Cases
---------
**Use Case 1:** VPNInterfaces may get triggered before VPNInstance Creation.

Current implementation: Delay based waits for handling VPNInterfaces that may
get triggered before VPNInstance Creation(waitForVpnInstance()).

**Use Case 2:** VPNManager to handle successful deletion of VPN which has a
large number of BGP Routes (internal/external):

Current implementation: Delay-based logic on VPNInstance delete in
VPNManager (waitForOpRemoval()).

**Use Case 3:** VpnSubnetRouteHandler that may get triggered before VPNInstance
Creation.

Current implementation: Delay based waits in VpnSubnetRouteHandler which may
get triggered before VPNInstance Creation(waitForVpnInstance()).

**Use Case 4:** VPN Swaps (Internal to External and vice-versa)

Current implementation: Currently we support max of 100 VM’s for swap
(VpnInterfaceUpdateTimerTask, waitForFibToRemoveVpnPrefix()).

Proposed change
===============
During Listener event call-back (AsyncDataTreeChangeListenerBase) from
DataStore, check for pending events in "Listener-Dependent-Queue" with
same InstanceIdentifier to avoid re-ordering.

Generic Queue Event Format:
---------------------------
key                             : Instance Identifier
eventType                       : Type of event (ADD/UPDATE/DELETE)
oldData                         : Data before modification (for Update event);
newData                         : Newly populated data
queuedTime                      : at which the event is queued to LDH.
lastProcessedTime               : latest time at which dependency list verified
expiryTime                      : beyond which processing for event is useless
waitBetweenDependencyCheckTime  : wait time between each dependency check
dependentIIDs                   : list of dependent InstanceIdentifiers
retryCount                      : max retries allowed.
databroker                      : data broker.
deferTimerBased                 : flag to choose between (timer/listener based).

For Use Case - 1: deferTimerBased shall be set to TRUE (as per the specification).

During processing of events (either directly from DataStore or from
"Listener-Dependent-Queue"), if there any dependent objects are yet to
populated; queue them to "Listener-Dependent-Queue".

Expectations from Listener: Listener will push the callable instance to
"Listener-Dependent-Queue" if it cannot proceed with processing of the
event due to dependent objects/InstanceIdentifier and list of dependent IID's.

There are two approaches the Listener Dependency check can be verified.

    **approach-1** Get the list of dependent-IID's, query DataStore/Cache for
    dependency resolution at regular intervals using "timer-task-pool". Once
    all the dependent IID's are resolved, call respective listener for
    processing.

LDH-task-pool : pool of threads which query for dependency resolution READ
ONLY operation in DataStore. These threads are part of LDH common for all
listeners.

hasDependencyResolved(<InstanceIdentifier iid, Boolean shouldDataExist,
DataStoreType DSType> List), this shall return either Null list (or) the list
which has dependencies yet to be resolved. In case Listener has local-cache
implemented for set of dependencies, it can look at cache and identify. This
api will be called from LDH-task-pool of thread(s).

instanceIdentifier is the MD-SAL key value which need to be verified for
existence/non-existence of data.
Boolean shouldDataExist: shall be TRUE, if the Listener expects to have the
information exists in MD-SAL; False otherwise.

    **approach-2** Register Listener for wild-card path of IID's.

When a Listener gets queued to ""Listener-Dependent-Queue", LDH shall register
itself as Listener for the dependent IID's (using wild-card-path/parent-node).
Once the listener gets fired, identify the dependent listeners waiting for the
Data. Once the dependent Listener is identified, if the dependent-IID list is
NULL. Trigger listener for processing the event.
LDH-task-pool shall unregister itself from wild-card-path/parent-node once there
are no dependent listeners on child-nodes.

**Re-Ordering**

The following scenario, when re-ordering can happen and avoidance of the same:

Example: Key1 and Value1 are present in MD-SAL Data Store under Tree1, SubTree1
 (for say). Update-Listener for Key1 is dependent on Dependency1.

Key1 received UPDATE event (UPDATE-1) with value=x, at the time of processing
UPDATE-1, dependency is not available. So Listener Queued ‘UPDATE-1’ event to
“UnProcessed-EventQueue”.
same key1 received UPDATE event (UPDATE-2) with value=y, at the time of
processing UPDATE-2, dependency is available (Dependency1 is resolved), so it
goes and processes the event and updates value of Key1 to y.

After WaitTime, event Key1, UPDATE-1 is de-queued from “UnProcessed-EventQueue”
 and put for processing in Lister. Listener processes it and updates the Key1
 value to x. (which is incorrect, happened due to re-ordering of events).

To avoid reordering of events within listener, every listener call back shall
peek into “UnProcessed-EventQueue” to identify if there exists a pending event
with same key value; if so, either suppress (or)
queue the event. Below are event ordering expected from MD-SAL and respective
actions:

**what to consider before processing the event to avoid re-ordering of events:**

+-----------------------------------------------------------------+
| Current Event| Queued Event| Action                             |
+-----------------------------------------------------------------+
|  ADD         |  ADD        | NOT EXPECTED                       |
+-----------------------------------------------------------------+
|  ADD         |  REMOVE     | QUEUE THE EVENT                    |
+-----------------------------------------------------------------+
|  ADD         |  UPDATE     | NOT EXPECTED                       |
+-----------------------------------------------------------------+
|  UPDATE      |  ADD        | QUEUE EVENT                        |
+-----------------------------------------------------------------+
|  UPDATE      |  UPDATE     | QUEUE EVENT                        |
+-----------------------------------------------------------------+
|  UPDATE      |  REMOVE     | NOT EXPECTED                       |
+-----------------------------------------------------------------+
|  REMOVE      |  ADD        | SUPPRESS BOTH                      |
+-----------------------------------------------------------------+
|  REMOVE      |  UPDATE     | EXECUTE REMOVE SUPPRESS UPDATE     |
+-----------------------------------------------------------------+
|  REMOVE      |  REMOVE     | NOT EXPECTED                       |
+-----------------------------------------------------------------+

Pipeline changes
----------------
none

Yang changes
------------
none

Configuration impact
--------------------
none

Clustering considerations
-------------------------
In the two approaches mentioned:
1 - Timer: polling MD-SAL for dependency resolution may incur in more
number of reads.

2 - RegisterListener: RegisterListener may some impact at the time of
registering listener after which a notification message to cluser nodes.

Predifined List of Listeners
----------------------------
operational/odl-l3vpn:vpn-instance-op-data/vpn-instance-op-data-entry/*
operational/odl-l3vpn:vpn-instance-op-data/vpn-instance-op-data-entry/
vpn-id/vpn-to-dpn-list/*
config/l3vpn:vpn-instances/*


Other Infra considerations
--------------------------

Security considerations
-----------------------
none

Scale and Performance Impact
----------------------------
this infra, shall improve scaling of application without having to wait for
dependent data store gets populated.
Performance shall remain intact.


Targeted Release
----------------

Alternatives
------------
- use polling/wait mechanisms


Features to Install
-------------------

REST API
--------

CLI
---
CLI will be added for debugging purpose.

Implementation
==============

Assignee(s)
-----------

Primary assignee:
Siva Kumar Perumalla (sivakumar.perumalla@ericsson.com)

Other contributors:
Suneelu Verma K.

Work Items
----------

Dependencies
============

Testing
=======

Unit Tests
----------

Integration Tests
-----------------

CSIT
----

Documentation Impact
====================

References
==========


Acronyms
--------
IID: InstanceIdentifier
