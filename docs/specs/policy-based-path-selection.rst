.. contents:: Table of Contents
      :depth: 3

=======================================================
Policy based path selection for multiple VxLAN tunnels
=======================================================

https://git.opendaylight.org/gerrit/#/q/topic:policy-based-path-selection

The purpose of this feature is to allow selection of primary and backup VxLAN tunnels for different types of VxLAN
encapsulated traffic between a pair of OVS nodes based on some predefined policy.

Egress traffic can be classified using different characteristics e.g. 5-tuple, ingress port+VLAN, service-id
to determine the best available path when multiple VxLAN endpoints are configured for the same destination.


Problem description
===================

Today, netvirt is not able to classify traffic and route it over different tunnel endpoints based on a set of
predefined characteristics. This is an essential infrastructure for applications on top of netvirt
offering premium and personalized services.

Use Cases
---------

* Forwarding of VxLAN traffic between hypervisors with multiple network cards connected to L2 switches in
  different networks.
* Forwarding of VxLAN traffic between hypervisors with multiple network cards connected to the same L2 switch.

Proposed change
===============

The current implementation of transport-zone creation generates vtep elements based on the ``local_ip``
definition in the ``other-config`` column of the Open_vSwitch schema where the ``local_ip`` value represents
the tunnel interface ip.
The format of this field will be enhanced to express the association between multiple tunnel ip
addresses and multiple underlay networks using the following format:
::

  local_ip=<tun1-ip>:<underlay1-net>,<tun2-ip>:<underlay2-net>,..,<tunN-ip>:<underlayN-net>

Upon transport-zone creation, if the ``local_ip`` configuration is formatted using the underlay network associations,
Transport-zone will be created for each configured underlay network per neutron network/router.
The transport-zone name will reflect both the neutron network/router and the underlay network name.

In addition, the underlay network name will be added to the ``odl-interface:if-tunnel`` augmentation as part of the
optional tunnel parameters.

  *Note that configuration of multiple tunnel IPs for the same DPN in the same underlay network is not a supported
  as part of this feature and requires further enhancements in both ITM and the transport-zone model.*

Policy-based path selection will be defined as a new egress tunnel service and depends on tunnel service binding
functionality detailed in [3].

The policy service will be bounded only for tunnels of type logical tunnel group defined in [2].

The service will classify different types of traffic based on a predefined set of policy rules to find the best
available path to route each type of traffic. The policy model will be agnostic to the specific topology details
including DPN ids, tunnel interface and logical interface names. The only reference from the policy model
to the list of preferred paths is made using underlay network-ids described earlier in this document.

Each policy references an ordered set of ``policy-routes``. Each ``policy-route`` can be a ``basic-route``
referencing single underlay-network or ``route-group`` composed of multiple underlay networks.
This set will get translated in each DPN to OF *fast-failover* group. The content of the buckets in each DPN depends
on the existing underlay networks configured as part of the ``local_ip`` in the specific DPN.

The order of the buckets in the *fast-failover* group depends on the order of the underlay networks in the ``policy-routes`` model.
``policy-routes`` with similar set of routes in different order will be translated to different groups.

Each bucket in the *fast-failover* group can either reference a single tunnel or an additional OF *select* group
depending on the type of policy route as detailed in the following table:

+----------------------+-------------------------+-------------------------+
|  Policy route type   |  Bucket actions         |  OF Watch type          |
+======================+=========================+=========================+
| Basic route          |  load reg6(tun-lport)   | watch_port(tun-port)    |
|                      |  resubmit(220)          |                         |
+----------------------+-------------------------+-------------------------+
| Route group          |  goto_group(select-grp) | watch_group(select-grp) |
|                      |                         |                         |
+----------------------+-------------------------+-------------------------+

This OF *select* group does not have the same content as the select groups defined in [2] and the content of its'
buckets is based on the defined ``route-group`` elements and weights.

Logical tunnel will be bounded to the policy service iff there is at least one ``policy-route`` referencing
one or more of the underlay networks in the logical group.

This service will take precedence over the default weighted LB service defined in [2] for logical tunnel group interfaces.

Policy-based path selection and weighted LB service pipeline example:

