Building a new release of odltools
==================================

.. contents::

Update HISTORY.rst or CHANGELOG.rst
-----------------------------------

TODO:
- decide on HISTORY.txt or CHANGELOG.txt
- release notes

::

   git add HISTORY.rst
   git commit -s -m "Changelog for upcoming release x.y.z"

Update the Version
------------------
::

   vi odltools/__init__.py
   git add odltools/__init__.py
   git commit -s -m "Update version x.y.z"
   git tags x.y.z

Run the Tests
-------------
::

   tox

Release on PyPi
---------------
::

   python setup.py check
   python setup.py clean
   python setup.py sdist
   python setup.py sdist upload -r pypi
   python setup.py bdist_wheel
   python setup.py bdist_wheel upload - r pypi

or in one shot:
::

   python setup.py clean sdist bdist_wheel upload

.. note::
   The above commands assume a .pypirc like below is used:

::

   distutils]
   index-servers =
       pypi
       testpypi

   [pypi]
   #repository=https://upload.pypi.org/legacy/
   #repository=https://pypi.python.org/pypi
   username = cooldude
   password = coolpw

   [testpypi]
   repository: https://test.pypi.org/legacy/
   username = cooldude
   password = coolpw

Test the PyPi Install
---------------------
::

   mkvirtualenv tmptest
   pip install odltools
   python -m odltools -V
   deactivate
   rmvirtualenv tmptest

Push the Code
-------------
::

   git push origin release_branch
   git push --tags

Verify All is Good in the World
-------------------------------

- Check the PyPi listing page, README, release notes, etc.
- Check the GitHub listing page