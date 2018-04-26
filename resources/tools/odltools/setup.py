import io
from setuptools import find_packages
from setuptools import setup
import textwrap
from odltools import __version__


with io.open("README.rst", "rt", encoding="utf8") as f:
    readme = f.read()

with open("requirements.txt") as f:
    requirements = f.read().splitlines()

setup(
    name="odltools",
    version=__version__,
    description="NetVirt tools for troubleshooting OpenDaylight and "
                "OpenStack integration",
    long_description=readme,
    long_description_content_type="text/x-rst; charset=UTF-8",
    url="http://github.com/shague/odltools",
    author="Sam Hague, Vishal Thapar",
    author_email="shague@gmail.com, thapar@gmail.com",
    license="Eclipse Public License",
    packages=find_packages(exclude=["tests"]),
    install_requires=requirements,
    platforms=["All"],
    python_requires=">=2.7",
    keywords="development",
    zip_safe=False,
    # entry_points={"console_scripts": ["odltools=odltools.__main__:main"]},
    classifiers=textwrap.dedent("""
        Development Status :: 1 - Planning
        Intended Audience :: Developers
        License :: OSI Approved :: Eclipse Public License 1.0 (EPL-1.0)
        Natural Language :: English
        Operating System :: OS Independent
        Programming Language :: Python
        Programming Language :: Python :: 2.7
        Topic :: Software Development
        Topic :: Utilities
        """).strip().splitlines()
)
