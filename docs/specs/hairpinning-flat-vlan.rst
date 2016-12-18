.. contents:: Table of Contents
         :depth: 3

==============================================================
Hairpinning of floating IPs in flat/VLAN provider networks
==============================================================

https://git.opendaylight.org/gerrit/#/q/topic:hairpinning

This feature enables VM instances connected to the same router to communicate with each other using their
floating ip addresses directly without traversing via the external gateway.

Problem description
===================

Local and East/West communication between VMs using floating ips for flat/VLAN provider types is not
handled internally by the pipeline currently. As a result, this type of traffic is mistakenly classified
as North/South and routed to the external network gateway.

Today, SNATted traffic to flat/VLAN network is routed directly to the external gateway after traversing
the SNAT/outbound NAPT pipeline using OF group per external network subnet.
The group itself sets the destination mac as the mac address of the external gw associated with the floating ip/
router gw and output to the provider network port via the egress table.
This workflow would be changed to align with the VxLAN provider type and direct SNATted traffic back to the FIB
where the destination can then resolved to be floating ip on local or remote compute node.

Use Cases
---------

- Local and East/West communication between VMs co-located on the same compute node using associated floating ip.
- Local and East/West communication between VMs located on different compute nodes using associated floating ip.

Proposed change
===============

* The vpn-id used for classification of floating ips and router gateway external addresses in flat/VLAN
  provider networks is based on the external network id. It will be changed to reflect the subnet id
  associated with the floating ip/router gateway. This will allow traffic from the SNAT/outbound NAPT
  table to be resubmitted back to the FIB while preserving the subnet id.

* Each floating ip already has VRF entry in the fib table. The vpn-id of this entry will also be based
  on the subnet id of the floating ip instead of the external network id. If the VM associated with the
  floating ip is located on remote compute node, the traffic will be routed to the remote compute based
  on the provider network of the subnet from which the floating ip was allocated e.g. if the private
  network is VxLAN and the external network is VLAN provider, traffic to floating ip on remote compute
  node will be routed to the provider port associated with the VLAN provider and not the tunnel
  associated with the VxLAN provider.

* In the FIB table of the egress node, the destination mac will be replaced with the mac address
  of the floating ip in case of routing to remote compute node. This will allow traffic from flat/VLAN
  provider enter the L3 pipeline for DNAT of the floating ip.

* Default flow will be added to the FIB table for each external subnet-id. If no floating ip match
  was found in the FIB table for the subnet id, the traffic will be sent to the group of the external
  subnet. Each group entry will perform the following:
  (a) replace the destination mac address to the external gateway mac address
  (b) send the traffic to the provider network via the egress table.

* Ingress traffic from flat/VLAN provider network is bounded to L3VPN service using vpn-id of the
  external network id. To allow traffic classification based on subnet id for floating ips and router
  gateway ips, the GW MAC table will replace the vpn-id of the external network with
  the vpn-id of the subnet id of the floating ip. For ingress traffic to router gateway mac, the vpn-id
  of the correct subnet will be deterined at the FIB table based on the router gateway fixed ip.

* A new model will be introduced to contain the new vpn/subnet associations - ``odl-nat:subnets-networks``.
  This model will be filled only for external  flat/VLAN provider networks and will take precedence over
  ``odl-nat:external-networks`` model for selection of vpn-id. BGPVPN use cases won't be affected by these
  changes as this model will not be applicable for these scenarios.

Pipeline changes
----------------

Egress traffic from VM with floating IP to the internet
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- For Pre SNAT, SNAT, FIB tables the vpn-id will be based on the subnet-id of the floating ip
- Packets from SNAT table resubmitted back to the FIB rather than straight to the external network subnet-id group.
  In the FIB table it should be matched against a new flow with lower priority than any other flow containing
  dst-ip match. Traffic will be redirected based on the vpn-id of the floating ip subnet to the external network
  subnet-id group.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id,src-ip=vm-ip set vpn-id=fip-subnet-id,src-ip=fip`` =>
  | SNAT table (28) ``match: vpn-id=fip-subnet-id,src-ip=fip set src-mac=fip-mac`` =>
  | FIB table (21) ``match: vpn-id=fip-subnet-id`` =>
  | Subnet-id group: ``set dst-mac=ext-subnet-gw-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider network

