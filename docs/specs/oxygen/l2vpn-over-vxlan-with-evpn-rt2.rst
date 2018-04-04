.. contents:: Table of Contents
      :depth: 5

=======================================================
Support of VXLAN based L2 connectivity across Datacenters
=======================================================

https://git.opendaylight.org/gerrit/#/q/topic:EVPN_RT2

Enable realization of L2 connectivity over VXLAN tunnels using L2 BGPVPNs,
internally taking advantage of EVPN as the BGP Control Plane mechanism.

Problem description
===================

OpenDaylight NetVirt service today supports L3VPN connectivity over VXLAN tunnels.
L2DCI communication is not possible so far.

This spec attempts to enhance the BGPVPN service in NetVirt to
embrace inter-DC L2 connectivity over external VXLAN tunnels.

In scope
---------

The scope primarily includes providing ability to support intra-subnet
connectivity across DataCenters over VXLAN tunnels using BGP EVPN with type L2.

When we mention that we are using EVPN BGP Control plane, this
spec proposes using the RouteType 2 as the primary
means to provision the control plane to enable inter-DC connectivity
over external VXLAN tunnels.

With this inplace we will be able to support the following.

* Intra-subnet connectivity across dataCenters over VXLAN tunnels.

The following are already supported as part of the other spec(RT5)
and will continue to function.

* Intra-subnet connectivity within a DataCenter over VXLAN tunnels.
* Inter-subnet connectivity within a DataCenter over VXLAN tunnels.
* Inter-subnet connectivity across dataCenters over VXLAN tunnels.

Out of scope
------------

Use Cases
---------

The following high level use-cases will be realized by the implementation of this Spec.

Datacenter access from another Datacenter over WAN via respective DC-Gateways (L2 DCI)
+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

This use-case involves providing intra-subnet connectivity between two DataCenters.
Tenant VMs in one datacenter will be able to communicate with tenant VMs on the other
datacenter provided they are part of the same BGP EVPN and they are on same subnets.

The dataplane between the tenant VMs themselves and between the tenant VMs
towards the DC-Gateway will be over VXLAN Tunnels.

The dataplane between the DC-Gateway to its other WAN-based BGP Peers is
transparent to this spec.  It is usually MPLS-based EPVPN.

The BGP Control plane between the ODL Controller and the DC-Gateway will be
via EVPN RouteType 2 as defined in EVPN_RT2.

The control plane between the DC-Gateway and it other BGP Peers in the WAN
is transparent to this spec, but can be EVPN IP-MPLS.

In this use-case:

1. We will have only a single DCGW for WAN connectivity
2. MAC IP prefix exchange between ODL controller and DC-GW (iBGP) using EVPN RT2
3. WAN control plane may use EVPN IP-MPLS for route exchange.
4. On the DC-Gateway, the VRF instance will be configured with two sets of import/export targets. One set of import/export route targets belong to EVPN inside DataCenter (realized using EVPN RT2) and the second set of import/export route target belongs to WAN control plane.
5. EVPN single homing to be used in all RT2 exchanges inside the DataCenter i.e., ESI=0 for all prefixes sent from DataCenter to the DC-Gateway.


Proposed change
===============

The following components of an Openstack-ODL-based solution need to be enhanced to provide
intra-subnet and inter-subnet connectivity across DCs using EVPN MAC IP Advertisement
(Route Type 2) mechanism (refer EVPN_RT2):

* Openstack Neutron BGPVPN Driver
* OpenDaylight Controller (NetVirt)
* BGP Quagga Stack to support EVPN with RouteType 2 NLRI
* DC-Gateway BGP Neighbour that supports EVPN with RouteType 2 NLRI

The changes required in Openstack Neutron BGPVPN Driver and BGP Quagga Stack
are captured in the Solution considerations section down below.

Pipeline changes
----------------

INTRA DC
+++++++++

Intra Subnet, Local DPN: VMs on the same subnet, same VPN, same DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are no explicit pipeline changes for this use-case.

Intra Subnet, Remote DPN: VMs on two different DPNs, both VMs on the same subnet and same VPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are no explicit pipeline changes for this use-case.

Inter Subnet, Local DPN: VMs on different subnet, same VPN, same DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are no explicit pipeline changes for this use-case.

