import io
from setuptools import find_packages
from setuptools import setup
from odltools import __version__


with io.open('README.rst', 'rt', encoding='utf8') as f:
    readme = f.read()

with open('requirements.txt') as f:
    requirements = f.read().splitlines()

setup(
    name='odltools',
    version=__version__,
    description='OpenDaylight Troubleshooting Tools',
    long_description=readme,
    url='http://github.com/shague/odltools',
    author='Sam Hague, Vishal Thapar',
    author_email='shague@gmail.com, thapar@gmail.com',
    license='EPL-1.0',
    packages=find_packages(exclude=['*.iml', 'tests*']),
    install_requires=requirements,
    python_requires='>=2.7',
    keywords='development',
    classifiers=[
        'Development Status :: 1 - Planning',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: ECLIPSE PUBLIC LICENSE 1.0 (EPL-1.0)'
        'Natural Language :: English',
        'Programming Language :: Python',
        'Programming Language :: Python :: 2.7',
        'Topic :: Software Development'
    ]
)
