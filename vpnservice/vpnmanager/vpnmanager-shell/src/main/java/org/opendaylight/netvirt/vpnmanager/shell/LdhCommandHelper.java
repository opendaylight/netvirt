package org.opendaylight.netvirt.vpnmanager.shell;

import org.apache.karaf.shell.commands.Option;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "ldh", name = "debug",
        description = "get counters for Listener Dependency Helper")
public class LdhCommandHelper extends OsgiCommandSupport {

    @Option(name = "--detail", description = "print all HashMaps and lists", required = false, multiValued = false)
    String detailString;

    private static final Logger LOG = LoggerFactory.getLogger(AsyncDataTreeChangeListenerBase.class);

    @Override
    protected Object doExecute() {
        try {
            LOG.debug("LDHdebugHelper: debug ");
            LdhDataTreeChangeListenerBase.ldhDebug(detailString);
        } catch (Exception e) {
            throw e;
        }
        return null;
    }

}

