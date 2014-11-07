## Using Forge Commands

The following will show you how to run some examples from [fabric8](http://fabric8.io/v2/index.html)

### Prerequisites

* follow the [get started guide](getStarted.html) to ensure you have Jube up and running
* setup the [Fabric8 Forge Addons](http://fabric8.io/v2/forge.html)
* clone and build the [fabric8 quickstarts repo](https://github.com/fabric8io/quickstarts)


    git clone https://github.com/fabric8io/quickstarts.git
    cd quickstarts
    mvn install

### Using Forge

Now lets start Forge inside the fabric8-mq folder


    cd quickstarts/apps/fabric8-mq
    forge

Now the following commands all apply various kubernetes json to the Kubernetes environment.

Start a broker (replication controller, pod and its service):

    kubernetes-apply --file  target/classes/kubernetes-extra.json
    kubernetes-apply --file  target/classes/kubernetes.json

Start a producer (replication controller and a pod):

    kubernetes-apply --file  ../fabric8-mq-producer/target/classes/kubernetes.json

Start a consumer (replication controller and a pod):

    kubernetes-apply --file  ../fabric8-mq-consumer/target/classes/kubernetes.json

Order isn't massively crucial; other than that the Fabric8 MQ service must be created first before producer/consumer pods.

