FAQ
=======

Q: What Openstack versions does OpenDaylight release work with?

    A:The following table indicates the Openstack version versions that
    are supported for OpenDaylight versions

    ============ ==============
    OpenDaylight OpenStack
    ============ ==============
    Carbon       Ocata to Pike
    Nitrogen     Pike to Queens
    Oxygen       Pike to Queens
    Fluorine     Queens
    ============ ==============


    * Differing release schedules will lead to short-lived combinations that will
      drop as the releases line up. An example is with Carbon that releases
      before Pike so for a period of time Carbon is supported with Ocata.
    * The current OpenDaylight version and the previous will be supported.
      Boron support will drop when Nitrogen releases; Carbon support will drop
      when Oxygen releases.

Q: What Open vSwitch versions does OpenDaylight release work with?

    A: The table shows the minimum Open vSwitch requirement for Openstack versions.
    ============ ==============
    OpenDaylight Open vSwitch
    ============ ==============
    Carbon       2.6
    Nitrogen     2.6
    Oxygen       2.9
    Fluorine     2.9
    ============ ==============

Q: What features work with Open vSwitch and OpenDaylight versions?

    A: The table below shows the minmum Open vSwitch and OpenDaylight requirement for some
    of the features.
    ================  ============== ==================   ==============
    Feature           Open vSwitch   Open vSwitch(DPDK)   OpenDaylight
    ================  ============== ==================   ==============
    Security groups   2.6            2.6                  Carbon
    Controller NAT    2.6            2.6                  Carbon
    Conntrack NAT     2.6            2.8                  Carbon
    ================  ============== ==================   ==============
