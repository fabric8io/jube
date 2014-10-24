## Example

The following will show you how to run some examples from [fabric8](http://fabric8.io/v2/index.html)

### Prerequisites

We assume you have cloned and built fabric8 and jube and followed the [get started guide](getStarted.html) to get Jube up and running.

The following assumes you have also setup the [Fabric8 Forge Addons](http://fabric8.io/v2/forge.html)

### Using Forge

First lets make sure that you have build fabric8 and created the [image zips](imageZips.html) for the default apps in fabric8

    cd fabric8
    mvn install
    cd apps
    mvn install -Pjube

Now lets start Forge inside the fabric8-mq folder


    cd fabric8/apps/fabric8-mq
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

### Checking whats happening

To check on what happens after each command you can watch the JSON in your browser:

* [http://localhost:8585/api/v1beta1/pods](http://localhost:8585/api/v1beta1/pods) for the pods
* [http://localhost:8585/api/v1beta1/replicationControllers](http://localhost:8585/api/v1beta1/replicationControllers) for replication controllers
* [http://localhost:8585/api/v1beta1/services](http://localhost:8585/api/v1beta1/services) for the services

You can obviously browse the above in hawtio too plus you can use these commands

    kubernetes-pod-list
    kubernetes-replication-controller-list
    kubernetes-service-list

### Looking at the log files

If you look in the **jube/app/processes** folder you should see a folder for each pod (so a broker, producer, consumer) and inside each of those folders you should see some useful files:

* **logs/cmd.out** shows the standard output of the image zip command along with the environment variables passed into the process
* **logs/out.out** standard output of the pod
* **logs/err.log** standard error of the pod
