.. contents:: Table of Contents
   :depth: 3

.. |TCP-MD5| replace:: TCP MD5 Signature Option

.. |netvirt| replace:: odl-netvirt-impl feature


================================================================
Support for TCP MD5 Signature Option configuration of Quagga BGP
================================================================

https://git.opendaylight.org/gerrit/#/q/topic:qbgp-tcp-md5-signature-option

This functionality adds support to |netvirt| to configure the |TCP-MD5|
[RFC2385]_ password in Quagga BGPs [QBGP]_.


Problem description
===================

Quagga [QBGP]_ supports |TCP-MD5| [RFC2385]_ in BGP traffic but current |netvirt|
implementation lacks support to configure the required passwords.

Use Cases
---------

UC1: Protect (Quagga [QBGP]_) BGP and DC gateway BGP interface using
|TCP-MD5| [RFC2385]_.

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

A new optional leaf with the |TCP-MD5| [RFC2385]_ password is added (by means of a
choice) to list ``neighbors``.


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

A new function ``setPeerSecret`` is added to the service ``BgpConfigurator``.


.. code-block:: none
   :caption: qbgp.thrift modifications

   --- a/vpnservice/bgpmanager/bgpmanager-impl/src/main/java/org/opendaylight/netvirt/bgpmanager/thrift/idl/qbgp.thrift
   +++ b/vpnservice/bgpmanager/bgpmanager-impl/src/main/java/org/opendaylight/netvirt/bgpmanager/thrift/idl/qbgp.thrift
   @@ -31,6 +31,8 @@ const i32 GET_RTS_NEXT = 1
     * ERR_NOT_ITER when GET_RTS_NEXT is called without
     *    initializing with GET_RTS_INIT
     * ERR_PARAM when there is an issue with params
   + * ERR_NOT_SUPPORTED when the server does not support
   + *    the operation.
     */
    
    const i32 BGP_ERR_FAILED = 1
   @@ -38,6 +40,7 @@ const i32 BGP_ERR_ACTIVE = 10
    const i32 BGP_ERR_INACTIVE = 11
    const i32 BGP_ERR_NOT_ITER = 15
    const i32 BGP_ERR_PARAM = 100
   +const i32 BGP_ERR_NOT_SUPPORTED = 200
    
    // these are the supported afi-safi combinations
    enum af_afi {
   @@ -122,6 +125,33 @@ service BgpConfigurator {
                           6:i32 stalepathTime, 7:bool announceFlush),
        i32 stopBgp(1:i64 asNumber),
        i32 createPeer(1:string ipAddress, 2:i64 asNumber),
   +
   +    /* 'setPeerSecret' sets the shared secret needed to protect the peer
   +     * connection using TCP MD5 Signature Option (see rfc 2385).
   +     *
   +     * Params:
   +     *
   +     *   'ipAddress' is the peer (neighbour) address.  Mandatory.
   +     *
   +     *   'rfc2385_sharedSecret' is the secret.  Mandatory.  Length must be
   +     *   greater than zero.
   +     *
   +     * Return codes:
   +     *
   +     *   0 on success.
   +     *
   +     *   BGP_ERR_FAILED if 'ipAddress' is missing or unknown.
   +     *
   +     *   BGP_ERR_PARAM if 'rfc2385_sharedSecret' is missing or invalid (e.g.
   +     *   it is too short or too long).
   +     *
   +     *   BGP_ERR_INACTIVE when there is no session.
   +     *
   +     *   BGP_ERR_NOT_SUPPORTED when TCP MD5 Signature Option is not supported
   +     *   (e.g. the underlying TCP stack does not support it)
   +     *
   +     */
   +    i32 setPeerSecret(1:string ipAddress, 2:string rfc2385_sharedSecret),
        i32 deletePeer(1:string ipAddress)
        i32 addVrf(1:layer_type l_type, 2:string rd, 3:list<string> irts, 4:list<string> erts),
        i32 delVrf(1:string rd),


An old server (i.e. using a previous version of ``qbgp.thrift``) will return
a ``TApplicationException`` with type ``UNKNOWN_METHOD``.  See
[TBaseProcessor]_.


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

On signature mismatch |TCP-MD5| [RFC2385]_ (page 2) specifies the following
behaviour:

.. code-block:: none
   :caption: RFC 2385 page 2

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


Quagga [QBGP]_ (up to version 1.0) keeps the password in memory in clear.
The password can be retrieved through Quagga's configuration interface.




Scale and Performance Impact
----------------------------

Negligible scale or performance impacts.

* datastore: A bounded (<=80) string per configured neighbor.

* Traffic (thrift ``BgpConfigurator`` service): A bounded (<=80) string field
  per neighbor addition operation.



Targeted Release
----------------
Carbon

Alternatives
------------

Three alternatives have been considered in order to avoid storing the plain
password in datastore: RPC, post-update, and transparent encryption.
They are briefly described below.

The best alternative is transparent encryption, but in Carbon time-frame
is not feasible.

The post-update alternative does not actually solve the limitation.

The RPC alternative is feasible in Carbon time-frame but, given that
currently ``BgpConfigurator`` thrift service is not secured, to add an RPC
does not pull its weight.


RPC encryption
^^^^^^^^^^^^^^

A new RPC ``add-neighbor(address, as-number[, tcp-md5-signature-password])``
is in charge of create ``neighbors`` elements.
The password is salted and encrypted with ``aaa-encryption-service``.
Both the salt and the encrypted password are stored in the ``neighbors``
element.


Post-update encryption
^^^^^^^^^^^^^^^^^^^^^^

The ``neighbors`` element contains both a ``plain-password`` leaf and a
``encrypted-password-with-salt`` leaf.
The listener ``BgpConfigurationManager.NeighborsReactor`` is in charge of
encrypt and remove the ``plain-password`` leaf when it is present (and the
encrypted one is not).

This alternative does not really solve the limitation because during a
brief period the password is stored in plain.


Transparent encryption
^^^^^^^^^^^^^^^^^^^^^^

A plain value is provided in REST write operations but it is `automagically`
encrypted before it reaches MD-SAL.
Read operations never decrypts the encrypted values.

This alternative impacts at least ``aaa``, ``yangtools``, and ``netconf``
projects. It can not possibly be done in Carbon.


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

A new option ``--tcp-md5-password`` will be added to commands
``odl:configure-bgp`` and ``odl:bgp-nbr``.

.. code-block:: none

   opendaylight-user@root> odl:configure-bgp -op add-neighbor --ip 192.168.50.2 --as-num 2791 --tcp-md5-password password
   opendaylight-user@root> odl:bgp-nbr --ip-address 192.168.50.2 --as-number 2791 --tcp-md5-password password add



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

* https://trello.com/c/87MAFjRf

#. Spec

#. ``ebgp.yang``

#. ``BgpConfigurator`` thrift service (both idl and client)

#. ``BgpConfigurationManager.NeighborsReactor``

#. ``ConfigureBgpCli``



Dependencies
============

Internal
--------

No internal dependencies are added or removed.

External
--------

To enable |TCP-MD5| [RFC2385]_ in a BGP the following conditions need to be
met:

* ``BgpConfigurator`` thrift service provider (e.g.
  ``opnfv-quagga-packaging`` [OSR]_ or ``Zebra Remote Procedure Call``
  [ZRPC]_) must support the new function ``setPeerSecret``.

* BGP's TCP stack must support |TCP-MD5| (e.g. in linux the kernel option
  CONFIG_TCP_MD5SIG must be set).



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

.. [OSR] `Open Source Routing's opnfv-quagga-packaging <https://git-us.netdef.org/projects/OSR/repos/opnfv-quagga-packaging/browse>`__

.. [QBGP] `Quagga Routing Suite <http://www.nongnu.org/quagga>`__

.. [RFC2385] `IETF RFC 2385: Protection of BGP Sessions via the TCP MD5 Signature Option <https://tools.ietf.org/html/rfc2385>`__

.. [TBaseProcessor] `thrift java library's TBaseProcessor.process <https://github.com/apache/thrift/blob/0.9.1/lib/java/src/org/apache/thrift/TBaseProcessor.java#L25-L41>`__

.. [ZRPC] `Zebra Remote Procedure Call <https://github.com/6WIND/zrpcd/>`__

..
   vi: ts=3 sts=3 sw=3 expandtab ai tw=77 :