Ingress traffic from the internet to VM with floating IP
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- For GW MAC, FIB table the vpn-id will be based on the subnet-id of the floating ip

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=ext-net-id`` =>
  | GW Mac table (19) ``match: vpn-id=ext-net-id,dst-mac=floating-ip-mac set vpn-id=fip-subnet-id`` =>
  | FIB table (21) ``match: vpn-id=fip-subnet-id,dst-ip=fip`` =>
  | Pre DNAT table (25) ``match: dst-ip=fip set vpn-id=router-id,dst-ip=vm-ip`` =>
  | DNAT table (27) ``match: vpn-id=router-id,dst-ip=vm-ip`` =>
  | FIB table (21) ``match: vpn-id=router-id,dst-ip=vm-ip`` =>
  | Local Next-Hop group: ``set dst-mac=vm-mac, reg6=vm-lport-tag`` =>
  | Egress table (220) output to VM port

Egress traffic from VM with no associated floating IP to the internet - NAPT switch
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- For Outbound NAPT, NAPT PFIB and FIB tables the vpn-id will be based on the subnet-id of the router gateway
- Packets from NAPT PFIB table resubmitted back to the FIB rather than straight to the external network subnet-id group.
  In the FIB table it should be matched against a new flow with lower priority than any other flow containing
  dst-ip match. Traffic will be redirected based on the vpn-id of the router gateway subnet to the external network
  subnet-id group.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | Outbound NAPT table (46) ``match: src-ip=vm-ip,port=int-port set src-ip=router-gw-ip,vpn-id=router-gw-subnet-id,port=ext-port`` =>
  | NAPT PFIB table (47) ``match: vpn-id=router-gw-subnet-id`` =>
  | FIB table (21) ``match: vpn-id=router-gw-subnet-id`` =>
  | Subnet-id group: ``set dst-mac=ext-subnet-gw-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider network

Ingress traffic from the internet to VM with no associated floating IP - NAPT switch
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- For FIB table the vpn-id will be based on the subnet-id of the router gateway

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=ext-net-id`` =>
  | GW Mac table (19) ``match vpn-id=ext-net-id,dst-mac=router-gw mac`` =>
  | FIB table (21) ``match: vpn-id=ext-net-id,dst-ip=router-gw set vpn-id=router-gw-subnet-id`` =>
  | Inbound NAPT table (44) ``match: dst-ip=router-gw,port=ext-port set dst-ip=vm-ip,vpn-id=router-id,port=int-port`` =>
  | PFIB table (47) ``match: vpn-id=router-id`` =>
  | FIB table (21) ``match: vpn-id=router-id,dst-ip=vm-ip`` =>
  | Local Next-Hop group: ``set dst-mac=vm-mac,reg6=vm-lport-tag`` =>
  | Egress table (220) output to VM port

Hairpinning - VM traffic to floating ip on the same compute node
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- For Pre SNAT, SNAT, FIB tables the vpn-id will be based on the subnet-id of the floating ips

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id,src-ip=src-vm-ip set vpn-id=fip-subnet-id,src-ip=src-fip`` =>
  | SNAT table (28) ``match: vpn-id=fip-subnet-id,src-ip=src-fip set src-mac=src-fip-mac`` =>
  | FIB table (21) ``match: vpn-id=fip-subnet-id,dst-ip=dst-fip`` =>
  | Pre DNAT table (25) ``match: dst-ip=dst-fip set vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | DNAT table (27) ``match: vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | FIB table (21) ``match: vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | Local Next-Hop group: ``set dst-mac=dst-vm-mac,reg6=dst-vm-lport-tag`` =>
  | Egress table (220) output to VM port

