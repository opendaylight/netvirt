# Copyright 2018 Red Hat, Inc. and others. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import re
from odltools.mdsal.models.models import Models
from odltools.mdsal.models.itm_state import DpnEndpoints
from odltools.mdsal.models.neutron import Neutron

gmodels = None
_nodes = {}
_ports_name = {}
_ports_mac = {}
_ports_nmac = {}
_ports_uuid = {}
_ports = {"name": _ports_name, "mac": _ports_mac, "nmac": _ports_nmac, "uuid": _ports_uuid}
_flows = {}


def get_models(args, models):
    global gmodels
    gmodels = Models()
    gmodels.get_models(args, models)


def get_gnodes():
    return _nodes


def get_gports():
    return _ports_name


def set_port(port, **kwargs):
    for k, v in kwargs.items():
        if not v:
            continue
        pmap = _ports.get(k)
        pmap[v] = port


def get_port(**kwargs):
    for k, v in kwargs.items():
        if not v:
            continue
        pmap = _ports.get(k)
        port = pmap.get(v)
        if port:
            return port


def set_port_data(port, **kwargs):
    for k, v in kwargs.items():
        if v is None:
            continue
        port[k] = v


def get_data_from_dpn_endpoints():
    nodes = gmodels.itm_state_dpn_endpoints.get_clist_by_key()

    for k, v in nodes.items():
        dpn_id = v.get(DpnEndpoints.DPN_ID)
        ip = gmodels.itm_state_dpn_endpoints.get_ip_address_from_dpn_info(v)
        node_map = {"dpn_id": dpn_id, "dpid": "", "ip": ip, "version": "", "hostname": ""}
        _nodes[dpn_id] = node_map


def get_data_from_inventory():
    nodes = gmodels.odl_inventory_nodes_operational.get_clist_by_key()
    for nodeid, node in nodes.items():
        dpn_id = nodeid.split(":")[1]
        ip = node.get("flow-node-inventory:ip-address")
        ovs_version = node.get("flow-node-inventory:software")
        node_connectors = node.get("node-connector", {})
        ports = {}
        for port in node_connectors:
            portno = port.get("flow-node-inventory:port-number")
            # Set the bridge port # to 0 rather than 0xfffffffe
            if portno == 0xfffffffe:
                portno = 0
            mac = port.get("flow-node-inventory:hardware-address")
            name = port.get("flow-node-inventory:name")
            port_map = {}
            set_port_data(port_map, name=name, mac=mac, uuid="", portno=portno, nmac="", dpn_id="")
            ports[portno] = port_map
            set_port(port_map, name=name, mac=mac)
        node_map = {"dpn_id": dpn_id, "ip": ip, "ovs_version": ovs_version, "ports": ports, "flows": {}}
        _nodes[dpn_id] = node_map


def get_data_from_topology():
    nodes = gmodels.network_topology_network_topology_operational.get_nodes_by_tid_and_key()
    for nodeid, node in nodes.items():
        conninfo = node.get("ovsdb:connection-info")
        # skip the nodes that are not the ovsdb nodes
        if conninfo is None:
            continue
        remoteip = conninfo.get("remote-ip")
        # skip the nodes that don't have an ip
        if remoteip is None:
            continue
        for gnodeid, gnode in _nodes.items():
            if remoteip == gnode.get("ip"):
                hostname = gmodels.network_topology_network_topology_operational.get_host_id_from_node(node)
                gnode["hostname"] = hostname


def get_data_from_interfaces():
    ifaces = gmodels.ietf_interfaces_interfaces.get_clist_by_key()
    for name, iface in ifaces.items():
        port = get_port(name=name)
        if not port:
            continue
        ip = iface.get("odl-interface:tunnel-source")
        if ip:
            remoteip = iface.get("odl-interface:tunnel-destination")
            set_port_data(port, ip=ip, remoteip=remoteip)
            set_port(port, name=name)


def get_data_from_interfaces_state():
    ifaces = gmodels.ietf_interfaces_interfaces_state.get_clist_by_key()
    for name, iface in ifaces.items():
        if name.find(":") != -1:
            continue
        name = iface.get("name")
        re_uuid = re.compile("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}", re.IGNORECASE)
        uuid = re_uuid.search(name)
        if uuid:
            name = None
            uuid = uuid.group()
        mac = iface.get("phys-address")
        port = get_port(name=name, mac=mac, uuid=uuid)
        if not port:
            continue
        set_port_data(port, name=name, mac=mac, uuid=uuid)
        set_port(port, name=name, mac=mac, uuid=uuid)


def get_data_from_neutron():
    neutron_ports = gmodels.neutron_neutron.get_objects_by_key(obj=Neutron.PORTS)
    for uuid, nport in neutron_ports.items():
        nmac = nport.get("mac-address")
        mac = nmac.replace("fa:", "fe:")
        port = get_port(uuid=uuid, mac=mac, nmac=nmac)
        if port is None:
            continue
        ip = gmodels.neutron_neutron.get_ip_address_from_port(nport)
        set_port_data(port, ip=ip, nmac=nmac, uuid=uuid)
        set_port(port, mac=mac, nmac=nmac, uuid=uuid)


def update_gnodes(args):
    get_models(args, {
        "ietf_interfaces_interfaces",
        "ietf_interfaces_interfaces_state",
        # "itm_state_dpn_endpoints",
        "network_topology_network_topology_operational",
        "neutron_neutron",
        "odl_inventory_nodes_operational"
    })

    # get_data_from_dpn_endpoints()
    get_data_from_inventory()
    get_data_from_topology()
    get_data_from_interfaces()
    get_data_from_interfaces_state()
    get_data_from_neutron()
