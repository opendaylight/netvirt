package org.opendaylight.bgpmanager.thrift.client.implementation;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import java.util.*;

import org.opendaylight.bgpmanager.thrift.client.globals.Route;
import org.opendaylight.bgpmanager.thrift.common.Constants;
import org.opendaylight.bgpmanager.thrift.gen.*;
import org.opendaylight.bgpmanager.thrift.exceptions.BgpRouterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BgpRouter {
    private TTransport transport;
    private TSocket sock;
    private TProtocol protocol;
    private static BgpConfigurator.Client bgpClient=null;
    private static final Logger logger = LoggerFactory.getLogger(BgpRouter.class);


    private final static int ADD_NBR = 1;
    private final static int DEL_NBR = 2;
    private final static int ADD_VRF = 3;
    private final static int DEL_VRF = 4;
    private final static int ADD_PFX = 5;
    private final static int DEL_PFX = 6;
    private final static int START_BGP = 7;

    //public final static int BGP_ERR_INITED = 101;
    //public final static int BGP_ERR_NOT_INITED = 102;


    private final static int GET_RTS_INIT = 0;
    private final static int GET_RTS_NEXT = 1;


    private class BgpOp {
        public int type;
        public String nbrIp;
        public int nbrAsNum;
        public String rd;
        public List<String> irts;
        public List<String> erts;
        public String pfx;
        public String nh;
        public int lbl;
        public int asNum;
        public String rtrId;
        public static final int ignore = 0;
        public BgpOp() {}
    }

    static BgpOp bop = null;

    private String bgpHost;
    private int bgpPort;

    public BgpRouter() {
    }


    public void setBgpServer(String host, int port) {
        this.bgpHost = host;
        this.bgpPort = port;
    }

    public void connect(String bgpHost, int bgpPort)
        throws TException, BgpRouterException {
        this.bgpHost = bgpHost;
        this.bgpPort = bgpPort;
        bop = new BgpOp();
        try {
            logger.info("Connecting to BGP Server " + bgpHost + " on port " + bgpPort);
            reInit();
        } catch (Exception e) {
            logger.error("Failed connecting to BGP server ");
            throw e;
        }
    }

    public void disconnect() {
        if(transport != null)
            transport.close();
    }

    public void reInit()
        throws TException, BgpRouterException {
        if(transport != null)
            transport.close();
        transport = new TSocket(bgpHost, bgpPort);
        ((TSocket)transport).setTimeout(Constants.CL_SKT_TIMEO_MS);
        transport.open();
        protocol = new TBinaryProtocol(transport);
        bgpClient = new BgpConfigurator.Client(protocol);
        if(bop == null)
            bop = new BgpOp();
    }

    private void dispatch(BgpOp op)
        throws TException, BgpRouterException {
        int result = 1;

        if (bgpClient == null)
            throw new BgpRouterException(BgpRouterException.BGP_ERR_NOT_INITED);

        switch (op.type) {
            case START_BGP:
                result = bgpClient.startBgpServer(op.asNum, op.rtrId,
                    op.ignore, op.ignore, op.ignore);
                break;
            case ADD_NBR:
                result = bgpClient.createPeer(op.nbrIp, op.nbrAsNum);
                break;
            case DEL_NBR:
                result = bgpClient.deletePeer(op.nbrIp);
                break;
            case ADD_VRF:
                result = bgpClient.addVrf(op.rd, op.irts, op.erts);
                break;
            case DEL_VRF:
                result = bgpClient.delVrf(op.rd);
                break;
            case ADD_PFX:
                result = bgpClient.pushRoute(op.pfx, op.nh, op.rd, op.lbl);
                break;
            case DEL_PFX:
                result = bgpClient.withdrawRoute(op.pfx, op.rd);
                break;
            default: break;
        }
        if (result != 0) throw new BgpRouterException(result);
    }

    public void startBgp(int asNum, String rtrId)
        throws TException, BgpRouterException {
        bop.type = START_BGP;
        bop.asNum = asNum;
        bop.rtrId = rtrId;
        logger.info("Starting BGP Server with as number " + asNum + " and router ID " + rtrId);
        dispatch(bop);
    }

    public synchronized void addNeighbor(String nbrIp, int nbrAsNum)
        throws TException, BgpRouterException {
        bop.type = ADD_NBR;
        bop.nbrIp = nbrIp;
        bop.nbrAsNum = nbrAsNum;
        logger.info("Adding BGP Neighbor " + nbrIp + " with as number " + nbrAsNum);
        dispatch(bop);
    }

    public synchronized void delNeighbor(String nbrIp)
        throws TException, BgpRouterException {
        bop.type = DEL_NBR;
        bop.nbrIp = nbrIp;
        logger.info("Deleting BGP Neighbor " + nbrIp);
        dispatch(bop);
    }

    public synchronized void addVrf(String rd, List<String> irts, List<String> erts)
        throws TException, BgpRouterException {
        bop.type = ADD_VRF;
        bop.rd = rd;
        bop.irts = irts;
        bop.erts = erts;
        logger.info("Adding BGP VRF rd: " + rd);
        dispatch(bop);
    }

    public synchronized void delVrf(String rd)
        throws TException, BgpRouterException {
        bop.type = DEL_VRF;
        bop.rd = rd;
        logger.info("Deleting BGP VRF rd: " + rd);
        dispatch(bop);
    }

    public synchronized void addPrefix(String rd, String prefix, String nexthop, int label)
        throws TException, BgpRouterException {
        bop.type = ADD_PFX;
        bop.rd = rd;
        bop.pfx = prefix;
        bop.nh = nexthop;
        bop.lbl = label;
        logger.info("Adding BGP route - rd:" + rd + " prefix:" + prefix + " nexthop:" + nexthop + " label:" + label);
        dispatch(bop);
    }

    public synchronized void delPrefix(String rd, String prefix)
        throws TException, BgpRouterException {
        bop.type = DEL_PFX;
        bop.rd = rd;
        bop.pfx = prefix;
        logger.info("Deleting BGP route - rd:" + rd + " prefix:" + prefix);
        dispatch(bop);
    }

    public int initRibSync(BgpSyncHandle handle)
        throws TException, BgpRouterException {
        if (bgpClient == null)
            throw new BgpRouterException(BgpRouterException.BGP_ERR_NOT_INITED);
        if (handle.getState() == BgpSyncHandle.ITERATING)
            return BgpRouterException.BGP_ERR_IN_ITER;
        handle.setState(BgpSyncHandle.INITED);
        handle.setMore(1);
        return 0;
    }

    public int endRibSync(BgpSyncHandle handle)
        throws TException, BgpRouterException {
        if (bgpClient == null)
            throw new BgpRouterException(BgpRouterException.BGP_ERR_NOT_INITED);
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

    public Routes doRibSync(BgpSyncHandle handle)
        throws TException, BgpRouterException {
        if (bgpClient == null)
            throw new BgpRouterException(BgpRouterException.BGP_ERR_NOT_INITED);
        int state = handle.getState();
        if (state != BgpSyncHandle.INITED && state != BgpSyncHandle.ITERATING) {
            Routes r = new Routes();
            r.setErrcode(BgpRouterException.BGP_ERR_NOT_ITER);
            return r;
        }
        int op = (state == BgpSyncHandle.INITED) ?
            GET_RTS_INIT : GET_RTS_NEXT;
        handle.setState(BgpSyncHandle.ITERATING);
        int winSize = handle.getMaxCount()*handle.getRouteSize();
        Routes outRoutes = bgpClient.getRoutes(op, winSize);
       if (outRoutes.errcode != 0)
            return outRoutes;
        handle.setMore(outRoutes.more);
        if (outRoutes.more == 0)
            handle.setState(BgpSyncHandle.DONE);
        return outRoutes;
    }

    //We would support this only when we support controller restarts
    public void doRouteSync()
        throws TException, BgpRouterException {
        BgpSyncHandle bsh = BgpSyncHandle.getInstance();

        try {
            logger.info("Starting BGP Route sync.. ");
            initRibSync(bsh);
            while (bsh.getState() != bsh.DONE) {
                Routes r = doRibSync(bsh);
                if(r.getErrcode() == BgpRouterException.BGP_ERR_INACTIVE) {
                    //BGP server is inactive; log and return
                    logger.error("BGP Server is inactive. Failed BGP Route sync");
                    return;
                }
                Iterator<Update> iter = r.getUpdatesIterator();
                while (iter.hasNext()) {
                    Update u = iter.next();
                    Route route = new Route(u.rd, u.prefix, u.prefixlen, u.nexthop, u.label);
                    //Add to FIB??
                    /*
                    if(bgpRouteCb != null) {
                        bgpRouteCb.setRoute(route);
                    }*/
                }
            }
            endRibSync(bsh);
            logger.info("Completed BGP Route sync.");
        }  catch (Exception e) {
            throw e;
        }
    };


    public List<Route> getRoutes()
        throws TException, BgpRouterException {

        BgpSyncHandle bsh = BgpSyncHandle.getInstance();
        List<Route> allRoutes = new ArrayList<Route>();

        try {
            initRibSync(bsh);
            while (bsh.getState() != bsh.DONE) {
                Routes r = doRibSync(bsh);
                Iterator<Update> iter = r.getUpdatesIterator();
                while (iter.hasNext()) {
                    Update u = iter.next();
                    Route route = new Route(u.rd, u.prefix, u.prefixlen, u.nexthop, u.label);

                    allRoutes.add(route);
                }
            }
            endRibSync(bsh);
        }  catch (Exception e) {
            throw e;
        }
        return allRoutes;
    };


}
