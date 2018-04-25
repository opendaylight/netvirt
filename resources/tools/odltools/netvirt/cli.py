import ds_analyze


def add_interface_parser(parsers):
    parser = parsers.add_parser("interface")
    parser.add_argument("outdir",
                        help="the directory that the parsed data is written into")
    parser.add_argument("-i", "--ip", default="localhost",
                        help="OpenDaylight ip address")
    parser.add_argument("-p", "--pretty_print", action="store_true",
                        help="json dump with pretty_print")
    parser.add_argument("-t", "--port", default="8181",
                        help="OpenDaylight restconf port, defaul: 8181")
    parser.add_argument("-u", "--user", default="admin",
                        help="OpenDaylight restconf username, default: admin")
    parser.add_argument("-w", "--pw", default="admin",
                        help="OpenDaylight restconf password, default: admin")
    parser.set_defaults(func=ds_analyze.analyze_interface)


def add_parser(parsers):
    parser = parsers.add_parser("analyze")
    subparsers = parser.add_subparsers(dest="subcommand")
    add_interface_parser(subparsers)
