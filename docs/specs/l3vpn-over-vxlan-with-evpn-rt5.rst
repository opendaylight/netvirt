=======================================================
Support of VXLAN based connectivity across Datacenters
=======================================================

https://git.opendaylight.org/gerrit/#/q/topic:EVPN_RT5

Enable realization of L3 connectivity over VXLAN tunnels using L3 BGPVPNs,
internally taking advantage of EVPN as the BGP Control Plane mechanism.

Problem description
===================

OpenDaylight NetVirt service today supports VLAN-based,
VXLAN-based connectivity and MPLSOverGRE-based overlays.

In this VXLAN-based underlay is supported only for traffic
within the DataCenter.   For all the traffic that need to
go via the DC-Gateway the only supported underlay is MPLSOverGRE.

Though there is a way to provision an external VXLAN tunnel
via the ITM service in Genius, the BGPVPN service in
NetVirt does not have the ability to take advantage of such
a tunnel to provide inter-DC connectivity.

This spec attempts to enhance the BGPVPN service (runs on
top of the current L3 Forwarding service) in NetVirt to
embrace inter-DC L3 connectivity over external VXLAN tunnels.

In scope
---------

The scope primarily includes providing ability to support Inter-subnet
connectivity across DataCenters over VXLAN tunnels by modeling a
new type of L3VPN which will realize this connectivity using
EVPN BGP Control plane semantics.

When we mention that we are using EVPN BGP Control plane, this
spec proposes using the RouteType 5 explained in [1] as the primary
means to provision the control plane en enable inter-DC connectivity
over external VXLAN tunnels.

| This new type of L3VPN will also inclusively support:
    * Intra-subnet connectivity within a DataCenter over VXLAN tunnels.
    * Inter-subnet connectivity within a DataCenter over VXLAN tunnels.

Out of scope
------------
    * Does not cover providing VXLAN connectivity between hypervisors (with OVS Datapath)
      and Top-Of-Rack switches that might be positioned within such DataCenters.

    * Does not cover providing intra-subnet connectivity across DCs.

Both the points above will be covered by another spec that will be Phase 2
of realizing intra-subnet inter-DC connectivity.

Use Cases
---------

The following high level use-cases will be realized by the implementation of this Spec.

1. **DataCenter access from a WAN-client via DC-Gateway (Single Homing)**

This use case involves communication within the DataCenter by tenant VMs and also
communication between the tenant VMs to a remote WAN-based client via DC-Gateway.
The dataplane between the tenant VMs themselves and between the tenant VMs
towards the DC-Gateway will be over VXLAN Tunnels.

The dataplane between the DC-Gateway to its other WAN-based BGP Peers is
transparent to this spec.  It is usually MPLS-based IPVPN.

The BGP Control plane between the ODL Controller and the DC-Gateway will be
via EVPN RouteType 5 as defined in [1].

The control plane between the DC-Gateway and it other BGP Peers in the WAN
is transparent to this spec, but can be IP-MPLS.

| In this use-case:
| * We will have only a single DCGW for WAN connectivity

| * IP prefix exchange between ODL controller and DC-GW (iBGP) using EVPN RT5

| * WAN control plane will use L3VPN IP-MPLS route exchange.

| * On the DC-Gateway, the VRF instance will be configured with two sets of import/export
| targets. One set of import/export route targets belong to L3VPN inside DataCenter
| (realized using EVPN RT5) and the second set of import/export route target belongs to
| WAN control plane.

| * EVPN single homing to be used in all RT5 exchanges inside the DataCenter
| i.e., ESI=0 for all prefixes sent from DataCenter to the DC-Gateway.

| * Inter AS option B is used at DCGW, route regeneration at DCGW

2. **Datacenter access from another Datacenter over WAN via respective DC-Gateways (L3 DCI)**

This use-case involves providing inter-subnet connectivity between two DataCenters.
Tenant VMs in one datacenter will be able to communicate with tenant VMs on the other
datacenter provided they are part of the same L3VPN and they are on different subnets.

Both the Datacenters can be managed by different ODL Controllers, but the L3VPN configured on
both ODL Controllers will use identical RDs and RTs.

High-Level Components:
======================
| The following components of an Openstack-ODL-based solution need to be enhanced to provide
| intra-subnet and inter-subnet connectivity across DCs using EVPN IP Prefix Advertisement
| (Route Type 5) mechanism (refer [1])

    * Openstack Neutron BGPVPN Driver
    * OpenDaylight Controller (NetVirt VpnService)
    * BGP Quagga Stack to support EVPN with RouteType 5 NLRI
    * DC-Gateway BGP Neighbour that supports EVPN with RouteType 5 NLRI

