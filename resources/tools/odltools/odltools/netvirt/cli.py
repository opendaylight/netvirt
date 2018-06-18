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

from odltools.cli_utils import add_common_args
from odltools.netvirt import analyze
from odltools.netvirt import show


def add_analyze_parser(parsers):
    parser = parsers.add_parser("interface")
    add_common_args(parser)
    parser.add_argument("--ifname",
                        help="interfaces-state:interface:name")
    parser.set_defaults(func=analyze.analyze_interface)

    parser = parsers.add_parser("inventory")
    add_common_args(parser)
    parser.add_argument("store", choices=["config", "operational"],
                        help="config or operational inventory")
    parser.add_argument("nodeid",
                        help="an openflow node id, not including the prefix such as openflow:")
    parser.add_argument("--ifname",
                        help="interfaces-state:interface:name")
    parser.set_defaults(func=analyze.analyze_inventory)

    parser = parsers.add_parser("nodes")
    add_common_args(parser)
    parser.set_defaults(func=analyze.analyze_nodes)

    parser = parsers.add_parser("trunks")
    add_common_args(parser)
    parser.set_defaults(func=analyze.analyze_trunks)


def call_func(args):
    args.func2(args)


def add_show_parser(parsers):
    parser = parsers.add_parser("elan-instances")
    add_common_args(parser)
    parser.set_defaults(func=show.show_elan_instances)

    parser = parsers.add_parser("flows")
    add_common_args(parser)
    parser.add_argument("--modules",
                        help="service module owning the flow",
                        choices=["ifm", "acl", "elan", "l3vpn", "nat"])
    parser.add_argument("flowtype", choices=["all", "duplicate", "elan", "learned", "stale"])
    parser.add_argument("--metaonly", action="store_true",
                        help="display flow meta info only")
    parser.add_argument("--urls", action="store_true",
                        help="show flow urls")
    parser.set_defaults(func=show.show_flows)

    parser = parsers.add_parser("id-pools")
    add_common_args(parser)
    parser.add_argument("type", choices=["all", "duplicate"])
    parser.add_argument("--short", action="store_true", default=False,
                        help="display less information")
    parser.set_defaults(func=show.show_idpools)

    parser = parsers.add_parser("groups")
    add_common_args(parser)
    parser.set_defaults(func=show.show_groups)

    parser = parsers.add_parser("stale-bindings")
    add_common_args(parser)
    parser.set_defaults(func=show.show_stale_bindings)
    # This was a test to see if we could call a func - which allows us more than func(args)
    # parser.add_argument("--func2", default=show.show_stale_bindings, help=argparse.SUPPRESS)
    parser.set_defaults(func=call_func)

    parser = parsers.add_parser("tables")
    add_common_args(parser)
    parser.set_defaults(func=show.show_tables)

    parser = parsers.add_parser("neutron")
    add_common_args(parser)
    parser.add_argument("object", choices=["all", "floatingips", "networks", "ports", "routers",
                                           "security-groups", "security-rules", "subnets", "trunks"])
    parser.add_argument("--short", action="store_true", default=False,
                        help="display less information")
    parser.set_defaults(func=show.show_neutron)


def add_parser(parsers):
    parser = parsers.add_parser("analyze")
    subparsers = parser.add_subparsers(dest="subcommand")
    add_analyze_parser(subparsers)

    parser = parsers.add_parser("show")
    subparsers = parser.add_subparsers(dest="showcommand")
    add_show_parser(subparsers)
