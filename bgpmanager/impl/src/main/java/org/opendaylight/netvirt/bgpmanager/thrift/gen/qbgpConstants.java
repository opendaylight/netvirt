/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.opendaylight.netvirt.bgpmanager.thrift.gen;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked"})
public class qbgpConstants {

  public static final int LBL_NO_LABEL = 0;

  public static final int LBL_EXPLICIT_NULL = 3;

  public static final int BGP_RT_ADD = 0;

  public static final int BGP_RT_DEL = 1;

  public static final int GET_RTS_INIT = 0;

  public static final int GET_RTS_NEXT = 1;

  public static final int BGP_ERR_FAILED = 1;

  public static final int BGP_ERR_ACTIVE = 10;

  public static final int BGP_ERR_INACTIVE = 11;

  public static final int BGP_ERR_NOT_ITER = 15;

  public static final int BGP_ERR_PEER_EXISTS = 19;

  public static final int BGP_ERR_PARAM = 100;

  public static final int BGP_ERR_NOT_SUPPORTED = 200;

  public static final int BGP_ETHTAG_MAX_ET = 268435455;

}
