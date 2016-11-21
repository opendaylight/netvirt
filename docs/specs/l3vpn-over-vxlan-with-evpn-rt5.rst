=======================================================
Support of VXLAN based connectivity across Datacenters
=======================================================

https://git.opendaylight.org/gerrit/#/q/topic:EVPN_RT5

**Important: All gerrit links that is raised for this feature must have topic name as "EVPN_RT5"**

This feature is to study the realization aspects of L3 connectivity using L3 BGPVPNs (by using EVPN as the BGP Control Plane mechanism)
which are orchestrated through OpenStack/Neutron and ODL RESTful APIs. The scope primarily includes providing ability to support:

a.    Intra-subnet connectivity within DC over VXLAN tunnels.
b.    Inter-subnet connectivity within DC over VXLAN tunnels.
c.    Inter-subnet connectivity across DCs over VXLAN tunnels.

This Design doc does not cover intra-subnet connectivity across DCs.

For more details on please refer [1] [2] and [3].
Phase 2 will have a separate Design Document and that will cover intra-subnet inter-DC as the primary use-case and it will use RT2 as
the advertisement type to accomplish the same.


Problem description
===================

**A. In Scope:**

    1.	Enable creation, deletion, updation (i.e., management) of BGP L3VPN (EVPN) with an L3VNI in the ODL NetVirt BGPVPN Implementation.
    2.  Provide intra-subnet connectivity between workloads (Virtual Machines) within a DataCenter (DC) for L3VPN (with L3VNI) by using
        the same way as it is managed today (using LPortTags instead of Openstack supplied VNIs).
    3.	Provide inter-subnet connectivity between workloads (Virtual Machines) within a DataCenter (DC) using L3VPN forwarding (but will
        use LPortTags instead of MPLS Labels since the transport plane is VXLAN).
    4.	Provide inter-subnet connectivity between workloads (Virtual Machines) across DataCenters by using L3VPN that internally uses EVPN
        RT5 in the BGP Control Plane.
    5.  Test with DC-GW to validate inter-DC L3 connectivity.
    6.  Support Import / Export RT BGP facility for L3VPN instances (with L3VNI available).
    7.  Support External Network Connectivity from within DC using SNAT / DNAT for ‘Internet VPN’ that is L3VPN-based.
    8.  Support Subnet Route driven connectivity from VPN Sites to Enterprise networks (via DC) over EVPN.


**B. Not in Scope**

    a.  Intra-subnet connectivity across DCs as this requires RT2 implementation.
    b.  Underlay configuration and any special control mechanisms for TORs
    c.  QoS, traffic policing for L3VPN instances
    d.  Service chaining and other Openflow based features for L3VPN Instances

Use Cases
---------

The following use-cases will be realized by this Implementation Sketch. Wherever CSC is mentioned it refers to an ODL Controller Cluster.

**A. DC Access**

This use case involves EVPN/VXLAN to IP-VPN interworking and this requires only L3 gateway functions are needed. L3 prefixes are
exchanged with EVPN RT5 and data-plane will be VxLAN. These use cases are very similar to existing L3VPN service functions in CSC (ODL).
However, in place of BGP VPNv4, it will use EVPN RT-5 for IP-prefix advertisement and the overlay transport will be VxLAN based.

    **A.1 Use Case 1.1: DC-access L3Gateway Single Homing**
    This use case involves EVPN/VXLAN to IP-VPN interworking for L3 connectivity between DC Fabric and WAN with single DCGW acting as ASBR[AKMA1] [NV2] .

      · This use case will have single DCGW for WAN connectivity
      · IP prefix exchange between CSC and DCGW (iBGP) using EVPN RT5.
      · WAN control plane will use L3VPN route exchange
      · On DCGW, the VRF instance configured with two sets of import/export targets, one set of import/export route targets belong to EVPN in DC and the second set of import/export route target belongs to WAN control plane.
      · EVPN single homing, ESI=0 for all prefixes generated from DC
      · Inter AS option B is used at DCGW, route regeneration at DCGW

**B. L3 DCI**

    **B.1 Use Case 1.2: L3DCI with DC EVPN VXLAN and IP-MPLS L3VPN in WAN using single DCGW** [AKMA3] [NV4]

    In scenarios where DCGW only support EVPN RT5 (as is the current capability of Cisco ASR9000), only L3 gateway functions are possible.
    This use case is very much same as UC1.1, however in place of Client DC access, it targets L3 DCI.

    **B.2 Use Case 1.3: L3 DCI with EVPN IP-MPLS in WAN using single DCGW**
    This use case is very much same as UC1.2, however in place of L3VPN in WAN there will be EVPN control plane in WAN.
    EVPN RT-5 will be used in WAN for route exchange.


Proposed change
===============

The following components within OpenDaylight Controller needs to be enhanced:
a.	NeutronvpnManager
b.	VPN Engine (VPN Manager and VPN Interface Manager)
c. 	FIB Manager
d.	BGP Manager
e.	VPN SubnetRoute Handler
f.  NAT Service

Pipeline changes
----------------
There are no explicit pipeline changes, however rules in the existing ODL pipeline will be configured for EVPN interfaces differently.

**1.1.1 INTRA DC:**
**Intra Subnet, Local DPN:**  VMs on the same subnet, same VPN, same DPN
TABLE 0 => DISPATCHER TABLE => MY-MAC-TABLE => DISPATCHER TABLE => SMAC TABLE => DMAC TABLE => Output to destination VM port


