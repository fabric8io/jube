## Get Started

To run jube:

    git clone https://github.com/jubeio/jube.git
    cd jube
    mvn install
    cd app
    mvn install
    cd target/jube
    ./start.sh

Then you can test its running by viewing the kubernetes REST API at: [http://localhost:8585/api/v1beta1/pods](http://localhost:8585/api/v1beta1/pods)

You can then type:

    export KUBERNETES_MASTER=http://localhost:8585/

and run the fabric8 JBoss Forge plugins for kubernetes or use the hawtio for kubernetes.


### Developing Jube

To run jube a little faster in a single command (without the embedded hawtio console) you can run


    cd app
    mvn clean test-compile exec:java


