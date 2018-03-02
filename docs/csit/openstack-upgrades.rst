OpenStack Upgrades
==================
.. contents:: :depth: 2

The NetVirt CSIT relies heavily on DevStack which changes heavily with each OpenStack release. This section will detail
how the NetVirt CSIT should be adapted to these changes.

.. note::
   This section should be used only as a guide. Each new release brings in a wide variety of changes that may or may
   not be captured below.

The table below lists the tasks to adapt CSIT to the new OpenStack versions.

.. csv-table:: CSIT OpenStack Upgrade Tasks
   :header: "Task", "Notes"

   Tempest exclusions, https://git.opendaylight.org/gerrit/69001
   Cirros flavors, https://git.opendaylight.org/gerrit/68971
   Update run-test.sh, https://git.opendaylight.org/gerrit/68800
   Update networking-odl plugins in local.conf, https://git.opendaylight.org/gerrit/68800
   Update networking-l2gw plugins in local.conf, https://git.opendaylight.org/gerrit/68441
   Update OpenStack CLI, https://git.opendaylight.org/gerrit/68687
   Update workarounds, Look for places where master and other recent branches are used
   Update CSIT jobs, https://git.opendaylight.org/gerrit/68909
