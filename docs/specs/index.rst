NetVirt Design Specifications
=============================
Starting from Carbon, NetVirt uses an RST format Design Specification document
for all new features. These specifications are a perfect way to understand
various NetVirt features.

Contents:

.. toctree::
   :maxdepth: 1

   Design Specification Template <specs-template>
   ACL Remote ACLs - Indirection Table to Improve Scale <remote_acl_indirection>
   IPv6 Inter Data Center connectivity using L3VPN <ipv6-interdc-l3vpn>
   ACL Statistics <acl-stats>
   Conntrack Based SNAT <conntrack-based-snat>
   Cross site connectivity with federation service <federation-plugin>
   Discovery of directly connected PNFs in Flat/VLAN provider networks <directly_connected_pnf_discovery>
   ECMP Support for BGP based L3VPN <ecmp-bgp-l3vpn>
   Hairpinning of floating IPs in flat/VLAN provider networks <hairpinning-flat-vlan>
   IPv6 L3 North-South support for Flat/VLAN based Provider Networks <ipv6-cvr-north-south>
   Quality of Service <qos>
   Setup Source-MAC-Address for routed packets to virtual endpoints <setup-smac-for-routed-packets-to-virt-endpoints>
   Support of VXLAN based connectivity across Datacenters <l3vpn-over-vxlan-with-evpn-rt5>
   Temporary SMAC Learning <temporary-smac-learning>
   VNI based L2 switching, L3 forwarding and NATing <vni-based-l2-switching-l3-forwarding-and-NATing>
   Netvirt counters <netvirt-statistics-spec>
   Policy based path selection for multiple VxLAN tunnels <policy-based-path-selection>
   QoS Alert <qos-alert>
   DHCP Server with dynamic allocation pool <dhcp-dynamic-allocation-pool>
   Listener Dependency Helper, avoids waiting for dependent IID <listener-dependency-helper>
   Element counters <element-counters>
   Migrate the SFC classifier from the old to the new netvirt <new-sfc-classifier>
