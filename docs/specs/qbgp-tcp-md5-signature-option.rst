.. contents:: Table of Contents
   :depth: 3

.. |RFC2385| replace:: TCP MD5 Signature Option [RFC2385]

.. |netvirt| replace:: odl-netvirt-impl feature

.. |Quagga| replace:: Quagga

.. |OSR-Q| replace:: Open Source Routing's opnfv-quagga-packaging


================================================================
Support for TCP MD5 Signature Option configuration of Quagga BGP
================================================================

https://git.opendaylight.org/gerrit/#/q/topic:qbgp-md5-signature-option

This functionality adds support to |netvirt| to configure the |RFC2385|_
password in |Quagga|_ BGPs.


Problem description
===================

|Quagga|_ supports |RFC2385|_ in BGP traffic but current |netvirt|
implementation lacks support to configure the required passwords.

Use Cases
---------

UC1: Protect (|Quagga|_) BGP and DC gateway BGP interface using |RFC2385|_.

Proposed change
===============

The following components need to be enhanced:

* BGP Manager


Pipeline changes
----------------

No pipeline changes.

API changes
-----------

Changes will be needed in ``ebgp.yang``, and ``qbgp.thrift``.


YANG changes
^^^^^^^^^^^^

A new optional leaf with the |RFC2385|_ password is added (by means of a
choice) to list ``neighbor``.


.. code-block:: none
   :caption: ebgp.yang additions

   typedef tcp-md5-signature-password-type {
     type string {
       length 1..80;
     } // subtype string
     description
       "The shared secret used by TCP MD5 Signature Option.  The length is
        limited to 80 chars because A) it is identified by the RFC as current
        practice and B) it is the maximum length accepted by Quagga
        implementation.";
     reference "RFC 2385";
   } // typedef tcp-md5-signature-password-type


   grouping tcp-security-option-grouping {
     description "TCP security options.";
     choice tcp-security-option {
       description "The tcp security option in use, if any.";

       case tcp-md5-signature-option {
         description "The connection uses TCP MD5 Signature Option.";
         reference "RFC 2385";
         leaf tcp-md5-signature-password {
           type tcp-md5-signature-password-type;
           description "The shared secret used to sign the packets.";
         } // leaf tcp-md5-signature-password
       } // case tcp-md5-signature-option

     } // choice tcp-security-option
   } // grouping tcp-security-option-grouping


