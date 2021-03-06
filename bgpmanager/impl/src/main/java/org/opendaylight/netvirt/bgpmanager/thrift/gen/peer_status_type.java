/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.opendaylight.netvirt.bgpmanager.thrift.gen;

import java.util.Map;
import java.util.HashMap;
import org.apache.thrift.TEnum;

public enum peer_status_type implements org.apache.thrift.TEnum {
  PEER_UP(0),
  PEER_DOWN(1),
  PEER_UNKNOWN(2),
  PEER_NOTCONFIGURED(3);

  private final int value;

  private peer_status_type(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  public static peer_status_type findByValue(int value) {
    switch (value) {
      case 0:
        return PEER_UP;
      case 1:
        return PEER_DOWN;
      case 2:
        return PEER_UNKNOWN;
      case 3:
        return PEER_NOTCONFIGURED;
      default:
        return PEER_UNKNOWN;
    }
  }
}