Inter Subnet, Local DPN: VMs on different subnet, same VPN, same DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are no explicit pipeline changes for this use-case.

INTER DC
+++++++++

Intra subnet Traffic from DC-Gateway to Local DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier table (0) =>
  | Dispatcher table (17) ``match: tunnel-type=vxlan`` =>
  | L2VNI_EXTERNAL_TUNNEL_DEMUX_TABLE (24) => ``match tunnel-id=l2vni, set elan-tag``
  | ELAN DMAC table (51) ``match: elan-tag=vxlan-net-tag,dst-mac=vm2-mac set reg6=vm-lport-tag`` =>
  | Egress table (220) ``match: reg6=vm-lport-tag output to vm port``

Intra subnet Traffic from Local DPN to DC-Gateway
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) =>
  | Dispatcher table (17) ``l2vpn service: set elan-tag=vxlan-net-tag`` =>
  | ELAN base table (48) =>
  | ELAN SMAC table (50) ``match: elan-tag=vxlan-net-tag,src-mac=vm1-mac`` =>
  | ELAN DMAC table (51) ``match: elan-tag=vxlan-net-tag,dst-mac=external-vm-mac set tun-id=vxlan-net-tag group=next-hop-group`` 
  | Next Hop Group ``bucket0 :set reg6=tunnel-lport-tag  bucket1 :set reg6=tunnel2-lport-tag``
  | Egress table (220) ``match: reg6=tunnel2-lport-tag`` output to ``tunnel2``


Inter subnet Traffic from Local DPN to DC-Gateway ( Symmetric IRB )
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier Table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | L3 Gateway MAC Table (19) ``match: vpn-id=l3vpn-id, dst-mac=vpn-subnet-gateway-mac-address`` =>
  | L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=dst-vm-ip-address set tun-id=l3vni output to nexthopgroup`` =>
  | NextHopGroup: ``set-eth-dst router-gw-vm, reg6=tunnel-lport-tag`` =>
  | Lport Egress Table (220) ``Output to tunnel port``

Inter subnet Traffic from DC-Gateway to Local DPN ( Symmetric IRB )
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier table (0) =>
  | Dispatcher table (17) ``match: tunnel-type=vxlan`` =>
  | L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE (23) => ``match tunnel-id=l3vni, set l3vpn-id`` =>
  | L3 Gateway MAC Table (19) => ``match dst-mac=vpn-subnet-gateway-mac-address`` =>
  | FIB table (21) ``match: l3vpn-tag=l3vpn-id,dst-ip=vm2-ip set reg6=vm-lport-tag goto=local-nexthop-group`` =>
  | local nexthop group ``set dst-mac=vm2-mac table=220`` =>
  | Egress table (220) ``match: reg6=vm-lport-tag output to vm port``

Inter subnet Traffic from Local DPN to DC-Gateway ( ASymmetric IRB )
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier Table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | L3 Gateway MAC Table (19) ``match: vpn-id=l3vpn-id, dst-mac=vpn-subnet-gateway-mac-address`` =>
  | L3 FIB Table (21) ``match: vpn-id=l3vpn-id, nw-dst=dst-vm-ip-address set tun-id=l2vni output to nexthopgroup`` =>
  | NextHopGroup: ``set-eth-dst dst-vm-mac, reg6=tunnel-lport-tag`` =>
  | Lport Egress Table (220) ``Output to tunnel port``

Intra subnet Traffic from DC-Gateway to Local DPN ( ASymmetric IRB )
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier table (0) =>
  | Dispatcher table (17) ``match: tunnel-type=vxlan`` =>
  | L2VNI_EXTERNAL_TUNNEL_DEMUX_TABLE (24) => ``match tunnel-id=l2vni, set elan-tag``
  | ELAN DMAC table (51) ``match: elan-tag=vxlan-net-tag,dst-mac=vm2-mac set reg6=vm-lport-tag`` =>
  | Egress table (220) ``match: reg6=vm-lport-tag output to vm port``


