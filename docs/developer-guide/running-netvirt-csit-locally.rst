CSIT (Continuous System Integration Test) is the collection of all system
tests and test tools that tests each ODL project as a stand-alone or with
other projects, for example netvirt along with OpenStack. These system
tests are required for all OpenDayLight (ODL) projects and are an
important release requirement. They are run periodically in ODL’s Jenkins
in Linux Foundation’s lab. The source code is stored and maintained at a
separate git repo called `integration-test <https://github.com/
opendaylight/integration-test/>`_.

This doc will describe the required installations and configurations to
run CSIT locally. We will run `netvirt <https://github.com/opendaylight/
integration-test/tree/master/csit/suites/netvirt>`_ suite here. It is
assumed that ODL and OpenStack are already installed, integrated and
running good. The environment described in this doc has 2 systems
(running Centos 7):

- 1 with ODL installed and IP is odlip

- 1 with OpenStack running and IP is osip1

You can run ROBOT in the any one of the systems along with ODL/OpenStack
or in a separate system. Here they are run in the same system as ODL.

=======================
Install ROBOT framework
=======================

Enable epel repo and install prerequisite and ROBOT
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
   ``$ sudo yum -y install epel-release``

   ``$ sudo yum -y install python-pip gcc libffi-devel python-devel``
   ``openssl-devel git``

   ``$ sudo pip install robotframework robotframework-requests``
   ``robotframework-sshlibrary robotframework-httplibrary1``

You can run the tests via CI now. If you wish to install RIDE UI, follow
next steps else skip it. RIDE can also be used to edit the test cases.

Install Python, wxPython and RIDE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Python version required is >2.6 and

   ``$ sudo yum groupinstall "Development Tools"``

   ``$ sudo yum install bzip2 wget gtk+-devel gtk2-devel freeglut-devel``
   ``wxGTK-devel``

   ``$ wget https://nchc.dl.sourceforge.net/project/wxpython/wxPython/``
   ``2.8.12.1/wxPython-src-2.8.12.1.tar.bz2``

   ``$ bzip2 wxPython-src-2.8.12.1.tar.bz2``

   ``$ tar xvf wxPython-src-2.8.12.1.tar``

   ``$ cd wxPython-src-2.8.12.1``

   ``$ ./configure --enable-unicode; make ; sudo make install``

   ``$ cd wxPython``

   ``$ sudo python setup.py install``

=================
Running the tests
=================

We are done with all the installations required. Clone the tests and
netvirt repo locally.

   ``$ git clone https://github.com/opendaylight/integration-test``

   ``$ git clone https://github.com/opendaylight/netvirt``

Open RIDE UI and import the test suite that you wish to run. Here we are
running basic_vpnservice.robot located at ~/integration-test/csit/suites/
netvirt/Netvirt_Vpnservice.

   ``$ ride.py``

The test can be imported from File -> Open Test Suite.

Once the test is open, you can run it from the RIDE. It would require
below arguments to run:

- USER_HOME:Home location (generally $HOME)
- ODL_SYSTEM_IP: IP of the ODL system
- ODL_SYSTEM_USER: system user for ODL
- ODL_SYSTEM_PASSWORD: password for the ODL system
- WORKSPACE: where you have cloned netvirt repo
- BUNDLEFOLDER: where built odl is located
- OS_CONTROL_NODE_IP:10.74.128.12
- OS_USER: openstack user (stack if you are running devstack)
- NUM_OS_SYSTEM: number of openstack systems (in this cae 1)
- NUM_ODL_SYSTEM: number of ODL systems (in this case 1)
- DEVSTACK_DEPLOY_PATH: where devstack folder is present
  (/opt/stack/devstack if you are running devstack)

Assuming that you are running devstack and netvirt is cloned at $HOME
directory, the arguments will be

   ``-v USER_HOME:$HOME -v ODL_SYSTEM_IP:odlip -v ODL_SYSTEM_USER:user``
   ``-v ODL_SYSTEM_PASSWORD:password -v WORKSPACE:$HOME/netvirt -v``
   ``BUNDLEFOLDER:distribution/karaf/target/assembly -v OS_CONTROL_NODE``
   ``_IP:osip -v OS_USER:stack -v NUM_OS_SYSTEM:1 -v NUM_ODL_SYSTEM:1 -v``
   ``DEVSTACK_DEPLOY_PATH:/opt/stack/devstack``

You can run it from terminal as below:

   ``pybot -v USER_HOME:$HOME -v ODL_SYSTEM_IP:odlip -v ODL_SYSTEM_USER:``
   ``user -v ODL_SYSTEM_PASSWORD:password -v WORKSPACE:$HOME/netvirt -v``
   ``BUNDLEFOLDER:distribution/karaf/target/assembly -v OS_CONTROL_NODE_``
   ``IP:osip -v OS_USER:stack -v NUM_OS_SYSTEM:1 -v NUM_ODL_SYSTEM:1 -v``
   ``DEVSTACK_DEPLOY_PATH:/opt/stack/devstack integration-test/csit/``
   ``suites/netvirt/Netvirt_Vpnservice/basic_vpnservice.robot``

CSIT tests expect “>” to be default linux prompt for both ODL and
OpenStack systems. If you have different prompt, either change the
DEFAULT_LINUX_PROMPT value in *~/integration-test/csit/variables/
Variables.py* or change the prompt in your systems.