We will review enhancements that will be made to each of the above components in following
sections.

Openstack Neutron BGPVPN Driver
===============================
The Openstack Neutron BGPVPN’s ODL driver in Newton release needs to be changed, so that
it is able to relay the configured L2 BGPVPNs, to the OpenDaylight Controller.
As of Mitaka release, only L3 BGPVPNs configured in Openstack are being relayed to the
OpenDaylight Controller. So in addition to addressing the ODL BGPVPN Driver changes in
Newton, we will provide a Mitaka based patch that will integrate into Openstack.

| The Newton changes for the BGPVPN Driver has merged and is here:
| https://review.openstack.org/#/c/370547/

Proposed change
===============

| The following components within OpenDaylight Controller needs to be enhanced:
    * NeutronvpnManager
    * VPN Engine (VPN Manager and VPN Interface Manager)
    * FIB Manager
    * BGP Manager
    * VPN SubnetRoute Handler
    * NAT Service

Pipeline changes
----------------
| There are no explicit pipeline changes, however rules in the existing ODL pipeline will
| be configured for VPN interfaces differently.

| The pipeline for only L3VPN and ELAN is shown here..
| (other service pipelines have no change and so have not been shown).

**1.1.1 INTRA DC:**

**Intra Subnet, Local DPN: VMs on the same subnet, same VPN, same DPN**

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER TABLE => L3_GW_MAC_TABLE =>
LPORT_DISPATCHER TABLE => ELAN_SMAC_TABLE => ELAN_DMAC_TABLE =>
Output to destination VM port


**Intra Subnet, Remote DPN: VMs on two different DPNs, both VMs on the same subnet and same VPN**

| *a. VM sourcing the traffic (Ingress DPN)*

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER_TABLE => L3_GW_MAC_TABLE =>
LPORT_DISPATCHER TABLE => ELAN_SMAC TABLE => ELAN_DMAC TABLE (Set Tunnel ID as LPORT TAG) =>
Output to Tunnel port

| *b. VM receiving the traffic (Egress DPN)*

VLAN_INTERFACE_INGRESS_TABLE => INTERNAL_TUNNEL_TABLE (match LPORT TAG) =>
Output to destination VM port


**Inter Subnet, Local DPN: VMs on different subnet, same VPN, same DPN**

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER_TABLE => L3_GW_MAC_TABLE (match routerMAC)
=> L3_FIB_TABLE => Output to NextHop Group for destination VM.

*Flow rules for case of Inter Subnet, Local DPN will be:*

  .. code-block:: bash

cookie=0x8000000, table=0, priority=4,in_port=1 actions=write_metadata:0x10000000000/0xffffff0000000001,goto_table:17
cookie=0x8000001, table=17, priority=5,metadata=0x5000010000000000/0xffffff0000000000 actions=write_metadata:0x60000100000222e0/0xfffffffffffffffe,goto_table:19
cookie=0x8000009, table=19, priority=20,metadata=0x222e0/0xfffffffe,dl_dst=de:ad:be:ef:00:01 actions=goto_table:21
cookie=0x8000003, table=21, priority=42,ip,metadata=0x222e0/0xfffffffe,nw_dst=10.0.0.2 actions=write_actions(group:150001)


**Inter Subnet, Remote DPN: VMs on two different DPNs, both VMs on different subnet, but same VPN**

