.. contents:: Table of Contents
   :depth: 3

================================================
Cross site connectivity with federation service
================================================

https://git.opendaylight.org/gerrit/#/q/topic:federation-plugin

Enabling neutron networks to expand beyond a single OpenStack instance to allow L2 switching and L3 routing
between sites. Sites may be geographically remote or partitioned in a single data center.

Each site is deployed with independent local ODL cluster. The clusters communicate using the federation
infrastructure [2] in order to publish MDSAL events whenever routable entities e.g. VM instances are added/removed
from remote sites.

VxLAN tunnels are used to form the overlay for cross site communication between OpenStack compute nodes.


Problem description
===================
Today, communication between VMs in remote sites is based on BGP control plane and requires DC-GW.
Overlay network between data centers is based on MPLSoverGRE or VxLAN if the DC-GW supports EVPN RT5 [4].
The purpose of this feature is to allow inter-DC communication independent from BGP control plane and DC-GW.

Use Cases
---------
This feature will cover the following use cases:

L2 switching use cases
^^^^^^^^^^^^^^^^^^^^^^^
* L2 Unicast frames exchanged between VMs sharing federated neutron network between OVS datapaths in
  remote sites
* L2 Unicast frames exchanged between VM and PNF sharing federated neutron network between OVS and HWVTEP
  datapath in remote sites
* L2 Broadcast frames exchanged between VMs sharing federated neutron network between OVS datapaths in
  remote sites
* L2 Broadcast frames exchanged between VM and PNF sharing federated neutron network between OVS and HWVTEP
  datapath in remote sites

L3 forwarding use cases
^^^^^^^^^^^^^^^^^^^^^^^^
* L3 traffic exchanged between VMs sharing federated neutron router between OVS datapaths in
  remote sites


Proposed change
===============
For Carbon release, cross-site connectivity will be based on the current HPE downstream federation plugin codebase.
This plugin implements the federation service API [3] to synchronize the following MDSAL subtrees between connected
sites:

* config/ietf-interfaces:interfaces
* config/elan:elan-interfaces
* config/l3vpn:vpn-interfaces
* config/network-topology:network-topology/topology/ovsdb:1
* operational/network-topology:network-topology/topology/ovsdb:1
* config/network-topology:network-topology/topology/hwvtep:1
* operational/network-topology:network-topology/topology/hwvtep:1
* config/opendaylight-inventory:nodes
* operational/opendaylight-inventory:nodes

The provisioning of connected networks between remote sites is out of the scope of this spec and described in [6].

Upon receiving a list of shared neutron networks and subnets, the federation plugin will propagate MDSAL entities from
all of the subtrees detailed above to remote sites based on the federation connection definitions.
The federated entities will be transformed to match the target network/subnet/router details in each remote site.

For example, ELAN interface will be federated with elan-instance-name set to the remote site elan-instance-name.
VPN interface will be federated with the remote site vpn-instance-name i.e. router-id and remote subnet-id contained
in the primary VPN interface adjacency.

This would allow remotely federated entities a.k.a shadow entities to be handled the same way local entities are handled
thus shadow entities will appear as if they were local entities in remote sites.
As a result, the following pipeline elements will be added for shadow entities on all compute nodes in each connected
remote site:

* ELAN remote DMAC flow for L2 unicast packets to remote site
* ELAN remote broadcast group buckets for L2 multicast packets to remote site
* FIB remote nexthop flow for L3 packet to remote site

The following limitations exist for the current federation plugin implementation:

* Federated networks use VxLAN network type and the same VNI is used across sites.
* The IP addresses allocated to VM instances in federated subnets do not overlap across sites.
* The neutron-configured VNI will be passed on the wire for inter-DC L2/L3 communication between VxLAN networks.
  The implementation is described in [5].


As part of Nitrogen, the federation plugin is planed to go through major redesign. The scope and internals have not
been finalized yet but this spec might be a good opportunity to agree on an alternate solution.

Some initial thoughts:

* For L3 cross site connectivity, it seems that federating the FIB vrf-entry associated with VMs in connected
  networks should be sufficient to form remote nexthop connectivity across sites.
* In order to create VxLAN tunnels to remote sites, it may be possible to use the external tunnel concept instead
  of creating internal tunnels that are dependent on federation of the OVS topology nodes from remote sites.
* L2 cross site connectivity seems to be the most challenging part for federation of MAC addresses of both VM
  instances PNFs connected to HWVTEP.
  It seems that if the ELAN model could be enhanced to have remote-mac-entry model contaning MAC address,
  ELAN instance name and remote TEP ip, it would be possible to federate such entity to remote sites in order
  to create remote DMAC flows for cases of remote VM instances and PNFs connected HWVTEP in remote sites.


Pipeline changes
----------------
No new pipeline changes are introduced as part of this feature. The pipeline flow between VM instances in
remote sites is similar to the current implementation of cross compute intra-DC traffic since the
realization of remote compute nodes is similar to local ones.

Yang changes
------------
The following new yang models will be introduced as part of the federation plugin API bundle:

