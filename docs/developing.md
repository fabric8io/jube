### Developing Jube

Clone jube and build it:

    git clone https://github.com/jubeio/jube.git
    cd jube
    mvn install

You can then run the local build in the jube module directly via its **run.sh** script for running in the foreground; or **start.sh** for running in the background

    cd jube
    target/jube/run.sh

Then hit Ctrl-C to terminate if you're using **run.sh** or **stop.sh** to stop if you started it with **start.sh**


### Debugging

The easiest way to debug Jube is to just run the [Main class](https://github.com/jubeio/jube/blob/master/jube/src/main/java/io/fabric8/jube/main/Main.java#L56) directly in your IDE in debug mode.

You may find the embedded web console doesn't work when running the Main in your IDE. If that's the case you could always just use the [mvn fabric8:run](http://fabric8.io/v2/mavenPlugin.html#running) goal to run projects and the [fabric8 Forge Addons](http://fabric8.io/v2/forge.html) to list/stop resources in Jube.