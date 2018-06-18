odltools
========

.. image:: https://travis-ci.org/shague/odltools.png?branch=master
   :target: https://travis-ci.org/shague/odltools
.. image:: https://pypip.in/d/odltools/badge.png
   :target: https://pypi.python.org/pypi/odltools

.. image:: https://pypip.in/v/odltools/badge.png
   :target: https://pypi.python.org/pypi/odltools

.. image:: https://pypip.in/wheel/odltools/badge.png
   :target: https://pypi.python.org/pypi/odltools
   :alt: Wheel Status

.. image:: https://pypip.in/license/odltools/badge.png
   :target: https://pypi.python.org/pypi/odltools
   :alt: License

A tool to troubleshoot the NetVirt OpenDaylight OpenStack integration.

The tool can be used to get mdsal model dumps, openvswitch flow dumps
and extracting dumps from CSIT output.xml files.

odltool's documentation can be found at `Read The Docs - TBD <http://odltools.readthedocs.org>`_.

Requirements
------------

* python 2.7, 3.3, 3.4, 3.5, 3.6
* requests

Installation
------------
::

  pip install odltools

Usage
-----
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

Contribute
----------
``odltools`` is an open source projects that welcomes any and all contributions
from the community. odltools is hosted on `GitHub <http://github.com/shague/odltools>`_
.

Feel free to contribute to:

- code,
- `documentation TBD <http://odltools.readthedocs.org/>`_ improvements,
- `bug reports <https://github.com/shague/odltools/issues>`_,
- `contribution reviews <https://github.com/shague/odltools/pulls>`_.

Please see the `Contributor Guidelines <http://github.com/shague/odltools/CONTRIBUTING.rst>`_
for information about contributing.

License
-------

See the `LICENSE <http://github.com/shague/odltools/LICENSE.txt>`_ file