::

  cookie=0x6900000, duration=0.802s, table=220, n_packets=0, n_bytes=0, priority=6,reg6=0x500
  actions=load:0xe000500->NXM_NX_REG6[],write_metadata:0xe000500000000000/0xffffffff00000000,goto_table:230
  cookie=0x6900000, duration=0.802s, table=220, n_packets=0, n_bytes=0, priority=6,reg6=0xe000500
  actions=load:0xf000500->NXM_NX_REG6[],write_metadata:0xf000500000000000/0xffffffff00000000,group:800002
  cookie=0x8000007, duration=0.546s, table=220, n_packets=0, n_bytes=0, priority=7,reg6=0x600 actions=output:3
  cookie=0x8000007, duration=0.546s, table=220, n_packets=0, n_bytes=0, priority=7,reg6=0x700 actions=output:4
  cookie=0x8000007, duration=0.546s, table=220, n_packets=0, n_bytes=0, priority=7,reg6=0x800 actions=output:5
  cookie=0x9000007, duration=0.546s, table=230, n_packets=0, n_bytes=0,priority=7,ip,
  metadata=0x222e0/0xfffffffe,nw_dst=10.0.123.2,tp_dst=8080 actions=write_metadata:0x200/0xfffffffe,goto_table:231
  cookie=0x9000008, duration=0.546s, table=230, n_packets=0, n_bytes=0,priority=0,resubmit(,220)
  cookie=0x7000007, duration=0.546s, table=231, n_packets=0, n_bytes=0,priority=7,metadata=0x200/0xfffffffe,
  actions=group:800000
  cookie=0x9000008, duration=0.546s, table=231, n_packets=0, n_bytes=0,priority=0,resubmit(,220)
  group_id=800000,type=ff,
  bucket=weight:0,watch_group=800001,actions=group=800001,
  bucket=weight:0,watch_port=5,actions=load:0x800->NXM_NX_REG6[],resubmit(,220)
  group_id=800001,type=select,
  bucket=weight:50,watch_port=3,actions=load:0x600->NXM_NX_REG6[],resubmit(,220),
  bucket=weight:50,watch_port=4,actions=load:0x700->NXM_NX_REG6[],resubmit(,220),
  group_id=800002,type=select,
  bucket=weight:50,watch_port=3,actions=load:0x600->NXM_NX_REG6[],resubmit(,220),
  bucket=weight:25,watch_port=4,actions=load:0x700->NXM_NX_REG6[],resubmit(,220),
  bucket=weight:25,watch_port=5,actions=load:0x800->NXM_NX_REG6[],resubmit(,220)

Each bucket in the *fast-failover* group will set the ``watch_port`` or ``watch_group`` property to monitor the
liveness of the OF port in case of ``basic-route`` and underlay group in case of ``route-group``.
This will allow the OVS to route egress traffic only to the first live bucket in each *fast-failover* group.

The policy model rules will be based on IETF ACL data model [4]. The following enhancements are proposed for
this model to support policy-based path selection:

+-----------------+-------------------+--------------------+-------------------------------+-------------------------+
|                 |     Name          | Attributes         | Description                   | OF implementation       |
+=================+===================+====================+===============================+=========================+
| **ACE matches** | ingress-interface | name               | Policy match based on the     | Match lport-tag         |
|                 |                   +--------------------+ ingress port and optionally   + metadata bits           |
|                 |                   | vlan-id            | the VLAN id                   |                         |
|                 +-------------------+--------------------+-------------------------------+-------------------------+
|                 | service           | service-type       | Policy match based on the     | Match service/vrf-id    |
|                 |                   +--------------------+ service-id of L2VPN/L3VPN     | metadata bits depending |
|                 |                   | service-id         | e.g. elan-tag/vpn-id          | on the service-type     |
+-----------------+-------------------+--------------------+-------------------------------+-------------------------+
| **ACE actions** | metadata          | policy-classifier  | Set ingress/egress classifier | Set policy classifier   |
|                 |                   +--------------------+ that can be later used for    + in the metadata service |
|                 |                   | direction          | policy routing etc.           | bits                    |
|                 |                   |                    | Only the egress classifier    |                         |
|                 |                   |                    | will be used in this feature  |                         |
+-----------------+-------------------+--------------------+-------------------------------+-------------------------+

