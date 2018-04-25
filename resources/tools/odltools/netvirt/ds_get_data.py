import collections
import json
import netvirt_utils as utils
import constants as const


def get_ds_data(name, file_name=None, ds_type=None):
    res = const.DSMAP[name]
    filename = '{}/{}'.format(utils.get_temp_path(), res[const.DSM_FILE])
    dstype = ds_type or res[const.DSM_DSTYPE]
    path = res[const.DSM_PATH]
    root1 = res[const.DSM_ROOT1]
    root2 = res[const.DSM_ROOT2]
    data = {}
    try:
        with open(filename) as data_file:
            data = json.load(data_file)[root1][root2]
    except IOError:
        url = utils.create_url(dstype, path)
        result = utils.grabJson(url)
        if result:
            data = result[root1][root2]
    return data


def get_all_dumps():
    for res in const.DSMAP.itervalues():
        filename = '{}/{}'.format(utils.get_temp_path(), res[const.DSM_FILE])
        dstype = res[const.DSM_DSTYPE]
        path = res[const.DSM_PATH]
        root1 = res[const.DSM_ROOT1]
        root2 = res[const.DSM_ROOT2]
        data = {}
        url = utils.create_url(dstype, path)
        result = utils.grabJson(url)
        with open(filename, 'w+') as data_file:
            json.dump(result, data_file, indent=2)


def get_config_interfaces(file_name=None):
    # Returns dict of ifaces, key is iface name
    if_dict = {}
    ifaces = get_ds_data('ifconfig',file_name)
    for iface in ifaces:
        if_dict[iface['name']] = iface
    return if_dict


def get_itm_config_interfaces(file_name=None):
    tun_dict = {}
    tunifaces = get_ds_data('itmconfig',file_name)
    for sourcedpn in tunifaces:
        for remotedpn in sourcedpn['remote-dpns']:
            tun_dict[remotedpn['tunnel-name']] = remotedpn
    return tun_dict


def get_neutron_ports(file_name=None, key_field='uuid'):
    port_dict = {}
    ports = get_ds_data('neutronports',file_name)
    for port in ports:
        port_dict[port[key_field]] = port
    return port_dict


def get_neutron_trunks(file_name=None):
    trunk_dict = {}
    trunks = get_ds_data('neutrontrunks',file_name)
    for trunk in trunks:
        trunk_dict[trunk['uuid']] = trunk
    return trunk_dict


def get_interface_states(file_name=None):
    ifs_dict = {}
    ifstates = get_ds_data('ifstate',file_name)
    for ifstate in ifstates:
        ifs_dict[ifstate['name']] = ifstate
    return ifs_dict


def get_config_tunnels(file_name='tunnel-config.log'):
    tun_dict = {}
    tunnels = {}
    try:
        with open(file_name) as tunconfig_file:
            tunnels = json.load(tunconfig_file)['tunnel-list']['internal-tunnel']
    except Exception:
        pass
    for tunnel in tunnels:
        for tun_name in tunnel['tunnel-interface-name']:
            tun_dict[tun_name] = tunnel
    return tun_dict


def get_tunnel_states(file_name=None):
    tun_dict = {}
    tunnels = get_ds_data('tunstate',file_name)
    for tunnel in tunnels:
        tun_dict[tunnel['tunnel-interface-name']] = tunnel
    return tun_dict


def get_topology_nodes(file_name, type = 'ovsdb:1', dsType = 'config'):
    nodes_dict = {}
    topologies = {}
    nodes = {}
    try:
        with open(file_name) as topology_file:
            topologies = json.load(topology_file)['topology']
            nodes = [topology['node'] for topology in topologies if topology['topology-id'] == type][0]
    except IOError:
        url = utils.create_url(dsType, "network-topology:network-topology/{}".format(type))
        result = utils.grabJson(url)
        if result:
            nodes = result['node']
    for node in nodes:
        nodes_dict[node['node-id']] = node
    return nodes_dict


def get_topology_config(file_name='topology-config.log', type = 'ovsdb:1'):
    return get_topology_nodes(file_name)


def get_topology_oper(file_name='topology-oper.log', type = 'ovsdb:1', dsType = 'operational'):
    return get_topology_nodes(file_name, type)


def get_inventory_nodes(file_name, dsType = 'config'):
    nodes_dict = {}
    nodes = get_ds_data('inventory', file_name, dsType)
    for node in nodes:
        nodes_dict[node['id']] = node
    return nodes_dict



def get_inventory_config(file_name=None):
    return get_inventory_nodes(file_name)


def get_inventory_oper(file_name='inventory-oper.log'):
    return get_inventory_nodes(file_name, 'operational')


def get_service_bindings(file_name=None):
    sb_dict = collections.defaultdict(dict)
    orphans_dict = collections.defaultdict(dict)
    sb_infos = get_ds_data('bindings')
    for sb_info in sb_infos:
        service_mode = sb_info['service-mode'][len('interface-service-bindings:'):]
        if sb_info.get('bound-services'):
            sb_dict[sb_info['interface-name']][service_mode] =  sb_info
        else:
            orphans_dict[sb_info['interface-name']][service_mode] =  sb_info
    return dict(sb_dict), dict(orphans_dict)


def get_elan_instances(file_name=None):
    einstances_dict = {}
    einstances = get_ds_data('elaninstances')
    for einstance in einstances:
        einstances_dict[einstance['elan-instance-name']] = einstance
    return einstances_dict


def get_elan_interfaces(file_name=None):
    eifaces_dict = {}
    eifaces = get_ds_data('elaninterfaces')
    for eiface in eifaces:
        eifaces_dict[eiface['name']] = eiface
    return eifaces_dict


def get_ifindexes(file_name=None):
    ifindexes_dict = {}
    ifindexes = get_ds_data('ifindexes')
    for ifindex in ifindexes:
        ifindexes_dict[ifindex['if-index']] = ifindex
    return ifindexes_dict


def get_fibentries_by_label(file_name=None):
    fibs_dict = {}
    fibs = get_ds_data('fibentries')
    for vrftable in fibs:
        for vrfEntry in vrftable.get('vrfentry', []):
            if vrfEntry.get('label'):
                vrfEntry['rd'] = vrftable['routeDistinguisher']
                fibs_dict[vrfEntry['label']] = vrfEntry
    return fibs_dict


def get_vpnids(filename=None):
    vpnids_dict = {}
    vpninstances = get_ds_data('vpninstance-to-vpnid')
    for vpninstance in vpninstances:
        vpnids_dict[vpninstance['vpn-id']] = vpninstance
    return vpnids_dict


def get_vpninterfaces(filename=None):
    vpninterfaces_dict = {}
    vpninterfaces = get_ds_data('vpninterfaces')
    for vpninterface in vpninterfaces:
        vpninterfaces_dict[vpninterface['name']] = vpninterface
    return vpninterfaces_dict


def get_idpools(filename=None):
    idpools_dict = {}
    idpools = get_ds_data('idpools')
    for idpool in idpools:
        idpools_dict[idpool['pool-name']] = idpool
    return idpools_dict


def get_mip_mac(filename='mip-mac.log'):
    mmac_dict = collections.defaultdict(dict)
    try:
        with open(filename) as data_file:
            data = json.load(data_file)
    except IOError:
        data = []
    for entry in data:
        entry['mac'] = entry['mac'].lower()
        mmac_dict[entry.get('mac')][entry.get('network-id')] = entry
    return mmac_dict
