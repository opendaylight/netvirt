import cmd


def add_dump_parser(parsers):
    parser = parsers.add_parser("dump", description="Get and write all mdsal models")
    parser.add_argument("path",
                        help="the directory that the parsed data is written into")
    parser.add_argument("--transport", default="http",
                        choices=["http", "https"],
                        help="transport for connections")
    parser.add_argument("-i", "--ip", default="localhost",
                        help="OpenDaylight ip address")
    parser.add_argument("-t", "--port", default="8181",
                        help="OpenDaylight restconf port, defaul: 8181")
    parser.add_argument("-u", "--user", default="admin",
                        help="OpenDaylight restconf username, default: admin")
    parser.add_argument("-w", "--pw", default="admin",
                        help="OpenDaylight restconf password, default: admin")
    parser.add_argument("-p", "--pretty_print", action="store_true",
                        help="json dump with pretty_print")
    parser.set_defaults(func=cmd.run_dump)


def add_parser(parsers):
    parser = parsers.add_parser("model", description="Tools for MDSAL models")
    subparsers = parser.add_subparsers(dest="subcommand", description="Model tools")
    add_dump_parser(subparsers)