To enable matching on previous services in the pipeline e.g. L2/L3VPN, the egress service binding for tunnel interfaces
will be changed to preserve the metadata of preceding services rather than override it as done in the current
implementation.

Each ``policy-classifier`` will be associated with ``policy-route``. The same route can be shared by multiple classifiers.

The policy service will also maintain counters on number of policy rules assigned to underlay network per dpn
in the operational DS.

Pipeline changes
----------------

* The following new tables will be added to support the policy-based path selection service:

+--------------------------------+--------------------+-----------------------+
|  Table Name                    |  Matches           |  Actions              |
+================================+====================+=======================+
| Policy classifier table (230)  |  ACE matches       | ACE policy actions:   |
|                                |                    | set policy-classifier |
+--------------------------------+--------------------+-----------------------+
| Policy routing table (231)     |  match             | set FF group-id       |
|                                |  policy-classifier |                       |
+--------------------------------+--------------------+-----------------------+

* Each Access List Entry (ACE) composed of standard and/or policy matches and policy actions will be translated
  to a flow in the policy classifier table.

  Each policy-classifier name will be allocated with id from a new pool - POLICY_SERVICE_POOL.
  Once a policy classifier has been determined for a given ACE match, the classifier-id will be set in the ``service``
  bits of the metadata.

* Classified traffic will be sent from the policy classifier table to the policy routing table where the classifier-id
  will be matched to select the preferred tunnel using OF *fast-failover* group. Multiple classifiers can point to a
  single group.

* The default flow in the policy tables will resubmit traffic with no predefined policy/set of routes back to the
  egress dispatcher table where in order to continue processing in the next bounded egress service.

* For all the examples below it is assumed that a logical tunnel group was configured for both ingress and egress DPNs.
  The logical tunnel group is composed of { ``tun1``, ``tun2``, ``tun3`` } and bound to a policy service.


Traffic between VMs on the same DPN
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
No pipeline changes required

L3 traffic between VMs on different DPNs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

VM originating the traffic (**Ingress DPN**):
"""""""""""""""""""""""""""""""""""""""""""""
- Remote next hop group in the FIB table references the logical tunnel group.
- Policy service on the logical group selects the egress interface by classifying the traffic e.g. based on
  destination ip and port.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id,dst-ip=vm2-ip set dst-mac=vm2-mac tun-id=vm2-label reg6=logical-tun-lport-tag`` =>
  | Egress table (220) ``match: reg6=logical-tun-lport-tag`` =>
  | Policy classifier table (230) ``match: vpn-id=router-id,dst-ip=vm2-ip,dst-tcp-port=8080 set egress-classifier=clf1`` =>
  | Egress policy indirection table (2312) ``match: egress-classifier=clf1`` =>
  | Logical tunnel tun1 FF group ``set reg6=tun1-lport-tag`` =>
  | Egress table (220) ``match: reg6=tun1-lport-tag`` output to ``tun1``


VM receiving the traffic (**Ingress DPN**):
"""""""""""""""""""""""""""""""""""""""""""
- No pipeline changes required

  | Classifier table (0) =>
  | Internal tunnel Table (36) ``match:tun-id=vm2-label`` =>
  | Local Next-Hop group: ``set dst-mac=vm2-mac,reg6=vm2-lport-tag`` =>
  | Egress table (220) ``match: reg6=vm2-lport-tag`` output to VM 2


SNAT traffic from non-NAPT switch
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

VM originating the traffic is non-NAPT switch:
"""""""""""""""""""""""""""""""""""""""""""""""
- NAPT group references the logical tunnel group.
- Policy service on the logical group selects the egress interface by classifying the traffic based on
  the L3VPN service id.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) ``match: vpn-id=router-id,dst-mac=router-interface-mac`` =>
  | FIB table (21) ``match: vpn-id=router-id`` =>
  | Pre SNAT table (26) ``match: vpn-id=router-id`` =>
  | NAPT Group ``set tun-id=router-id reg6=logical-tun-lport-tag`` =>
  | Egress table (220) ``match: reg6=logical-tun-lport-tag`` =>
  | Policy classifier table (230) ``match: vpn-id=router-id set egress-classifier=clf2`` =>
  | Policy routing table (231) ``match: egress-classifier=clf2`` =>
  | Logical tunnel tun2 FF group ``set reg6=tun2-lport-tag`` =>
  | Egress table (220) ``match: reg6=tun2-lport-tag`` output to ``tun2``

