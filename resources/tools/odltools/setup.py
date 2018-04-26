import io
import re
from setuptools import find_packages
from setuptools import setup


# with io.open('README.rst', 'rt', encoding='utf8') as f:
#     readme = f.read()

with io.open('odltools/__init__.py', 'rt', encoding='utf8') as f:
    version = re.search(r'__version__ = \'(.*?)\'', f.read()).group(1)


setup(
    name='odltools',
    version=version,
    description='OpenDaylight Troubleshooting Tools',
    url='http://github.com/shague/odltools',
    author='Sam Hague, Vishal Thapar',
    author_email='shague@gmail.com, thapar@gmail.com',
    license='EPL',
    packages=find_packages(exclude=['*.iml', 'tests*']),
    install_requires=['requests'],
    zip_safe=False,
    classifiers=[
        'Development Status :: 3 - Alpha',
        'Intended Audience :: Developers',
        'Programming Language :: Python',
        'Programming Language :: Python :: 2',
        'Programming Language :: Python :: 2.7',
        'Topic :: Software Development'
    ],
    keywords='development',
    python_requires='>=2.7'
)