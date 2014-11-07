## Troubleshooting

There are a few different ways to try figure out whats going on if things don't behave as you expect:

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