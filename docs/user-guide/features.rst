===============
Carbon Features
===============
.. contents:: :depth: 2

.. csv-table:: Carbon Features
   :header: Major Feature, Sub Feature, sub-category, Status, Comments, Links

   Security Groups,conntrack,,Full,ovs 2.6 needed for dpdk-conntrack
   ,learn,,Full,alternative for dpdk instead of using conntarck
   ,transparent,,Full,no-ops security groups
   ,allowed_address_pairs,,Full,
   ,,,,
   L2,east-west,vlan provider,Full,
   ,,flat provider,Full,
   ,,vxlan provider,Full,
   ,north-south (external n/w),vlan provider,Full,no BGP
   ,,flat provider,Full,
   ,,vxlan provider,WIP,using EVPN RT2 and BGP
   ,vlan aware vms,vxlan provider,Full,
   ,vlan transparency,vxlan provider,Full,None with hwvtep
   ,MAC learning,"vlan, vxlan provider",Full,
   ,,,,
   ARP,ARP supression for Neutron Router,,Full,
   ,distributed ARP responses for floating IPs,vlan provider,Full,
   ,,,,
   L3 - IPV4,east-west,vlan provider,None,
   ,,vxlan provider,Full,DVR
   ,, gre provider,None,
   ,east-west with ECMP,vxlan provider,Full,
   ,north-south (external n/w),vlan provider,Full,no BGP
   ,,vxlan provider,Full,with EVPN RT5
   ,,gre provider,None,"needs OVS patch, implemented using BGP-MPLS"
   ,floating-ip,"vlan, vxlan, gre provider",Full,gre needs OVS patch
   ,snat - centralized-Controller,"vlan, vxlan, gre provider",Full,"Using controller gre needs ovs patch
   limited to TCP and UDP.
   NAPT switches are selected from the switches having the port in the router subnet.
   Failover happens when there is no port in the swtich on the router subnet or when the switch is down."
   ,snat - centralized- Conntrack,"vlan, vxlan, gre provider",Full,"Used OVS conntrack feature.
   Works with TCP UDP and ICMP.
   The NAPT switch selection is based on weighted round robin and a pseudo port of each router subnet is added to the switch.
   Failover happens only if switch is down."
   ,Extra routes,vxlan provider,Full,
   ,Invisible IP learning,vxlan provider,Full,"Uses controller generated ARPs, subnet router feature"
   ,,,,
   Transport,auto-bridge creation,,Full,br-int
   ,auto-tunnel creation,,Full,vxlan tunnels for neutron networks
   ,pre-configured full mesh of tunnels between vswitches,,Full,
   ,VxLAN tunnels with IPv6 endpoints (requires OVS 2.6),,,
   ,,,,
   DHCP,Neutron-based,"IPv4, IPv6",Full,
   ,Controller-based,IPv4,Full,Missing metadata support if neutron qdhcp is not used.
   ,Metadata Server,Neutron-based,Full,Requires neutron qdhcp to act as metadata proxy.
   ,,,,
   IPv6 control,IPv6 IP SLAAC,"vlan, vxlan",Full,Detailed status: https://docs.google.com/presentation/d/1dDHciJcPtCGrFQYYSNGbefO60uKD9hgdQFvZ0uu-aRQ/edit#slide=id.p
   ,IPv6 RAs,"vlan, vxlan",Full,
   ,,,,
   IPV6 forwarding,,,,
   ,"Security Groups, allowed address pairs",,Full,
   IPv6 L2,east-west,vlan provider,,
   ,,flat provider,,
   ,,vxlan provider,Full,
   ,north-south (external n/w),vlan provider,?,no BGP
   ,,flat provider,?,
   ,,vxlan provider,,
   ,north-south VPN connectivity,vlan provider,,
   ,,vxlan provider,,
   ,,gre provider,,
   ,vlan aware vms,vxlan provider,Full,"no CSIT yet, so should we claim supported?"
   ,vlan transparency,vxlan provider,Full,None with hwvtep
   ,MAC learning,"vlan, vxlan provider",Full,
   ,,,,
   ND,ND supression for Neutron Router,,WIP,
   IPv6 L3,east-west,vlan provider,?,
   ,,vxlan provider,Full,DVR
   ,, gre provider,,
   ,north-south (external n/w),vlan provider,Full,no BGP
   ,,vxlan provider,,
   ,,gre provider,WIP,"needs OVS patch, implemented using BGP-MPLS"
   ,north-south VPN connectivity,vlan provider,,
   ,,vxlan provider,,
   ,,gre provider,Full,"needs OVS patch, implemented using BGP-MPLS"
   ,Extra routes,"vxlan, vlan? provider",,
   ,Invisible IP learning,"vxlan, vlan? provider",,
   ,,,,
   L2GW/HWVTEP,L2 connectivity with PNF/baremetal,,Full,Vlan transparency not supported
   ,SR-IOV with multi-provider extension,,Full,upstream nova bug for multi-provider externsion fixed in pike
   ,L3,,None,target for nitrogen
   ,HA,"active/active, active/backup",Full,
   ,DHCP service for SR-IOV endpts,,Full,using a designated OVS for ODL DHCP
   ,,,,
   VM Migration,all features,,Full,
   ,,,,
   QoS,rate limiting,,Full,
   ,DSCP marking,,Full,
   ,meters,,Full,needs OVS 2.7?
   ,supported rule types,,None,
   ,minimum bandwidth rule,,None,
   ,,,,
   Federation,L2 Connectivity between OpenStack sites,,Full,
   ,L3 Connectivity between OpenStack sites,,Full,
   ,"ACLs federation, micro-segmentation between sites",,Full,
   ,,,,
   Statistics,REST based statistics for neutron elements,,Full,
   ,Element to Element counters,,Full,
   ,,,,
   Neutron APIs,network,,Full,
   ,subnet,,Full,
   ,port,,Full,
   ,router,,Full,
   ,security-groups,,Full,
   ,floating-ips,,Full,
   ,external-networks,,Full,
   ,provider-network,,Full,
   ,allowed address pairs,,Full,
   ,L2GW,,Full,v2 driver added in Ocata
   ,bgpvpn,,Full,v2 driver added in Ocata
   ,vlan aware vms,,Ocata only,Ocata only
   ,extra routes,,Full,
   ,QoS,,Full,
   ,"pseudo-agent binding, host config",,Full,"Default Ocata, supported in Newton"
   ,,,,
   ETREE,,,WIP,
   ,,,,
   DPDK,,,Full,conn-track based SNAT not supported until ovs 2.8
   ,,,,
   SFC classifier,Datapath,NSH based,WIP,
   ,,non-NSH based,WIP,
   ,Northbound,openstack-sfc,WIP,
   ,,ODL SFC models,WIP,
   ,,,,
   LBaaS,Octavia implementation,,Partial,"L2 supported, L3 Partially"
   ,ODL implementation,,Future,
   ,,,,
   PNFs,Connectivity to PNFs on Flat/VLAN networks,L2,Full,
   ,,L3,Full,
   ,,External Network,Full,