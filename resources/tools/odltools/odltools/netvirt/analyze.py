from odltools.netvirt import utils
from odltools.mdsal.models import constants
from odltools.mdsal.models import ietf_interfaces
from odltools.mdsal.models import itm_state
from odltools.mdsal.models import l3vpn
from odltools.mdsal.models import neutron
from odltools.mdsal.models.model import Model


def get_all_flows(modules=['ifm'], filter=[]):
    return []


def sort(data, field):
    return sorted(data, key=lambda x: x[field])


def print_keys(ifaces, ifstates):
    print "InterfaceNames: {}\n".format(ifaces.keys())
    print
    print "IfStateNames: {}".format(ifstates.keys())


def by_ifname(args, ifname, ifstates, ifaces):
    itm_state_tunnels_state = itm_state.tunnels_state(Model.OPERATIONAL, args)
    neutron_neutron = neutron.neutron(Model.CONFIG, args)
    ifstate = ifstates.get(ifname)
    iface = ifaces.get(ifname)
    port = None
    tunnel = None
    tun_state = None
    if iface and iface.get('type') == constants.IFTYPE_VLAN:
        ports = neutron_neutron.get_ports_by_key()
        port = ports.get(ifname)
    elif iface and iface.get('type') == constants.IFTYPE_TUNNEL:
        tunnels = itm_state_tunnels_state.get_tunnels_by_key()
        tunnel = tunnels.get(ifname)
        tun_states = itm_state_tunnels_state.get_tunnel_states()
        tun_state = tun_states.get(ifname)
    else:
        print "UNSUPPORTED IfType"
    return iface, ifstate, port, tunnel, tun_state


def analyze_interface(args):
    ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, args)
    ifaces = ietf_interfaces_interfaces.get_interfaces_by_key()

    ietf_interfaces_interfaces_state = ietf_interfaces.interfaces_state(Model.OPERATIONAL, args)
    ifstates = ietf_interfaces_interfaces_state.get_interfaces_by_key()

    if not args.ifname:
        print_keys(ifaces, ifstates)
        exit(1)

    ifname = args.ifname
    iface, ifstate, port, tunnel, tunState = by_ifname(args, ifname, ifstates, ifaces)
    print "InterfaceConfig: \n{}".format(utils.format_json(args, iface))
    print "InterfaceState: \n{}".format(utils.format_json(args, ifstate))
    if port:
        print "NeutronPort: \n{}".format(utils.format_json(args, port))
        # analyze_neutron_port(port, iface, ifstate)
        return
    if tunnel:
        print "Tunnel: \n{}".format(utils.format_json(args, tunnel))
    if tunState:
        print "TunState: \n{}".format(utils.format_json(args, tunState))
    if ifstate:
        ncId = ifstate.get('lower-layer-if')[0]
        nodeId = ncId[:ncId.rindex(':')]
        # analyze_inventory(nodeId, True, ncId, ifname)
        # analyze_inventory(nodeId, False, ncId, ifname)


def analyze_trunks(args):
    ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, args)
    ietf_interfaces_interfaces_state = ietf_interfaces.interfaces_state(Model.OPERATIONAL, args)
    l3vpn_vpn_interfaces = l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, args)
    neutron_neutron = neutron.neutron(Model.CONFIG, args)

    nports = neutron_neutron.get_ports_by_key()
    ntrunks = neutron_neutron.get_trunks_by_key()
    vpninterfaces = l3vpn_vpn_interfaces.get_vpn_ids_by_key()
    ifaces = ietf_interfaces_interfaces.get_interfaces_by_key()
    ifstates = ietf_interfaces_interfaces_state.get_interfaces_by_key()
    subport_dict = {}
    for v in ntrunks.itervalues():
        nport = nports.get(v.get('port-id'))
        s_subports = []
        for subport in v.get('sub-ports'):
            sport_id = subport.get('port-id')
            snport = nports.get(sport_id)
            svpniface = vpninterfaces.get(sport_id)
            siface = ifaces.get(sport_id)
            sifstate = ifstates.get(sport_id)
            subport['SubNeutronPort'] = 'Correct' if snport else 'Wrong'
            subport['SubVpnInterface'] = 'Correct' if svpniface else 'Wrong'
            subport['ofport'] = Model.get_ofport_from_ncid(sifstate.get('lower-layer-if')[0])
            if siface:
                vlan_mode = siface.get('odl-interface:l2vlan-mode')
                parent_iface_id = siface.get('odl-interface:parent-interface')
                if vlan_mode !='trunk-member':
                    subport['SubIface'] = 'WrongMode'
                elif parent_iface_id !=v.get('port-id'):
                    subport['SubIface'] = 'WrongParent'
                elif siface.get('odl-interface:vlan-id') !=subport.get('segmentation-id'):
                    subport['SubIface'] = 'WrongVlanId'
                else:
                    subport['SubIface'] = 'Correct'
            else:
                subport['SubIface'] = 'Wrong'
            s_subport = 'SegId:{}, PortId:{}, SubNeutronPort:{}, SubIface:{}, SubVpnIface:{}'.format(
                subport.get('segmentation-id'), subport.get('port-id'),
                subport.get('SubNeutronPort'),
                subport.get('SubIface'),
                subport.get('SubVpnInterface'))
            s_subports.append(subport)
            subport_dict[subport['port-id']] = subport
        s_trunk = 'TrunkName:{}, TrunkId:{}, PortId:{}, NeutronPort:{}, SubPorts:{}'.format(
            v.get('name'), v.get('uuid'), v.get('port-id'),
            'Correct' if nport else 'Wrong', utils.format_json(args, s_subports))
        print s_trunk
    print '\n------------------------------------'
    print   'Analyzing Flow status for SubPorts'
    print   '------------------------------------'
    for flow in sort(get_all_flows(['ifm'], ['vlanid']), 'ifname'):
        subport = subport_dict.get(flow.get('ifname')) or None
        vlanid = subport.get('segmentation-id') if subport else None
        ofport = subport.get('ofport') if subport else None
        flow_status = 'Okay'
        if flow.get('ofport') and flow.get('ofport') != ofport:
            flow_status = 'OfPort mismatch for SubPort:{} and Flow:{}'.format(subport, flow.get('flow'))
        if flow.get('vlanid') and flow.get('vlanid') != vlanid:
            flow_status = 'VlanId mismatch for SubPort:{} and Flow:{}'.format(subport, flow.get('flow'))
        if subport:
            print 'SubPort:{},Table:{},FlowStatus:{}'.format(
                subport.get('port-id'), flow.get('table'), flow_status)
