package org.opendaylight.netvirt.bgpmanager.commands;


import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.omg.CORBA.Object;
import org.opendaylight.netvirt.bgpmanager.BgpManager;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;

@Command(scope = "odl", name = "multipath", description = "Enable/Disable multipaths")
public class Multipath extends OsgiCommandSupport {

    private static final String AF = "--address-family";

    @Option(name=AF, aliases = {"-f"},
            description="Address family",
            required=true, multiValued=false)
    String addrFamily = null;


    @Argument(name = "enable|disable",
            description = "The desired operation",
            required = true, multiValued = false)
    private String action = null;

    @Override
    protected Object doExecute() throws Exception {

        if (!Commands.bgpRunning()) {
            return null;
        }

        BgpManager bm = Commands.getBgpManager();

        af_afi afi = null;
        af_safi safi = null;

        if (addrFamily != null) {
            if (!addrFamily.equals("lu"))  {
                System.err.println("error: "+AF+" must be lu");
                return null;
            }
            afi = af_afi.findByValue(1);
            safi = af_safi.findByValue(4);
        }
        switch (action) {
            case "enable":
                bm.enableMultipath(afi, safi);
                break;
            case "disable":
                bm.disableMultipath(afi, safi);
                break;
            default:
                return usage();
        }

        return null;
    }

    private Object usage()
    {
        return null;
    }

}