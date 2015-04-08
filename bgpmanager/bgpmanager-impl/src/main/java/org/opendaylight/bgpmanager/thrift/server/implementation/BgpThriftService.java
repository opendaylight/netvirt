package org.opendaylight.bgpmanager.thrift.server.implementation;

import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.*;
import org.opendaylight.bgpmanager.thrift.common.Constants;
import org.opendaylight.bgpmanager.thrift.gen.BgpUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpThriftService {
	
	private static final Logger logger = LoggerFactory.getLogger(BgpThriftService.class);
	
	private int port;
	//private int serverType;
	private int maxWorkerThreads;
	private int minWorkerThreads;
    private TServerTransport serverTransport;
	//private TNonblockingServerTransport serverTransport;
    private TServer server;
	private BgpUpdateHandler notificationHandler;
    
    public BgpThriftService() {
    	
    }


	public void start() {
		logger.info("BGP Thrift Server starting.");
		startBgpThriftServer();
	}
	
	public void stop() {
		logger.info("BGP Thrift Server stopping.");
		stopBgpThriftServer();
	}

	/**
	 * Destroy method called up after the bundle has been stopped
	 */
	public void destroy() {
		logger.debug("BGP Thrift Server destroy ");
	}

    /**
     * Loading the parameters required for a connection
     * 
     */
    private void loadParameters() {
        port = Integer.getInteger(Constants.PROP_BGP_THRIFT_PORT, Constants.BGP_SERVICE_PORT);
		maxWorkerThreads = Integer.getInteger(Constants.PROP_MAX_WORKER_THREADS,
			Constants.DEFAULT_MAX_WORKER_THREADS);
		minWorkerThreads = Integer.getInteger(Constants.PROP_MIN_WORKER_THREADS,
			Constants.DEFAULT_MIN_WORKER_THREADS);

	}


	public void startBgpThriftServer() {
		loadParameters();
		new Thread(new ThriftRunnable()).start();
	}

	public void stopBgpThriftServer() {
		try {
            logger.debug("Server stopping");

            if (serverTransport != null) {
                serverTransport.close();
            }
            
            server.stop();
        } catch (Exception e) {
            logger.error("Error while stopping the server - {} {}", getClass().getName(), e.getMessage());
        }
	}
	
	private class ThriftRunnable implements Runnable {
		@Override
		public void run() {
			notificationHandler = new BgpUpdateHandler();

	        try {
				serverTransport = new TServerSocket(port);
				//serverTransport = new TNonblockingServerSocket(port);
	            logger.info("Server Socket on Port {} ", port);
			} catch (TTransportException e) {			
				e.printStackTrace();
				return;
			}
			/* This may need to change. Right now, its as good as a blocking server for each client (client would be
			single - qbgp). We may want to change the server to TSimpleServer which queues up the notifications and
			let worker threads process notifications based on rd.
			 */
	        server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport)
	                .maxWorkerThreads(maxWorkerThreads).minWorkerThreads(minWorkerThreads)
	                .processor(new BgpUpdater.Processor<BgpUpdateHandler>(notificationHandler)));
			/*
			THsHaServer.Args args = new THsHaServer.Args(serverTransport);
			args.workerThreads(10);
			server = new THsHaServer(args.processor(new BgpUpdater.Processor<BgpUpdateHandler>(notificationHandler)));*/

			server.serve();
		}		
	}

}
