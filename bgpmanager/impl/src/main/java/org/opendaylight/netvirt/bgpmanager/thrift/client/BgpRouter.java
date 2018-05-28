/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.thrift.client;

import com.google.common.annotations.VisibleForTesting;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;
import org.opendaylight.netvirt.bgpmanager.RetryOnException;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.BgpConfigurator;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.Routes;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.encap_type;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.layer_type;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.opendaylight.ovsdb.utils.mdsal.utils.TransactionHistory;
import org.opendaylight.ovsdb.utils.mdsal.utils.TransactionType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.LayerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BgpRouter {
    private static final Logger LOG = LoggerFactory.getLogger(BgpRouter.class);

    private static final int THRIFT_TIMEOUT_MILLI = 10000;
    private static final int GET_RTS_INIT = 0;
    private static final int GET_RTS_NEXT = 1;
    private static final int CONNECTION_TIMEOUT = 60000;

    private enum Optype {
        START, STOP, NBR, VRF, PFX, SRC, MHOP, LOG, AF, GR, MP, VRFMP, EOR, DELAY_EOR
    }

    private static class BgpOp {
        static final int IGNORE = 0;

        Optype type;
        boolean add;
        String[] strs;
        int[] ints;
        List<String> irts;
        List<String> erts;
        long asNumber;
        layer_type thriftLayerType;
        protocol_type thriftProtocolType;
        int ethernetTag;
        String esi;
        String macAddress;
        int l2label;
        int l3label;
        encap_type thriftEncapType;
        String routermac;
        public af_afi afi;
        int delayEOR;
        public af_safi safi;

        BgpOp() {
            strs = new String[3];
            ints = new int[2];
        }

        BgpOp(BgpOp bgpOp) {
            strs = new String[3];
            ints = new int[2];
            this.afi = bgpOp.afi;
            this.safi = bgpOp.safi;
            this.type = bgpOp.type;
            this.add = bgpOp.add;
            this.asNumber = bgpOp.asNumber;
            this.thriftProtocolType = bgpOp.thriftProtocolType;
            this.thriftLayerType = bgpOp.thriftLayerType;
            this.ethernetTag = bgpOp.ethernetTag;
            this.esi = bgpOp.esi;
            this.macAddress = bgpOp.macAddress;
            this.l2label = bgpOp.l2label;
            this.l3label = bgpOp.l3label;
            this.routermac = bgpOp.routermac;
            this.thriftEncapType = bgpOp.thriftEncapType;
            this.delayEOR = bgpOp.delayEOR;

        }

        @Override
        public String toString() {
            //TODO pretty print
            return "BgpOp{"
                    + "type=" + type
                    + ", add=" + add
                    + ", strs=" + Arrays.toString(strs)
                    + ", ints=" + Arrays.toString(ints)
                    + ", irts=" + irts
                    + ", erts=" + erts
                    + ", asNumber=" + asNumber
                    + ", thriftLayerType=" + thriftLayerType
                    + ", thriftProtocolType=" + thriftProtocolType
                    + ", ethernetTag=" + ethernetTag
                    + ", esi='" + esi + '\''
                    + ", macAddress='" + macAddress + '\''
                    + ", l2label=" + l2label
                    + ", l3label=" + l3label
                    + ", thriftEncapType=" + thriftEncapType
                    + ", routermac='" + routermac + '\''
                    + ", afi=" + afi
                    + ", delayEOR=" + delayEOR
                    + ", safi=" + safi
                    + '}' + '\n';
        }
    }

    private final BgpOp bop = new BgpOp();
    private final Supplier<Bgp> bgpConfigSupplier;
    private final BooleanSupplier isBGPEntityOwner;

    private volatile TTransport transport;
    private volatile BgpConfigurator.Client bgpClient;
    private volatile boolean isConnected = false;
    private volatile long startTS;
    private volatile long connectTS;
    private volatile long lastConnectedTS;
    private volatile boolean configServerUpdated = false;
    private final TransactionHistory transactionHistory;

    private BgpRouter(Supplier<Bgp> bgpConfigSupplier, BooleanSupplier isBGPEntityOwner,
                      TransactionHistory transactionHistory) {
        this.bgpConfigSupplier = bgpConfigSupplier;
        this.isBGPEntityOwner = isBGPEntityOwner;
        this.transactionHistory = transactionHistory;
    }

    // private ctor FOR UNIT TESTS ONLY
    private BgpRouter(BgpConfigurator.Client bgpClient) {
        this(() -> null, () -> false, new TransactionHistory(10000, 7500));
        this.bgpClient = bgpClient;
    }

    // FOR UNIT TESTS ONLY
    @VisibleForTesting
    static BgpRouter newTestingInstance(BgpConfigurator.Client bgpClient) {
        return new BgpRouter(bgpClient);
    }

    public static BgpRouter newInstance(Supplier<Bgp> bgpConfigSupplier, BooleanSupplier isEntityBGPOwner,
                                        TransactionHistory transactionHistory) {
        return new BgpRouter(bgpConfigSupplier, isEntityBGPOwner, transactionHistory);
    }

    public TTransport getTransport() {
        return transport;
    }

    public long getLastConnectedTS() {
        return lastConnectedTS;
    }

    public void setLastConnectedTS(long lastConnectedTS) {
        this.lastConnectedTS = lastConnectedTS;
    }

    public long getConnectTS() {
        return connectTS;
    }

    public void setConnectTS(long connectTS) {
        this.connectTS = connectTS;
    }

    public long getStartTS() {
        return startTS;
    }

    public void setStartTS(long startTS) {
        this.startTS = startTS;
    }

    public void configServerUpdated() {
        configServerUpdated = true;
    }

    public synchronized void disconnect() {
        bgpClient = null;
        isConnected = false;
        if (transport != null) {
            transport.close();
        }
    }

    public synchronized boolean connect(String bgpHost, int bgpPort) {
        String msgPiece = "BGP config server at " + bgpHost + ":" + bgpPort;

        if (!BgpConfigurationManager.isValidConfigBgpHostPort(bgpHost, bgpPort)) {
            LOG.error("Invalid config server host: {}, port: {}", bgpHost, bgpPort);
            return false;
        }

        final int numberOfConnectRetries = 180;
        configServerUpdated = false;
        RetryOnException connectRetry = new RetryOnException(numberOfConnectRetries);

        disconnect();
        setConnectTS(System.currentTimeMillis());
        do {
            if (!isBGPEntityOwner.getAsBoolean()) {
                LOG.error("Non Entity BGP owner trying to connect to thrift. Returning");
                isConnected = false;
                return false;
            }
            if (configServerUpdated) {
                LOG.error("Config server updated while connecting to server {} {}", bgpHost, bgpPort);
                isConnected = false;
                return false;
            }
            try {
                LOG.error("Trying to connect BGP config server at {} : {}", bgpHost, bgpPort);
                TSocket ts = new TSocket(bgpHost, bgpPort, CONNECTION_TIMEOUT);
                transport = ts;
                transport.open();
                ts.setTimeout(THRIFT_TIMEOUT_MILLI);
                isConnected = true;
                setLastConnectedTS(System.currentTimeMillis());
                LOG.error("Connected to BGP config server at {} : {}", bgpHost, bgpPort);
                break;
            } catch (TTransportException tte) {
                LOG.debug("Failed connecting to BGP config server at {} : {}. msg: {}; Exception :",
                        bgpHost, bgpPort, msgPiece, tte);
                if (tte.getCause() instanceof ConnectException) {
                    LOG.debug("Connect exception. Failed connecting to BGP config server at {} : {}. "
                            + "msg: {}; Exception :", bgpHost, bgpPort, msgPiece, tte);
                    connectRetry.errorOccured();
                } else {
                    //In Case of other exceptions we try only 3 times
                    connectRetry.errorOccured(60);
                }
            }
        } while (connectRetry.shouldRetry());

        if (!connectRetry.shouldRetry()) {
            isConnected = false;
            return false;
        }

        bgpClient = new BgpConfigurator.Client(new TBinaryProtocol(transport));
        LOG.info("Connected to {}", msgPiece);
        return true;
    }

    public boolean isBgpConnected() {
        return isConnected;
    }

    private void dispatch(BgpOp op) throws TException, BgpRouterException {
        try {
            dispatchInternal(op);
            LOG.error("Doing op {}", op);
            transactionHistory.addToHistory(getTransactionType(op), new BgpOp(op));
            LOG.error("History size is {}", transactionHistory.getElements().size());
            transactionHistory.getElements().forEach(ele -> LOG.error("history is {}", ele.getData()));
        } catch (TTransportException tte) {
            LOG.error("dispatch command to qthriftd failed, command: {}, exception:", op.toString(), tte);
            reConnect(tte);
            dispatchInternal(op);
        }
    }

    private TransactionType getTransactionType(BgpOp op) {
        return op.add ? TransactionType.ADD : TransactionType.DELETE;
    }

    private void reConnect(TTransportException tte) {
        Bgp bgpConfig = bgpConfigSupplier.get();
        if (bgpConfig != null) {
            LOG.error("Received TTransportException, while configuring qthriftd, goind for Disconnect/Connect "
                            + " Host: {}, Port: {}", bgpConfig.getConfigServer().getHost().getValue(),
                    bgpConfig.getConfigServer().getPort().intValue());
            disconnect();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                LOG.error("Exception wile reconnecting ", e);
            }
            connect(bgpConfig.getConfigServer().getHost().getValue(),
                    bgpConfig.getConfigServer().getPort().intValue());
        } else {
            LOG.error("Unable to send commands to thrift and fetch bgp configuration", tte);
        }
    }

    private void dispatchInternal(BgpOp op) throws TException, BgpRouterException {
        int result = 1;

        if (bgpClient == null) {
            throw new BgpRouterException(BgpRouterException.BGP_ERR_NOT_INITED);
        }

        if (op.type == null) {
            LOG.error("dispatchInternal called with op.type null", new Throwable("stack trace"));
            return;
        }

        af_afi afi = af_afi.findByValue(op.ints[0]);
        af_safi safi = af_safi.findByValue(op.ints[1]);

        switch (op.type) {
            case START:
                setStartTS(System.currentTimeMillis());
                LOG.debug("startBgp thrift call for AsId {}", op.asNumber);
                result = bgpClient.startBgp(op.asNumber, op.strs[0],
                        BgpOp.IGNORE, BgpOp.IGNORE, BgpOp.IGNORE, op.ints[0], op.add);
                LOG.debug("Result of startBgp thrift call for AsId {} : {}", op.asNumber, result);
                break;
            case STOP:
                result = bgpClient.stopBgp(op.asNumber);
                break;
            case NBR:
                if (bop.add) {
                    result = bgpClient.createPeer(op.strs[0], op.asNumber);
                    if (result == 0 && op.strs[1] != null) { // createPeer worked and password is specified
                        result = bgpClient.setPeerSecret(op.strs[0], op.strs[1]);
                        if (result != 0) {
                            throw new BgpRouterException(BgpRouterException.Function.SET_PEER_SECRET, result);
                        }
                    }
                } else { // delete
                    result = bgpClient.deletePeer(op.strs[0]);
                }
                break;
            case VRF:
                result = bop.add
                           ? bgpClient.addVrf(op.thriftLayerType, op.strs[0], op.irts, op.erts, op.afi, op.safi)
                           : bgpClient.delVrf(op.strs[0], op.afi, op.safi);
                break;
            case PFX:
                // order of args is different in addPrefix(), hence the
                // seeming out-of-order-ness of string indices
                afi = af_afi.findByValue(org.opendaylight.netvirt.bgpmanager.BgpUtil
                        .getAFItranslatedfromPrefix(op.strs[1]));
                result = bop.add
                        ? bgpClient.pushRoute(
                                op.thriftProtocolType,
                                op.strs[1],//prefix
                                op.strs[2],//nexthop
                                op.strs[0],//rd
                                op.ethernetTag,
                                op.esi,
                                op.macAddress,
                                op.l3label,
                                op.l2label,
                                op.thriftEncapType,
                                op.routermac,
                                afi)

                        : bgpClient.withdrawRoute(
                        op.thriftProtocolType,
                        op.strs[1],//prefix
                        op.strs[0],//rd
                        op.ethernetTag,
                        op.esi,
                        op.macAddress,
                        afi);
                break;
            case LOG:
                result = bgpClient.setLogConfig(op.strs[0], op.strs[1]);
                break;
            case MHOP:
                result = bop.add
                        ? bgpClient.setEbgpMultihop(op.strs[0], op.ints[0])
                        : bgpClient.unsetEbgpMultihop(op.strs[0]);
                break;
            case SRC:
                result = bop.add
                        ? bgpClient.setUpdateSource(op.strs[0], op.strs[1])
                        : bgpClient.unsetUpdateSource(op.strs[0]);
                break;
            case AF:
                result = bop.add
                        ? bgpClient.enableAddressFamily(op.strs[0], afi, safi)
                        : bgpClient.disableAddressFamily(op.strs[0], afi, safi);
                break;
            case GR:
                result = bop.add
                        ? bgpClient.enableGracefulRestart(op.ints[0])
                        : bgpClient.disableGracefulRestart();
                break;
            case MP:
                result = bop.add
                        ? bgpClient.enableMultipath(afi, safi)
                        : bgpClient.disableMultipath(afi, safi);
                break;
            case VRFMP:
                result = bgpClient.multipaths(bop.strs[0], bop.ints[0]);
                break;
            case EOR:
                result = bgpClient.sendEOR();
                break;
            case DELAY_EOR:
                bgpClient.send_enableEORDelay(op.delayEOR);
                break;
            default:
                break;
        }
        if (result != 0) {
            throw new BgpRouterException(result);
        }
    }

    public synchronized void startBgp(long asNum, String rtrId, int stalepathTime, boolean announceFbit)
            throws TException, BgpRouterException {
        bop.type = Optype.START;
        bop.add = announceFbit;
        bop.asNumber = asNum;
        bop.ints[0] = stalepathTime;
        bop.strs[0] = rtrId;
        LOG.debug("Starting BGP with as number {} and router ID {} StalePathTime: {}", asNum, rtrId, stalepathTime);
        dispatch(bop);
    }

    public synchronized void stopBgp(long asNum)
            throws TException, BgpRouterException {
        bop.type = Optype.STOP;
        bop.asNumber = asNum;
        LOG.debug("Stopping BGP with as number {}", asNum);
        dispatch(bop);
    }

    public synchronized void addNeighbor(String nbrIp, long nbrAsNum, @Nullable String md5Secret)
            throws TException, BgpRouterException {
        if (md5Secret == null) {
            LOG.debug("Adding BGP Neighbor {} with as number {} ", nbrIp, nbrAsNum);
        } else {
            LOG.debug("Adding BGP Neighbor {} with as number {} and MD5 secret {}", nbrIp, nbrAsNum, md5Secret);
        }
        bop.type = Optype.NBR;
        bop.add = true;
        bop.strs[0] = nbrIp;
        bop.asNumber = nbrAsNum;
        bop.strs[1] = md5Secret;
        dispatch(bop);
    } // public addNeighbor( nbrIp, nbrAsNum, md5Secret )

    public synchronized void delNeighbor(String nbrIp) throws TException, BgpRouterException {
        bop.type = Optype.NBR;
        bop.add = false;
        bop.strs[0] = nbrIp;
        LOG.debug("Deleting BGP Neighbor {} ", nbrIp);
        dispatch(bop);
    }

    public synchronized void addVrf(LayerType layerType, String rd, List<String> irts, List<String> erts)
            throws TException, BgpRouterException {
        bop.thriftLayerType = layerType == LayerType.LAYER2 ? layer_type.LAYER_2 : layer_type.LAYER_3;
        bop.type = Optype.VRF;
        bop.add = true;
        bop.strs[0] = rd;
        bop.irts = irts;
        bop.erts = erts;
        LOG.debug("Adding BGP VRF rd: {} ", rd);
        dispatch(bop);
    }

    public synchronized void delVrf(String rd, long afi, long safi) throws TException, BgpRouterException {
        bop.type = Optype.VRF;
        bop.add = false;
        bop.strs[0] = rd;
        bop.afi = af_afi.findByValue((int)afi);
        bop.safi = af_safi.findByValue((int)safi);
        LOG.debug("Deleting BGP VRF rd: {}", rd);
        dispatch(bop);
    }

    // bit of a mess-up: the order of arguments is different in
    // the Thrift RPC: prefix-nexthop-rd-label.

    public synchronized void addPrefix(String rd,
                                       String prefix,
                                       String nexthop,
                                       int label,
                                       int l3vni,
                                       int l2vni,
                                       protocol_type protocolType,
                                       int ethtag,
                                       String esi,
                                       String macaddress,
                                       encap_type encapType,
                                       String routermac)
            throws TException, BgpRouterException {
        bop.type = Optype.PFX;
        bop.add = true;
        bop.strs[0] = rd;
        bop.strs[1] = prefix;
        bop.strs[2] = nexthop;
        // TODO: set label2 or label3 based on encapsulation type and protocol type once L2label is applicable
        bop.ints[0] = label;
        if (protocolType.equals(protocol_type.PROTOCOL_EVPN) && encapType.equals(encap_type.VXLAN)) {
            bop.l3label = l3vni; //L3VPN Over VxLan
            bop.l2label = l2vni;
        } else {
            bop.l3label = label; // L3VPN Over MPLSGRE
        }
        bop.thriftProtocolType = protocolType;
        bop.ethernetTag = ethtag;
        bop.esi = esi;
        bop.macAddress = macaddress;
        bop.thriftEncapType = encapType;
        bop.routermac = routermac;

        LOG.debug("Adding BGP route - rd:{} prefix:{} nexthop:{} label:{} ", rd ,prefix, nexthop, label);
        dispatch(bop);
    }

    public synchronized void delPrefix(String rd, String prefix) throws TException, BgpRouterException {
        bop.type = Optype.PFX;
        bop.add = false;
        bop.strs[0] = rd;
        bop.strs[1] = prefix;
        LOG.debug("Deleting BGP route - rd:{} prefix:{} ", rd, prefix);
        dispatch(bop);
    }

    public int initRibSync(BgpSyncHandle handle) throws BgpRouterException {
        if (bgpClient == null) {
            throw new BgpRouterException(BgpRouterException.BGP_ERR_NOT_INITED);
        }
        if (handle.getState() == BgpSyncHandle.ITERATING) {
            return BgpRouterException.BGP_ERR_IN_ITER;
        }
        handle.setState(BgpSyncHandle.INITED);
        return 0;
    }

    public int endRibSync(BgpSyncHandle handle) throws BgpRouterException {
        if (bgpClient == null) {
            throw new BgpRouterException(BgpRouterException.BGP_ERR_NOT_INITED);
        }
        int state = handle.getState();
        switch (state) {
            case BgpSyncHandle.INITED:
            case BgpSyncHandle.ITERATING:
                handle.setState(BgpSyncHandle.ABORTED);
                break;
            case BgpSyncHandle.DONE:
                break;
            case BgpSyncHandle.NEVER_DONE:
                return BgpRouterException.BGP_ERR_NOT_ITER;
            default:
                break;
        }
        return 0;
    }

    public Routes doRibSync(BgpSyncHandle handle, af_afi afi) throws TException, BgpRouterException {
        if (bgpClient == null) {
            throw new BgpRouterException(BgpRouterException.BGP_ERR_NOT_INITED);
        }
        int state = handle.getState();
        if (state != BgpSyncHandle.INITED && state != BgpSyncHandle.ITERATING) {
            Routes routes = new Routes();
            routes.setErrcode(BgpRouterException.BGP_ERR_NOT_ITER);
            return routes;
        }
        int op = state == BgpSyncHandle.INITED ? GET_RTS_INIT : GET_RTS_NEXT;
        handle.setState(BgpSyncHandle.ITERATING);
        int winSize = handle.getMaxCount() * handle.getRouteSize();


        // TODO: receive correct protocol_type here, currently populating with dummy protocol type
        Routes outRoutes = bgpClient.getRoutes(protocol_type.PROTOCOL_ANY, op, winSize, afi);
        if (outRoutes.more == 0) {
            handle.setState(BgpSyncHandle.DONE);
        }
        return outRoutes;
    }

    public synchronized void setLogging(String fileName, String debugLevel) throws TException, BgpRouterException {
        bop.type = Optype.LOG;
        bop.strs[0] = fileName;
        bop.strs[1] = debugLevel;
        LOG.debug("Setting Log file to BGP VRF rd: {}, {}", fileName, debugLevel);
        dispatch(bop);
    }

    public synchronized void addEbgpMultihop(String nbrIp, int nhops) throws TException, BgpRouterException {
        bop.type = Optype.MHOP;
        bop.add = true;
        bop.strs[0] = nbrIp;
        bop.ints[0] = nhops;
        LOG.debug("ebgp-multihop set for peer {}, num hops = {}",
                nbrIp, nhops);
        dispatch(bop);
    }

    public synchronized void delEbgpMultihop(String nbrIp) throws TException, BgpRouterException {
        bop.type = Optype.MHOP;
        bop.add = false;
        bop.strs[0] = nbrIp;
        LOG.debug("ebgp-multihop deleted for peer {}", nbrIp);
        dispatch(bop);
    }

    public synchronized void addUpdateSource(String nbrIp, String srcIp) throws TException, BgpRouterException {
        bop.type = Optype.SRC;
        bop.add = true;
        bop.strs[0] = nbrIp;
        bop.strs[1] = srcIp;
        LOG.debug("update-source added for peer {}, src-ip = {}",
                nbrIp, srcIp);
        dispatch(bop);
    }

    public synchronized void delUpdateSource(String nbrIp) throws TException, BgpRouterException {
        bop.type = Optype.SRC;
        bop.add = false;
        bop.strs[0] = nbrIp;
        LOG.debug("update-source deleted for peer {}", nbrIp);
        dispatch(bop);
    }

    public synchronized void addAddressFamily(String nbrIp, af_afi afi, af_safi safi)
            throws TException, BgpRouterException {
        bop.type = Optype.AF;
        bop.add = true;
        bop.strs[0] = nbrIp;
        bop.ints[0] = afi.getValue();
        bop.ints[1] = safi.getValue();
        LOG.debug("addr family added for peer {}, afi = {}, safi = {}",
                nbrIp, bop.ints[0], bop.ints[1]);
        dispatch(bop);
    }

    public synchronized void delAddressFamily(String nbrIp, af_afi afi, af_safi safi)
            throws TException, BgpRouterException {
        bop.type = Optype.AF;
        bop.add = false;
        bop.strs[0] = nbrIp;
        bop.ints[0] = afi.getValue();
        bop.ints[1] = safi.getValue();
        LOG.debug("addr family deleted for peer {}, afi = {}, safi = {}",
                nbrIp, bop.ints[0], bop.ints[1]);
        dispatch(bop);
    }

    public synchronized void addGracefulRestart(int stalepathTime) throws TException, BgpRouterException {
        bop.type = Optype.GR;
        bop.add = true;
        bop.ints[0] = stalepathTime;
        LOG.debug("graceful restart added, stale-path-time = {}",
                stalepathTime);
        dispatch(bop);
    }

    public synchronized void delGracefulRestart() throws TException, BgpRouterException {
        bop.type = Optype.GR;
        bop.add = false;
        LOG.debug("graceful restart deleted");
        dispatch(bop);
    }

    public synchronized void enableMultipath(af_afi afi, af_safi safi) throws TException, BgpRouterException {
        bop.type = Optype.MP;
        bop.add = true;
        LOG.debug("Enabling multipath for afi {}, safi {}", afi.getValue(), safi.getValue());
        bop.ints[0] = afi.getValue();
        bop.ints[1] = safi.getValue();
        dispatch(bop);
    }

    public synchronized void disableMultipath(af_afi afi, af_safi safi) throws TException, BgpRouterException {
        bop.type = Optype.MP;
        bop.add = false;
        LOG.debug("Disabling multipath for afi {}, safi {}", afi.getValue(), safi.getValue());
        bop.ints[0] = afi.getValue();
        bop.ints[1] = safi.getValue();
        dispatch(bop);
    }

    public synchronized void multipaths(String rd, int maxpath) throws TException, BgpRouterException {
        bop.type = Optype.VRFMP;
        bop.strs[0] = rd;
        bop.ints[0] = maxpath;
        dispatch(bop);
    }

    public synchronized void sendEOR() throws TException, BgpRouterException {
        bop.type = Optype.EOR;
        LOG.debug("EOR message sent");
        dispatch(bop);
    }

    public synchronized void delayEOR(int delay) throws TException, BgpRouterException {
        bop.type = Optype.DELAY_EOR;
        bop.delayEOR = delay;
        LOG.debug("EOR delay time in Seconds sent");
        dispatch(bop);
    }
}

