================================
ECMP Support for BGP based L3VPN
================================

https://git.opendaylight.org/gerrit/#/q/topic:l3vpn_ecmp

This Feature is needed for load balancing of traffic in a cloud and also
redundancy of paths for resiliency in cloud.

Problem description
===================

The current L3VPN implementation for BGP VPN doesn't support load balancing
behavior for external routes through multiple DC-GWs and reaching starting
route behind Nova VMs through multiple compute nodes.

This spec provides implementation details about providing traffic load
balancing using ECMP for L3 routing and forwarding. The load balancing of
traffic can be across virtual machines with each connected to the different
compute nodes, DC-Gateways. ECMP also enables fast failover of traffic
The ECMP forwarding is required for both inter-DC and intra-DC data traffic
types. For inter-DC traffic, spraying from DC-GW to compute nodes & VMs for
the traffic entering DC and spraying from compute node to DC-GWs for the
traffic exiting DC is needed. For intra-DC traffic, spraying of traffic
within DC across multiple compute nodes & VMs is needed. There should be
tunnel monitoring (e.g. GRE-KA or BFD) logic implemented to monitor DC-GW
/compute node GRE tunnels which helps to determine available ECMP paths to
forward the traffic.

Use Cases
---------

UC1: ECMP  forwarding of  traffic entering a DC (i.e. Spraying of
DC-GW -> OVS traffic across multiple Compute Nodes & VMs).
In this case, DC-GW can load balance the traffic if a static route can be reachable
through multiple NOVA VMs (say VM1 and VM2 connected on different compute nodes)
running some networking application (example: vRouter).

UC2: ECMP forwarding of  traffic exiting a DC (i.e. Spraying of
OVS -> DC-GW traffic across multiple DC Gateways).
In this case, a Compute Node can LB the traffic if external route can be
reachable from multiple DC-GWs.

UC3: ECMP  forwarding of intra-DC traffic (i.e. Spraying of traffic within DC
across multiple Compute Nodes & VMs)
This is similar to UC1, but load balancing behavior is applied on remote Compute
Node for intra-DC communication.

UC4: OVS -> DC-GW tunnel status based ECMP for inter and intra-DC traffic.
Tunnel status based on monitoring (BFD)  is considered in ECMP path set determination.


High-Level Components:
======================

The following components of the Openstack - ODL solution need to be enhanced to provide
ECMP support
* Openstack Neutron BGPVPN Driver (for supporting multiple RDs)
* OpenDaylight Controller (NetVirt VpnService)
We will review enhancements that will be made to each of the above components in following
sections.

Proposed change
===============

The following components within OpenDaylight Controller needs to be enhanced:
* NeutronvpnManager
* VPN Engine (VPN Manager and VPN Interface Manager)
* FIB Manager

Pipeline changes
----------------

Local FIB entry/Nexthop Group programming:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
A static route (example: 100.0.0.0/24) reachable through two VMs connected
with same compute node.

cookie=0x8000003, duration=46.020s, table=21, n_packets=0, n_bytes=0, priority=34,ip,metadata=0x222e4/0xfffffffe, nw_dst=100.0.0.0/24 actions=write_actions(group:150002)
group_id=150002,type=select,bucket=weight:50,actions=group:150001,bucket=weight:50,actions=group:150000
group_id=150001,type=all,bucket=actions=set_field:fa:16:3e:34:ff:58->eth_dst,load:0x200->NXM_NX_REG6[],resubmit(,220)
group_id=150000,type=all,bucket=actions=set_field:fa:16:3e:eb:61:39->eth_dst,load:0x100->NXM_NX_REG6[],resubmit(,220)

Table 0=>Table 17=>Table 19=>Table 21=>LB Group=>Local VM Group=>Table 220

Remote FIB entry/Nexthop Group programming:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
1) A static route (example: 10.0.0.1/32) reachable through two VMs connected
with different compute node.

on remote compute node,