Federation Plugin Yang
^^^^^^^^^^^^^^^^^^^^^^^
Marking for each federated entity using ``shadow-properties`` augmentation
::

 module federation-plugin {
    yang-version 1;
    namespace "urn:opendaylight:netvirt:federation:plugin";
    prefix "federation-plugin";

    import yang-ext {
         prefix ext;
         revision-date "2013-07-09";
    }

    import ietf-yang-types {
         prefix yang;
    }

    import network-topology {
         prefix topo;
    }

    import opendaylight-inventory {
         prefix inv;
    }

    import ietf-interfaces {
         prefix if;
    }

    import elan {
         prefix elan;
    }

    import l3vpn {
         prefix l3vpn;
    }

    import neutronvpn {
         prefix nvpn;
    }

    revision "2017-02-19" {
        description "Federation plugin model";
    }

    grouping shadow-properties {
        leaf shadow {
            type boolean;
            description "Represents whether this is a federated entity";
        }
        leaf generation-number {
            type int32;
            description "The current generation number of the federated entity";
        }
        leaf remote-ip {
            type string;
            description "The IP address of the original site of the federated entity";
        }
    }

    augment "/topo:network-topology/topo:topology/topo:node" {
        ext:augment-identifier "topology-node-shadow-properties";
        uses shadow-properties;
    }

    augment "/inv:nodes/inv:node" {
        ext:augment-identifier "inventory-node-shadow-properties";
        uses shadow-properties;
    }

    augment "/if:interfaces/if:interface" {
        ext:augment-identifier "if-shadow-properties";
        uses shadow-properties;
    }

    augment "/elan:elan-interfaces/elan:elan-interface" {
        ext:augment-identifier "elan-shadow-properties";
        uses shadow-properties;
    }

    augment "/l3vpn:vpn-interfaces/l3vpn:vpn-interface" {
        ext:augment-identifier "vpn-shadow-properties";
        uses shadow-properties;
    }
 }


Federation Plugin Manager Yang
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Management of federated networks and routed RPCs subscription
::

 module federation-plugin-manager {
    yang-version 1;
    namespace "urn:opendaylight:netvirt:federation:plugin:manager";
    prefix "federation-plugin-manager";

    import yang-ext {
        prefix ext;
        revision-date "2013-07-09";
   }

   import ietf-yang-types {
        prefix yang;
   }

   revision "2017-02-19" {
       description "Federation plugin model";
   }

    identity mgr-context {
        description "Identity for a routed RPC";
    }

    container routed-container {
        list route-key-item {
            key "id";
            leaf id {
                type string;
            }

            ext:context-instance "mgr-context";
        }
    }

    container federated-networks {
        list federated-network {
            key self-net-id;
            uses federated-nets;
        }
    }

    container federation-generations {
        description
                "Federation generation information for a remote site.";
        list remote-site-generation-info {
            max-elements "unbounded";
            min-elements "0";
            key "remote-ip";
            leaf remote-ip {
                mandatory true;
                type string;
                description "Remote site IP address.";
            }
            leaf generation-number {
                type int32;
                description "The current generation number used for the remote site.";
            }
        }
    }

    grouping federated-nets {
        leaf self-net-id {
            type string;
            description "UUID representing the self net";
        }
        leaf self-subnet-id {
            type yang:uuid;
            description "UUID representing the self subnet";
        }
        leaf self-tenant-id {
            type yang:uuid;
            description "UUID representing the self tenant";
        }
        leaf subnet-ip {
            type string;
            description "Specifies the subnet IP in CIDR format";
        }

        list site-network {
            key id;
            leaf id {
                type string;
                description "UUID representing the site ID (from xsite manager)";
            }
            leaf site-ip {
                type string;
                description "Specifies the site IP";
            }
            leaf site-net-id {
                type string;
                description "UUID of the network in the site";
            }
            leaf site-subnet-id {
                type yang:uuid;
                description "UUID of the subnet in the site";
            }
            leaf site-tenant-id {
                type yang:uuid;
                description "UUID of the tenant holding this network in the site";
            }
        }
    }
 }

Federation Plugin RPC Yang
^^^^^^^^^^^^^^^^^^^^^^^^^^^
FederationPluginRpcService yang definition for ``updateFederatedNetworks`` RPC
::

 module federation-plugin-rpc {
    yang-version 1;
    namespace "urn:opendaylight:netvirt:federation:plugin:rpc";
    prefix "federation-plugin-rpc";

    import yang-ext {
        prefix ext;
        revision-date "2013-07-09";
   }

   import ietf-yang-types {
        prefix yang;
   }

   import federation-plugin-manager {
	prefix federation-plugin-manager;
   }

   revision "2017-02-19" {
       description "Federation plugin model";
   }

    rpc updateFederatedNetworks {
        input {
            leaf route-key-item {
                type instance-identifier;
                ext:context-reference federation-plugin-manager:mgr-context;
            }

            list federated-networks-in {
                key self-net-id;
                uses federation-plugin-manager:federated-nets;
                description "Contain all federated networks in this site that are still
                             connected, a federated network that does not appear will be considered
                             disconnected";
            }
        }
    }
 }


