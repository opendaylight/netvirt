.. contents:: Table of Contents
      :depth: 5

========================================
Neutron Port Allocation For DHCP Service
========================================

https://git.opendaylight.org/gerrit/#/q/topic:neutron_port_dhcp

This feature will enable the Neutron DHCP proxy service within controller
to reserve and use a Neutron port per subnet for communication with
Neutron endpoints.

Problem description
===================

The DHCP service currently assumes availability of the subnet gateway IP address
and its mac address for its DHCP proxy service, which may or may not be available
to the controller. This can lead to service unavailability.

Problem - 1: L2 Deployment with 3PP gateway
===========================================
There can be deployment scenario in which L2 network is created with no distributed
Router/VPN functionality. This deployment can have a separate gateway for the network
such as a 3PP LB VM, which acts as a TCP termination point and this LB VM is
configured with a default gateway IP. It means all inter-subnet traffic is terminated
on this VM which takes the responsibility of forwarding the traffic.

But the current DHCP proxy service in controller hijacks gateway IP address for
serving DHCP discover/request messages. If the LB is up, this can continue to work,
DHCP broadcasts will get hijacked by the ODL, and responses
sent as PKT_OUTs with SIP = GW IP.

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
can be unicast requests, which the HWVTEP may forward to an external Gateway VM (3PP
LB VM) as unicast packets. Designated vswitch will never receive these pkts, and thus
not be able to punt them to the controller, so renews will fail.

High-Level Components:
======================

The following components of the Openstack - ODL solution need to be enhanced to provide
port allocation for DHCP service.

* Openstack ODL Mechanism Driver
* OpenDaylight Controller (NetVirt VpnService/DHCP Service/Elan Service)
We will review enhancements that will be made to each of the above components in following
sections.

Proposed change
===============

The following components within OpenDaylight Controller needs to be enhanced:
* Neutron VPN module
* DHCP module
* ELAN and L3VPN modules

OpenDaylight controller needs to preserve a Neutron port for every subnet so that DHCP proxy
service can be enabled in Openstack deployment. The Neutron port's device owner property is
set to ``network:dhcp`` and uses this port for all outgoing DHCP messages. Since this port gets
a distinct IP address and MAC address from the subnet, both problem-1 and problem-2 will be
solved.

ODL Driver Changes:
===================
ODL driver will need a config setting when ODL DHCP service is in use, as against when Neutron
DHCP agent is deployed (Community ODL default setting). This needs to be enabled for ODL deployment

ODL driver will insert an async call in subnet create/update workflow in POST_COMMIT for subnets
with DHCP set to ‘enabled’, with a port create request, with device owner set to ``network:dhcp``,
and device ID set to controller hostname/IP (from ml2_conf.ini file)

ODL driver will insert an async call in subnet delete, and DHCP ‘disable’ workflow to ensure
the allocated port is deleted

ODL driver needs to ensure at any time no more than a single port is allocated per subnet
for these requirements

Pipeline changes
----------------

For example, If a VM interface is having 30.0.0.1/de:ad:be:ef:00:05 as its Gateway (or) Router
Interface IP/MAC address and its subnet DHCP neutron port is created with IP/MAC address
30.0.0.4/de:ad:be:ef:00:04. The ELAN pipeline is changed like below.