ARP Pipeline changes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Local DPN: VMs on the same subnet, same DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
a. Introducing a new Table aka ELAN_ARP_SERVICE_TABLE (Table 81).
This table will be the first table in elan pipeline.

  | Classifier table (0) =>
  | Dispatcher table (17) ``elan service: set elan-id=vxlan-net-tag`` =>
  | Arp Service table (81) => ``match: arp-op=req, dst-ip=vm-ip, ela-id=vxlan-net-tag inline arp reply``

Intra Subnet, Local DPN: VMs on the same subnet, on remote DC
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

  | Classifier table (0) =>
  | Dispatcher table (17) ``elan service: set elan-id=vxlan-net-tag`` =>
  | Arp Service table (81) => ``match: arp-op=req, dst-ip=vm-ip, ela-id=vxlan-net-tag inline arp reply``


Yang changes
------------
Changes will be needed in ``l3vpn.yang`` , ``odl-l3vpn.yang`` , ``odl-fib.yang`` and
``neutronvpn.yang`` to start supporting EVPN functionality.

ODL-L3VPN YANG changes
++++++++++++++++++++++
A new container evpn-rd-to-networks is added
This holds the rd to networks mapping
This will be useful to extract in which elan the received RT2 route can be injected into.

.. code-block:: none
   :caption: odl-l3vpn.yang

    container evpn-rd-to-networks {
        config false;
        description "Holds the networks to which given evpn is attached to";
        list evpn-rd-to-network {
           key rd;
           leaf rd {
             type string;
           }
           list evpn-networks {
            key network-id;
            leaf network-id {
              type string;
            }
           }
        }
    }

ODL-FIB YANG changes
++++++++++++++++++++
A new field macVrfEntries is added to the container ``fibEntries``
This holds the RT2 routes received for the given rd

.. code-block:: none
   :caption: odl-fib.yang

    grouping vrfEntryBase {
        list vrfEntry{
            key  "destPrefix";
            leaf destPrefix {
                type string;
                mandatory true;
            }
            leaf origin {
                type string;
                mandatory true;
            }
            leaf encap-type {
               type enumeration {
                  enum mplsgre {
                     value "0";
                     description "MPLSOverGRE";
                  }
                  enum vxlan {
                     value "1";
                     description “VNI";
                  }
               }
               default "mplsgre";
            }
            leaf l3vni {
               type uint32;
            }
            list route-paths {
                key "nexthop-address";
                leaf nexthop-address {
                    type string;
                }
                leaf label {
                    type uint32;
                }
                leaf gateway_mac_address {
                    type string;
                }
            }
        }
    }

    grouping vrfEntries{
        list vrfEntry{
            key  "destPrefix";
            uses vrfEntryBase;
        }
    }

    grouping macVrfEntries{
        list MacVrfEntry {
            key  "mac_address";
            uses vrfEntryBase;
            leaf l2vni {
               type uint32;
            }
        }
    }

   container fibEntries {
         config true;
         list vrfTables {
            key "routeDistinguisher";
            leaf routeDistinguisher {type string;}
            uses vrfEntries;
            uses macVrfEntries;//new field
         }
         container ipv4Table{
            uses ipv4Entries;
         }
    }

NEUTRONVPN YANG changes
+++++++++++++++++++++++
A new rpc ``createEVPN`` is added
Existing rpc associateNetworks is reused to attach a network to EVPN assuming
uuid of L3VPN and EVPN does not collide with each other.

.. code-block:: none
   :caption: neutronvpn.yang

    rpc createEVPN {
        description "Create one or more EVPN(s)";
        input {
            list evpn {
                uses evpn-instance;
            }
        }
        output {
            leaf-list response {
                type    string;
                description "Status response for createVPN RPC";
            }
        }
    }

    rpc deleteEVPN{
        description "delete EVPNs for specified Id list";
        input {
            leaf-list id {
                type    yang:uuid;
                description "evpn-id";
            }
        }
        output {
            leaf-list response {
                type    string;
                description "Status response for deleteEVPN RPC";
            }
        }
    }

    grouping evpn-instance {

        leaf id {
            mandatory "true";
            type    yang:uuid;
            description "evpn-id";
        }

        leaf name {
          type    string;
          description "EVPN name";
        }

        leaf tenant-id {
            type    yang:uuid;
            description "The UUID of the tenant that will own the subnet.";
        }

        leaf-list route-distinguisher {
            type string;
            description
            "configures a route distinguisher (RD) for the EVPN instance.
             Format is ASN:nn or IP-address:nn.";
        }

        leaf-list import-RT {
            type string;
            description
            "configures a list of import route target.
             Format is ASN:nn or IP-address:nn.";
        }

        leaf-list export-RT{
            type string;
            description
            "configures a list of export route targets.
             Format is ASN:nn or IP-address:nn.";
        }

        leaf l2vni {
           type uint32;
        }
    }

