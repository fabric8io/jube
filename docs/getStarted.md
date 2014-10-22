## Get Started

To run jube:

    git clone https://github.com/jubeio/jube.git
    cd jube
    mvn install
    cd app
    mvn test-compile exec:java


Then you can test its running by viewing the kubernetes REST API at: [http://localhost:8585/api/v1beta1/pods](http://localhost:8585/api/v1beta1/pods)

You can then type:

    export KUBERNETES_MASTER=http://localhost:8585/

and run the fabric8 JBoss Forge plugins for kubernetes or use the hawtio for kubernetes.