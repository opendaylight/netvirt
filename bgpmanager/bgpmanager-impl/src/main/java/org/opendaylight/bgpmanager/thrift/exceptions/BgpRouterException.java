package org.opendaylight.bgpmanager.thrift.exceptions;

public class BgpRouterException extends Exception {
    public final static int BGP_ERR_INITED = 101;
    public final static int BGP_ERR_NOT_INITED = 102;

    // the following consts are server-dictated. do not modify
    public final static int BGP_ERR_ACTIVE = 10;
    public final static int BGP_ERR_INACTIVE = 11;
    public final static int BGP_ERR_COMM = 12;
    public final static int BGP_ERR_LOCAL = 13;
    public final static int BGP_ERR_IN_ITER =  14;
    public final static int BGP_ERR_NOT_ITER = 15;
    public final static int BGP_ERR_UNKNOWN = 100;


    public BgpRouterException(int cause) {
        errcode = cause;
    }

    public int getErrorCode() {
        return errcode;
    }
    
    private int errcode;
}
