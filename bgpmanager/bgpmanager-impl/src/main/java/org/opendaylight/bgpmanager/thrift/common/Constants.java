package org.opendaylight.bgpmanager.thrift.common;


public class Constants {


        public static final String PROP_MAX_WORKER_THREADS = "bgpthrift.maxWorkerThreads";
        public static final String PROP_MIN_WORKER_THREADS = "bgpthrift.minWorkerThreads";

        //  Configurable parameters
        public static final String PROP_BGP_THRIFT_PORT = "bgp.thrift.service.port";

        // Default configurations

        public static final int BGP_SERVICE_PORT = 6644;
        public static final int DEFAULT_MIN_WORKER_THREADS = 1; //1 client only- quagga server - so 1 thread to service it
        public static final int DEFAULT_MAX_WORKER_THREADS = 1;

        public static final int CL_SKT_TIMEO_MS = 30000;

}