| *a. VM sourcing the traffic (Ingress DPN)*

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER_TABLE => L3_GW_MAC_TABLE (match routerMAC) =>
L3_FIB TABLE => (SET DESTINATION MAC ADDRESS, SET Tunnel ID (LPORT TAG), Output to Internal Tunnel port

*Flow rules for case of Inter Subnet, Remote DPN, VM sourcing packets to VXLAN tunnel, will be:*

  .. code-block:: bash

cookie=0x8000000, table=0, priority=4,in_port=1 actions=write_metadata:0x10000000000/0xffffff0000000001,goto_table:17
cookie=0x8000001, table=17, priority=5,metadata=0x5000010000000000/0xffffff0000000000 actions=write_metadata:0x60000100000222e0/0xfffffffffffffffe,goto_table:19
cookie=0x8000009, table=19, priority=20,metadata=0x222e0/0xfffffffe,dl_dst=de:ad:be:ef:00:01 actions=goto_table:21
cookie=0x8000003, table=21, priority=42,ip,metadata=0x222e0/0xfffffffe,nw_dst=10.0.0.2 actions=write_actions(group:150001)
cookie=0x8000003, table=21, priority=42,ip,metadata=0x222e0/0xfffffffe,nw_dst=10.0.0.3 actions=write_actions(set_field:fa:16:3e:f8:59:af->eth_dst,set_field:0x2->tun_id,output:2)

| *b. VM receiving the traffic (Egress DPN)*

VLAN_INTERFACE_INGRESS_TABLE => INTERNAL_TUNNEL_TABLE (match LPORT TAG) => Output to destination VM port

*Flow rules for case of Inter Subnet, Remote DPN, VM receiving packets from VXLAN tunnel, will be:*

  .. code-block:: bash


cookie=0x8000001, table=0, priority=5,in_port=2 actions=write_metadata:0x40000000001/0xfffff0000000001,goto_table:36
cookie=0x9000001, table=36, priority=5,tun_id=0x2 actions=load:0x400->NXM_NX_REG6[],resubmit(,220)

**1.1.2 INTER DC:**

**Intra Subnet**

Not supported in this Phase

**Inter Subnet**

*Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB):*
VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER TABLE => L3VNI_EXT_TUNNEL_DEMUX_TABLE =>
L3_GW_MAC_TABLE (matching routerMAC) => L3_FIB_TABLE => Output to NextHop Group for Destination VM

*Flow rules for case of Inter Subnet, DC-Gateway to Local DPN, VM receiving packets from VXLAN tunnel, will be:*

  .. code-block:: bash

cookie=0x8000001, table=0, priority=5,in_port=9 actions=write_metadata:0x70000000001/0x1fffff0000000001,goto_table:23
cookie=0x9000001, table=23, priority=5,tun_id=0x16 actions= write_metadata:0x222e0/0xfffffffe,goto_table:21
cookie=0x8000003, table=21, priority=42,ip,metadata=0x222e0/0xfffffffe,nw_dst=10.0.0.2 actions=write_actions(group:150001)
cookie=0x8000003, table=21, priority=42,ip,metadata=0x222e0/0xfffffffe,nw_dst=10.0.0.3 actions=write_actions(set_field:fa:16:3e:f8:59:af->eth_dst,set_field:0x2->tun_id,output:2)

In the above flow rules Table 23 is the new L3VNI_EXT_TUNNEL_DEMUX_TABLE.  The in_port=9 reprsents an
external VXLAN Tunnel port. As Genius is enhanced to allow multiple services to bind on an external
tunnel port, the flows above will move from Table 0 to Table 17 and then to Table 23.

*Traffic from Local DPN to DC-Gateway (SYMMETRIC IRB):*

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER TABLE => L3_GW_MAC_TABLE (matching routerMAC) =>
L3_FIB TABLE => (set the DST MAC Address, SET TUNNEL ID as L3VNI, Output to EXTERNAL VXLAN Tunnel Port)

**SNAT pipeline (Access to External Network Access over VXLAN)**

*SNAT Traffic from Local DPN to External IP (assuming this DPN is NAPT Switch):*

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER TABLE => L3_GW_MAC_TABLE => L3_FIB_TABLE =>
PSNAT_TABLE => SNAT_TABLE => L3_FIB_TABLE => (set the DST MAC Address, SET TUNNEL ID as L3VNI,
Output to EXTERNAL VXLAN Tunnel Port)

*SNAT Reverse Traffic from External IP to Local DPN (assuming this DPN is NAPT Switch):*

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER TABLE => L3VNI_EXT_TUNNEL_DEMUX_TABLE =>
L3_GW_MAC_TABLE => NAPT_PFIB_TABLE => L3_FIB_TABLE => GROUP POINTING TO VM

**DNAT pipeline (Access from External Network over VXLAN)**

*DNAT Traffic from External IP to Local DPN:*

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER_TABLE => L3VNI_EXT_TUNNEL_DEMUX_TABLE =>
L3_GW_MAC_TABLE => PDNAT_TABLE => DNAT_TABLE => L3_FIB TABLE => GROUP POINTING TO VM

*DNAT Reverse Traffic from Local DPN to External IP:*

