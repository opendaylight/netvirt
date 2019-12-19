package org.opendaylight.netvirt.elan;

public class EgressActionMissingException extends Exception {

    private static final long serialVersionUID = 1L;

    public EgressActionMissingException(String message, Throwable cause) {
        super(message, cause);
    }

    public EgressActionMissingException(String message) {
        super(message);
    }

}