Traffic from NAPT switch punted to controller:
"""""""""""""""""""""""""""""""""""""""""""""""
- No explicit pipeline changes required

  | Classifier table (0) =>
  | Internal tunnel Table (36) ``match:tun-id=router-id`` =>
  | Outbound NAPT table (46) ``set vpn-id=router-id, punt-to-controller``

L2 unicast traffic between VMs in different DPNs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

VM originating the traffic (**Ingress DPN**):
"""""""""""""""""""""""""""""""""""""""""""""
- ELAN DMAC table references the logical tunnel group
- Policy service on the logical group selects the egress interface by classifying the traffic based on
  the ingress port.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) =>
  | Dispatcher table (17) ``l2vpn service: set elan-tag=vxlan-net-tag`` =>
  | ELAN base table (48) =>
  | ELAN SMAC table (50) ``match: elan-tag=vxlan-net-tag,src-mac=vm1-mac`` =>
  | ELAN DMAC table (51) ``match: elan-tag=vxlan-net-tag,dst-mac=vm2-mac set tun-id=vm2-lport-tag reg6=logical-tun-lport-tag`` =>
  | Egress table (220) ``match: reg6=logical-tun-lport-tag`` =>
  | Policy classifier table (230) ``match: lport-tag=vm1-lport-tag set egress-classifier=clf3`` =>
  | Policy routing table (231) ``match: egress-classifier=clf3`` =>
  | Logical tunnel tun1 FF group ``set reg6=tun1-lport-tag`` =>
  | Egress table (220) ``match: reg6=tun1-lport-tag`` output to ``tun1``

VM receiving the traffic (**Ingress DPN**):
"""""""""""""""""""""""""""""""""""""""""""
- No explicit pipeline changes required

  | Classifier table (0) =>
  | Internal tunnel Table (36) ``match:tun-id=vm2-lport-tag set reg6=vm2-lport-tag`` =>
  | Egress table (220) ``match: reg6=vm2-lport-tag`` output to VM 2

L2 unicast traffic between VMs in different DPNs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

VM originating the traffic (**Ingress DPN**):
"""""""""""""""""""""""""""""""""""""""""""""
- ELAN DMAC table references the logical tunnel group
- Policy service on the logical group selects the egress interface by classifying the traffic based on
  the ingress port.

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) =>
  | Dispatcher table (17) ``l2vpn service: set elan-tag=vxlan-net-tag`` =>
  | ELAN base table (48) =>
  | ELAN SMAC table (50) ``match: elan-tag=vxlan-net-tag,src-mac=vm1-mac`` =>
  | ELAN DMAC table (51) ``match: elan-tag=vxlan-net-tag,dst-mac=vm2-mac set tun-id=vm2-lport-tag reg6=logical-tun-lport-tag`` =>
  | Egress table (220) ``match: reg6=logical-tun-lport-tag`` =>
  | Policy classifier table (230) ``match: lport-tag=vm1-lport-tag set egress-classifier=clf3`` =>
  | Policy routing table (231) ``match: egress-classifier=clf3`` =>
  | Logical tunnel tun1 FF group ``set reg6=tun1-lport-tag`` =>
  | Egress table (220) ``match: reg6=tun1-lport-tag`` output to ``tun1``

VM receiving the traffic (**Ingress DPN**):
"""""""""""""""""""""""""""""""""""""""""""
- No explicit pipeline changes required

  | Classifier table (0) =>
  | Internal tunnel Table (36) ``match:tun-id=vm2-lport-tag set reg6=vm2-lport-tag`` =>
  | Egress table (220) ``match: reg6=vm2-lport-tag`` output to VM 2


L2 multicast traffic between VMs in different DPNs with undefined policy
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

VM originating the traffic (**Ingress DPN**):
"""""""""""""""""""""""""""""""""""""""""""""
- ELAN broadcast group references the logical tunnel group.
- Policy service on the logical group has no classification for this type of traffic. Fallback to the default
  logical tunnel service - weighted LB [2].

  | Classifier table (0) =>
  | Dispatcher table (17) ``l3vpn service: set vpn-id=router-id`` =>
  | GW Mac table (19) =>
  | Dispatcher table (17) ``l2vpn service: set elan-tag=vxlan-net-tag`` =>
  | ELAN base table (48) =>
  | ELAN SMAC table (50) ``match: elan-tag=vxlan-net-tag,src-mac=vm1-mac`` =>
  | ELAN DMAC table (51) =>
  | ELAN DMAC table (52) ``match: elan-tag=vxlan-net-tag`` =>
  | ELAN BC group ``goto_group=elan-local-group, set tun-id=vxlan-net-tag reg6=logical-tun-lport-tag`` =>
  | Egress table (220) ``match: reg6=logical-tun-lport-tag set reg6=default-egress-service&logical-tun-lport-tag`` =>
  | Policy classifier table (230) =>
  | Egress table (220) ``match: reg6=default-egress-service&logical-tun-lport-tag`` =>
  | Logical tunnel LB select group ``set reg6=tun2-lport-tag`` =>
  | Egress table (220) ``match: reg6=tun2-lport-tag`` output to ``tun2``

