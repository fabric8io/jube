Committers
----------

Be sure to check out the [committer instructions](http://174.129.32.31:8080/) on how to fork this repo and submit Pull Requests

Building Jube
============

First of all, the Jube build process needs, most
of the time, more memory than the default allocated
to the maven process. Therefore, ensure to set the 
MAVEN_OPTS system property with the following settings
before starting

    > MAVEN_OPTS="-Xmx1024m -XX:MaxPermSize=512m"

Build Jube and run the associated smoke tests

    > mvn clean install
    
Build Jube using latest hawtio Snapshot and run the associated tests

    > mvn -Phawtio-snapshot clean install


Quick Builds
==========

You can do quick builds by appending `-DskipTests`

Build Jube and skip tests

    > mvn clean install -DskipTests

Build Jube with all modules and skip tests


Sourcecheck
===========

The source code is intended to be formatted using a `checkstyle` guideline and rules. The check can be executed from maven using the following goal

    > mvn clean install -Psourcecheck

The check can be executed in sub modules such as:

    > cd process-manager
    > mvn clean install -Psourcecheck

You may need for the first time to run `mvn clean install` in the root project or from the `buildingtools` directory, to build the build tools module, which is needed when running the sourcecode check.


License check
=============

The source code uses the license header from the file ```jube-license-header.txt``` in the root directory.

You can check for missing licenses in the source code, by running the following goal from the root directory. Notice this will check all the source code:

    > mvn com.mycila:license-maven-plugin:2.6:check -Dlicense.header=jube-license-header.txt

And from any sub module, you need to refer to the license file using a relative path:

```
   > cd process-manager
   > mvn com.mycila:license-maven-plugin:2.6:check -Dlicense.header=../jube-license-header.txt
```

You can update the license headers in the source code using the ```format``` goal, for example:

    > mvn com.mycila:license-maven-plugin:2.6:format -Dlicense.header=../jube-license-header.txt


GitBook
=======

The documentation is compiled into a book using [GitBook](https://github.com/GitbookIO/gitbook).

First install gitbook using npm

    npm install -g gitbook

And then install the anchor plugin

    sudo npm install -g gitbook-plugin-anchors    

Note on osx you may need to run these commands with `sudo`

And then build the book locally using

    cd docs
    gitbook serve ./

And access the book from a web browser at

    http://localhost:4000

To add new sections into the gitbook, ecit the `docs/SUMMARY.md` file.
