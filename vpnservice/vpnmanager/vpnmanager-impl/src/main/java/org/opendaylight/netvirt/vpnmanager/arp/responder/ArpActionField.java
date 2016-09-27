/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.arp.responder;

import java.math.BigInteger;

import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.netvirt.utils.mdsal.openflow.ActionUtils;
import org.opendaylight.netvirt.vpnmanager.ArpReplyOrRequest;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;

/**
 * List of actions that enables to program flows on DPN to respond with ARP
 * packets
 *
 * @author karthik.p
 *
 */
public enum ArpActionField {

    /**
     * Load macAddress to SHA(Sender Hardware Address)
     * <p>
     * Media address of the sender. In an ARP request this field is used to
     * indicate the address of the host sending the request. In an ARP reply
     * this field is used to indicate the address of the host that the request
     * was looking for.
     *
     */
    LOAD_MAC_TO_SHA {

        /*
         * (non-Javadoc)
         *
         * @see
         * org.opendaylight.netvirt.vpnmanager.arp.responder.ArpActionField#
         * buildAction(int, int, java.lang.Object[])
         */
        @Override
        public Action buildAction(final int order, final int actionKey,
                final Object... objects) {

            if (objects == null || objects.length == 0
                    || !(objects[0] instanceof MacAddress)) {
                throw new IllegalArgumentException(
                        "No valid macaddress field passed");
            }
            final MacAddress mac = (MacAddress) objects[0];
            if (objects[0] instanceof MacAddress) {
                return new ActionBuilder().setKey(new ActionKey(actionKey))
                        .setOrder(order)
                        .setAction(ActionUtils.nxLoadArpShaAction(mac)).build();
            } else {
                return new ActionBuilder().setKey(new ActionKey(actionKey))
                        .setOrder(order)
                        .setAction(ActionUtils.nxLoadArpShaAction(
                                BigInteger.valueOf((long) objects[0])))
                        .build();
            }

        }

    },

    /**
     * Load IP Address to SPA(Sender Protocol Address)
     * <p>
     * IP address of the sender. In an ARP request this field is used to
     * indicate the address of the host sending the request. In an ARP reply
     * this field is used to indicate the address of the host that the request
     * was looking for
     */
    LOAD_IP_TO_SPA {

        /*
         * (non-Javadoc)
         *
         * @see
         * org.opendaylight.netvirt.vpnmanager.arp.responder.ArpActionField#
         * buildAction(int, int, java.lang.Object[])
         */
        @Override
        public Action buildAction(final int order, final int actionKey,
                final Object... objects) {

            if (objects == null || objects.length == 0) {
                throw new IllegalArgumentException(
                        "No valid ipadress field passed");
            }
            if ((objects[0] instanceof String)) {
                final String ipAddress = (String) objects[0];
                return new ActionBuilder().setKey(new ActionKey(actionKey))
                        .setOrder(order)
                        .setAction(ActionUtils.nxLoadArpSpaAction(ipAddress))
                        .build();
            } else {
                final long ipAddress = (long) objects[0];
                return new ActionBuilder().setKey(new ActionKey(actionKey))
                        .setOrder(order)
                        .setAction(ActionUtils.nxLoadArpSpaAction(
                                BigInteger.valueOf(ipAddress)))
                        .build();
            }

        }

    },

    /**
     * Move Source Eth to Destination Eth, to where the ARP response need to be
     * addressed to.
     *
     */
    MOVE_ETH_SRC_TO_ETH_DST {
        /*
         * (non-Javadoc)
         *
         * @see
         * org.opendaylight.netvirt.vpnmanager.arp.responder.ArpActionField#
         * buildAction(int, int, java.lang.Object[])
         */
        @Override
        public Action buildAction(final int order, final int actionKey,
                final Object... objects) {

            return new ActionBuilder().setKey(new ActionKey(actionKey))
                    .setOrder(order)
                    .setAction(ActionUtils.nxMoveEthSrcToEthDstAction())
                    .build();
        }
    },

