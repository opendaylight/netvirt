RELEASES
=========

Openstack versions that work with OpenDaylight releases
=========================================================
    A:The following table indicates the Openstack version versions that
    are supported for OpenDaylight versions

    ============ ==============
    OpenDaylight OpenStack
    ============ ==============
    Carbon       Ocata
    Nitrogen     Pike
    Oxygen       Queens
    Fluorine     Rocky
    ============ ==============


    * The opendaylight releases are now expected to be in sync with the openstack releases.
      So fluorine should align with Rocky
    * The current OpenDaylight version and the previous will be supported.
      Carbon support will drop when Oxygen releases.

Open vSwitch versions that work with OpenDaylight releases
===========================================================
    A: The table shows the minimum Open vSwitch tested  for Opendaylight versions. Anything
       version outside the matrix is best-effort support
    ============ ==============
    OpenDaylight Open vSwitch
    ============ ==============
    Carbon       2.6
    Nitrogen     2.6
    Oxygen       2.9*
    Fluorine     2.9*
    ============ ==============
    *The current tests are run on 2.7 as devstack is yet to pull the 2.9. But all the features
     does not work with ovs2.7.

Open vSwitch versions and OpenDaylight versions required for features
======================================================================
    A: The table below shows the minimum Open vSwitch and OpenDaylight requirement for some
    of the features. Any version above this combination is expected to have the feature support.
    ================  ============== ==================   ==============
    Feature           Open vSwitch   Open vSwitch(DPDK)   OpenDaylight
    ================  ============== ==================   ==============
    Security groups   2.6            2.6                  Carbon
    Controller NAT    2.6            2.6                  Carbon
    Conntrack NAT     2.6            2.8                  Carbon
    ================  ============== ==================   ==============