Configuration impact
--------------------
None.

Clustering considerations
-------------------------
The federation plugin will be active only on one of the ODL instances in the cluster. The cluster singleton service
infrastructure will be used in order to register the federation plugin routed RPCs only on the selected ODL instance.


Other Infra considerations
--------------------------
None

Security considerations
-----------------------
None

Scale and Performance Impact
----------------------------
None

Targeted Release
----------------
Carbon

Alternatives
------------
None

Usage
=====

Features to Install
-------------------
odl-netvirt-federation

This is a new feature that will load odl-netvirt-openstack and the federation service features.
It will not be installed by default and requires manual startup using karaf ``feature:install`` command.

REST API
--------
Connecting neutron networks from remote sites

**URL:** restconf/operations/federation-plugin-manager:updateFederatedNetworks

**Sample JSON data**
::

 {
    "input": {
        "federated-networks-in": [
            {
                "self-net-id": "c4976ee7-c5cd-4a5e-9cf9-261f28ba7920",
                "self-subnet-id": "93dee7cb-ba25-4318-b60c-19a15f2c079a",
                "subnet-ip": "10.0.123.0/24",
                "site-network": [
                    {
                        "id": "c4976ee7-c5cd-4a5e-9cf9-261f28ba7922",
                        "site-ip": "10.0.43.146",
                        "site-net-id": "c4976ee7-c5cd-4a5e-9cf9-261f28ba7921",
                        "site-subnet-id": "93dee7cb-ba25-4318-b60c-19a15f2c079b",
                    }
                ]
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

Primary assignee:
  Tali Ben-Meir <tali@hpe.com>

Other contributors:
  Guy Sela <guy.sela@hpe.com>

  Shlomi Alfasi <shlomi.alfasi@hpe.com>

  Yair Zinger <yair.zinger@hpe.com>

Work Items
----------
Trello card https://trello.com/c/mgdUO6xx/154-federation-plugin-for-netvirt

Since the code was already implemented in downstream no work items will be defined

Dependencies
============
This feature will be implemented in 2 new bundles - ``federation-plugin-api`` and `federation-plugin-impl``
the implementaion will be dependent on ``federation-service-api`` [3] bundle from OpenDaylight federation project.

The new karaf feature ``odl-netvirt-federation`` will encapsulate the ``federation-plugin`` bundles
and will be dependant on the followings features:

* ``federation-with-rabbit`` from federation project
* ``odl-netvirt-openstack`` from netvirt project


Testing
=======

Unit Tests
----------
End-to-end component service will test the federation plugin on top of the federation service.

Integration Tests
-----------------
None

CSIT
----
The CSIT infrastructure will be enhanced to support connect/disconnect operations between sites using
updateFederatedNetworks RPC call.

A new federation suite will test L2 and L3 connectivity between remote sites and will be based based on the
existing L2/L3 connectivity suites.
CSIT will load sites A,B and C in 1-node/3-node deployment options to run the following tests:

1. Install odl-netvirt-federation feature

  * Basic L2 connectivity test within the site
  * Basic L3 connectivity test within the site
  * L2 connectivity between sites - expected to fail since sites are not connected
  * L3 connectivity between sites - expected to fail since sites are not connected

2. Connect sites A,B

  * Basic L2 connectivity test within the site
  * L2 connectivity test between VMs in sites A,B
  * L2 connectivity test between VMs in sites A,C and B,C - expected to fail since sites are not connected
  * Basic L3 connectivity test within the site
  * L3 connectivity test between VMs in sites A,B
  * L3 connectivity test between VMs in sites A,C and B,C - expected to fail since sites are not connected

3. Connect site C to A,B

  * L2 connectivity test between VMs in sites A,B B,C and A,C
  * L3 connectivity test between VMs in sites A,B B,C and A,C
  * Connectivity test between VMs in non-federated networks in sites A,B,C - expected to fail

4. Disconnect site C from A,B

  * Repeat the test steps from 2 after C disconnect. Identical results expected.

5. Disconnect sites A,B

  * Repeat the test steps from 1 after A,B disconnect. Identical results expected.

6. Federation cluster test

 * Repeat test steps 1-5 while rebooting the ODLs between the steps similarly to the existing cluster suite.


Documentation Impact
====================
None.

References
==========

[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] `Federation project <https://wiki.opendaylight.org/view/Federation:Main>`__

[3] `Federation service API <https://github.com/opendaylight/federation/tree/master/federation-service/api>`__

[4] `Support of VxLAN based connectivity across Datacenters <http://docs.opendaylight.org/en/latest/submodules/netvirt/docs/specs/l3vpn-over-vxlan-with-evpn-rt5.html>`__

[5] `VNI based L2 switching, L3 forwarding and NATing <http://docs.opendaylight.org/en/latest/submodules/netvirt/docs/specs/vni-based-l2-switching-l3-forwarding-and-NATing.html>`__

[6] `Cross site manager presentation ODL Summit 2016 <https://www.youtube.com/watch?v=wDdP6ONg8wU&list=PL8F5jrwEpGAiRCzJIyboA8Di3_TAjTT-2>`__