VLAN_INTERFACE_INGRESS_TABLE => LPORT_DISPATCHER_TABLE => L3_GW_MAC_TABLE => L3_FIB_TABLE =>
PSNAT_TABLE => SNAT_TABLE => L3_FIB_TABLE => (set the DST MAC Address, SET TUNNEL ID as L3VNI,
Output to EXTERNAL VXLAN Tunnel Port)

More details of the NAT pipeline changes are in the NAT Service section of this spec.

As you would have noticed, the L3VNI_EXT_TUNNEL_DEMUX_TABLE is a new table (might become Table 18)
that will match only the L3VNIs and based on them fix the VPNID in the metadata and forward the
packet to L3_GW_MAC_TABLE.  The new table is responsible for handling L3VPN specific traffic
egressing from the external VXLAN Tunnel port , and send that packet with right vpn metadata
eventually to the L3_FIB_TABLE (and so on to the resident VM).

The TableMiss in L3VNI_EXT_TUNNEL_DEMUX_TABLE will resubmit the packet to LPORT_DISPATCHER_TABLE.

Yang changes
------------
Changes will be needed in ``l3vpn.yang`` , ``odl-l3vpn.yang`` , ``odl-fib.yang`` and
``neutronvpn.yang`` to start supporting EVPN functionality.

L3VPN YANG changes
^^^^^^^^^^^^^^^^^^
A new leaf l3vni and a new leaf type will be added to container ``vpn-instances``

.. code-block:: none
   :caption: l3vpn.yang

    leaf type {
              description
              "The type of the VPN Instance.
              ipvpn indicates it is an L3VPN.
              evpn indicates it is EVPN”;

              type enumeration {
                    enum ipvpn {
                    value "0";
                    description “L3VPN";
                    }
                    enum evpn {
                    value "1";
                    description "EVPN";
                    }
              }
              default "ipvpn";
    }

    leaf l3vni {
               description
               "The L3 VNI to use for this L3VPN Instance.
               If this attribute is non-zero, it indicates
               this L3VPN will do L3Forwarding over VXLAN.
               If this value is non-zero, and the type field is ‘l2’,
               it is an error.
               If this value is zero, and the type field is ‘l3’, it is
               the legacy L3VPN that will do L3Forwarding
               with MPLSoverGRE.
               If this value is zero, and the type field is ‘l2’, it
               is an EVPN that will provide L2 Connectivity with
               Openstack supplied VNI”.

               type uint24;
               mandatory false;
    }

    The **type** value comes from Openstack BGPVPN ODL Driver based on what type of BGPVPN is
    orchestrated by the tenant. That same **type** value must be retrieved and stored into
    VPNInstance model above maintained by NeutronvpnManager.

ODL-L3VPN YANG changes
^^^^^^^^^^^^^^^^^^^^^^
A new leaf l3vni and a new leaf type will be added to container ``vpn-instance-op-data``

.. code-block:: none
   :caption: odl-l3vpn.yang

   leaf type {
             description
             "The type of the VPN Instance.
             ipvpn indicates it is an L3VPN.
             evpn indicates it is EVPN”;

             type enumeration {
                   enum ipvpn {
                   value "0";
                   description “L3VPN";
                   }
                   enum evpn {
                   value "1";
                   description "EVPN";
                   }
             }
             default "ipvpn";
   }

   leaf l3vni {
              description
              "The L3 VNI to use for this L3VPN Instance.
              If this attribute is non-zero, it indicates
              this L3VPN will do L3Forwarding over VXLAN.
              If this value is non-zero, and the type field is ‘l2’,
              it is an error.
              If this value is zero, and the type field is ‘l3’, it is
              the legacy L3VPN that will do L3Forwarding
              with MPLSoverGRE.
              If this value is zero, and the type field is ‘l2’, it
              is an EVPN that will provide L2 Connectivity with
              Openstack supplied VNI”.

              type uint24;
              mandatory false;
   }

   For every interface in the cloud that is part of an L3VPN which has an L3VNI setup, we should
   extract that L3VNI from the config VPNInstance and use that to both program the flows as well
   as advertise to BGP Neighbour using RouteType 5 BGP Route exchange.
   Fundamentally, what we are accomplishing is L3 Connectivity over VXLAN tunnels by using the
   EVPN RT5 mechanism.

ODL-FIB YANG changes
^^^^^^^^^^^^^^^^^^^^
Few new leafs like mac_address , gateway_mac_address , l2vni, l3vni and a leaf encap-type will
be added to container ``fibEntries``