.. code-block:: none
   :caption: ebgp.yang modifications

       list neighbors {
         key "address";
         leaf address {
           type inet:ipv4-address;
           mandatory "true";
         }
         leaf remote-as {
           type uint32;
           mandatory "true";
         }
    +    use tcp-security-option-grouping;



Thrift changes
^^^^^^^^^^^^^^

A new field ``rfc2385_sharedSecret`` is added to the function ``createPeer``
of the service ``BgpConfigurator``.


.. code-block:: none
   :caption: qbgp.thrift modifications

   @@ -123,3 +123,9 @@ service BgpConfigurator {
        i32 stopBgp(1:i64 asNumber),
   -    i32 createPeer(1:string ipAddress, 2:i64 asNumber),
   +    /*
   +     *  'rfc2385_sharedSecret' is the password used with RFC 2385 "TCP MD5
   +     *  Signature Option".  If this field is empty or missing "TCP MD5
   +     *  Signature Option" will be not used.  An string longer than 80
   +     *  characters will be silently right-truncated.
   +     */
   +    i32 createPeer(1:string ipAddress, 2:i64 asNumber, 3:string rfc2385_sharedSecret),
        i32 deletePeer(1:string ipAddress)

The proposed change is backward compatible. See section 5.3 of [thrift2007]_.


Configuration impact
--------------------

No configuration parameters deprecated.

New optional leaf ``tcp-md5-signature-password`` does not impact existing
deployments.

The recommended AAA configuration (See `Security considerations`_) may impact
existing deployments.

Clustering considerations
-------------------------
NA

Other Infra considerations
--------------------------

Signature mismatch
^^^^^^^^^^^^^^^^^^

On signature mismatch |RFC2385|_ (page 2) specifies the following behaviour:

.. code-block:: none
   :caption: Rfc 2385 page 2

   Upon receiving a signed segment, the receiver must validate it by
   calculating its own digest from the same data (using its own key) and
   comparing the two digest.  A failing comparison must result in the
   segment being dropped and must not produce any response back to the
   sender.  Logging the failure is probably advisable.

A BGP will be unable to connect with a neighbor with a wrong password because
the TCP SYN,ACK will be dropped.  The neighbor state will bounce between
"Active" and "Connect" while it retries.



Security considerations
-----------------------


``tcp-md5-signature-password`` is stored in clear in the datastore.  This is
a limitation of the proposed change.

Because ``tcp-md5-signature-password`` is stored in clear the REST access to
``neighbors`` list  should be restricted.  See the following AAA
configuration examples:

.. code-block:: none
   :caption: etc/shiro.ini example

   #
   # DISCOURAGED since Carbon
   #
   /config/ebgp:bgp/neighbors/** = authBasic, roles[admin]

.. code-block:: json
   :caption: AAA MDSALDynamicAuthorizationFilter example

   { "aaa:policies":
      {  "aaa:policies": [
         {  "aaa:resource": "/restconf/config/ebgp:bgp/neighbors/**",
            "aaa:permissions": [
            {  "aaa:role": "admin",
               "aaa:actions": [ "get","post","put","patch","delete" ]
            } ]
         } ]
      }
   }


If ``BgpConfigurator`` thrift service is not secured then
``tcp-md5-signature-password`` goes clear on the wire.



Scale and Performance Impact
----------------------------

Negligible scale or performance impacts.

* datastore

   * A bounded (<=80) string per configured neighbor.

* Traffic (thrift ``BgpConfigurator`` service)

   * A bounded (<=80) string field per neighbor addition operation.



Targeted Release
----------------
Carbon

Alternatives
------------
To store the password encrypted in the datastore has been considered.  It has
been not selected (in Carbon time frame) because:

* Currently ``BgpConfigurator`` thrift service is not secured.

* It would need an RPC operation.


Usage
=====

Features to Install
-------------------
odl-netvirt-openstack


REST API
--------

The RESTful API for neighbors creation
(``/restconf/config/ebgp:bgp/neighbors/{address}``) will be enhanced to
accept an additional ``tcp-md5-signature-password`` attribute:

.. code-block:: json

   { "neighbors": {
      "address": "192.168.50.2",
      "remote-as": "2791",
      "tcp-md5-signature-password": "password"
   }}


CLI
---

A new option ``--tcp-md5-password`` will be added to command
``odl:configure-bgp``:

.. code-block:: none

   opendaylight-user@root> odl:configure-bgp -op add-neighbor --ip 192.168.50.2 --as-num 2791 --tcp-md5-password password



Implementation
==============

Assignee(s)
-----------

Primary assignee:
  Jose-Santos Pulido, JoseSantos, jose.santos.pulido.garcia@ericsson.com

Other contributors:
  TBD

Work Items
----------

#. Spec

#. ``ebgp.yang``

#. ``BgpConfigurator`` thrift service (both idl and client)

#. ``BgpConfigurationManager.NeighborsReactor``

#. ``ConfigureBgpCli``

* TBD: trello card


Dependencies
============

Internal
--------

No internal dependencies are added or removed.

External
--------

To enable |RFC2385|_ the ``BgpConfigurator`` thrift service provider (e.g.
|OSR-Q|_) should support the new field ``rfc2385_sharedSecret`` of
``createPeer`` function.



Testing
=======

Unit Tests
----------

Currently ``bgpmanager`` has no unit tests related to configuration.

Integration Tests
-----------------

Currently ``bgpmanager`` has no integration tests.

CSIT
----

Currently there is no CSIT test exercising ``bgpmanager``.


Documentation Impact
====================

Currently there is no documentation related to ``bgpmanager``.


References
==========

.. [RFC2385] `IETF RFC 2385: Protection of BGP Sessions via the TCP MD5 Signature Option <https://tools.ietf.org/html/rfc2385>`__

.. [Quagga] `Quagga Routing Suite <http://www.nongnu.org/quagga>`__

.. [thrift2007] `Thrift white paper <https://thrift.apache.org/static/files/thrift-20070401.pdf>`__

.. [OSR-Q] `Open Source Routing's opnfv-quagga-packaging <https://git-us.netdef.org/projects/OSR/repos/opnfv-quagga-packaging/browse>`__


..
   vi: ts=3 sts=3 sw=3 expandtab ai tw=77 :