VM receiving the traffic (**Ingress DPN**):
"""""""""""""""""""""""""""""""""""""""""""
- No explicit pipeline changes required

  | Classifier table (0) =>
  | Internal tunnel Table (36) ``match:tun-id=vxlan-net-tag`` =>
  | ELAN local BC group ``set tun-id=vm2-lport-tag`` =>
  | ELAN filter equal table (55) ``match: tun-id=vm2-lport-tag set reg6=vm2-lport-tag`` =>
  | Egress table (220) ``match: reg6=vm2-lport-tag`` output to VM 2


Yang changes
------------
The following yang modules will be added to support policy-based routing:

Policy Service Yang
^^^^^^^^^^^^^^^^^^^^
``policy-service.yang`` define policy profiles and add augmentations on top of
``ietf-access-control-list:access-lists`` to apply policy classifications on access control entries.
::

  module policy-service {
      yang-version 1;
      namespace "urn:opendaylight:netvirt:policy";
      prefix "policy";

      import ietf-interfaces {
        prefix if;
      }

      import ietf-access-control-list {
          prefix ietf-acl;
      }

      import aclservice {
          prefix acl;
      }

      import yang-ext {
          prefix ext;
      }

      import opendaylight-l2-types {
            prefix ethertype;
            revision-date "2013-08-27";
      }

      description
          "Policy Service module";

      revision "2017-02-07" {
          description
              "Initial revision";
      }

      identity policy-acl {
          base ietf-acl:acl-base;
     }

     augment "/ietf-acl:access-lists/ietf-acl:acl/"
        + "ietf-acl:access-list-entries/ietf-acl:ace/ietf-acl:matches" {
         ext:augment-identifier "ingress-interface";
	     leaf name {
	         type if:interface-ref;
	     }

	     leaf vlan-id {
	         type ethertype:vlan-id;
	     }
     }

     augment "/ietf-acl:access-lists/ietf-acl:acl/"
        + "ietf-acl:access-list-entries/ietf-acl:ace/ietf-acl:matches" {
         ext:augment-identifier "service";
         leaf service-type {
             type identityref {
                  base service-type-base;
             }
         }

         leaf service-id {
             type string;
         }
     }

     augment "/ietf-acl:access-lists/ietf-acl:acl/"
        + "ietf-acl:access-list-entries/ietf-acl:ace/ietf-acl:actions" {
         ext:augment-identifier "set-policy-classifier";
         leaf policy-classifier {
              type leafref {
                   path "/policy-profiles/policy-profile/policy-classifier";
              }
         }

         leaf direction {
              type identityref {
                   base acl:direction-base;
              }
         }
     }

     container underlay-networks {
         list underlay-network {
              key "network-name";
              leaf network-name {
                   type string;
              }

              leaf network-access-type {
                   type identityref {
                       base access-network-base;
                   }
              }

              leaf bandwidth {
                   type uint64;
                   description "Maximum bandwidth. Units in byte per second";
              }
         }
     }

     container underlay-network-groups {
          list underlay-network-group {
              key "group-name";
              leaf group-name {
                   type string;
              }

              list underlay-network {
                  key "network-name";
                  leaf network-name {
                       type leafref {
                            path "/underlay-networks/underlay-network/network-name";
                       }
                  }

                  leaf weight {
                       type uint16;
                       default 1;
                  }
             }

             leaf bandwidth {
                  type uint64;
                  description "Maximum bandwidth of the group. Units in byte per second";
             }
         }
     }

     container policy-profiles {
         list policy-profile {
             key "policy-classifier";
             leaf policy-classifier {
                  type string;
             }

             list policy-route {
                  key "route-name";
                  leaf route-name {
                       type string;
                  }

                  choice route {
                       case basic-route {
                            leaf network-name {
                                  type leafref {
                                       path "/underlay-networks/underlay-network/network-name";
                                  }
                            }
                       }

                       case route-group {
                            leaf group-name {
                                 type leafref {
                                      path "/underlay-network-groups/underlay-network-group/group-name";
                                 }
                            }
                       }
                  }
             }
         }
     }

     container policy-route-counters {
         config false;

         list underlay-network-counters {
             key "network-name";
             leaf network-name {
                 type leafref {
                      path "/underlay-networks/underlay-network/network-name";
                 }
             }

             list dpn-counters {
                 key "dp-id";
                 leaf dp-id {
                     type uint64;
                 }

                 leaf counter {
                     type uint32;
                 }
            }

            list path-counters {
                 key "source-dp-id destination-dp-id";
                 leaf source-dp-id {
                     type uint64;
                 }

                 leaf destination-dp-id {
                     type uint64;
                 }

                 leaf counter {
                     type uint32;
                 }
            }
         }
     }

     identity service-type-base {
         description "Base identity for service type";
     }

     identity l3vpn-service-type {
         base service-type-base;
     }

     identity l2vpn-service-type {
         base service-type-base;
     }

     identity access-network-base {
         description "Base identity for access network type";
     }

     identity mpls-access-network {
         base access-network-base;
     }

     identity docsis-access-network {
         base access-network-base;
     }

     identity pon-access-network {
         base access-network-base;
     }

     identity dsl-access-network {
         base access-network-base;
     }

     identity umts-access-network {
         base access-network-base;
     }

     identity lte-access-network {
         base access-network-base;
     }
  }