.. code-block:: none
   :caption: odl-fib.yang

   leaf encap-type {
      description
      "This flag indicates how to interpret the existing label field.
      A value of mpls indicates that the label will continue to
      be considered as an MPLS Label.
      A value of vxlan indicates that vni should be used to
      advertise to bgp.
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

   leaf mac_address {
       type string;
       mandatory false;
   }

   leaf l3vni {
       type uint24;
       mandatory false;
   }

   leaf l2vni {
       type uint24;
       mandatory false;
   }

   leaf gateway_mac_address {
       type string;
       mandatory false;
   }
   Augment:parent_rd {
       type string;
       mandatory false;
   }

The encaptype indicates whether an MPLSOverGre or VXLAN encapsulation should be used
for this route. If the encapType is MPLSOverGre then the usual label field will carry
the MPLS Label to be used in datapath for traffic to/from this VRFEntry IP prefix.

If the encaptype is VXLAN, the VRFEntry implicitly refers that this route is reachable
via a VXLAN tunnel. The L3VNI will carry the VRF VNI and there will also be an L2VNI which
represents the VNI of the network to which the VRFEntry belongs to.

Based on whether Symmetric IRB (or) Asymmetric IRB is configured to be used by the CSC
(see section13 below). If Symmetric IRB​ is configured, then the L3VNI should be used​ to
program the flows rules. If Asymmetric IRB​ is configured, then L2VNI should be used​ in
the flow rules.

The mac_address​ field must be filled​ for every route​ in an EVPN. This mac_address field
will be used for support intra-DC communication for both inter-subnet and intra-subnet routing.

The gateway_mac_address must always be filled f​or every route in an EVPN.[AKMA7] [NV8]
This gateway_mac_address will be used for all packet exchanges between DC-GW and the
DPN in the DC to support L3 based forwarding with Symmetric IRB.

NEUTRONVPN YANG changes
^^^^^^^^^^^^^^^^^^^^^^^
One new leaf l3vni will be added to container grouping ``vpn-instance``

.. code-block:: none
   :caption: odl-fib.yang

   leaf l3vni {
       type uint32;
       mandatory false;
   }


Import / Export RT for EVPN
---------------------------

Currently Import/Export logic for L3VPN uses a LabelRouteInfo structure to build information
about imported prefixes using MPLS Label as the key. However, this structure cannot be used
for EVPN as the L3VNI will be applicable for an entire EVPN Instance instead of the MPLS Label.
In lieu of LabelRouteInfo, we will maintain an IPPrefixInfo keyed structure that can be used
for facilitating Import/Export of VRFEntries across both EVPNs and L3VPNs.

.. code-block:: none
   :caption: odl-fib.yang

   list ipprefix-info {

       key "prefix, parent-rd"
       leaf prefix {
           type string;
       }

       leaf parent-rd {
           type string;
       }

       leaf label {
           type uint32;
       }

       leaf dpn-id {
           type uint64;
       }

       leaf-list next-hop-ip-list {
           type string;
       }

       leaf-list vpn-instance-list {
           type string;
       }

       leaf parent-vpnid {
           type uint32;
       }

       leaf vpn-interface-name {
           type string;
       }

       leaf elan-tag {
           type uint32;
       }

       leaf is-subnet-route {
           type boolean;
       }

       leaf encap-type {
           description
           "This flag indicates how to interpret the existing label field.
           A value of mpls indicates that the l3label should be considered as an MPLS
           Label.
           A value of vxlan indicates that l3label should be considered as an VNI.
           type enumeration {
               enum mplsgre {
                   value "0";
                   description "MPLSOverGRE";
               }
               enum vxlan {
                   value "1";
                   description “VNI";
               }
               default "mplsgre";
           }
       }

       leaf l3vni {
           type uint24;
           mandatory false;
       }

       leaf l2vni {
           type uint24;
           mandatory false;
       }

       leaf gateway_mac_address {
           type string;
           mandatory false;
       }
   }

Configuration impact
--------------------
The following parameters have been initially made available as configurable for EVPN. These
configurations can be made via the RESTful interface:

    **1.Multi-homing-mode** – For multi-homing use cases where redundant DCGWs are used ODL can
                              be configured with ‘none’, ‘all-active’ or ‘single-active’ multi-homing
                              mode.
                              Default will be ‘none’.
    **2.IRB-mode** – Depending upon the support on DCGW, ODL can be configured with either ‘Symmetric’
                     or ‘Asymmetric’ IRB mode.
                     Default is ‘Symmetric’.

There is another important parameter though it won’t be configurable:

    **MAC Address Prefix for EVPN** – This MAC Address prefix represents the MAC Address prefix
     that will be hardcoded and that MACAddress will be used as the gateway mac address if it
     is not supplied from Openstack.  This will usually be the case when networks are associated
     to an L3VPN with no gateway port yet configured in Openstack for such networks.

SubnetRoute support on EVPN
---------------------------
The subnetRoute feature will continue to be supported on EVPN and we will use RT5 to publish
subnetRoute entries with either the router-interface-mac-address if available (or) if not
available use the pre-defined hardcoded MAC Address described in section 13.
For both ExtraRoutes and “MIPs (invisible IPs) discovered via subnetroute”, we will continue
to use RT5 to publish those prefixes.[AKMA9] [NV10]
On the dataplane, VXLAN packets from the DC-GW will carry the MAC Address of the gateway-ip
for the subnet in the inner DMAC.

NAT Service support for EVPN
----------------------------
However, since external network NAT should continue to be supported on VXLAN, making NAT
service work on L3VPNs that use VXLAN as the tunnel type becomes imperative.

Existing SNAT/DNAT design assumed internetVpn to be using mplsogre as the connectivity
from external network towards DCGW. This needs to be changed such that it can handle even
EVPN case with VXLAN connectivity as well.

As of the implementation required for this specification, the workflow will be to create
InternetVPN with and associate a single external network to that is of VXLAN Provider Type.
The Internet VPN itself will be an L3VPN that will be created via the ODL RESTful API and
during creation an L3VNI parameter will be supplied to enable this L3VPN to operate on a
VXLAN dataplane. The L3VNI provided to the Internet VPN can be different from the VXLAN
segmentation ID associated to the external network.

However, it will be a more viable use-case in the community if we mandate in our workflow
that both the L3VNI configured for Internet VPN and the VXLAN segmentation id of the
associated external network to the Internet VPN be the same.
NAT service can use vpninstance-op-data model to classify the DCGW connectivity for internetVpn.

For the Pipeline changes for NAT Service, please refer to 'Pipeline changes' section.

SNAT to start using Router Gateway MAC, in translated entry in table 46 (Outbound SNAT table)
and in table 19 (L3_GW_MAC_Table). Presently Router gateway mac is already stored in odl-nat model
in External Routers.

DNAT to start using Floating MAC, in table 28 (SNAT table) and in table 19 (L3_GW_MAC Table).
Change in pipeline mainly reverse traffic for SNAT and DNAT so that when packet arrives from DCGW,
it goes to 0->38->17->19 and based on Vni and MAC matching, take it back to SNAT or DNAT pipelines.

Also final Fib Entry pointing to DCGW in forward direction also needs modification where we should
start using VXLAN’s vni, FloatingIPMAC (incase of DNAT) and ExternalGwMacAddress(incase of SNAT)
and finally encapsulation type as VXLAN.

For SNAT advertise to BGP happens during external network association to Vpn and during High
availability scenarios where you need to re-advertise the NAPT switch. For DNAT we need to
advertise when floating IP is associated to the VM.
For both SNAT and DNAT this IS mandates that we do only RT5 based advertisement. That RT5
advertisement must carry the external gateway mac address assigned for the respective Router
for SNAT case while for DNAT case the RT5 will carry the floating-ip-mac address.

ARP request/response and MIP handling Support for EVPN
------------------------------------------------------
Will not support ARP across DCs, as we donot support intra-subnet inter-DC scenarios.

| * For intra-subnet intra-DC scenarios, the ARPs will be serviced by existing ELAN pipeline.

| * For inter-subnet intra-DC scenarios, the ARPs will be processed by ARP Responder
| implementation that is already pursued in Carbon.

| * For inter-subnet inter-DC scenarios, ARP requests won’t be generated by DC-GW.  Instead the
| DC-GW will use ‘gateway mac’ extended attribute MAC Address information and put that directly
| into DSTMAC field of Inner MAC Header by the DC-GW for all packets sent to VMs within the DC.

| * As quoted, intra-subnet inter-DC scenario is not a supported use-case as per this Implementation Spec.

Tunnel state handling Support
-----------------------------
We have to handle both the internal and external tunnel events for L3VPN (with L3VNI) the same way
it is handled for current L3VPN.

InterVPNLink support for EVPN
-----------------------------
Not supported as this is not a requirement for this Spec.

Supporting VLAN Aware VMs (Trunk and SubPorts)
----------------------------------------------
Not supported as this is not a requirement for this Spec.

VM Mobility with RT5
--------------------
We will continue to support cold migration of VMs across hypervisors across L3VPNs as supported
already in current ODL Carbon Release.

BGP Quagga Stack - EVPN with RouteType 5 NLRI
=============================================
The BGP Quagga Stack that works with the ODL Controller, enabling the ODL Controller itself
to become a BGP Router need to be enhanced so that it is able to embrace EVPN with Route Type
5 on the following two interfaces:

* Thrift Interface where ODL pushes routes to BGP Quagga Stack
* Route exchanges from BGP Quagga Stack to other BGP Neighbors (including DC-GW).

Tenant Interface Changes
========================

Enhancement to existing ODL RESTful API
---------------------------------------
Only the creational RESTful API for the L3VPN will be enhanced to accept the L3VNI as an
additional attribute as in the below request format:

.. code-block:: none

   {'input': {
       'l3vpn': [
           {'name': 'L3VPN2',
            'export-RT': ['50:2'],
            'route-distinguisher': ['50:2'],
            'import-RT': ['50:2'],
            'id': '4ae8cd92-48ca-49b5-94e1-b2921a260007',
            ‘l3vni’: ‘200’,
            'tenant-id': 'a565b3ed854247f795c0840b0481c699'
   }]}}

There is no change in the REST API for associating networks, associating routers (or) deleting
the L3VPN.

Openstack-side configuration
----------------------------
The vni_ranges configured in Openstack Neutron ml2_conf.ini should not overlap with the L3VNI
provided in the ODL RESTful API.
In an inter-DC case, where both the DCs are managed by two different Openstack Controller
Instances, the workflow will be to do the following:

1. Configure the DC-GW2 facing OSC2 and DC-GW1 facing OSC1 with the same BGP configuration parameters.
2. On first Openstack Controller (OSC1) create an L3VPN1 with RD1 and L3VNI1
3. Create a network Net1 and Associate that Network Net1 to L3VPN1
4. On second Openstack Controller (OSC2) create an L3VPN2 with RD1 with L3VNI2
5. Create a network Net2 on OSC2 and associate that Network Net2 to L3VPN2.
6. Spin-off VM1 on Net1 in OSC1.
7. Spin-off VM2 on Net2 in OSC2.
8. Now VM1 and VM2 should be able to communicate.

Reboot Scenarios
----------------
This feature support all the following Reboot Scenarios for EVPN:
    *  Entire Cluster Reboot
    *  Leader PL reboot
    *  Candidate PL reboot
    *  OVS Datapath reboots
    *  Multiple PL reboots
    *  Multiple Cluster reboots
    *  Multiple reboots of the same OVS Datapath.
    *  Openstack Controller reboots

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
This feature doesn't add any new karaf feature.

REST API
--------

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Kiran N Upadhyaya (kiran.n.upadhyaya@ericsson.com)

  Sumanth MS (sumanth.ms@ericsson.com)
 
  Basavaraju Chickmath (basavaraju.chickmath@ericsson.com)

Other contributors:
  Vivekanandan Narasimhan (n.vivekanandan@ericsson.com)

Work Items
----------
The Trello cards have already been raised for this feature
under the Heading L2VPN.

Here is the link for the Trello Card:
https://trello.com/c/Tfpr3ezF/33-l2-bgp-vpn

New Trello cards will be added to cover Java UT and
CSIT.


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

User Guide will need to add information on how to add TEPs with flow based
tunnels.

Developer Guide will need to capture how to use changes in IFM to create
individual tunnel interfaces.

References
==========
[1] https://tools.ietf.org/html/draft-ietf-bess-evpn-prefix-advertisement-03

[2] https://www.ietf.org/id/draft-ietf-bess-evpn-overlay-06.txt

[3] https://tools.ietf.org/html/draft-ietf-bess-evpn-inter-subnet-forwarding-01

[4] https://tools.ietf.org/html/draft-boutros-bess-vxlan-evpn-02

[5] Ethernet VPN IETF RFC - https://tools.ietf.org/html/rfc7432

* http://docs.opendaylight.org/en/latest/documentation.html
* https://wiki.opendaylight.org/view/Genius:Carbon_Release_Plan
