Support
=======
.. contents:: Table of Contents
   :depth: 2

Supported Combinations
----------------------
This section describes which versions of OpenStack and Open vSwitch are expected
to be supported when integrated with OpenDaylight. Using combinations outside
this list may work but are not officially supported.

.. note::
   Support is defined as combinations that are actively maintained.
   OpenDaylight, OpenStack and Open vSwitch are very active and quickly adding
   new features that makes it difficult to support all the different release
   combinations. Different combinations are likely to work but support will be
   limited.

The following table details the expected supported combinations.

.. csv-table:: Supported Version Matrix
   :header: OpenDaylight, OpenStack, Open vSwitch, Sync, Notes
   :widths: 12, 12, 12, 5, 40

   Boron, Newton, 2.6, *,
   Carbon, Ocata, 2.7,, "Combination drops when Pike releases"
   Carbon, Pike, 2.7, *,
   Nitrogen, Ocata, 2.7,, "Combination drops when Pike releases"
   Nitrogen, Pike, 2.7,,"Combination drops when Queens releases"
   Nitrogen, Queens, 2.8/2.9, *,
   Oxygen, Pike, 2.7,,"Combination drops when Queens releases"
   Oxygen, Queens, 2.8/2.9,, "Combination drops when OpenStack R releases"
   Oxygen, R, 2.9, *,

* (*): in the Sync column indicates the final supported combination for that
  OpenDaylight release.
* Differing release schedules will lead to short-lived combinations that will
  drop as the releases line up. An example is with Carbon that releases
  before Pike so for a period of time Carbon is supported with Ocata.
* The current OpenDaylight version and the previous will be supported.
  Boron support will drop when Nitrogen releases; Carbon support will drop
  when Oxygen releases.

Open vSwitch Kernel and DPDK Modes
----------------------------------
The table below lists the Open vSwitch requirements for the Carbon release.

.. csv-table:: Kernel and DPDK Modes
   :header: "Feature", "OVS 2.6 kernel mode", "OVS 2.6 dpdk mode"

   Conntrack - security groups, yes, yes
   Conntrack - NAT, yes, no (target 2.8*)
   Security groups stateful, yes (conntrack), yes(conntrack)
   Security groups learn, yes (but not needed), yes (but not needed)
   IPV4 NAT (without pkt punt to controller), yes (conntrack), no (target 2.8*)
   IPV4 NAT (with pkt punt to controller), not needed, yes (until 2.8*)

(*) support is tentatively scheduled for Open vSwitch 2.8
