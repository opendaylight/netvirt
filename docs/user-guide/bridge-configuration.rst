Bridge Configuration
====================

.. contents:: Table of Contents
   :depth: 2

The following describes OVS bridge configurations supported by OpenDaylight.

The "br-int" Bridge
---------------------------------------
If the br-int bridge is not configured prior to the ovsdb manager connection with ODL, 
ODL will create it. If br-int exists prior to the ovsdb manager connection, ODL will retain 
any existing configurations on br-int. ODL will add the following configuration items to br-int.

1. ODL will set itself as br-int's controller
2. Any provider network configuration (see section "Provider Networks" below)

It is important to note that once the ovsdb manager connection is established with ODL, ODL 
"owns" br-int and other applications should not modify its settings.

Provider Networks
---------------------------------------
Provider networks should be configured prior to OVSDB connecting to ODL. These are configured 
in the Open_vSwitch table’s other_Config column and have the format "<physnet>:<connector>" 
where <physnet> is the name of the provider network and <connector> is one of the following 
three options.

1. The name of a local interface (ODL will add this port to br-int)
2. The name of a bridge on OpenVSwitch (ODL will create patch ports between br-int and this bridge)
3. The name of a port already present on br-int (ODL will use that port)
