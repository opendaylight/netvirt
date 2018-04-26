import argparse

import mdsal.cli
import netvirt.cli
import odltools.csit.cli

__version__ = "0.1"


def create_parser():
    parser = argparse.ArgumentParser(prog="python -m odltools", description="OpenDaylight Troubleshooting Tools")
    parser.add_argument("-v", "--verbose", dest="verbose", action="count", default=0,
                        help="verbosity (-v, -vv)")
    parser.add_argument("-V", "--version", action="version",
                        version="%(prog)s (version {version})".format(version=__version__))
    subparsers = parser.add_subparsers(dest="command", description="Command Tool")
    odltools.csit.cli.add_parser(subparsers)
    odltools.mdsal.cli.add_parser(subparsers)
    odltools.netvirt.cli.add_parser(subparsers)

    return parser


def parse_args():
    parser = create_parser()
    args = parser.parse_args()

    return args


def main():
    args = parse_args()
    args.func(args)