Policy service tree view
"""""""""""""""""""""""""
::

 module: policy-service
    +--rw underlay-networks
    |  +--rw underlay-network* [network-name]
    |     +--rw network-name           string
    |     +--rw network-access-type?   identityref
    |     +--rw bandwidth?             uint64
    +--rw underlay-network-groups
    |  +--rw underlay-network-group* [group-name]
    |     +--rw group-name          string
    |     +--rw underlay-network* [network-name]
    |     |  +--rw network-name    -> /underlay-networks/underlay-network/network-name
    |     |  +--rw weight?         uint16
    |     +--rw bandwidth?          uint64
    +--rw policy-profiles
    |  +--rw policy-profile* [policy-classifier]
    |     +--rw policy-classifier    string
    |     +--rw policy-route* [route-name]
    |        +--rw route-name      string
    |        +--rw (route)?
    |           +--:(basic-route)
    |           |  +--rw network-name?   -> /underlay-networks/underlay-network/network-name
    |           +--:(route-group)
    |              +--rw group-name?     -> /underlay-network-groups/underlay-network-group/group-name
    +--ro policy-route-counters
       +--ro underlay-network-counters* [network-name]
          +--ro network-name     -> /underlay-networks/underlay-network/network-name
          +--ro dpn-counters* [dp-id]
          |  +--ro dp-id      uint64
          |  +--ro counter?   uint32
          +--ro path-counters* [source-dp-id destination-dp-id]
             +--ro source-dp-id         uint64
             +--ro destination-dp-id    uint64
             +--ro counter?             uint32
  augment /ietf-acl:access-lists/ietf-acl:acl/ietf-acl:access-list-entries/ietf-acl:ace/ietf-acl:matches:
    +--rw name?      if:interface-ref
    +--rw vlan-id?   ethertype:vlan-id
  augment /ietf-acl:access-lists/ietf-acl:acl/ietf-acl:access-list-entries/ietf-acl:ace/ietf-acl:matches:
    +--rw service-type?   identityref
    +--rw service-id?     string
  augment /ietf-acl:access-lists/ietf-acl:acl/ietf-acl:access-list-entries/ietf-acl:ace/ietf-acl:actions:
    +--rw policy-classifier?   -> /policy-profiles/policy-profile/policy-classifier
    +--rw direction?           identityref



