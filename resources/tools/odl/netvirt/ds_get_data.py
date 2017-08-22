import json
import netvirt_utils as utils


def get_config_interfaces(file_name='iface-config.log'):
    # Returns dict of ifaces, key is iface name
    if_dict = {}
    ifaces = {}
    try:
        with open(file_name) as ifconfig_file:
            ifaces = json.load(ifconfig_file)['interfaces']['interface']
    except IOError:
        url = utils.create_url("config", "ietf-interfaces:interfaces")
        result = utils.grabJson(url)
        if result:
            ifaces = result['interfaces']['interface']
    for iface in ifaces:
        if_dict[iface['name']] = iface
    return if_dict


def get_neutron_ports(file_name='neutron-ports.log'):
    port_dict = {}
    ports = {}
    try:
        with open(file_name) as ports_file:
            ports = json.load(ports_file)['ports']['port']
    except IOError:
        url = utils.create_url("config", "neutron:neutron/ports")
        result = utils.grabJson(url)
        if result:
            ports = result['ports']['port']
    except Exception:
        pass
    for port in ports:
        port_dict[port['uuid']] = port
    return port_dict


def get_interface_states(file_name='ifstate.log'):
    ifs_dict = {}
    ifstates = {}
    try:
        with open(file_name) as ifstate_file:
            ifstates = json.load(ifstate_file)['interfaces-state']['interface']
    except IOError:
        url = utils.create_url("operational", "ietf-interfaces:interfaces-state")
        result = utils.grabJson(url)
        if result:
            ifstates = result['interfaces-state']['interface']
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
        for tun_name in tunnel['tunnel-interface-names']:
            tun_dict[tun_name] = tunnel
    return tun_dict


def get_tunnel_states(file_name='tunnel-state.log'):
    tun_dict = {}
    tunnels = {}
    try:
        with open(file_name) as tunstate_file:
            tunnels = json.load(tunstate_file)['tunnels_state']['state-tunnel-list']
    except IOError:
        url = utils.create_url("operational", "itm-state:tunnels_state")
        result = utils.grabJson(url)
        if result:
            tunnels = result['tunnels_state']['state-tunnel-list']
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
    nodes = {}
    try:
        with open(file_name) as inventory_file:
            nodes = json.load(inventory_file)['nodes']['node']
    except IOError:
        url = utils.create_url(dsType, "opendaylight-inventory:nodes")
        result = utils.grabJson(url)
        if result:
            nodes = result['nodes']['node']
    for node in nodes:
        nodes_dict[node['id']] = node
    return nodes_dict


def get_inventory_config(file_name='inventory-config.log'):
    return get_inventory_nodes(file_name)


def get_inventory_oper(file_name='inventory-oper.log'):
    return get_inventory_nodes(file_name, 'operational')
