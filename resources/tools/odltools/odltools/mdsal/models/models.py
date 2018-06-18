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

import logging
from odltools.mdsal.models import elan
from odltools.mdsal.models import id_manager
from odltools.mdsal.models import ietf_interfaces
from odltools.mdsal.models import interface_service_bindings
from odltools.mdsal.models import itm_state
from odltools.mdsal.models import l3vpn
from odltools.mdsal.models import mip
from odltools.mdsal.models import network_topology
from odltools.mdsal.models import neutron
from odltools.mdsal.models import odl_fib
from odltools.mdsal.models import odl_interface_meta
from odltools.mdsal.models import odl_l3vpn
from odltools.mdsal.models import opendaylight_inventory
from odltools.mdsal.models import model
from odltools.mdsal.models.model import Model
from odltools.mdsal.models.Modules import netvirt_data_models

logger = logging.getLogger("mdsal.models")


class Singleton(object):
    def __new__(cls, *args, **kwds):
        it = cls.__dict__.get("__it__")
        if it is not None:
            return it
        cls.__it__ = it = object.__new__(cls)
        it.init(*args, **kwds)
        return it

    def init(self, *args, **kwds):
        pass


def singleton(cls):
    instances = {}

    def getinstance():
        if cls not in instances:
            instances[cls] = cls()
        return instances[cls]
    return getinstance


@singleton
class Models:

    def __init__(self):
        self.args = None
        self.elan_elan_instances = None
        self.elan_elan_interfaces = None
        self.id_manager_id_pools = None
        self.ietf_interfaces_interfaces = None
        self.ietf_interfaces_interfaces_state = None
        self.interface_service_bindings_service_bindings = None
        self.itm_state_dpn_endpoints = None
        self.itm_state_tunnels_state = None
        self.l3vpn_vpn_interfaces = None
        self.mip_mac = None
        self.network_topology_network_topology = None
        self.network_topology_network_topology_operational = None
        self.neutron_neutron = None
        self.odl_fib_fib_entries = None
        self.odl_interface_meta_if_index_interface_map = None
        self.odl_inventory_nodes = None
        self.odl_inventory_nodes_operational = None
        self.odl_l3vpn_vpn_instance_to_vpn_id = None

    def get_all_models(self, args):
        self.get_models(args, {
            "elan_elan_instances",
            "elan_elan_interfaces",
            "id_manager_id_pools",
            "ietf_interfaces_interfaces",
            "ietf_interfaces_interfaces_state",
            "interface_service_bindings_service_bindings",
            "itm_state_tunnels_state",
            "l3vpn_vpn_interfaces",
            "network_topology_network_topology",
            "network_topology_network_topology_operational",
            "neutron_neutron",
            "odl_fib_fib_entries",
            "odl_interface_meta_if_index_interface_map",
            "odl_inventory_nodes",
            "odl_inventory_nodes_operational",
            "odl_l3vpn_vpn_instance_to_vpn_id"
        })

    def get_models(self, args, models):
        self.args = args

        if "elan_elan_instances" in models:
            self.elan_elan_instances = elan.elan_instances(Model.CONFIG, args)
        if "elan_elan_interfaces" in models:
            self.elan_elan_interfaces = elan.elan_interfaces(Model.CONFIG, args)
        if "id_manager_id_pools" in models:
            self.id_manager_id_pools = id_manager.id_pools(Model.CONFIG, args)
        if "ietf_interfaces_interfaces" in models:
            self.ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, args)
        if "ietf_interfaces_interfaces_state" in models:
            self.ietf_interfaces_interfaces_state = ietf_interfaces.interfaces_state(Model.OPERATIONAL, args)
        if "interface_service_bindings_service_bindings" in models:
            self.interface_service_bindings_service_bindings = \
                interface_service_bindings.service_bindings(Model.CONFIG, args)
        if "itm_state_dpn_endpoints" in models:
            self.itm_state_dpn_endpoints = itm_state.dpn_endpoints(Model.CONFIG, args)
        if "itm_state_tunnels_state" in models:
            self.itm_state_tunnels_state = itm_state.tunnels_state(Model.OPERATIONAL, args)
        if "l3vpn_vpn_interfaces" in models:
            self.l3vpn_vpn_interfaces = l3vpn.vpn_interfaces(Model.CONFIG, args)
        if "mip_mac" in models:
            self.mip_mac = mip.mac(Model.CONFIG, args)
        if "network_topology_network_topology" in models:
            self.neutron_neutron = neutron.neutron(Model.CONFIG, args)
        if "network_topology_network_topology" in models:
            self.network_topology_network_topology = network_topology.network_topology(Model.CONFIG, args)
        if "network_topology_network_topology_operational" in models:
            self.network_topology_network_topology_operational = \
                network_topology.network_topology(Model.OPERATIONAL, args)
        if "neutron_neutron" in models:
            self.neutron_neutron = neutron.neutron(Model.CONFIG, args)
        if "odl_fib_fib_entries" in models:
            self.odl_fib_fib_entries = odl_fib.fib_entries(Model.CONFIG, args)
        if "odl_interface_meta_if_index_interface_map" in models:
            self.odl_interface_meta_if_index_interface_map = \
                odl_interface_meta.if_indexes_interface_map(Model.OPERATIONAL, args)
        if "odl_inventory_nodes" in models:
            self.odl_inventory_nodes = opendaylight_inventory.nodes(Model.CONFIG, args)
        if "odl_inventory_nodes_operational" in models:
            self.odl_inventory_nodes_operational = opendaylight_inventory.nodes(Model.OPERATIONAL, args)
        if "odl_l3vpn_vpn_instance_to_vpn_id" in models:
            self.odl_l3vpn_vpn_instance_to_vpn_id = odl_l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, args)


def get_models(args):
    if args.modules == ["all"]:
        data_models = netvirt_data_models
    else:
        data_models = args.modules

    logger.debug("get_models: modules: %s, data_models: %s", args.modules, data_models)

    if data_models == [""]:
        print("please enter a list of modules")
        return

    for resource in data_models:
        filename = model.make_filename_from_resource(args, resource)
        url = model.make_url_from_resource(args, resource)
        model.get_model_data(None, filename, url, args)
