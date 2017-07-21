Version Compatibility
=====================
.. contents:: :depth: 2

This section will detail version compatibilities between OpenDaylight and other components.

==================================
Open_vSwitch Kernel and DPDK Modes
==================================
The table below lists the Open_vSwitch requirements for the Carbon release.

.. csv-table:: Kernel and DPDK Modes
:header: "Feature", "OVS 2.6 kernel mode", "OVS 2.6 dpdk mode"

   Conntrack - security groups, yes, yes
   Conntrack - NAT, yes, no (target 2.8*)
   Security groups stateful, yes (conntrack), yes(conntrack)
   security groups learn, yes (but not needed), yes (but not needed)
   IPV4 NAT (without pkt punt to controller), yes (conntrack), no (target 2.8*)
   IPV4 NAT (with pkt punt to controller), not needed, yes (until 2.8*)

(*) support is tentatively scheduled for Open_vSwitch 2.8