ELAN YANG changes
+++++++++++++++++++++++
Existing container elan-instances is augmented with evpn information.

A new list ``external-teps`` is added to elan container.
This captures the broadcast domain of the given network/elan.
When the first RT2 route is received from the dc gw,
it's tep ip is added to the elan to which this RT2 route belongs to.

.. code-block:: none
   :caption: elan.yang

    augment "/elan:elan-instances/elan:elan-instance" {
        ext:augment-identifier "evpn";
        leaf evpn-name {
            type string;
        }
        leaf l3vpn-name {
            type string;
        }
    }

    container elan-instances {
        list elan-instance {
            key "elan-instance-name";
            leaf elan-instance-name {
                type string;
            }
            //omitted other existing fields
            list external-teps {
                key tep-ip;
                leaf tep-ip {
                    type inet:ip-address;
                }
            }
        }
    }

    container elan-interfaces {
        list elan-interface  {
            key "name";
            leaf name {
                type leafref {
                    path "/if:interfaces/if:interface/if:name";
                }
            }
            leaf elan-instance-name {
                mandatory true;
                type string;
            }
            list static-mac-entries {
                key "mac";
                leaf mac {
                    type yang:phys-address;
                }
                leaf prefix {//new field
                    mandatory false;
                    type inet:ip-address;
                }
            }
        }
    }

    grouping forwarding-entries {
        list mac-entry {
          key "mac-address";
          leaf mac-address {
              type yang:phys-address;
          }
          leaf interface {
             type leafref {
                 path "/if:interfaces/if:interface/if:name";
             }
          }
          leaf controllerLearnedForwardingEntryTimestamp {
            type uint64;
          }
          leaf isStaticAddress {
            type boolean;
          }
          leaf prefix {//new field
            mandatory false;
            type inet:ip-address;
          }
        }
    }

Solution considerations
-----------------------

Proposed change in Openstack Neutron BGPVPN Driver
+++++++++++++++++++++++++++++++++++++++++++++++++++
The Openstack Neutron BGPVPN’s ODL driver in Newton release is changed (mitaka release), so that
it is able to relay the configured L2 BGPVPNs, to the OpenDaylight Controller.

The Newton changes for the BGPVPN Driver has merged and is here:
https://review.openstack.org/#/c/370547/

Proposed change in BGP Quagga Stack
++++++++++++++++++++++++++++++++++++
The BGP Quagga Stack is a component that interfaces with ODL Controller to enable ODL Controller itself
to become a BGP Peer.  This BGP Quagga Stack need to be enhanced so that it is able to embrace EVPN
with Route Type 5 on the following two interfaces:

* Thrift Interface where ODL pushes routes to BGP Quagga Stack
* Route exchanges from BGP Quagga Stack to other BGP Neighbors (including DC-GW).

Proposed change in OpenDaylight-specific features
+++++++++++++++++++++++++++++++++++++++++++++++++

The following components within OpenDaylight Controller needs to be enhanced:

* NeutronvpnManager
* VPN Engine (VPN Manager)
* ELAN Manager
* FIB Manager
* BGP Manager

Reboot Scenarios
^^^^^^^^^^^^^^^^
This feature support all the following Reboot Scenarios for EVPN:

*  Entire Cluster Reboot
*  Leader PL reboot
*  Candidate PL reboot
*  OVS Datapath reboots
*  Multiple PL reboots
*  Multiple Cluster reboots
*  Multiple reboots of the same OVS Datapath.
*  Openstack Controller reboots


Configuration impact
--------------------
The following parameters have been initially made available as configurable for EVPN. These
configurations can be made via the RESTful interface:

