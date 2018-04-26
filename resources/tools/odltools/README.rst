========
odltools
========
A tool to troubleshoot the NetVirt OpenDaylight OpenStack integration.

The tool can be used to get mdsal model dumps, openvswitch flow dumps
and extracting dumps from CSIT output.xml files.

*****
Usage
*****
::

  usage: python -m odltools [-h] [-v] [-V] {csit,model,analyze} ...

  OpenDaylight Troubleshooting Tools

  optional arguments:
    -h, --help            show this help message and exit
    -v, --verbose         verbosity (-v, -vv)
    -V, --version         show program's version number and exit

  subcommands:
    Command Tool

    {csit,model,analyze}

************
Installation
************
::

  pip install odltools