**Intra Subnet, Remote DPN:**  VMs on two different DPNs, both VMs on the same subnet and same VPN.
a.    VM sourcing the traffic (Ingress DPN)
TABLE 0 => DISPATCHER TABLE => MY MAC TABLE => DISPATCHER TABLE => SMAC TABLE => DMAC TABLE => Set Tunnel ID (LPORT TAG) => Output to Tunnel port
b.    VM receiving the traffic (Egress DPN)
TABLE 0 => TERMINATING SERVICE TABLE (match LPORT TAG) => Output to destination VM port


**Inter Subnet, Local DPN:** VMs on different subnet, same VPN, same DPN
TABLE 0 => DISPATCHER TABLE => MY MAC TABLE (match routerMAC) => FIB TABLE => Output to NextHop Group for destination VM.[AKMA5] [NV6]


**Inter Subnet, Remote DPN:**  VMs on two different DPNs, both VMs on different subnet, but same VPN.
a.    VM sourcing the traffic (Ingress DPN)
TABLE 0 => DISPATCHER TABLE => MY MAC TABLE (match routerMAC) => FIB TABLE => (SET DESTINATION MAC ADDRESS à SET Tunnel ID (LPORT TAG) à Output to Internal Tunnel port
b.    VM receiving the traffic (Egress DPN)
TABLE 0 => TERMINATING SERVICE TABLE (match LPORT TAG) => Output to destination VM port

**1.1.2 INTER DC:**

**Intra Subnet**
Not supported in this Phase

**Inter Subnet**
Traffic from DC-Gateway to Local DPN (SYMMETRIC IRB):
TABLE 0 => DISPATCHER TABLE => EXTERNAL_TUNNEL_TABLE => MY MAC TABLE (matching routerMAC) => FIB TABLE => Output to NextHop Group for Destination VM

Traffic from Local DPN to DC-Gateway (SYMMETRIC IRB):
TABLE 0-> DISPATCHER TABLE => MY MAC TABLE (matching routerMAC) => FIB TABLE => SET TUNNEL ID (VNI) à Output to EXTERNAL VXLAN Tunnel Port (add the inner Dst MAC Address)

Yang changes
------------
Changes will be needed in ``l3vpn.yang`` , ``odl-l3vpn.yang`` and ``odl-fib.yang`` to start supporting EVPN functionality.

L3VPN YANG changes
^^^^^^^^^^^^^^^^^^
A new leaf l3vni and a new leaf type will be added to container ``vpn-instances``

.. code-block:: none
   :caption: l3vpn.yang

    leaf type {
              description
              "The type of the VPN Instance.
              L3 indicates it is an L3VPN.
              L2 indicates it is EVPN”;

              type enumeration {
                    enum l3 {
                    value "0";
                    description “L3VPN";
                    }
                    enum l2 {
                    value "1";
                    description "EVPN";
                    }
              }
              default "l3";
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
             L3 indicates it is an L3VPN.
             L2 indicates it is EVPN”;

             type enumeration {
                   enum l3 {
                   value "0";
                   description “L3VPN";
                   }
                   enum l2 {
                   value "1";
                   description "EVPN";
                   }
             }
             default "l3";
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
   EVPN RT5 technology .

ODL-FIB YANG changes
^^^^^^^^^^^^^^^^^^^^
Few new leafs like mac_address , gateway_mac_address , l2vni, l3vni and a leaf encap-type will
be added to container ``fibEntries``

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
for this route. If the encapType is MPLSOverGre then the usual ‘label’​ field will carry
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

The gateway_mac_address must always be filled f​ or every route in an EVPN.[AKMA7] [NV8]
This gateway_mac_address will be used for all packet exchanges between DC-GW and the
DPN in the DC to support L3 based forwarding with Symmetric IRB.

Configuration impact
---------------------
The following parameters have been initially made available as configurable for EVPN. These configurations can be made via the RESTful interface:

    **1.Multi-homing-mode** – For multi-homing use cases where redundant DCGWs are used ODL can be configured with
                              ‘none’, ‘all-active’ or ‘single-active’ multi-homing mode.
                               Default will be ‘none’.
    **2.IRB-mode** – Depending upon the support on DCGW, ODL can be configured with either ‘Symmetric’ or ‘Asymmetric’ IRB mode.
                     Default is ‘Symmetric’.

There is another important parameter though it won’t be configurable:

    **MAC Address Prefix for EVPN** – This MAC Address prefix represents the MAC Address prefix that will be hardcoded
     and that MACAddress will be used as the gateway mac address if it is not supplied from Openstack.  This will
     usually be the case when networks are associated to an L3VPN with no gateway port yet configured in Openstack for such networks.

Reboot Scenarios
----------------
This feature support all the following Reboot Scenarios for EVPN:
    a.	Entire Cluster Reboot
    b.	Leader PL reboot
    c. 	Candidate PL reboot
    d.	OVS Datapath reboots
    e.	Multiple PL reboots
    f.  Multiple Cluster reboots
    g.	Multiple reboots of the same OVS Datapath.
    h.	Openstack Controller reboots

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
-----------------
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
[1] https://tools.ietf.org/html/draft-ietf-bess-evpn-prefix-advertisement-02

[2] https://www.ietf.org/id/draft-ietf-bess-evpn-overlay-04.txt

[3] https://www.ietf.org/archive/id/draft-sajassi-l2vpn-evpn-inter-subnet-forwarding-05.txt

[4] https://tools.ietf.org/html/draft-boutros-bess-vxlan-evpn-01[AKMA13] [NV14]

[5] Ethernet VPN IETF RFC - https://tools.ietf.org/html/rfc7432

* http://docs.opendaylight.org/en/latest/documentation.html
* https://specs.openstack.org/openstack/nova-specs/specs/kilo/template.html