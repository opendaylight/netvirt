import analyze
import show


def add_common_args(parser):
    parser.add_argument("path",
                        help="the directory that the parsed data is written into")
    parser.add_argument("-i", "--ip", default="localhost",
                        help="OpenDaylight ip address")
    parser.add_argument("-p", "--pretty_print", action="store_true",
                        help="json dump with pretty_print")
    parser.add_argument("-t", "--port", default="8181",
                        help="OpenDaylight restconf port, default: 8181")
    parser.add_argument("-u", "--user", default="admin",
                        help="OpenDaylight restconf username, default: admin")
    parser.add_argument("-w", "--pw", default="admin",
                        help="OpenDaylight restconf password, default: admin")


def add_interface_parser(parsers):
    parser = parsers.add_parser("interface")
    add_common_args(parser)
    parser.add_argument("--ifname",
                        help="interfaces-state:interface:name")
    parser.set_defaults(func=analyze.analyze_interface)

    parser = parsers.add_parser("trunks")
    add_common_args(parser)
    parser.set_defaults(func=analyze.analyze_trunks)


def add_show_parser(parsers):
    parser = parsers.add_parser("elan-instances")
    add_common_args(parser)
    parser.set_defaults(func=show.show_elan_instances)

    parser = parsers.add_parser("id-pools")
    add_common_args(parser)
    parser.set_defaults(func=show.show_idpools)

    parser = parsers.add_parser("groups")
    add_common_args(parser)
    parser.set_defaults(func=show.show_groups)

    parser = parsers.add_parser("stale-bindings")
    add_common_args(parser)
    parser.set_defaults(func=show.show_stale_bindings)

    parser = parsers.add_parser("tables")
    add_common_args(parser)
    parser.set_defaults(func=show.show_tables)


def add_parser(parsers):
    parser = parsers.add_parser("analyze")
    subparsers = parser.add_subparsers(dest="subcommand")
    add_interface_parser(subparsers)

    parser = parsers.add_parser("show")
    subparsers = parser.add_subparsers(dest="showcommand")
    add_show_parser(subparsers)
