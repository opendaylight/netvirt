========================================
Neutron Port Allocation For DHCP Service
========================================

This feature will enable the Neutron DHCP proxy service within controller
to reserve and use a Neutron port per subnet for communication with
Neutron endpoints.

Problem description
===================

The DHCP service currently assumes availability of the gateway router IP,
which may or may not be available to the controller. This can lead to service
unavailability.

Problem - 1: L2 Deployment with 3PP gateway
===========================================
There can be deployment scenario in which L2 network is created with no distributed
Router/VPN functionality. This deployment can have a separate g/w for the network
such as a 3PP LB VM, which acts as a TCP termination point and this LB VM is
configured with a default gateway IP. But the current DHCP proxy service in controller
hijacks gateway IP address for serving DHCP discover/request messages. If the LB is up,
this can continue to work, DHCP broadcasts will get hijacked by the CSC, and responses
sent as PKT_OUTs with SIP = GW IP

However, if the LB is down, and the VM ARPs for the same IP as part of a DHCP renew
workflow, the ARP resolution can fail, due to which renew request will not be
generated. This can cause the DHCP lease to lapse.

Problem - 2: Designated DHCP for SR-IOV VMs via HWVTEP
======================================================
In this Deployment scenario, L2 network is created with no distributed Router/VPN
functionality, and HWVTEP for SR-IOV VMs. DHCP flood requests from SR-IOV VMs
(DHCP discover, request during bootup), are flooded by the HWVTEP on the ELAN,
and punted to the controller by designated vswitch. DHCP offers are sent as unicast
responses from Controller, which are forwarded by the HWVTEP to the VM. DHCP renews
can be unicast requests, which the HWVTEP may forward to an external g/w entity as
unicast packets. Designated vswitch will never receive these pkts, and thus not be
able to punt them to the controller, so renews will fail.


High-Level Components:
======================

The following components of the Openstack - ODL solution need to be enhanced to provide
port allocation for DHCP service.

* Openstack ODL Mechanism Driver
* OpenDaylight Controller (NetVirt VpnService)
We will review enhancements that will be made to each of the above components in following
sections.

Proposed change
===============

The following components within OpenDaylight Controller needs to be enhanced:
* Neutron VPN module
* DHCP module
* ELAN and L3VPN modules

Opendaylight controller needs to preserve a Neutron port for every subnet it is providing
DHCP proxy services to in Openstack deployments. Neutron DHCP agent does a port allocation
in every subnet, with device owner set to ‘ODL:Netvirt’ and uses this port for all outgoing
messages. Since this port gets a distinct IP/MAC from the router g/w port IP/MAC,
both problem-1 and problem-2 will be solved.

ODL Driver Changes:
===================
ODL driver will need a config setting when ODL DHCP service is in use, as against when Neutron
DHCP agent is deployed (Community ODL default setting). This needs to be enabled for CSC deployment

ODL driver will insert an async call in subnet create/update workflow in POST_COMMIT for subnets
with DHCP set to ‘enabled’, with a port create request, with device owner set to “ODL:Netvirt”,
and device ID set to controller hostname/IP (from ml2_conf.ini file)

ODL driver will insert an async call in subnet delete, and DHCP ‘disable’ workflow to ensure
the allocated port is deleted

ODL driver needs to ensure at any time no more than a single port is allocated per subnet
for these requirements

Pipeline changes
----------------

For example, VM interface is having 30.0.0.1 as its Gateway IP address and DHCP neutron port 
is created with IP address 30.0.0.4 for the same subnet. The ELAN pipeline is changed like below.

