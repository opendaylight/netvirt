import elan
import id_manager
import ietf_interfaces
import interface_service_bindings
import itm_state
import l3vpn
import mip
import network_topology
import neutron
import odl_fib
import odl_interface_meta
import odl_l3vpn
import opendaylight_inventory
from model import Model


class Models:
    def __init__(self, outdir="/tmp", ip="localhost", port="8181", user="admin", pw="admin", pretty_print=False):
        self.outdir = outdir
        self.ip = ip
        self.port = port
        self.user = user
        self.pw = pw
        self.pretty_print = pretty_print

        self.elan_elan_instances = elan.elan_instances(Model.CONFIG, outdir)
        self.elan_elan_interfaces = elan.elan_interfaces(Model.CONFIG, outdir)
        self.id_manager_id_pools = id_manager.id_pools(Model.CONFIG, outdir)
        self.ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, outdir)
        self.ietf_interfaces_interfaces_state = ietf_interfaces.interfaces_state(Model.OPERATIONAL, outdir)
        self.interface_service_bindings_service_bindings = interface_service_bindings.service_bindings(Model.CONFIG, outdir)
        self.itm_state_tunnels_state = itm_state.tunnels_state(Model.OPERATIONAL, outdir)
        self.l3vpn_vpn_interfaces = l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, outdir)
        self.mip_mac = mip.mac(Model.CONFIG, outdir)
        self.neutron_neutron = neutron.neutron(Model.CONFIG, outdir)
        self.network_topology_network_topology_config = network_topology.network_topology(Model.CONFIG, outdir)
        self.network_topology_network_topology_operational = network_topology.network_topology(Model.CONFIG, outdir)
        self.neutron_neutron = neutron.neutron(Model.CONFIG, outdir)
        self.odl_fib_fib_entries = odl_fib.fib_entries(Model.CONFIG, outdir)
        self.odl_interface_meta_if_index_interface_map = odl_interface_meta.if_indexes_interface_map(Model.OPERATIONAL, outdir)
        self.odl_inventory_nodes_config = opendaylight_inventory.nodes(Model.CONFIG, outdir)
        self.odl_inventory_nodes_operational = opendaylight_inventory.nodes(Model.OPERATIONAL, outdir)
        self.odl_l3vpn_vpn_instance_to_vpn_id = odl_l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, outdir)
