Debugging Netvirt/Networking-odl Port Status Update
===================================================

.. contents:: Table of Contents
   :depth: 2

Recent versions of Opendaylight Netvirt support dynamic update of Neutron port status via
websocket and the networking-odl ML2 plugin knows how to take advantage of this feature.
The following is a basic description of the internals of this feature followed by a guide
for how to debug it.

How Port Status Update Works
----------------------------

When Neutron/networking-odl boots it registers a websocket based subscription to all neutron
ports in the ODL operational yang model.  Once this websocket subscription is connected,
networking-odl receives json based notifications for every time Netvirt changes the status of
a port. Note that for now this only happens at the time of port binding, if a port should go
down, e.g., the VM crashes, Netvirt will not update the port status.

How to Debug Port Status
------------------------

**1) BEFORE YOU GET STARTED, SET THE LOG LEVELS**

Neutron logs need to be set to debug. You can do this by having “debug = True” in your
neutron.conf, generally under /etc/neutron.

The following ODL component log levels need to be set in
<your karaf installation>/etc/org.ops4j.pax.logging.cfg:

::

  log4j2.logger.npcl.level = TRACE
  log4j2.logger.npcl.name =org.opendaylight.netvirt.neutronvpn.NeutronPortChangeListener
  log4j2.logger.nu.level = DEBUG
  log4j2.logger.nu.name =org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils
  log4j2.logger.oisah.level = INFO
  log4j2.logger.oisah.name =org.opendaylight.genius.interfacemanager.renderer.ovs.statehelpers.OvsInterfaceStateAddHelper

**2) Check that the websocket is connected**

Websocket connection status is logged in the neutron logs. Check that this line is the last
websocket status logged before your port was created:

  ``websocket transition to status ODL_WEBSOCKET_CONNECTED``

Note: The connection can disconnect but should reconnect so you can have multiple lines like
this in the log with different statuses. You can now follow the transitions in this case.

If the websocket is not connected, either something is wrong with your deployment or ODL is
not running. It may be worth checking if a firewall is blocking the websocket port which is
8185 by default but may be custom configured in a file called 10-rest-connector.xml under
your karaf installation.

**3) Check whether networking-odl received the port status update**

All port status notifications are logged in the neutron logs like this:

  ``Update port for port id <uuid of port> <ACTIVE|DOWN>``

Note that for VM ports Netvirt will initially report that the port is DOWN until the basic
flows are configured.

If there is no log line like this reporting your port is ACTIVE it is best to…

**4) Check whether Netvirt transitioned the port to ACTIVE**

Look for the following in karaf.log:

  ``writePortStatus: operational port status for <uuid of port> set to <ACTIVE|DOWN>``

Again, remember that for VM ports the port is initially reported as DOWN and soon after as ACTIVE.

If this log line is missing…

**5) Check whether the Neutron port was received by Netvirt**

  ``Adding Port : key: <iid of the port, including uuid>, value=<dump of the port>``

If this line is missing it means that something is wrong with the REST communication between
networking-odl and ODL.

If this line is present but the line from (4) is not, it probably means that the southbound OpenFlow
Port Status event was never received. Now…

**6) Check whether the Genius operational port status was created**

Check for this:

  ``Adding Interface State to Oper DS for interface <tap interface name>``

The tap interface name is the word “tap-” followed by the initial 7 characters of the neutron port’s
UUID. If this line is missing, you have confirmed that the southbound port was never received via
openflowplugin. This could mean that the switch is not connected or perhaps the VM never booted.

**7) Something deeper is wrong**

Although unlikely, if you will have made it this far and still have no answer, something much deeper
is wrong and a more serious debugging effort is required.
