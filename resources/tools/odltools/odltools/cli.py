import argparse
import csit.cli
import mdsal.cli
import netvirt.cli

__version__ = "0.1"


def create_parser():
    parser = argparse.ArgumentParser(description="OpenDaylight Troubleshooting Tools")
    parser.add_argument("-v", "--verbose", dest="verbose", action="count", default=0,
                        help="verbosity (-v, -vv)")
    parser.add_argument("-V", "--version", action="version",
                        version="%(prog)s (version {version})".format(version=__version__))
    subparsers = parser.add_subparsers(dest="command")
    csit.cli.add_parser(subparsers)
    mdsal.cli.add_parser(subparsers)
    netvirt.cli.add_parser(subparsers)

    return parser


def parse_args():
    parser = create_parser()
    args = parser.parse_args()

    return args


def main():
    args = parse_args()
    args.func(args)
