 // https://wiki.opendaylight.org/view/Vpnservice:BGP_Stack_setup
 //
 // the label argument in pushRoute can use these consts
 const i32 LBL_NO_LABEL = 0
 
 /*
  * mpls explicit null isn't 3, but this is not meant
  * to be put in a packet but is used only between odl
  * and bgp because zero is already used up for
  * 'LBL_NO_LABEL'. besides, if this should accdientally
  * leak into a packet header, it will at least be an
  * implicit null
  */ 
 const i32 LBL_EXPLICIT_NULL = 3
 
 // FIB entry type
 const i32 BGP_RT_ADD = 0
 const i32 BGP_RT_DEL = 1
 
 // FIB table iteration op
 const i32 GET_RTS_INIT = 0
 const i32 GET_RTS_NEXT = 1
 
 /*
  * error codes. 
  * 0 is success.
  * ERR_FAILED because something bad happens deeper
  *    inside BGP, such as deleting a route that 
  *    doesn't exist.
  * ERR_PARAM when params don't validate 
  * ERR_ACTIVE when routing instance is already 
  *    running but ODL calls startBgp() anyway
  * ERR_INACTIVE when an RPC is invkoed but there  
  *    is no session.
  * ERR_NOT_ITER when GET_RTS_NEXT is called without 
  *    initializing with GET_RTS_INIT 
  */
  
 const i32 API_CALL_SUCCESS = 0 
 const i32 API_CALL_FAIL = 1 

 const i32 BGP_ERR_FAILED = 1 
 const i32 BGP_ERR_ACTIVE = 10
 const i32 BGP_ERR_INACTIVE = 11
 const i32 BGP_ERR_NOT_ITER = 15
 const i32 BGP_ERR_PEER_EXISTS = 19
 const i32 BGP_ERR_PARAM = 100
 const i32 BGP_ERR_NOT_SUPPORTED = 200
 
 const i32 BGP_ETHTAG_MAX_ET = 0xfffffff

 // supported afi-safi combinations 
 enum af_afi {
     AFI_IP = 1,
     AFI_IPV6 = 2,
     AFI_L2VPN = 3
 }
 
 enum af_safi {
     SAFI_IP_LABELED_UNICAST = 4,
     SAFI_MPLS_VPN = 5,
     SAFI_EVPN = 6
 }
 
 // supported encapsulation types - RFC 5512
 enum encap_type {
     L2TPV3_OVER_IP = 1,
     GRE = 2,
     IP_IN_IP = 7,
     VXLAN = 8,
     MPLS = 10
 }

 // layer type
 // used to mention to which layer a VRF belongs to. 
 enum layer_type {
     LAYER_2 = 1,
     LAYER_3 = 2
 }

 // protocol type
 // used to know to which route type is referred