cookie=0x8040000, duration=627.038s, table=17, n_packets=0, n_bytes=0, priority=6, metadata=0xc019a00000000000/0xffffff0000000000 actions=write_metadata:0xe019a01771000000/0xfffffffffffffffe,goto_table:47
cookie=0x1080000, duration=979.713s, table=47, n_packets=0, n_bytes=0, priority=100,arp,arp_tpa=30.0.0.1,arp_op=2 actions=CONTROLLER:65535,resubmit(,48)
cookie=0x1080000, duration=979.712s, table=47, n_packets=0, n_bytes=0, priority=100,arp,arp_op=1,,arp_tpa=30.0.0.1 actions=group:5000
cookie=0x8030000, duration=979.717s, table=47, n_packets=0, n_bytes=0, priority=0 actions=goto_table:48
cookie=0x262219a4, duration=312.151s, table=81, n_packets=0, n_bytes=0, priority=100,arp,metadata=0xe019a01771000000/0xffffff00fffffffe,arp_tpa=30.0.0.1,arp_op=1 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:de:ad:be:ef:00:05->eth_src,load:0x2->NXM_OF_ARP_OP[],
move:NXM_NX_ARP_SHA[]->NXM_NX_ARP_THA[],move:NXM_OF_ARP_SPA[]->NXM_OF_ARP_TPA[],load:0xdeadbeef0005->NXM_NX_ARP_SHA[],load:0x1e000001->NXM_OF_ARP_SPA[],load:0->NXM_OF_IN_PORT[],load:0x19a000->NXM_NX_REG6[],resubmit(,220)
priority=100,arp,metadata=0xe019a01771000000/0xffffff00fffffffe,arp_tpa=30.0.0.4,arp_op=1 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:cc:ad:be:ef:00:04->eth_src,load:0x2->NXM_OF_ARP_OP[],
move:NXM_NX_ARP_SHA[]->NXM_NX_ARP_THA[],move:NXM_OF_ARP_SPA[]->NXM_OF_ARP_TPA[],load:0xccadbeef0004->NXM_NX_ARP_SHA[],load:0x1e000001->NXM_OF_ARP_SPA[],load:0->NXM_OF_IN_PORT[],load:0x19a000->NXM_NX_REG6[],resubmit(,220)

group_id=5000,type=all,bucket=actions=CONTROLLER:65535,bucket=actions=resubmit(,48),bucket=actions=resubmit(,81)

ARP Changes for DHCP port
-------------------------
1. Client VM ARP requests for DHCP server IP need to be answered in L2 as well
as L3 deployment.
2. Create ARP responder table flow entry for DHCP server IP in computes nodes
on which ELAN footprint is available.
3. Currently ARP responder is part of L3VPN pipeline, however no L3 service
may be available in an L2 deployment to leverage the current ARP pipeline,
for DHCP IP ARP responses. To ensure ARP responses are sent in L2 deployment,
ARP processing needs to be migrated to the ELAN pipeline.
4. ELAN service to provide API to other services needing ARP responder entries
including L3VPN service (for router MAC, router-gw MAC and floating IPs,
and EVPN remote MAC entries).
5. ELAN service will be responsible for punting a copy of each ARP packet to the
controller if the source MAC address is not already learned.

Assumptions
-----------
Support for providing port allocation for DHCP service is available from
Openstack ODL Mechanism Driver

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
The programming of flow rules in Table 47 and 81 is handled in ELAN module and
following APIs are exposed from IElanService so that L3VPN and DHCP modules can
use it to program ARP responder table flow entries Gateway/Router Interface and
DHCP port.

void addArpResponderEntry(BigIneger dpId, String ingressInterfaceName,
 String ipAddress, String macAddress);
void removeArpResponderEntry(BigIneger dpId, String ingressInterfaceName,
 String ipAddress, String macAddress);

ELAN module updates existing forwarding-entries model to hold mac entries of differenty types.

              grouping forwarding-entries {
                description "Details of the MAC entries";

                list mac-entry {
                  key "mac-address";
                  description "Details of a MAC address";
                  max-elements "unbounded";
                  min-elements "0";

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

                  leaf mac-type {
                      description "The VLAN mode of the L2Vlan Interface.";
                      type enumeration {
                          enum "static" {
                              value 1;
                          }
                          enum "learned" {
                              value 2;
                          }
                          enum "gateway" {
                              value 3;
                          }
                          enum "router-interface" {
                              value 4;
                          }
                          enum "dhcp" {
                              value 5;
                          }
                      }
                  }
                }
              }

elan-state container is changed to have these special (gateway, router-interface, dhcp)
forwarding-entries so that when ELAN footprint is added/removed on a particular compute
node, the flow rules in Table 47 and 81 can be updated accordingly.

              /* operational data stores */
              container elan-state {
                config false;
                description
                  "operational state of elans.";

                list elan {
                    key "name";
                    description "The list of interfaces on the device.";
                    max-elements "unbounded";
                    min-elements "0";
                    leaf name {
                        type string;
                        description
                          "The name of the elan-instance.";
                    }
                    leaf-list elan-interfaces{
                        type leafref {
                            path "/if:interfaces/if:interface/if:name";
                        }
                        description "Interfaces connected to this elan instance.";
                    }
                    uses forwarding-entries;
                }
              }

Assignee(s)
-----------

Primary assignee:
   Periyasamy Palanisamy <periyasamy.palanisamy@ericsson.com>

Other contributors:
   Dayavanti Gopal Kamath <dayavanti.gopal.kamath@ericsson.com>  

Work Items
----------


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
CSIT will be enhanced to cover this feature by providing new CSIT tests.

Documentation Impact
====================
This will require changes to User Guide and Developer Guide.

References
==========
