/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryOnException {
    public static final int DEFAULT_RETRIES = Integer.MAX_VALUE;
    public static final long TIME_IN_MILLI = 1000;
    public static final int MAX_DELAY_FACTOR = 15;
    private static final Logger LOG = LoggerFactory.getLogger(BgpConfigurationManager.class);

    private int numberOfTriesLeft;
    private int delayFactor;

    public RetryOnException(int numberOfRetries) {
        numberOfTriesLeft = numberOfRetries;
    }

    public int getNumberOfTriesLeft() {
        return numberOfTriesLeft;
    }

    public boolean shouldRetry() {
        return numberOfTriesLeft > 0;
    }

    public boolean decrementAndRetry() {
        numberOfTriesLeft--;
        return numberOfTriesLeft > 0;
    }

    public void errorOccured() {
        numberOfTriesLeft--;
        delayFactor++;
        LOG.info("number of retries left {} delay factor {}", numberOfTriesLeft, delayFactor);
        if (!shouldRetry()) {
            return;
        }
        waitUntilNextTry();
    }

    public void errorOccured(int decrementTries) {
        numberOfTriesLeft = numberOfTriesLeft - decrementTries;
        delayFactor++;
        LOG.info("number of retries left {} delay factor {}", numberOfTriesLeft, delayFactor);
        if (!shouldRetry()) {
            return;
        }
        waitUntilNextTry();
    }


    public long getTimeToWait() {
    /*
     Disabling exponential backoff
       if (delayFactor > MAX_DELAY_FACTOR) { // max wait time is 225 seconds
            delayFactor = 1;
        }
        return delayFactor * delayFactor * TIME_IN_MILLI;
     */
        return TIME_IN_MILLI;
    }

    private void waitUntilNextTry() {
        try {
            Thread.sleep(getTimeToWait());
        } catch (InterruptedException ignored) {
            LOG.error("Exception while waiting for next try", ignored);
        }
    }
}