enum protocol_type {
     PROTOCOL_LU   = 1,  // no overlay configuration
     PROTOCOL_L3VPN = 2, // MPLS over GRE overlay
     PROTOCOL_EVPN = 3   // VxLAN overlay
     PROTOCOL_ANY = 4   // for getRoutes() only
}

 // peer status type
 // used for getPeerStatus()
 enum peer_status_type {
     PEER_UP = 0,
     PEER_DOWN = 1,
     PEER_UNKNOWN = 2,
     PEER_NOTCONFIGURED = 3
 }

 // FIB update
 struct Update {
     1: i32 type, // either BGP_RT_ADD or RT_DEL
     2: i32 reserved, // JNI impl used a RIB-version here
     3: i32 prefixlen,
     4: i32 l3label,
     5: i32 l2label,
     6: i32 ethtag,
     7: string esi,
     8: string macaddress,
     9: string rd,
     10: string prefix,
     11: string nexthop,
     12: string routermac,
 }
 
 /*
  * a sequence of FIB updates, valid only if errcode
  * is zero. returned by getRoutes(). more=0 means end 
  * of iteration.
  */
 
 struct Routes {
     1: i32 errcode, // one of the BGP_ERR's
     2: optional list<Update> updates,
     4: optional i32 more
 }

 struct BfdConfigData {
     1: i8 bfdConfigDataVersion,
     2: optional i32  bfdRxInterval,
     3: optional i8 bfdFailureThreshold,
     4: optional i32  bfdTxInterval,
     5: optional i32  bfdDebounceDown,
     6: optional i32  bfdDebounceUp,
     7: optional bool bfdMultihop
 }

 service BgpConfigurator {
     /*
      * startBgp() starts a bgp instance on the bgp VM. Graceful 
      * Restart also must be configured (stalepathTime > 0). if 
      * local dataplane remains undisturbed relative to previous
      * invocation, announceFbit tells neighbors to retain all 
      * routes advertised by us in our last incarnation. this is 
      * the F bit of RFC 4724. 
      */
     i32 startBgp(1:i64 asNumber, 2:string routerId, 3: i32 port, 
                      4:i32 holdTime, 5:i32 keepAliveTime, 
                      6:i32 stalepathTime, 7:bool announceFbit),
     i32 stopBgp(1:i64 asNumber),
     i32 createPeer(1:string ipAddress, 2:i64 asNumber),
     /* 'setPeerSecret' sets the shared secret needed to protect the peer
      * connection using TCP MD5 Signature Option (see rfc 2385).
      *
      * Params:
      *
      *   'ipAddress' is the peer ( neighbour) address. Mandatory.
      *
      *   'rfc2385_sharedSecret' is the secret. Mandatory. Length must be
      *   greater than zero.
      *
      * Return codes:
      *
      *   0 on success.
      *
      *   BGP_ERR_FAILED if 'ipAddress' is missing or unknown.
      *
      *   BGP_ERR_PARAM if 'rfc2385_sharedSecret' is missing or invalid (e.g.
      *   it is too short or too long).
      *
      *   BGP_ERR_INACTIVE when there is not session.
      *
      *   BGP_ERR_NOT_SUPPORTED when TCP MD5 Signature Option is not supported
      *   (e.g. the underlying TCP stack does not support it)
      *
      */
     i32 setPeerSecret(1:string ipAddress, 2:string rfc2385_sharedSecret),
     i32 deletePeer(1:string ipAddress)
     i32 addVrf(1:layer_type l_type, 2:string rd, 3:list<string> irts,
                4:list<string> erts, 5:af_afi afi, 6:af_safi safi),
     i32 delVrf(1:string rd, 2:af_afi afi, 3:af_safi safi),
     /*
      * pushRoute:
      * IPv6 is not supported.
      * 'p_type' is mandatory
      * 'nexthop' cannot be null for VPNv4 and LU.
      * 'rd' is null for LU (and unicast). 
      * 'label' cannot be NO_LABEL for VPNv4 MPLS and LU.
      * 'ethtag stands 32 bit value. only 24 bit value is used for now (VID of vxlan).
      * 'esi' is a 10 byte hexadecimal string. 1st byte defines the type. Only '00' is supported for now.
      *       value should have 'colon' separators : 00:02:ab:de:45:23:54:75:fd:ab as example
      * encap_type: restricted for VXLAN if L3VPN-EVPN configured.
      *             ignored if L3VPN-MPLS is configured.
      * af_afi: indicates whether prefix is IPv4 or IPv6
      */
     i32 pushRoute(1:protocol_type p_type, 2:string prefix, 3:string nexthop, 4:string rd,
                   5:i64 ethtag, 6:string esi, 7:string macaddress,
                   8:i32 l3label, 9:i32 l2label, 10:encap_type enc_type,
                   11:string routermac, 12:af_afi afi),
     /*
      * 'p_type' is mandatory
      * kludge: second argument is either 'rd' (VPNv4) or 
      * label (v4LU) as a string (eg: "2500")
      * af_afi: indicates whether prefix is IPv4 or IPv6
      */
     i32 withdrawRoute(1:protocol_type p_type, 2:string prefix, 3:string rd,
                       4:i64 ethtag, 5:string esi, 6:string macaddress, 7:af_afi afi),
     i32 setEbgpMultihop(1:string peerIp, 2:i32 nHops),
     i32 unsetEbgpMultihop(1:string peerIp),
     i32 setUpdateSource(1:string peerIp, 2:string srcIp),
     i32 unsetUpdateSource(1:string peerIp),
     i32 enableAddressFamily(1:string peerIp, 2:af_afi afi, 3:af_safi safi),
     i32 disableAddressFamily(1:string peerIp, 2:af_afi afi, 3:af_safi safi),
     i32 setLogConfig(1:string logFileName, 2:string logLevel),
     i32 enableGracefulRestart(1:i32 stalepathTime),
     i32 disableGracefulRestart(),
     /*
      * getRoutes():
      * 'p_type' is mandatory. selects the VRF RIB to be dumped
      *   if PROTOCOL_LU chosen ( no VRF RIBs implemented), global RIB will be dumped)
      * optype is one of: GET_RTS_INIT: start the iteration,
      * GET_RTS_NEXT: get next bunch of routes. winSize is
      * the size of the buffer that caller has allocated to 
      * receive the array of routes. qbgp sends no more than
      * the number of routes that would fit in this buffer, 
      * but not necessarily the maximum number that would fit
      * (we currently use min(winSize, tcpWindowSize) ).
      * calling INIT when NEXT is expected causes reinit.
      * only vpnv4 RIBs are supported.
      */
     Routes getRoutes(1:protocol_type p_type, 2:i32 optype, 3:i32 winSize, 4:af_afi afi),
     i32 enableMultipath(1:af_afi afi, 2:af_safi safi),
     i32 disableMultipath(1:af_afi afi, 2:af_safi safi),
     i32 multipaths(1:string rd, 2:i32 maxPath),
     i32 enableEORDelay(1:i32 delay),
     i32 sendEOR(),
     i32 enableBFDFailover(1:BfdConfigData bfdConfig),
     i32 disableBFDFailover(),
     peer_status_type getPeerStatus(1:string ipAddress, 2:i64 asNumber),
 }
 
 service BgpUpdater {
   // 'p_type' is mandatory. indicates the origin of data
   i32 onUpdatePushRoute(1:protocol_type p_type, 2:string rd, 3:string prefix,
                                 4:i32 prefixlen, 5:string nexthop, 
                                 6:i64 ethtag, 7:string esi, 8:string macaddress,
                                 9:i32 l3label, 10:i32 l2label,
                                 11:string routermac, 12:af_afi afi),
   i32 onUpdateWithdrawRoute(1:protocol_type p_type, 2:string rd, 3:string prefix,
                                     4:i32 prefixlen, 5:string nexthop,
                                     6:i64 ethtag, 7:string esi, 8:string macaddress,
                                     9:i32 l3label, 10:i32 l2label, 11:af_afi afi),
   // tell them we're open for business
   i32 onStartConfigResyncNotification(),
   // relay to odl a bgp Notification we got from peer 
   i32 onNotificationSendEvent(1:string prefix, 
                                       2:i8 errCode, 3:i8 errSubcode),
   i32 peerDown(1:string ipAddress, 2:i64 asNumber),
   i32 peerUp(1:string ipAddress, 2:i64 asNumber)

} 