cookie=0x8000003, duration=46.020s, table=21, n_packets=0, n_bytes=0, priority=34,ip,metadata=0x222e4/0xfffffffe, nw_dst=10.0.0.1 actions=set_field:0xEF->tun_id, group:150003
group_id=150003,type=select,bucket=weight:50,actions=output:1,bucket=weight:50,actions=output:2

Table 0=>Table 17=>Table 19=>Table 21=>LB Group=>VxLAN port

on local compute node,

cookie=0x8000003, duration=46.020s, table=21, n_packets=0, n_bytes=0, priority=34,ip,metadata=0x222e4/0xfffffffe, nw_dst=10.0.0.1 actions=group:150003
group_id=150003,type=select,bucket=weight:50,group=150001,bucket=weight:50,actions=set_field:0xEF->tun_id, output:2
group_id=150001,type=all,bucket=actions=set_field:fa:16:3e:34:ff:58->eth_dst,load:0x200->NXM_NX_REG6[],resubmit(,220)

Table 0=>Table 17=>Table 19=>Table 21=>LB Group=>Local VM Group=>Table 220|=>VxLAN port


2) An external route (example: 20.0.0.1/32) reachable through two DC-GWs.

cookie=0x8000003, duration=13.044s, table=21, n_packets=0, n_bytes=0,priority=42,ip,metadata=0x222ec/0xfffffffe,nw_dst=20.0.0.1 actions=load:0x64->NXM_NX_REG0[0..19],load:0xc8->NXM_NX_REG1[0..19],group:150111
group_id=150111,type=select,bucket=weight:50,actions=push_mpls:0x8847, move:NXM_NX_REG0[0..19]->OXM_OF_MPLS_LABEL[],output:3, bucket=weight:50,actions=push_mpls:0x8847,move:NXM_NX_REG1[0..19]->OXM_OF_MPLS_LABEL[],output:4

Table 0=>Table 17=>Table 19=>Table 21=>LB Group=>GRE port

Yang changes
------------
Changes will be needed in ``l3vpn.yang`` , ``odl-l3vpn.yang`` and ``odl-fib.yang``
to support ECMP functionality.