.. code-block:: bash

   LPort Dispatcher Table (17)=>ELAN ARP Check Table(43) => ARP Responder Group (5000) => ARP Responder Table (81) => Egress dispatcher Table(220)

   cookie=0x8040000, duration=627.038s, table=17, n_packets=0, n_bytes=0, priority=6, metadata=0xc019a00000000000/0xffffff0000000000 actions=write_metadata:0xe019a01771000000/0xfffffffffffffffe,goto_table:43
   cookie=0x1080000, duration=979.713s, table=43, n_packets=0, n_bytes=0, priority=100,arp,arp_tpa=30.0.0.1,arp_op=2 actions=CONTROLLER:65535,resubmit(,48)
   cookie=0x1080000, duration=979.712s, table=43, n_packets=0, n_bytes=0, priority=100,arp,arp_op=1,,arp_tpa=30.0.0.1 actions=group:5000
   cookie=0x1080000, duration=979.712s, table=43, n_packets=0, n_bytes=0, priority=100,arp,arp_op=1,,arp_tpa=30.0.0.4 actions=group:5000
   cookie=0x8030000, duration=979.717s, table=43, n_packets=0, n_bytes=0, priority=0 actions=goto_table:48
   cookie=0x262219a4, duration=312.151s, table=81, n_packets=0, n_bytes=0, priority=100,arp,metadata=0xe019a01771000000/0xffffff00fffffffe,arp_tpa=30.0.0.1,arp_op=1 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:de:ad:be:ef:00:05->eth_src,load:0x2->NXM_OF_ARP_OP[], move:NXM_NX_ARP_SHA[]->NXM_NX_ARP_THA[],move:NXM_OF_ARP_SPA[]->NXM_OF_ARP_TPA[],load:0xdeadbeef0005->NXM_NX_ARP_SHA[],load:0x1e000001->NXM_OF_ARP_SPA[],load:0->NXM_OF_IN_PORT[],load:0x19a000->NXM_NX_REG6[],resubmit(,220)
   cookie=0x262219a4, duration=312.151s, table=81, n_packets=0, n_bytes=0, priority=100,arp,metadata=0xe019a01771000000/0xffffff00fffffffe,arp_tpa=30.0.0.4,arp_op=1 actions=move:NXM_OF_ETH_SRC[]->NXM_OF_ETH_DST[],set_field:de:ad:be:ef:00:04->eth_src,load:0x2->NXM_OF_ARP_OP[], move:NXM_NX_ARP_SHA[]->NXM_NX_ARP_THA[],move:NXM_OF_ARP_SPA[]->NXM_OF_ARP_TPA[],load:0xdeadbeef0004->NXM_NX_ARP_SHA[],load:0x1e000001->NXM_OF_ARP_SPA[],load:0->NXM_OF_IN_PORT[],load:0x19a000->NXM_NX_REG6[],resubmit(,220)   

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
Openstack Pike release.

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
Nitrogen.

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
The programming of flow rules in Table 43 and Table 81 is handled in ELAN module and
following APIs are exposed from ``IElanService`` so that L3VPN and DHCP modules can
use it to program ARP responder table flow entries Gateway/Router Interface, floating
IPs and DHCP port.

.. code-block:: bash

   void addArpResponderEntry(BigIneger dpId, String ingressInterfaceName,
       String ipAddress, String macAddress, Optional<Integer> lportTag);
   void removeArpResponderEntry(BigIneger dpId, String ingressInterfaceName,
       String ipAddress, String macAddress, Optional<Integer> lportTag);

A new container is introduced to hold the network DHCP port information.

.. code-block:: none
   :caption: dhcpservice-api.yang
   
        container network-dhcpport-data {
            config true;
            list network-to-dhcpport {
                key "port-networkid";
                leaf port-networkid { 
			        type string;
			    }
                leaf port-name {
			        type string;
		        }
                leaf port-fixedip {
 			        type string;
			    }
                leaf mac-address {
			        type string;
			    }
            }
        }
		
When the ODL dhcp port is available its fixed IP will be used as the server IP
to serve DHCP offer/renew requests for the virtual endpoints. The subnet
gateway IP address will continue to be used as the server IP when no ODL dhcp
port is avaialable for the subnet.	

Assignee(s)
-----------

Primary assignee:
  Karthik Prasad <karthik.p@altencalsoftlabs.com>
  Achuth Maniyedath <achuth.m@altencalsoftlabs.com>
  Vijayalakshmi CN <vijayalakshmi.c@altencalsoftlabs.com>

Other contributors:
   Dayavanti Gopal Kamath <dayavanti.gopal.kamath@ericsson.com>
   Periyasamy Palanisamy <periyasamy.palanisamy@ericsson.com>

Work Items
----------

Dependencies
============

Testing
=======

CSIT
----
CSIT will be enhanced to cover this feature by providing new CSIT tests.

Documentation Impact
====================
This will require changes to User Guide and Developer Guide.

References
==========

* OpenStack Spec  - https://review.openstack.org/#/c/453160
