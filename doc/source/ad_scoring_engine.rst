.. _ad_scoring_engine:

Scoring Engine
==============

This section covers the scoring engine installation, configuration
and running the scoring engine.

Installation
------------

The scoring engine repositories are automatically installed as part of the
|PACKAGE| repositories.
See the :ref:`package installation <ad_inst_ta1>` section for further
information.

Scoring Models Implementation
-----------------------------

The scoring engine is a generic engine and is independent of any scoring model.
See the :ref:`Models <python_api/models/index>` section of the
:ref:`Python API <python_api/index>` for more information about developing
models.
The implementation, model bytes, and class name are provided in a tar file
at the startup of the scoring engine.
The scoring engine expects three files in the tar file:

#)  The model implementation jar file.
#)  The file 'modelname.txt', which contains the name of the class that
    implements the scoring in the jar file.
#)  The file that has the model bytes that will be used by the scoring.
    The name of this file is the name of the URL of the rest server.
    See section :ref:`starting_scoring_engine` below.

.. note::

    If |PACKAGE| is used to build the model, the publish method on the model
    will create a tar file that can used as input to the scoring engine.

Configuration of the Engine
---------------------------

The scoring engine installation provides a configuration template file which is used
to create a working configuration file.
Copy the configuration template file 'application.conf.tpl' to
'application.conf', in the same directory:

.. code::

    $ cd /etc/trustedanalytics/scoring
    $ sudo cp application.conf.tpl application.conf

Open the file with a text editor:

.. code::

    $ sudo vi application.conf

Modify the section for the scoring engine to point to the where the scoring
tar file is located:

.. code::

    trustedanalytics.scoring-engine {
      archive-tar = "hdfs://scoring-server.company.com:8020/user/atkuser/kmeans.tar"
    }

.. _starting_scoring_engine:

Starting the Scoring Engine Service
-----------------------------------

Once the application.conf file has been modified to point to the scoring tar
file, the scoring engine can be started with the following command:

.. code::

    $ sudo service scoring-engine start

This will also launch the rest server for the engine.
The REST API is:

.. code::

    GET /v1/models/[name]?data=[urlencoded record 1]

See the :ref:`REST API <rest_api/v1/index>` section for more information and
examples of using the :ref:`GET <rest_api/v1/commands/get_command>` function.


Scoring Client
--------------

This is a sample python script to connect to the scoring engine:

.. code::

    >>> import requests
    >>> import json
    >>> headers = {'Content-type': 'application/json',
    ...            'Accept': 'application/json,text/plain'}
    >>> r = requests.post('http://localhost:9099/v1/models/testjson?data=2', headers=headers)