    /**
     * Move Source Hardware address to Destination address, to where the ARP
     * response need to be addressed to.
     *
     */
    MOVE_SHA_TO_THA {

        /*
         * (non-Javadoc)
         *
         * @see
         * org.opendaylight.netvirt.vpnmanager.arp.responder.ArpActionField#
         * buildAction(int, int, java.lang.Object[])
         */
        @Override
        public Action buildAction(final int order, final int actionKey,
                final Object... objects) {
            return new ActionBuilder().setKey(new ActionKey(actionKey))
                    .setOrder(order)
                    .setAction(ActionUtils.nxMoveArpShaToArpThaAction())
                    .build();
        }
    },

    /**
     *
     * Move Source IP address to Destination IP address, to where the ARP
     * response need to be addressed to.
     *
     */
    MOVE_SPA_TO_TPA {

        /*
         * (non-Javadoc)
         *
         * @see
         * org.opendaylight.netvirt.vpnmanager.arp.responder.ArpActionField#
         * buildAction(int, int, java.lang.Object[])
         */
        @Override
        public Action buildAction(final int order, final int actionKey,
                final Object... objects) {
            return new ActionBuilder().setKey(new ActionKey(actionKey))
                    .setOrder(order)
                    .setAction(ActionUtils.nxMoveArpSpaToArpTpaAction())
                    .build();
        }
    },

    /**
     * Set Source Ethernet Address( MAC Address)
     *
     * <p>
     * Require to pass the source mac address that indicates form where ARP
     * Response was generated
     *
     * @author karthik.p
     *
     */
    SET_SRC_ETH {

        /*
         * (non-Javadoc)
         *
         * @see
         * org.opendaylight.netvirt.vpnmanager.arp.responder.ArpActionField#
         * buildAction(int, int, java.lang.Object[])
         */
        @Override
        public Action buildAction(final int order, final int actionKey,
                final Object... objects) {
            if (objects == null || objects.length == 0
                    || !(objects[0] instanceof MacAddress)) {
                throw new IllegalArgumentException(
                        "No valid macaddress field passed");
            }
            final MacAddress mac = (MacAddress) objects[0];
            return new ActionBuilder().setKey(new ActionKey(actionKey))
                    .setOrder(order).setAction(ActionUtils.setDlSrcAction(mac))
                    .build();
        }

    },
    /**
     * Set ARP Operation Type that is Request or Replay.
     * <p>
     * Valid values to be passed {@link ArpReplyOrRequest}
     */
    SET_ARP_OP {

        /*
         * (non-Javadoc)
         *
         * @see
         * org.opendaylight.netvirt.vpnmanager.arp.responder.ArpActionField#
         * buildAction(int, int, java.lang.Object[])
         */
        @Override
        public Action buildAction(final int order, final int actionKey,
                final Object... objects) {
            if (objects == null || objects.length == 0
                    || !(objects[0] instanceof ArpReplyOrRequest)) {
                throw new IllegalArgumentException(
                        "No valid Arp operation field passed");
            }
            return new ActionBuilder().setKey(new ActionKey(actionKey))
                    .setOrder(order)
                    .setAction(ActionUtils.nxLoadArpOpAction(
                            BigInteger.valueOf(((ArpReplyOrRequest) objects[0])
                                    .getArpOperation())))
                    .build();
        }

    },
    /**
     * Set output action to the INPORT, The action sends packets to the packet
     * from which the packet was received
     *
     */
    OUTPUT_TO_INPORT {

        /*
         * (non-Javadoc)
         *
         * @see
         * org.opendaylight.netvirt.vpnmanager.arp.responder.ArpActionField#
         * buildAction(int, int, java.lang.Object[])
         */
        @Override
        public Action buildAction(final int order, final int actionKey,
                final Object... objects) {
            return ActionType.output.buildAction(actionKey, new ActionInfo(
                    ActionType.output, new String[] { "INPORT" }));
        }

    };

    /**
     * Get Action per ENUM value.
     *
     * @param order
     *            Action order number
     * @param actionKey
     *            Action key
     * @param objects
     *            variables needed for action to be generated, it can vary per
     *            ENUM
     * @return Action generated
     */
    public abstract org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action buildAction(
            final int order, final int actionKey, final Object... objects);
}
