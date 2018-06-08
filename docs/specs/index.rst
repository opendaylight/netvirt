NetVirt Design Specifications
=============================
Starting from Carbon, NetVirt uses an RST format Design Specification document
for all new features. These specifications are a perfect way to understand
various NetVirt features.

Contents:

.. toctree::
   :maxdepth: 1

   Design Specification Template <specs-template>
   ACLs - ACL Statistics <acl-stats>
   ACLs - Remote ACL - Indirection Table to Improve Scale <remote_acl_indirection>
   ACLs - ACL reflection on existing traffic <acl-reflection-on-existing-traffic>
   ACLs - Support for protocols that are not supported by conntrack <acl-non-conntrack>
   Coe Netvirt Integration <coe-integration.rst>
   Conntrack Based SNAT <conntrack-based-snat>
   Cross site connectivity with Federation service <federation-plugin>
   DHCP Server with Dynamic Allocation Pool <dhcp-dynamic-allocation-pool>
   Discovery of directly connected PNFs in Flat/VLAN provider networks <discovery_of_directly_connected_pnfs>
   ECMP Support for BGP based L3VPN <ecmp-bgp-l3vpn>
   ELAN Service Recovery Test Plan <service-recovery-elan>
   Element Counters <element-counters>
   Hairpinning of floating IPs in flat/VLAN provider networks <hairpinning-flat-vlan>
   IPv6 Data Center to internet connectivity using L3VPN <ipv6-l3vpn-internet>
   IPv6 Inter Data Center connectivity using L3VPN <ipv6-interdc-l3vpn>
   IPv6 L3 North-South support for Flat/VLAN based Provider Networks <ipv6-cvr-north-south>
   L3VPN Dual Stack for VMs <l3vpn-dual-stack-vms>
   Listener Dependency Helper, avoids waiting for dependent IID <listener-dependency-helper>
   Migrate the SFC classifier from the old to the new netvirt <new-sfc-classifier>
   Netvirt counters <netvirt-statistics-spec>
   Neutron Port Allocation For DHCP Service <neutron-port-for-dhcp-service>
   Policy based path selection for multiple VxLAN tunnels <policy-based-path-selection>
   QoS Alert <qos-alert>
   Quality of Service <qos>
   Setup Source-MAC-Address for routed packets to virtual endpoints <setup-smac-for-routed-packets-to-virt-endpoints>
   SR-IOV Hardware Offload for OVS <sriov-hardware-offload>
   Support for TCP MD5 Signature Option configuration of Quagga BGP <qbgp-tcp-md5-signature-option>
   Support of VXLAN based L2 connectivity across Datacenters <l2vpn-over-vxlan-with-evpn-rt2>
   Support of VXLAN based connectivity across Datacenters <l3vpn-over-vxlan-with-evpn-rt5>
   Temporary SMAC Learning <temporary-smac-learning>
   VLAN provider network enhancement <vlan-provider-enhancement>
   VNI based L2 switching, L3 forwarding and NATing <vni-based-l2-switching-l3-forwarding-and-NATing>
   Weighted NAPT Selection <weighted-napt-selection>
   Support for compute node scale in and scale out functionality <compute-node-scalein-and-scaleout>
   Faster DC-GW Failure Detection <faster-dcgw-failure-detection>