L3VPN YANG changes
^^^^^^^^^^^^^^^^^^
route-distinguisher type is changed from leaf to leaf-list in vpn-af-config
 grouping in l3vpn.yang.

    grouping vpn-af-config {
        description
          "A set of configuration parameters that is applicable to both IPv4 and
           IPv6 address family for a VPN instance .";

        leaf-list route-distinguisher {
          description
        "The route-distinguisher command configures a route distinguisher (RD)
         for the IPv4 or IPv6 address family of a VPN instance.

         Format is ASN:nn or IP-address:nn.";

          config "true";
          type string{
        length "3..21";
          }

    }

ODL-L3VPN YANG changes
^^^^^^^^^^^^^^^^^^^^^^
Add vrf-id (RD) in adjacency list in odl-l3vpn.yang.

    grouping adjacency-list {
        list adjacency{
            key "ip_address";
            leaf-list next-hop-ip-list { type string; }
            leaf ip_address {type string;}
            leaf primary-adjacency {

                type boolean;
                default false;

              description "Value of True indicates this is a primary adjacency";

            }

            leaf label { type uint32; config "false"; } /optional/

            leaf mac_address {type string;} /optional/

            leaf vrf-id {type string;}

        }

    }

vpn-to-extraroute have to be updated with multiple RDs (vrf-id) when extra route from VMs
connected with different compute node and when connected on same compute node, just use
same RD and update nexthop-ip-list with new VM IP address like below.

    container vpn-to-extraroutes {
        config false;
        list vpn-extraroutes {

            key vpn-name;
            leaf vpn-name {
            type uint32;

           }

           list extra-routes {
               key vrf-id;
               leaf vrf-id {
               description "The vrf-id command configures a route distinguisher (RD) for the IPv4
               or IPv6 address family of a VPN instance or vpn instance name for
               internal vpn case.";
               type string;

              }

              list route-paths {
                  key prefix;
                  leaf prefix {type string;}
                  leaf-list nexthop-ip-list {
                  type string;

                 }

              }

           }

        }

    }

To manage RDs for extra with multiple next hops, the following yang
model is required  to advertise (or) withdraw the extra routes with
unique NLRI accordingly.

     container extraroute-routedistinguishers-map {
         config true;
         list extraroute-routedistingueshers {

             key vpnid;
             leaf vpnid {
             type uint32;

             }

             list dest-prefixes {
                 key "dest-prefix";
                 leaf dest-prefix {
                 type string;
                 mandatory true;

                 }

                 leaf-list route-distinguishers {
                     type string;

                 }

             }

         }

    }

ODL-FIB YANG changes
^^^^^^^^^^^^^^^^^^^^
When Quagga BGP announces route with multiple paths, then it is ODL responsibility
to program Fib entries in all compute nodes where VPN instance blueprint is present,
so that traffic can be load balanced between these two DC gateways. It requires
changes in existing odl-fib.yang model (like below) to support multiple
routes for same destination IP prefix.

    grouping vrfEntries {
        list vrfEntry {
            key  "destPrefix";
            leaf destPrefix {
            type string;
            mandatory true;

            }

            leaf origin {
                type string;
                mandatory true;

            }

            list route-paths {
                key "nexthop-address";
                leaf nexthop-address {
                type string;
                mandatory true;


             }

             leaf label {
                 type uint32;

             }

            }

        }

    }

New YANG model to update load balancing next hop group buckets according
to VxLAN/GRE tunnel status [Note that these changes are required only if
watch_port in group bucket is not working based on tunnel port liveness
monitoring affected by the BFD status]. When one of the VxLAN/GRE tunnel
is going down, then retrieve nexthop-key from dpid-l3vpn-lb-nexthops by
providing tep-device-idâ€™s from src-info and dst-info of StateTunnelList
while handling its update DCN. After retrieving next hop key, fetch
target-device-id list from l3vpn-lb-nexthops and reprogram
VxLAN/GRE load balancing group in each remote Compute Node based
on tunnel state between source and destination Compute Node. Similarly,
when tunnel comes up, then logic have to be rerun to add its
bucket back into Load balancing group.

     container l3vpn-lb-nexthops {
         config false;
         list nexthops {

             key "nexthop-key";
             leaf group-id { type string; }
             leaf nexhop-key { type string; }
             leaf-list target-device-id { type string;
             //dpId or ip-address }

         }

     }

     container dpid-l3vpn-lb-nexthops {
         config false;
         list dpn-lb-nexthops {

             key "src-dp-id dst-device-id";
             leaf src-dp-id { type uint64; }
             leaf dst-device-id { type string;
             //dpId or ip-address }
             leaf-list nexthop-keys { type string; }

         }

     }

ECMP forwarding through multiple Compute Node and VMs
-----------------------------------------------------
In some cases, extra route can be added which can have reachability through
multiple Nova VMs. These VMs can be either connected on same compute node
(or) different Compute Nodes. When VMs are in different compute nodes, DC-GW
should learn all the route paths such that ECMP behavior can be applied for
these multi path routes. When VMs are co-located in same compute node, DC-GW
will not perform ECMP and compute node performs traffic splitting instead.

ECMP forwarding for dispersed VMs
---------------------------------
When configured extra route are reached through nova VMs which are connected
with different compute node, then it is ODL responsibility to advertise these
multiple route paths (but with same MPLS label) to Quagga BGP which in turn
sends these routes into DC-GW. But DC-GW replaces the existing route with a new
route received from the peer if the NLRI (prefix) is same in the two routes.
This is true even when multipath is enabled on the DC-GW and it is as per standard
BGP RFC 4271, Section 9 UPDATE Message Handling. Hence the route is lost in DC-GW
even before path computation for multipath is applied.This scenario is solved by
adding multiple route distinguisher (RDs) for the vpn instance and let ODL uses
the list of RDs to advertise the same prefix with different BGP NHs. Multiple RDs
will be supported only for BGP VPNs.

ECMP forwarding for co-located VMs
-----------------------------------
When extra routes on VM interfaces are connected with same compute node, LFIB/FIB
and Terminating service table flow entries should be programmed so that traffic can
be load balanced between local VMs. This can be done by creating load balancing next
hop group for each vpn-to-extraroute (if nexthop-ip-list size is greater than 1) with
buckets pointing to the actual VMs next hop group on source Compute Node. Even for the
co-located VMs, VPN interface manager should assign separate RDs for each adjacency of
same dest IP prefix and let route can be advertised again to Quagga BGP with same next
hop (TEP IP address). This will enable DC-Gateway to realize ECMP behavior when an IP
prefix can be reachable through multiple co located VMs on one Compute Node and an
another VM connected on different Compute Node.

To create load balancing next hop group, the dest IP prefix is used as the key to
generate group id. When any of next hop is removed, then adjust load balancing nexthop
group so that traffic can be sent through active next hops.

ECMP forwarding through two DC-Gateways
---------------------------------------
The current ITM implementation provides support for creating multiple GRE tunnels for
the provided list of DC-GW IP addresses from compute node. This should help in creating
corresponding load balancing group whenever Quagga BGP is advertising two routes on same
IP prefix pointing to multiple DC GWs. The group id of this load balancing group can be
derived from sorted order of DC GW TEP IP addresses with the following format dc_gw_tep_ip
_address_1: dc_gw_tep_ip_address_2. This will be useful when multiple external IP prefixes
share the same next hops. The load balancing next hop group buckets is programmed according
to sorted remote end point DC-Gateway IP address. The support of action move:NXM_NX_REG0(1)
-> MPLS Label is not supported in ODL openflowplugin. It has to be implemented. Since there
are two DC gateways present for the data center, it is possible that multiple equal cost
routes are supplied to ODL by Quagga BGP like Fig 2. The current Quagga BGP doesnâ€™t have
multipath support and it will be done. When Quagga BGP announces route with multiple
paths, then it is ODL responsibility to program Fib entries in all compute nodes where
VPN instance blueprint is present, so that traffic can be load balanced between these
two DC gateways. It requires changes in existing odl-fib.yang model (like below) to
support multiple routes for same destination IP prefix.

BGPManager should be able to create vrf entry for the advertised IP prefix with multiple
route paths. VrfEntryListener listens to DCN on these vrf entries and program Fib entries
(21) based on number route paths available for given IP prefix. For the given (external)
destination IP prefix, if there is only one route path exists, use the existing approach
to program FIB table flow entry matches on (vpnid, ipv4_dst) and actions with push mpls
label and output to gre tunnel port. For the given (external) destination IP prefix, if
there are two route paths exist, then retrieve next hop ip address from routes list in
the same sorted order (i.e. using same logic which is used to create buckets for load
balancing next hop group for DC- Gateway IP addresses), then program FIB table flow entry
with an instruction like Fig 3. It should have two set field actions where first action sets
mpls label to NX_REG0 for first sorted DC-GW IP address and second action sets mpls label
to NX_REG1 for the second sorted DC-GW IP address. When more than two DC Gateways are used,
then more number of NXM Registries have to be used to push appropriate MPLS label before
sending it to next hop group. It needs operational DS container to have mapping between DC
Gateway IP address and NXM_REG. When one of the route is withdrawn for the IP prefix, then
modify the FIB table flow entry with with push mpls label and output to the available
gre tunnel port.

ECMP for Intra-DC L3VPN communication
-------------------------------------
ECMP within data center is required to load balance the data traffic when extra route can
be reached through multiple next hops (i.e. Nova VMs) when these are connected with different
compute nodes. It mainly deals with how Compute Nodes can spray the traffic when dest IP prefix
can be reached through two or more VMs (next hops) which are connected with multiple compute
nodes.
When there are multiple RDs (if VPN is of type BGP VPN) assigned to VPN instance so that VPN
engine can be advertise IP route with different RDs to achieve ECMP behavior in DC-GW as
mentioned before. But for intra-DC, this doesnâ€™t make any more sense since itâ€™s all about
programming remote FIB entries on computes nodes to achieve data traffic
spray behavior.
Irrespective of RDs, when multiple next hops (which are from different Compute Nodes) are
present for the extra-route adjacency, then FIB Manager has to create load balancing next
hop group in remote compute node with buckets pointing with targeted Compute Node VxLAN
tunnel ports.
To allocate group id for this load balancing next hop, the same destination IP prefix is
used as the group key. The remote FIB table flow should point to this next hop group after
writing prefix label into tunnel_id. The bucket weight of remote next hop is adjusted
according to number of VMs associated to given extra route and on which compute node
the VMs are connected. For example, two compute node having one VM each, then bucket
weight is 50 each. One compute node having two VMs and another compute node having one
VM, then bucket weight is 66 and 34 each. The hop-count property in vrfEntry data store
helps to decide what is the bucket weight for each bucket.

ECMP Path decision based on Internal/External Tunnel Monitoring
---------------------------------------------------------------
ODL will use GRE-KA or BFD protocol to implement monitoring of GRE external tunnels.
This implementation detail is out of scope in this document. Based on the tunnel state,
GRE Load Balancing Group is adjusted accordingly as mentioned like below.

GRE tunnel state handling
-------------------------
As soon as GRE tunnel interface is created in ODL, interface manager uses alivenessmonitor
to monitor the GRE tunnels for its liveness using GRE Keep-alive protocol. When tunnel state
changes, it has to handled accordingly to adjust above load balancing group so that data
traffic is sent to only active DC-GW tunnel. This can be done with listening to update
StateTunnelList DCN.
When one GRE tunnel is operationally going down, then retrieve the corresponding bucket
from the load balancing group and delete it.
When GRE tunnel comes up again, then add bucket back into load balancing group and
reprogram it.
When both GRE tunnels are going down, then just recreate load balancing group with empty.
Withdraw the routes from that particular DC-GW.
With the above implementation, there is no need of modifying Fib entries for GRE tunnel
state changes.
But when BGP Quagga withdrawing one of the route for external IP prefix, then reprogram
FIB flow entry (21) by directly pointing to output=<gre_port> after pushing MPLS label.

VxLAN tunnel state handling
---------------------------
Similarly, when VxLAN tunnel state changes, the Load Balancing Groups in Compute Nodes have
to be updated accordingly so that traffic can flow through active VxLAN tunnels. It can be
done by having config mapping between target data-path-id to next hop group Ids
and vice versa.
For both GRE and VxLAN tunnel monitoring, L3VPN has to implement the following YANG model
to update load balancing next hop group buckets according to tunnel status.
When one of the VxLAN/GRE tunnel is going down, then retrieve nexthop-key from
dpid-l3vpn-lb-nexthops by providing tep-device-idâ€™s from src-info and dst-info of
StateTunnelList while handling its update DCN.
After retrieving next hop key, fetch target-device-id list from l3vpn-lb-nexthops
and reprogram VxLAN/GRE load balancing group in each remote Compute Node based on
tunnel state between source and destination Compute Node. Similarly, when tunnel
comes up, then logic have to be rerun to add its bucket back into
Load balancing group.

Assumptions
-----------
The support for action move:NXM_NX_REG0(1) -> MPLS Label is already available
in Compute Node.

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
  Manu B <manu.b@ericsson.com>
  Kency Kurian <kency.kurian@ericsson.com>
  Gobinath <gobinath@ericsson.com>
  P Govinda Rajulu <p.govinda.rajulu@ericsson.com>

Other contributors:
  Periyasamy Palanisamy <periyasamy.palanisamy@ericsson.com>

Work Items
----------


Dependencies
============
Quagga BGP multipath support and APIs. This is needed to support when two DC-GW advertises
routes for same external prefix with different route labels
GRE tunnel monitoring. This is need to implement ECMP forwarding based on MPLSoGRE tunnel state.
Support for action move:NXM_NX_REG0(1) -> MPLS Label in ODL openflowplugin

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
[1] https://docs.google.com/document/d/1KRxrIGCLCBuz2D8f8IhU2I84VrM5EMa1Y7Scjb6qEKw/edit#