Hairpinning - VM traffic to floating ip on remote compute node
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
VM originating the traffic (**Ingress DPN**):
""""""""""""""""""""""""""""""""""""""""""""""
- For Pre SNAT, SNAT, FIB tables the vpn-id will be based on the subnet-id of the floating ip
- The destination mac is updated by the FIB table to be the floating ip mac. Traffic is sent to the egress DPN over
  the port of the flat/VLAN provider network.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id,src-ip=src-vm-ip set vpn-id=fip-subnet-id,src-ip=src-fip`` =>
  | SNAT table (28) ``match: vpn-id=fip-subnet-id,src-ip=src-fip set src-mac=src-fip-mac`` =>
  | FIB table (21) ``match: vpn-id=fip-subnet-id,dst-ip=dst-fip set dst-mac=dst-fip-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider network

VM receiving the traffic (**Egress DPN**):
"""""""""""""""""""""""""""""""""""""""""""
- For GW MAC, FIB table the vpn-id will be based on the subnet-id of the floating ip

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=ext-net-id`` =>
  | GW Mac table (19) ``match: vpn-id=ext-net-id,dst-mac=dst-fip-mac set vpn-id=fip-subnet-id`` =>
  | FIB table (21) ``match: vpn-id=fip-subnet-id,dst-ip=dst-fip`` =>
  | Pre DNAT table (25) ``match: dst-ip=dst-fip set vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | DNAT table (27) ``match: vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | FIB table (21) ``match: vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | Local Next-Hop group: ``set dst-mac=dst-vm-mac,lport-tag=dst-vm-lport-tag`` =>
  | Egress table (220) output to VM port

Hairpinning - traffic from VM with no associated floating IP to floating ip on remote compute node
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

VM originating the traffic (**Ingress DPN**) is non-NAPT switch:
""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
- No flow changes required. Traffic will be directed to NAPT switch and directed to the outbound NAPT table straight
  from the internal tunnel table

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | NAPT Group ``output to tunnel port of NAPT switch`` =>


VM originating the traffic (**Ingress DPN**) is the NAPT switch:
""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
- For Outbound NAPT, NAPT PFIB, Pre DNAT, DNAT and FIB tables the vpn-id will be based on the common subnet-id of the
  router gateway and the floating-ip.
- Packets from NAPT PFIB table resubmitted back to the FIB where they will be matched against the destnation floating ip.
- The destination mac is updated by the FIB table to be the floating ip mac. Traffic is sent to the egress DPN over
  the port of the flat/VLAN provider network.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | Outbound NAPT table (46) ``match: src-ip=vm-ip,port=int-port set src-ip=router-gw-ip,vpn-id=router-gw-subnet-id,port=ext-port`` =>
  | NAPT PFIB table (47) ``match: vpn-id=router-gw-subnet-id`` =>
  | FIB table (21) ``match: vpn-id=router-gw-subnet-id dst-ip=dst-fip set dst-mac=dst-fip-mac, reg6=provider-lport-tag`` =>
  | Egress table (220) output to provider network

VM receiving the traffic (**Egress DPN**):
"""""""""""""""""""""""""""""""""""""""""""
- For GW MAC, FIB table the vpn-id will be based on the subnet-id of the floating ip

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=ext-net-id`` =>
  | GW Mac table (19) ``match: vpn-id=ext-net-id,dst-mac=dst-fip-mac set vpn-id=fip-subnet-id`` =>
  | FIB table (21) ``match: vpn-id=fip-subnet-id,dst-ip=dst-fip`` =>
  | Pre DNAT table (25) ``match: dst-ip=dst-fip set vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | DNAT table (27) ``match: vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | FIB table (21) ``match: vpn-id=router-id,dst-ip=dst-vm-ip`` =>
  | Local Next-Hop group: ``set dst-mac=dst-vm-mac,lport-tag=dst-vm-lport-tag`` =>
  | Egress table (220) output to VM port


Yang changes
---------------
odl-nat module will be enhanced with the following container
::

  container external-subnets {
    list subnets  {
      key id;
      leaf id {
         type yang:uuid;
      }
      leaf vpnid {
         type yang:uuid;
      }
      leaf-list router-ids {
         type yang:uuid;
      }
      leaf external-network-id {
         type yang:uuid;
      }
    }
  }


This model will be filled out only for flat/VLAN external network provider types.
If this model is missing, vpn-id will be taken from ``odl-nat:external-networks`` model
to maintain compatibility with BGPVPN models.

``odl-nat:ext-routers`` container will be enhanced with the list of the external subnet-ids
associated with the router.
::

  container ext-routers {
    list routers {
      key router-name;
      leaf router-name {
        type string;
      }
      ...

      leaf-list external-subnet-id {
        type yang:uuid; }
      }
    }
  }

Configuration impact
---------------------
None

Clustering considerations
-------------------------
None

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
-----------------
Carbon

Alternatives
------------
None

Usage
=====

Create external network with two subnets
------------------------------------------

::

 neutron net-create public-net -- --router:external --is-default --provider:network_type=flat
 --provider:physical_network=physnet1
 neutron subnet-create --ip_version 4 --gateway 10.64.0.1 --name public-subnet1 <public-net-uuid> 10.64.0.0/16
 -- --enable_dhcp=False
 neutron subnet-create --ip_version 4 --gateway 10.65.0.1 --name public-subnet2 <public-net-uuid> 10.65.0.0/16
 -- --enable_dhcp=False

Create internal networks with subnets
-------------------------------------------

::

 neutron net-create private-net1
 neutron subnet-create --ip_version 4 --gateway 10.0.123.1 --name private-subnet1 <private-net1-uuid>
 10.0.123.0/24
 neutron net-create private-net2
 neutron subnet-create --ip_version 4 --gateway 10.0.124.1 --name private-subnet2 <private-net2-uuid>
 10.0.124.0/24
 neutron net-create private-net3
 neutron subnet-create --ip_version 4 --gateway 10.0.125.1 --name private-subnet3 <private-net3-uuid>
 10.0.125.0/24
 neutron net-create private-net4
 neutron subnet-create --ip_version 4 --gateway 10.0.126.1 --name private-subnet4 <private-net4-uuid>
 10.0.126.0/24

Create two router instances and connect each router to one internal subnet and one external subnet
----------------------------------------------------------------------------------------------------

::

 neutron router-create router1
 neutron router-interface-add <router1-uuid> <private-subnet1-uuid>
 neutron router-gateway-set --fixed-ip subnet_id=<public-subnet1-uuid> <router1-uuid> <public-net-uuid>
 neutron router-create router2
 neutron router-interface-add <router2-uuid> <private-subnet2-uuid>
 neutron router-gateway-set --fixed-ip subnet_id=<public-subnet2-uuid> <router2-uuid> <public-net-uuid>

Create router instance connected to both external subnets and the remaining internal subnets
---------------------------------------------------------------------------------------------

::

 neutron router-create router3
 neutron router-interface-add <router3-uuid> <private-subnet3-uuid>
 neutron router-interface-add <router3-uuid> <private-subnet4-uuid>
 neutron router-gateway-set --fixed-ip subnet_id=<public-subnet1-uuid> --fixed-ip subnet_id=<public-subnet2-uuid>
 <router3-uuid> <public-net-uuid>

Create floating ips from both subnets
---------------------------------------

::

 neutron floatingip-create --subnet <public-subnet1-uuid> public-net
 neutron floatingip-create --subnet <public-subnet1-uuid> public-net
 neutron floatingip-create --subnet <public-subnet2-uuid> public-net

Create 2 VM instance in each subnet and associate with floating ips
---------------------------------------------------------------------

::

 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net1-uuid> VM1
 nova floating-ip-associate VM1 <fip1-public-subnet1>
 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net1-uuid> VM2
 nova floating-ip-associate VM2 <fip2-public-subnet1>
 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net2-uuid> VM3
 nova floating-ip-associate VM3 <fip1-public-subnet2>
 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net2-uuid> VM4
 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net3-uuid> VM5
 nova boot --image <image-id> --flavor <flavor-id> --nic net-id=<private-net4-uuid> VM6

Connectivity tests
--------------------

* Connect to the internet from all VMs. ``VM1`` and ``VM2`` will route traffic through external gateway 10.64.0.1
  ``VM3`` and ``VM4`` route traffic through external gateway 10.65.0.1.

* Connect to the internet from ``VM5`` and ``VM6``. Each connection will be routed to different external gateway
  with the corresponding subnet router-gateway ip.

* Hairpinning when source VM is associated with floating ip - ping between ``VM1`` and ``VM2`` using their floating ips.

* Hairpinning  when source VM is not associated with floating ip - ping from ``VM4`` to ``VM3`` using floating ip.
  Since ``VM4`` has no associated floating ip a NAPT entry will be allocated using the router-gateway ip.

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
N/A

CLI
---
N/A

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Yair Zinger <yair.zinger@hpe.com>

Other contributors:
  Tali Ben-Meir <tali@hpe.com>


Work Items
----------
https://trello.com/c/uDcQw95v/104-pipeline-changes-fip-w-multiple-subnets-in-ext-net-hairpinning

* Add external-subnets model
* Add vpn-instances for external flat/VLAN sunbets
* Change pipeline to prefer vpn-id from external-subnets over vpn-id from external-networks
* Add write metadata to GW MAC table for floating ip/router gw mac addresses
* Add default subnet-id match in FIB table to external subnet group entry
* Changes in remote next-hop flow for floating ip in FIB table
    - Set destination mac to floating ip mac
    - Set egress actions to provider port of the network attached to the floating ip subnet
* Resubmit SNAT + Outbound NAPT flows to FIB table

Dependencies
============

None

Testing
=======

Unit Tests
----------

Integration Tests
-----------------

CSIT
----
* Hairpinning between VMs in the same subnet
* Hairpinning between VMs in different subnets connected to the same router
* Hairpinning with NAPT - source VM is not associated with floating ip
* Traffic to external network with multiple subnets

Documentation Impact
====================
None

References
==========

[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__