Configuration impact
---------------------
As detailed above, ``local_ip`` parameter format has been extended to support multiple ip:network associations.
Compatibility with the current format will be maintained.

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

Features to Install
-------------------
odl-netvirt-openstack

REST API
--------
**Sample JSON data**

Create policy rule
^^^^^^^^^^^^^^^^^^^
**URL:** restconf/config/ietf-access-control-list:access-lists

The following REST will create rule to classify all http traffic to ports 8080-8181 from specific vpn-id
::

  {
    "access-lists": {
          "acl": [
            {
              "acl-type": "policy-service:policy-acl",
              "acl-name": "http-policy",
              "access-list-entries": {
                "ace": [
                  {
                    "rule-name": "http-ports",
                    "matches": {
                      "protocol": 6,
                      "destination-port-range": {
                        "lower-port": 8080,
                        "upper-port": 8181
                      },
                      "policy-service:service-type": "l3vpn",
                      "policy-service:service-id": "71f7eb47-59bc-4760-8150-e5e408d2ba10"
                    },
                    "actions": {
                      "policy-service:policy-classifier" : "classifier1",
                      "policy-service:direction" : "egress"
                    }
                  }
                ]
              }
            }
          ]
        }
     }
   }

Create underlay networks
^^^^^^^^^^^^^^^^^^^^^^^^^
**URL:** restconf/config/policy-service:underlay-networks

The following REST will create multiple underlay networks with different access types
::

    {
      "underlay-networks": {
        "underlay-network": [
          {
            "network-name": "MPLS",
            "network-access-type": "policy-service:mpls-access-network"
          },
          {
            "network-name": "DLS1",
            "network-access-type": "policy-service:dsl-access-network"
          },
          {
            "network-name": "DSL2",
            "network-access-type": "policy-service:dsl-access-network"
          }
        ]
      }
    }

Create underlay group
^^^^^^^^^^^^^^^^^^^^^^
**URL:** restconf/config/policy-service:underlay-network-groups

The following REST will create group for the DSL underlay networks
::

    {
      "underlay-network-groups": {
        "underlay-network-group": [
          {
            "group-name": "DSL",
            "underlay-network": [
              {
                "network-name": "DSL1",
                "weight": 75
              },
              {
                "network-name": "DSL2",
                "weight": 25
              }
            ]
          }
        ]
      }
    }

Create policy profile
^^^^^^^^^^^^^^^^^^^^^^
**URL:** restconf/config/policy-service:policy-profiles

The following REST will create profile for classifier1 with multiple policy-routes
::

    {
      "policy-profiles": {
        "policy-profile": [
          {
            "policy-classifier": "classifier1",
            "policy-route": [
              {
                "route-name": "primary",
                "network-name": "MPLS"
              },
              {
                "route-name": "backup",
                "group-name": "DSL"
              }
            ]
          }
        ]
      }
    }

CLI
---
None

Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Tali Ben-Meir <tali@hpe.com>

Other contributors:
  TBD


Work Items
----------

Trello card: https://trello.com/c/Uk3yrjUG/25-multiple-vxlan-endpoints-for-compute