**1.Multi-homing-mode** – For multi-homing use cases where redundant DCGWs are used ODL can be configured with ‘none’, ‘all-active’ or ‘single-active’ multi-homing mode.  Default will be ‘none’.

**2.IRB-mode** – Depending upon the support on DCGW, ODL can be configured with either ‘Symmetric’ or ‘Asymmetric’ IRB mode.  Default is ‘Symmetric’.

There is another important parameter though it won’t be configurable:

**MAC Address Prefix for EVPN** – This MAC Address prefix represents the MAC Address prefix that will be hardcoded and that MACAddress will be used as the gateway mac address if it is not supplied from Openstack.  This will usually be the case when networks are associated to an L3VPN with no gateway port yet configured in Openstack for such networks.


Clustering considerations
-------------------------
The feature should operate in ODL Clustered environment reliably.

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
Carbon.

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
A new rpc is added to create and delete evpn:

.. code-block:: none

   {'input': {
       'evpn': [
           {'name': 'EVPN1',
            'export-RT': ['50:2'],
            'route-distinguisher': ['50:2'],
            'import-RT': ['50:2'],
            'id': '4ae8cd92-48ca-49b5-94e1-b2921a260007',
            ‘l2vni’: ‘200’,
            'tenant-id': 'a565b3ed854247f795c0840b0481c699'
   }]}}

There is no change in the REST API for associating networks to the EVPN.

On the Openstack-side configuration, the vni_ranges configured in Openstack Neutron ml2_conf.ini
should not overlap with the L3VNI provided in the ODL RESTful API.
In an inter-DC case, where both the DCs are managed by two different Openstack Controller
Instances, the workflow will be to do the following:

1. Configure the DC-GW2 facing OSC2 (Openstack) and DC-GW1 facing OSC1 with the same BGP configuration parameters.
2. On first Openstack Controller (OSC1) create an L3VPN1 with RD1 and L3VNI1
3. On first Openstack Controller (OSC1) create an EVPN1 with RD2 and L2VNI1
4. Create a network Net1 and Associate that Network Net1 to L3VPN1
5. Create a network Net1 and Associate that Network Net1 to EVPN1
6. On second Openstack Controller (OSC2) create an L3VPN2 with RD1 with L3VNI1
7. On second Openstack Controller (OSC2) create an EVPN2 with RD2 with L2VNI1
8. Create a network Net2 on OSC2 with same cidr as the first one with a different allocation pool and associate that Network Net2 to L3VPN2.
9. Associate that Network Net2 to EVPN2.
10. Spin-off VM1 on Net1 in OSC1.
11. Spin-off VM2 on Net2 in OSC2.
12. Now VM1 and VM2 should be able to communicate.


Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Vyshakh Krishnan C H <vyshakh.krishnan.c.h@ericsson.com>

  Yugandhar Reddy Kaku <yugandhar.reddy.kaku@ericsson.com>

  Riyazahmed D Talikoti <riyazahmed.d.talikoti@ericsson.com>

Other contributors:
  K.V Suneelu Verma <k.v.suneelu.verma@ericsson.com>

Work Items
----------
Trello card details https://trello.com/c/PysPZscm/150-evpn-evpn-rt2.

Dependencies
============
Requires a DC-GW that is supporting EVPN RT2 on BGP Control plane.

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
CSIT will be enhanced to cover this feature by providing new CSIT tests.

Documentation Impact
====================
This will require changes to User Guide and Developer Guide.

References
==========
[1] `EVPN_RT5 <https://tools.ietf.org/html/draft-ietf-bess-evpn-prefix-advertisement-03>`_

[2] `Network Virtualization using EVPN <https://www.ietf.org/id/draft-ietf-bess-evpn-overlay-07.txt>`_

[3] `Integrated Routing and Bridging in EVPN <https://tools.ietf.org/html/draft-ietf-bess-evpn-inter-subnet-forwarding-04>`_

[4] `VXLAN DCI using EVPN <https://tools.ietf.org/html/draft-boutros-bess-vxlan-evpn-02>`_

[5] `BGP MPLS-Based Ethernet VPN <https://tools.ietf.org/html/rfc7432>`_

[6] `Trello card details <https://trello.com/c/PysPZscm/150-evpn-evpn-rt2>`_