* Transport-zone creation for multiple tunnels based on underlay network definitions
* Extract ACL flow programming to common location so it can be used by the policy service
* Create policy OF groups based on underlay network/group definitions
* Create policy classifier table based on ACL rules
* Create policy routing table
* Bind policy service to logical tunnels
* Maintain policy-route-counters per dpn/dpn-path

Dependencies
============
None

Testing
=======

Unit Tests
----------

Integration Tests
-----------------
The test plan defined for CSIT below could be reused for integration tests.

CSIT
----
Adding multiple ports to the CSIT setups is challenging due to rackspace limitations.
As a result, the test plan defined for this feature uses white-box methodology and not verifying actual traffic was
sent over the tunnels.


Policy routing with single tunnel per access network type
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
* Set ``local_ip`` to contain tep ips for networks ``underlay1`` and ``underlay2``
* Each underlay network will be defined with different ``access-network-type``
* Create the following policy profiles

  * Profile1: ``policy-classifier=clf1, policy-routes=underlay1, underlay2``
  * Profile2: ``policy-classifier=clf2, policy-routes=underlay2, underlay1``

* Create the following policy rules

  * Policy rule 1: ``dst_ip=vm2_ip,dst_port=8080 set_policy_classifier=clf1``
  * Policy rule 2: ``src_ip=vm1_ip set_policy_classifier=clf2``
  * Policy rule 3: ``service_type=l2vpn service-id=elan-tag set_policy_classifier=clf1``
  * Policy rule 4: ``service_type=l3vpn service-id=vpn-id set_policy_classifier=clf2``
  * Policy rule 5: ``ingress-port=vm3_port set_policy_classifier=clf1``
  * Policy rule 6: ``ingress-port=vm4_port vlan=vlan-id set_policy_classifier=clf2``

* Verify policy service flows/groups for all policy rules
* Verify flows/groups removal after the profiles were deleted

Policy routing with multiple tunnels per access network type
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
* Set ``local_ip`` to contain tep ips for networks ``underlay1``..``underlay4``
* ``underlay1``, ``underlay2`` and ``underlay3``, ``underlay4`` are from the same ``access-network-type``
* Create the following policy profiles where each route can be either group or basic route

  * Profile1: ``policy-classifier=clf1, policy-routes={underlay1, underlay2}, {underlay3,underlay4}``
  * Profile2: ``policy-classifier=clf2, policy-routes={underlay3,underlay4}, {underlay1, underlay2}``
  * Profile3: ``policy-classifier=clf3, policy-routes=underlay1, {underlay3,underlay4}``
  * Profile4: ``policy-classifier=clf4, policy-routes={underlay1, underlay2}, underlay3``
  * Profile5: ``policy-classifier=clf5, policy-routes={underlay1, underlay2}``
  * Profile6: ``policy-classifier=clf6, policy-routes=underlay4``

* Create the following policy rules

  * Policy rule 1: ``dst_ip=vm2_ip,dst_port=8080 set_policy_classifier=clf1``
  * Policy rule 2: ``src_ip=vm1_ip set_policy_classifier=clf2``
  * Policy rule 3: ``service_type=l2vpn service-id=elan-tag set_policy_classifier=clf3``
  * Policy rule 4: ``service_type=l3vpn service-id=vpn-id set_policy_classifier=clf4``
  * Policy rule 5: ``ingress-port=vm3_port set_policy_classifier=clf5``
  * Policy rule 6: ``ingress-port=vm4_port vlan=vlan-id set_policy_classifier=clf6``

* Verify policy service flows/groups for all policy rules
* Verify flows/groups removal after the profiles were deleted

Documentation Impact
====================
Netvirt documentation needs to be updated with description and examples of policy service configuration

References
==========
[1] `OpenDaylight Documentation Guide <http://docs.opendaylight.org/en/latest/documentation.html>`__

[2] `Load balancing and high availability of multiple VxLAN tunnels <https://git.opendaylight.org/gerrit/#/c/50779>`__

[3] `Service Binding On Tunnels <https://git.opendaylight.org/gerrit/#/c/51270>`__

[4] `Network Access Control List (ACL) YANG Data Model <https://tools.ietf.org/html/draft-ietf-netmod-acl-model-09>`__
